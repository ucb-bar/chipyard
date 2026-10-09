// See LICENSE for license details.

#include "bridges/intervaltrace.h"
#include "bridges/peek_poke.h"
#include "core/simif.h"
#include "core/simulation.h"

#include <gmp.h>

#include <algorithm>
#include <cinttypes>
#include <cstdio>
#include <iomanip>
#include <limits>
#include <map>
#include <random>
#include <sstream>
#include <stdexcept>
#include <string>
#include <tuple>
#include <vector>

// Drives a fixed retirement and PMU sequence into IntervalTraceModule's bridges
// and writes, next to each bridge's output, the files it should produce. The
// expected files come from a software model of the bridge: the widget's
// alignment and capture rules, then the driver's interval aggregation.

namespace {

// Must match IntervalTraceModule.scala and IntervalTraceSuite.scala.
constexpr unsigned IADDR_BITS = 40;
constexpr unsigned increment_bits(unsigned event) {
  constexpr unsigned bits[] = {64, 1, 4, 8};
  return bits[event % 4];
}
constexpr uint64_t START_PC = 0x80000400, END_PC = 0x80000800;
constexpr uint32_t START_MARKER = 0x00008013, END_MARKER = 0x00010013;

// Instructions that end a block.
constexpr uint32_t BEQ = 0x00000063, BNE = 0x00001063, JAL = 0x0000006f, JALR = 0x00000067;
constexpr uint32_t C_J = 0xa001, C_BEQZ = 0xc001, C_BNEZ = 0xe001, C_JR = 0x8082, C_JALR = 0x9082;
constexpr uint32_t SRET = 0x10200073, MRET = 0x30200073;
// Instructions that do not, including encodings close to control flow.
constexpr uint32_t ADDI = 0x00000013, C_ADDI = 0x0001, C_MV = 0x8086;
constexpr uint32_t BRANCH_FUNCT3_2 = 0x00002063, JALR_FUNCT3_1 = 0x00001067, ECALL = 0x00000073;

// Stream record kinds and flags.
constexpr unsigned BB = 1, PMU = 2, EPOCH_START = 3, EPOCH_END = 4;
constexpr unsigned TERMINATE_INTERVAL = 1, PARTIAL_START = 2, PARTIAL_END = 4, BASELINE = 8,
                   FINAL_SNAPSHOT = 16, TARGET_RESET = 32, TRAP = 64, DISCONTINUITY = 128,
                   PRIVILEGE_CHANGE = 256, PRIVILEGE_SHIFT = 13;

uint64_t mask(unsigned bits) { return bits >= 64 ? ~0ull : (1ull << bits) - 1; }
uint64_t sign_extend(uint64_t pc) {
  pc &= mask(IADDR_BITS);
  return (pc >> (IADDR_BITS - 1)) & 1 ? pc | ~mask(IADDR_BITS) : pc;
}
unsigned instruction_bytes(uint32_t insn) { return (insn & 3) == 3 ? 4 : 2; }

bool ends_block(uint32_t insn) {
  const unsigned funct3 = (insn >> 12) & 7, c_funct3 = (insn >> 13) & 7;
  if ((insn & 3) != 3) {
    const bool c_jump_branch = (insn & 3) == 1 && c_funct3 >= 5;
    const bool c_register_jump = (insn & 3) == 2 && c_funct3 == 4 &&
                                 ((insn >> 7) & 31) != 0 && ((insn >> 2) & 31) == 0;
    return c_jump_branch || c_register_jump;
  }
  const unsigned opcode = insn & 127;
  return (opcode == 0x63 && funct3 != 2 && funct3 != 3) || opcode == 0x6f ||
         (opcode == 0x67 && funct3 == 0) || insn == 0x00200073 || insn == SRET ||
         insn == MRET || insn == 0x7b200073;
}

struct slot_t {
  bool valid = false, exception = false, interrupt = false;
  uint64_t pc = 0;
  uint32_t insn = 0;
  unsigned priv = 3;
};

// One target cycle as poked, or one aligned observation as the widget sees it.
struct cycle_t {
  bool reset = false;
  std::vector<slot_t> slots;
  std::vector<uint64_t> increments;
};

class stimulus_t {
public:
  stimulus_t(unsigned lanes, unsigned events) : lanes(lanes), events(events) {}

  // Retire an instruction at the current PC; control flow continues at target.
  void retire(uint32_t insn, uint64_t target = 0) {
    slot_t slot;
    slot.valid = true;
    slot.pc = pc;
    slot.insn = insn;
    slot.priv = priv;
    add(slot);
    pc = target ? target : pc + instruction_bytes(insn);
  }
  void hole() { add(slot_t{}); }
  void trap(bool interrupt) { (interrupt ? pending_interrupt : pending_exception) = true; }
  void jump(uint64_t target) { pc = target; }
  void privilege(unsigned value) { priv = value; }
  void wrap() { wrap_event0 = true; }
  void end_cycle() {
    if (!current.empty() || pending_exception || pending_interrupt)
      flush(false);
  }
  void idle(unsigned count) {
    end_cycle();
    for (unsigned i = 0; i < count; ++i)
      flush(false);
  }
  // Reset cycles carry valid retirements and increments, which must be ignored.
  void reset(unsigned count) {
    end_cycle();
    for (unsigned i = 0; i < count; ++i) {
      for (unsigned lane = 0; lane < lanes; ++lane) {
        slot_t slot;
        slot.valid = true;
        slot.pc = 0x80002000 + 4 * lane;
        slot.insn = lane % 2 ? BEQ : ADDI;
        current.push_back(slot);
      }
      flush(true);
    }
  }
  uint64_t next_random() { return random(); }
  std::vector<cycle_t> cycles;

private:
  void add(const slot_t &slot) {
    if (current.size() == lanes)
      flush(false);
    current.push_back(slot);
  }
  void flush(bool reset) {
    current.resize(lanes);
    if (pending_exception || pending_interrupt) {
      current.back().exception = pending_exception;
      current.back().interrupt = pending_interrupt;
    }
    cycle_t cycle;
    cycle.reset = reset;
    cycle.slots = current;
    unsigned retired = 0;
    for (const auto &slot : current)
      retired += slot.valid;
    for (unsigned event = 0; event < events; ++event) {
      if (event == 0)
        cycle.increments.push_back(wrap_event0 ? ~0ull - 4 : retired);
      else
        cycle.increments.push_back(random() & mask(increment_bits(event)));
    }
    cycles.push_back(cycle);
    current.clear();
    pending_exception = pending_interrupt = wrap_event0 = false;
  }
  const unsigned lanes, events;
  std::mt19937_64 random{7};
  std::vector<slot_t> current;
  uint64_t pc = 0x80000000;
  unsigned priv = 3;
  bool pending_exception = false, pending_interrupt = false, wrap_event0 = false;
};

void random_code(stimulus_t &s, unsigned instructions) {
  static const uint32_t choices[] = {ADDI, ADDI, ADDI, C_ADDI, BEQ, BNE, JAL, JALR, C_J, C_BEQZ,
                                     C_JR, MRET, BRANCH_FUNCT3_2, C_MV};
  for (unsigned i = 0; i < instructions; ++i) {
    const uint64_t r = s.next_random();
    if (r % 16 == 0)
      s.hole();
    if (r % 23 == 1)
      s.privilege((r >> 8) % 2 ? 3 : 0);
    if (r % 29 == 2)
      s.jump(0x80001000 + ((r >> 12) % 0x400) * 4);
    const uint32_t insn = choices[(r >> 20) % (sizeof(choices) / sizeof(choices[0]))];
    // Taken control flow stays clear of the trigger endpoints.
    const bool taken = ends_block(insn) && (r >> 40) % 2;
    s.retire(insn, taken ? 0x80001000 + ((r >> 44) % 0x400) * 4 : 0);
    if (r % 13 == 3)
      s.trap(false);
    if (r % 11 == 4)
      s.idle(1);
  }
}

// Exercises long blocks, holes, compressed and near-control-flow encodings,
// discontinuities, privilege changes, traps, counter wrap, sign-extended PCs,
// reset in and out of capture, and two windows between trigger endpoints.
std::vector<cycle_t> build_stimulus(unsigned lanes, unsigned events) {
  stimulus_t s(lanes, events);
  s.reset(2);
  s.idle(1);
  for (int i = 0; i < 13; ++i)
    s.retire(ADDI);
  s.retire(BEQ);
  s.retire(C_ADDI);
  s.hole();
  s.retire(C_J, 0x80000100);
  s.retire(ADDI);
  s.retire(JAL, 0x80000140);
  s.retire(C_ADDI);
  s.retire(C_BEQZ);
  s.retire(JALR, 0x80000180);
  s.retire(C_JR, 0x800001c0);
  s.retire(C_JALR, 0x80000200);
  s.retire(C_BNEZ);
  s.idle(2);
  s.retire(BRANCH_FUNCT3_2);
  s.retire(JALR_FUNCT3_1);
  s.retire(C_MV);
  s.retire(ECALL);
  s.retire(BNE);
  s.retire(ADDI);
  s.retire(ADDI);
  s.jump(0x80000300);
  s.retire(ADDI);
  s.retire(BEQ);
  s.retire(ADDI);
  s.privilege(1);
  s.retire(ADDI);
  s.retire(SRET, 0x80000340);
  s.privilege(0);
  s.retire(ADDI);
  s.retire(ADDI);
  s.trap(false);
  s.end_cycle();
  s.privilege(3);
  s.retire(ADDI);
  s.end_cycle();
  s.trap(true);
  s.end_cycle();
  s.retire(MRET, 0x80000380);
  s.wrap();
  s.retire(ADDI);
  s.retire(BEQ);
  s.retire(JAL, 0xfffffff000);
  s.retire(ADDI);
  s.retire(C_ADDI);
  s.retire(JAL, START_PC);
  s.retire(START_MARKER);
  for (int i = 0; i < 6; ++i)
    s.retire(ADDI);
  s.retire(BEQ);
  s.retire(JAL, END_PC);
  s.retire(END_MARKER);
  s.retire(BEQ);
  s.retire(ADDI);
  s.retire(ADDI);
  // Target reset with an open block: the epoch closes and capture restarts.
  s.reset(2);
  s.idle(1);
  random_code(s, 40);
  s.retire(JAL, START_PC);
  s.retire(START_MARKER);
  random_code(s, 20);
  // An end endpoint then a start endpoint, in one cycle where lanes allow.
  s.end_cycle();
  s.retire(JAL, END_PC);
  s.retire(END_MARKER);
  s.retire(JAL, START_PC);
  s.retire(START_MARKER);
  random_code(s, 20);
  s.retire(JAL, END_PC);
  s.retire(END_MARKER);
  random_code(s, 10);
  // Close any open epoch: reset leaves nothing for the final host stop.
  s.reset(4);
  return s.cycles;
}

struct record_t {
  unsigned kind, flags;
  uint64_t epoch;
  std::vector<uint64_t> words;
};

struct bridge_config_t {
  unsigned events, latency;
  bool enabled;
  uint64_t interval, start, end;
  uint32_t selector;
  uint64_t catalog_id;
};

// The widget pairs each cycle's increments and reset with the trace retirement
// `latency` cycles later. The first poked trace has no earlier sample.
std::vector<cycle_t> align(const std::vector<cycle_t> &poked, const bridge_config_t &config) {
  std::vector<cycle_t> observations;
  for (size_t c = config.latency; c < poked.size(); ++c) {
    cycle_t observation = poked[c - config.latency];
    observation.slots = poked[c].slots;
    observation.increments.resize(config.events);
    observations.push_back(observation);
  }
  return observations;
}

class bridge_model {
public:
  explicit bridge_model(const bridge_config_t &config)
      : config(config), counters(config.events) {}

  void observe(const cycle_t &observation) {
    const uint64_t index = trigger_cycle;
    bool start_match = false, end_match = false;
    for (const auto &slot : observation.slots) {
      const bool start = matches(slot, config.start), end = matches(slot, config.end);
      start_match |= start;
      if (start || end)
        end_match = end;
    }
    const bool in_window = !window_exhausted && index >= config.start && index <= config.end;
    const bool start_condition = config.selector == 0 || (config.selector == 1 && in_window) ||
                                 (config.selector >= 2 && start_match);
    const bool stop_after = config.selector == 1 ? index >= config.end
                                                 : config.selector >= 2 && end_match;
    if (!active && config.enabled && start_condition && !observation.reset)
      start();
    if (active && observation.reset) {
      stop(TARGET_RESET);
    } else if (active) {
      accept(observation);
      if (stop_after)
        stop(0);
    }
    if (config.selector == 1 && index >= config.end)
      window_exhausted = true;
    if (trigger_cycle != ~0ull)
      ++trigger_cycle;
  }

  void host_stop() {
    if (active)
      stop(0);
  }

  std::vector<record_t> records;

private:
  struct block_state {
    uint64_t start_pc = 0, last_pc = 0, next_pc = 0, instructions = 0, last_boundary_cycle = 0;
    uint64_t retired = 0, progress = 0, snapshot_id = 0;
    unsigned privilege = 0;
    bool have_privilege = false, partial_start = false;
  };

  bool matches(const slot_t &slot, uint64_t endpoint) const {
    if (!slot.valid)
      return false;
    if (config.selector == 2)
      return sign_extend(slot.pc) == endpoint;
    return ((slot.insn ^ uint32_t(endpoint)) & uint32_t(endpoint >> 32)) == 0;
  }

  void emit(unsigned kind, unsigned flags, std::vector<uint64_t> words) {
    records.push_back({kind, flags, epoch, std::move(words)});
  }
  record_t block(const block_state &s, uint64_t end_pc, uint64_t count, uint64_t cycle_end,
                 unsigned flags) const {
    flags |= s.privilege << PRIVILEGE_SHIFT;
    if (s.partial_start)
      flags |= PARTIAL_START;
    return {BB, flags, epoch, {s.start_pc, end_pc, count, cycle_end - s.last_boundary_cycle}};
  }
  std::vector<uint64_t> snapshot(uint64_t id, uint64_t boundary, uint64_t cycle, uint64_t physical,
                                 const std::vector<uint64_t> &values) const {
    std::vector<uint64_t> words{id, boundary, cycle, physical};
    words.insert(words.end(), values.begin(), values.end());
    return words;
  }

  void start() {
    active = true;
    ++epoch;
    nominal = config.interval;
    cycles = 0;
    counters.assign(config.events, 0);
    state = block_state{};
    state.partial_start = true;
    emit(EPOCH_START, 0, {epoch, nominal, config.events, config.catalog_id});
    emit(PMU, BASELINE, snapshot(0, 0, 0, 0, counters));
  }

  void stop(unsigned reason) {
    active = false;
    emit(PMU, FINAL_SNAPSHOT | reason,
         snapshot(state.snapshot_id + 1, state.retired, cycles, state.retired, counters));
    if (state.instructions)
      records.push_back(block(state, state.last_pc, state.instructions, cycles, PARTIAL_END | reason));
    ++state.snapshot_id;
    emit(EPOCH_END, reason, {epoch, state.retired, cycles, state.snapshot_id});
  }

  void accept(const cycle_t &observation) {
    const uint64_t next_cycle = cycles + 1;
    std::vector<uint64_t> next_counters(counters);
    for (unsigned i = 0; i < config.events; ++i)
      next_counters[i] += observation.increments[i];
    std::vector<std::pair<uint64_t, uint64_t>> boundaries; // snapshot ID, logical retirement
    std::vector<record_t> blocks;
    block_state s = state;
    bool trap = false;
    for (const auto &slot : observation.slots) {
      trap |= slot.exception || slot.interrupt;
      const uint64_t pc = sign_extend(slot.pc);
      const bool privilege_changed = s.have_privilege && s.privilege != slot.priv;
      const bool pc_changed = s.instructions != 0 && s.next_pc != pc;
      if (slot.valid && s.instructions != 0 && (privilege_changed || pc_changed)) {
        blocks.push_back(block(s, s.last_pc, s.instructions, next_cycle,
                               PARTIAL_END | (privilege_changed ? PRIVILEGE_CHANGE : DISCONTINUITY)));
        s.instructions = 0;
        s.last_boundary_cycle = next_cycle;
        s.partial_start = true;
      }
      if (!slot.valid)
        continue;
      if (s.instructions == 0)
        s.start_pc = pc;
      s.partial_start = s.partial_start || privilege_changed;
      s.privilege = slot.priv;
      s.have_privilege = true;
      s.last_pc = pc;
      s.next_pc = pc + instruction_bytes(slot.insn);
      ++s.instructions;
      ++s.retired;
      ++s.progress;
      if (ends_block(slot.insn)) {
        const bool closes = s.progress >= nominal;
        blocks.push_back(block(s, pc, s.instructions, next_cycle, closes ? TERMINATE_INTERVAL : 0));
        s.instructions = 0;
        s.last_boundary_cycle = next_cycle;
        s.partial_start = false;
        if (closes) {
          s.progress -= nominal;
          ++s.snapshot_id;
          boundaries.push_back({s.snapshot_id, s.retired});
        }
      }
    }
    if (trap) {
      if (s.instructions != 0) {
        blocks.push_back(block(s, s.last_pc, s.instructions, next_cycle, PARTIAL_END | TRAP));
        s.last_boundary_cycle = next_cycle;
      }
      s.instructions = 0;
      s.partial_start = true;
    }
    // A cycle's PMU snapshots precede its blocks.
    for (const auto &[id, boundary] : boundaries)
      emit(PMU, 0, snapshot(id, boundary, next_cycle, s.retired, next_counters));
    records.insert(records.end(), blocks.begin(), blocks.end());
    cycles = next_cycle;
    counters = next_counters;
    state = s;
  }

  const bridge_config_t config;
  bool active = false, window_exhausted = false;
  uint64_t trigger_cycle = 0, epoch = 0, nominal = 0, cycles = 0;
  std::vector<uint64_t> counters;
  block_state state;
};

FILE *open_output(const std::string &path) {
  FILE *file = std::fopen(path.c_str(), "w");
  if (!file)
    throw std::runtime_error("cannot open expected output " + path);
  return file;
}

// The driver's per-record CSVs (+interval-trace-csv).
void write_records(const std::string &prefix, const std::vector<record_t> &records,
                   const std::vector<std::string> &names) {
  FILE *bb = open_output(prefix + "-bb.csv");
  FILE *pmu = open_output(prefix + "-pmu.csv");
  FILE *epochs = open_output(prefix + "-epochs.csv");
  std::fprintf(bb, "record,epoch,start_pc,end_pc,instructions,elapsed_cycles,terminate_interval,"
                   "partial_start,partial_end,privilege,flags\n");
  std::fprintf(pmu, "record,epoch,snapshot_id,kind,logical_retired,cycle,physical_retired,flags");
  for (const auto &name : names)
    std::fprintf(pmu, ",\"%s\"", name.c_str());
  std::fprintf(pmu, "\n");
  std::fprintf(epochs, "record,epoch,kind,nominal_interval,counter_count,catalog_id,retired,cycles,"
                       "final_snapshot_id,flags\n");
  uint64_t number = 0;
  for (const auto &r : records) {
    const auto &w = r.words;
    ++number;
    if (r.kind == BB) {
      std::fprintf(bb, "%" PRIu64 ",%" PRIu64 ",0x%" PRIx64 ",0x%" PRIx64 ",%" PRIu64 ",%" PRIu64
                       ",%u,%u,%u,%u,%u\n",
                   number, r.epoch, w[0], w[1], w[2], w[3], !!(r.flags & TERMINATE_INTERVAL),
                   !!(r.flags & PARTIAL_START), !!(r.flags & PARTIAL_END), r.flags >> PRIVILEGE_SHIFT,
                   r.flags);
    } else if (r.kind == PMU) {
      const char *kind = r.flags & BASELINE ? "baseline" : r.flags & FINAL_SNAPSHOT ? "final" : "nominal";
      std::fprintf(pmu, "%" PRIu64 ",%" PRIu64 ",%" PRIu64 ",%s,%" PRIu64 ",%" PRIu64 ",%" PRIu64 ",%u",
                   number, r.epoch, w[0], kind, w[1], w[2], w[3], r.flags);
      for (size_t i = 4; i < w.size(); ++i)
        std::fprintf(pmu, ",%" PRIu64, w[i]);
      std::fprintf(pmu, "\n");
    } else if (r.kind == EPOCH_START) {
      std::fprintf(epochs, "%" PRIu64 ",%" PRIu64 ",start,%" PRIu64 ",%" PRIu64 ",%" PRIu64 ",,,,%u\n",
                   number, r.epoch, w[1], w[2], w[3], r.flags);
    } else {
      std::fprintf(epochs, "%" PRIu64 ",%" PRIu64 ",end,,,,%" PRIu64 ",%" PRIu64 ",%" PRIu64 ",%u\n",
                   number, r.epoch, w[1], w[2], w[3], r.flags);
    }
  }
  for (FILE *file : {bb, pmu, epochs})
    std::fclose(file);
}

// Interval JSONL version 2 and its block definitions, from the record stream.
void write_intervals(const std::string &prefix, const std::vector<record_t> &records) {
  using block_key = std::tuple<uint64_t, uint64_t, unsigned, unsigned>;
  struct block_value { uint64_t instructions = 0, cycles = 0, executions = 0; };
  FILE *definitions = open_output(prefix + "-blocks.csv");
  FILE *intervals = open_output(prefix + "-intervals.jsonl");
  std::fprintf(definitions, "id,start_pc,end_pc,privilege,partial_start,partial_end\n");
  std::map<block_key, uint64_t> ids;
  std::map<block_key, block_value> blocks;
  std::vector<uint64_t> previous;
  std::vector<record_t> snapshots;
  uint64_t epoch = 0, nominal = 0, instructions = 0, attributed = 0, partial_blocks = 0;
  unsigned bb_flags = 0;
  auto close_interval = [&](bool final) {
    if (previous.empty() || snapshots.empty())
      throw std::runtime_error("expected interval has no closing snapshot");
    const record_t next = snapshots.front();
    snapshots.erase(snapshots.begin());
    const auto &w = next.words;
    for (const auto &entry : blocks) {
      if (ids.emplace(entry.first, ids.size() + 1).second) {
        const auto &[start_pc, end_pc, privilege, partial] = entry.first;
        std::fprintf(definitions, "%zu,0x%" PRIx64 ",0x%" PRIx64 ",%u,%u,%u\n", ids.size(), start_pc,
                     end_pc, privilege, !!(partial & PARTIAL_START), !!(partial & PARTIAL_END));
      }
    }
    const uint64_t cycles = w[2] - previous[2];
    std::ostringstream line;
    line << "{\"interval_format_version\":2,\"epoch\":" << epoch << ",\"snapshot_id\":" << w[0]
         << ",\"nominal_interval\":" << nominal << ",\"start_retired\":" << previous[1]
         << ",\"end_retired\":" << w[1] << ",\"start_cycle\":" << previous[2] << ",\"end_cycle\":" << w[2]
         << ",\"start_physical_retired\":" << previous[3] << ",\"end_physical_retired\":" << w[3]
         << ",\"instructions\":" << instructions << ",\"cycles\":" << cycles
         << ",\"attributed_cycles\":" << attributed << ",\"unattributed_cycles\":" << cycles - attributed
         << ",\"physical_retirements\":" << w[3] - previous[3] << ",\"partial_blocks\":" << partial_blocks
         << ",\"flags\":" << next.flags << ",\"bb_flags\":" << bb_flags << ",\"ipc\":";
    if (cycles)
      line << std::setprecision(std::numeric_limits<double>::max_digits10)
           << double(instructions) / double(cycles);
    else
      line << "null";
    line << ",\"final\":" << (final ? "true" : "false") << ",\"counter_deltas\":[";
    for (size_t i = 4; i < w.size(); ++i)
      line << (i > 4 ? "," : "") << w[i] - previous[i];
    line << "],\"blocks\":[";
    bool comma = false;
    for (const auto &[key, value] : blocks) {
      line << (comma ? "," : "") << '[' << ids.at(key) << ',' << value.instructions << ','
           << value.cycles << ',' << value.executions << ']';
      comma = true;
    }
    line << "]}\n";
    std::fputs(line.str().c_str(), intervals);
    previous = w;
    blocks.clear();
    instructions = attributed = partial_blocks = 0;
    bb_flags = 0;
  };
  for (const auto &r : records) {
    const auto &w = r.words;
    if (r.kind == EPOCH_START) {
      epoch = w[0];
      nominal = w[1];
    } else if (r.kind == PMU) {
      if (r.flags & BASELINE)
        previous = w;
      else
        snapshots.push_back(r);
    } else if (r.kind == BB) {
      auto &value = blocks[{w[0], w[1], r.flags >> PRIVILEGE_SHIFT, r.flags & (PARTIAL_START | PARTIAL_END)}];
      value.instructions += w[2];
      value.cycles += w[3];
      ++value.executions;
      instructions += w[2];
      attributed += w[3];
      partial_blocks += (r.flags & (PARTIAL_START | PARTIAL_END)) != 0;
      bb_flags |= r.flags;
      if (r.flags & TERMINATE_INTERVAL)
        close_interval(false);
    } else {
      close_interval(true);
    }
  }
  std::fclose(definitions);
  std::fclose(intervals);
}

uint64_t manifest_number(const std::string &manifest, const std::string &field) {
  const auto at = manifest.find("\"" + field + "\":");
  if (at == std::string::npos)
    throw std::runtime_error("interval trace manifest has no " + field);
  size_t begin = at + field.size() + 3;
  if (manifest[begin] == '"')
    ++begin;
  return std::stoull(manifest.substr(begin));
}

} // namespace

class IntervalTraceModule final : public simulation_t {
public:
  IntervalTraceModule(widget_registry_t &registry, const std::vector<std::string> &args)
      : simulation_t(registry, args), peek_poke(registry.get_widget<peek_poke_t>()),
        bridges(registry.get_bridges<intervaltrace_t>()) {}

  int simulation_run() override {
    if (bridges.empty())
      throw std::runtime_error("IntervalTraceModule has no interval trace bridges");
    const unsigned lanes = manifest_number(bridges.front()->manifest_json(), "retire_width");
    unsigned events = 0;
    for (auto *bridge : bridges)
      events = std::max<unsigned>(events, bridge->event_list().size());
    const auto cycles = build_stimulus(lanes, events);

    peek_poke.poke("reset", 1, true);
    for (size_t c = 0; c < cycles.size(); ++c) {
      if (c == 1)
        peek_poke.poke("reset", 0, true);
      poke_cycle(cycles[c]);
      step_cycle();
    }

    for (auto *bridge : bridges) {
      const bridge_config_t config{
          unsigned(bridge->event_list().size()),
          unsigned(manifest_number(bridge->manifest_json(), "retirement_latency")),
          bridge->capture_enabled(),
          bridge->interval_length(),
          bridge->trigger_start_endpoint(),
          bridge->trigger_end_endpoint(),
          bridge->trigger_mode(),
          manifest_number(bridge->manifest_json(), "catalog_id")};
      bridge_model model(config);
      for (const auto &observation : align(cycles, config))
        model.observe(observation);
      model.host_stop();
      const std::string prefix = bridge->output_prefix() + "-expected";
      write_records(prefix, model.records, bridge->event_list());
      write_intervals(prefix, model.records);
    }
    return EXIT_SUCCESS;
  }

private:
  void poke(const std::string &name, uint64_t value) {
    const auto previous = poked.find(name);
    if (previous != poked.end() && previous->second == value)
      return;
    poked[name] = value;
    mpz_t wide;
    mpz_init(wide);
    mpz_import(wide, 1, -1, sizeof(value), 0, 0, &value);
    peek_poke.poke(name, wide);
    mpz_clear(wide);
  }

  void poke_cycle(const cycle_t &cycle) {
    poke("io_trace_reset", cycle.reset);
    poke("io_trace_time", 0);
    for (size_t lane = 0; lane < cycle.slots.size(); ++lane) {
      const auto &slot = cycle.slots[lane];
      const std::string base = "io_trace_retiredinsns_" + std::to_string(lane) + "_";
      poke(base + "valid", slot.valid);
      poke(base + "iaddr", slot.pc & mask(IADDR_BITS));
      poke(base + "insn", slot.insn);
      poke(base + "priv", slot.priv);
      poke(base + "exception", slot.exception);
      poke(base + "interrupt", slot.interrupt);
      poke(base + "cause", 0);
      poke(base + "tval", 0);
    }
    for (size_t event = 0; event < cycle.increments.size(); ++event)
      poke("io_increments_" + std::to_string(event), cycle.increments[event]);
  }

  // One target cycle, servicing the bridges until the step completes.
  void step_cycle() {
    peek_poke.step(1, false);
    for (unsigned i = 0; i < 100000 && !peek_poke.is_done(); ++i)
      for (auto *bridge : registry.get_all_bridges())
        bridge->tick();
    if (!peek_poke.is_done())
      throw std::runtime_error("IntervalTraceModule: target step did not complete");
  }

  peek_poke_t &peek_poke;
  std::vector<intervaltrace_t *> bridges;
  std::map<std::string, uint64_t> poked;
};

std::unique_ptr<simulation_t> create_simulation(simif_t &simif,
                                                widget_registry_t &registry,
                                                const std::vector<std::string> &args) {
  return std::make_unique<IntervalTraceModule>(registry, args);
}
