// See LICENSE for license details.
#include "intervaltrace.h"
#include "intervaltrace_json.h"
#include <sstream>
#include <cerrno>
#include <charconv>
#include <cinttypes>
#include <cstring>
#include <limits>
#include <stdexcept>

char intervaltrace_t::KIND;

static uint64_t trigger_number(std::string value, int base) {
  if (base == 16 && value.size() > 2 && value[0] == '0' && (value[1] == 'x' || value[1] == 'X'))
    value.erase(0, 2);
  uint64_t number = 0;
  const auto result = std::from_chars(value.data(), value.data() + value.size(), number, base);
  if (value.empty() || result.ec != std::errc{} || result.ptr != value.data() + value.size())
    throw std::runtime_error("invalid interval trigger number: " + value);
  return number;
}

intervaltrace_t::intervaltrace_t(simif_t &sim, StreamEngine &stream,
    const INTERVALTRACEBRIDGEMODULE_struct &addrs, int index,
    const std::vector<std::string> &args, int stream_idx, int stream_depth,
    unsigned counters, uint64_t catalog_id, const char *manifest_json,
    const std::vector<std::string> &event_names)
    : streaming_bridge_driver_t(sim, stream, &KIND), addrs(addrs),
      stream_idx(stream_idx), stream_depth(stream_depth),
      aggregate([this](const intervaltrace_features &features) {
        write_features(features);
      }),
      decoder(counters, catalog_id, [this](unsigned kind, unsigned flags, uint64_t epoch,
                                         const std::vector<uint64_t> &words) {
        aggregate.consume(kind, flags, epoch, words);
        if (dump_csv) write_record(kind, flags, epoch, words);
      }), manifest(manifest_json), event_names(event_names) {
  if (event_names.size() != counters)
    throw std::runtime_error("interval trace event names do not match counter count");
  std::string base = "intervaltrace";
  bool marker_alias = false, have_selector = false, have_start = false, have_end = false;
  std::string start_value, end_value;
  for (const auto &arg : args) {
    const std::string file_arg = "+interval-trace-file=";
    const std::string length_arg = "+interval-trace-interval=";
    const std::string select_arg = "+interval-trace-select=";
    const std::string start_arg = "+interval-trace-start=";
    const std::string end_arg = "+interval-trace-end=";
    if (arg == "+interval-trace-disable")
      enabled = false;
    else if (arg == "+interval-trace-trigger")
      marker_alias = true;
    else if (arg == "+interval-trace-csv")
      dump_csv = true;
    else if (arg == "+interval-trace-raw")
      dump_raw = true;
    else if (arg.compare(0, select_arg.size(), select_arg) == 0) {
      const auto selector = trigger_number(arg.substr(select_arg.size()), 10);
      if (selector > 3) throw std::runtime_error("interval-trace-select must be 0, 1, 2, or 3");
      trigger_selector = selector;
      have_selector = true;
    } else if (arg.compare(0, start_arg.size(), start_arg) == 0) {
      start_value = arg.substr(start_arg.size());
      have_start = true;
    } else if (arg.compare(0, end_arg.size(), end_arg) == 0) {
      end_value = arg.substr(end_arg.size());
      have_end = true;
    } else if (arg.compare(0, file_arg.size(), file_arg) == 0)
      base = arg.substr(file_arg.size());
    else if (arg.compare(0, length_arg.size(), length_arg) == 0) {
      const auto value = arg.substr(length_arg.size());
      if (value.empty() || value.find_first_not_of("0123456789") != std::string::npos)
        throw std::runtime_error("interval-trace-interval must be a positive integer");
      nominal_interval = std::stoull(value);
      if (!nominal_interval)
        throw std::runtime_error("interval-trace-interval must be nonzero");
    }
  }
  if (marker_alias) {
    if (have_selector && trigger_selector != 3)
      throw std::runtime_error("interval-trace-trigger requires selector 3");
    trigger_selector = 3;
  }
  if (trigger_selector == 0 && (have_start || have_end))
    throw std::runtime_error("interval trigger endpoints require a nonzero selector");
  if (trigger_selector == 2 && (!have_start || !have_end))
    throw std::runtime_error("PC trigger requires both interval-trace-start and interval-trace-end");
  if (trigger_selector == 3) {
    trigger_start = UINT64_C(0xffffffff00008013);
    trigger_end = UINT64_C(0xffffffff00010013);
  }
  const int trigger_base = trigger_selector == 1 ? 10 : 16;
  if (have_start) trigger_start = trigger_number(start_value, trigger_base);
  if (have_end) trigger_end = trigger_number(end_value, trigger_base);
  if (trigger_selector == 1 && trigger_end < trigger_start)
    throw std::runtime_error("interval trigger cycle end precedes start");
  if (stream_depth <= 0 || base.empty())
    throw std::runtime_error("invalid interval trace stream depth or filename");
  prefix = base + "-" + std::to_string(index);
}

intervaltrace_t::~intervaltrace_t() {
  for (FILE *file : {block_catalog_output, interval_output, raw_output, bb_output, pmu_output, epoch_output})
    if (file) std::fclose(file);
}

void intervaltrace_t::fail(const char *message) {
  std::fprintf(stderr, "IntervalTrace %s: error: %s\n", prefix.c_str(), message);
  failed = true;
}

void intervaltrace_t::init() {
  if (enabled) {
    auto open_output = [&](const char *suffix) {
      FILE *file = std::fopen((prefix + suffix).c_str(), "wb");
      if (!file)
        throw std::runtime_error("cannot open interval trace output " + prefix + suffix + ": " + std::strerror(errno));
      return file;
    };
    // Publish aggregates only after the complete capture has passed validation
    // and all buffered writes have succeeded. Invalidate a previous run first.
    if (std::remove((prefix + "-intervals.jsonl").c_str()) != 0 && errno != ENOENT)
      throw std::runtime_error("cannot replace interval trace aggregates");
    interval_output = open_output("-intervals.jsonl.partial");
    block_catalog_output = open_output("-blocks.csv");
    if (std::fputs("id,start_pc,end_pc,privilege,partial_start,partial_end\n", block_catalog_output) < 0 ||
        std::fflush(block_catalog_output) != 0)
      throw std::runtime_error("cannot write interval trace block catalog header");
    if (dump_raw) raw_output = open_output(".bin");
    if (dump_csv) {
      bb_output = open_output("-bb.csv");
      pmu_output = open_output("-pmu.csv");
      epoch_output = open_output("-epochs.csv");
      std::fprintf(bb_output, "record,epoch,start_pc,end_pc,instructions,elapsed_cycles,terminate_interval,partial_start,partial_end,privilege,flags\n");
      std::fprintf(pmu_output, "record,epoch,snapshot_id,kind,logical_retired,cycle,physical_retired,flags");
      for (const auto &name : event_names) {
        // Always quote names, including any embedded comma, quote or newline.
        std::fputs(",\"", pmu_output);
        for (char c : name) {
          if (c == '\"') std::fputc('\"', pmu_output);
          std::fputc(c, pmu_output);
        }
        std::fputc('\"', pmu_output);
      }
      std::fputc('\n', pmu_output);
      std::fprintf(epoch_output, "record,epoch,kind,nominal_interval,counter_count,catalog_id,retired,cycles,final_snapshot_id,flags\n");
      if (std::ferror(bb_output) || std::ferror(pmu_output) || std::ferror(epoch_output))
        throw std::runtime_error("cannot write interval trace CSV headers");
    }
    FILE *json = std::fopen((prefix + ".json").c_str(), "w");
    if (!json)
      throw std::runtime_error("cannot open interval trace manifest");
    const bool wrote = std::fwrite(manifest.data(), 1, manifest.size(), json) == manifest.size();
    const bool closed = std::fclose(json) == 0;
    if (!wrote || !closed)
      throw std::runtime_error("cannot write interval trace manifest");
  }
  write(addrs.interval_low, uint32_t(nominal_interval));
  write(addrs.interval_high, uint32_t(nominal_interval >> 32));
  write(addrs.trigger_selector, trigger_selector);
  write(addrs.trigger_start_low, uint32_t(trigger_start));
  write(addrs.trigger_start_high, uint32_t(trigger_start >> 32));
  write(addrs.trigger_end_low, uint32_t(trigger_end));
  write(addrs.trigger_end_high, uint32_t(trigger_end >> 32));
  write(addrs.capture_enable, enabled ? 1 : 0);
  write(addrs.init_done, 1);
}

size_t intervaltrace_t::drain(size_t minimum_bytes) {
  page_aligned_sized_array(buffer, STREAM_WIDTH_BYTES * stream_depth);
  const size_t size = pull(stream_idx, buffer, STREAM_WIDTH_BYTES * stream_depth, minimum_bytes);
  if (size) {
    // Optional raw capture precedes validation so malformed bytes remain inspectable.
    if (raw_output && std::fwrite(buffer, 1, size, raw_output) != size)
      throw std::runtime_error("cannot write interval trace output");
    bytes_received += size;
    decoder.push(reinterpret_cast<const uint8_t *>(buffer), size);
  }
  return size;
}

void intervaltrace_t::write_features(const intervaltrace_features &features) {
  for (const auto &entry : features.blocks) {
    const auto &key = entry.first;
    const auto inserted = block_ids.try_emplace(key, block_ids.size() + 1);
    if (inserted.second &&
        std::fprintf(block_catalog_output, "%" PRIu64 ",0x%" PRIx64 ",0x%" PRIx64 ",%u,%u,%u\n",
                     inserted.first->second, key.start_pc, key.end_pc, key.privilege,
                     !!(key.partial_flags & 2), !!(key.partial_flags & 4)) < 0)
      throw std::runtime_error("cannot write interval trace block definition");
  }
  // Definitions must leave the userspace buffer before records reference them.
  // This is incremental process-crash protection, not power-loss durability.
  if (std::fflush(block_catalog_output) != 0)
    throw std::runtime_error("cannot flush interval trace block catalog");
  std::ostringstream record;
  intervaltrace_write_features(record, features, block_ids);
  const auto data = record.str();
  if (std::fwrite(data.data(), 1, data.size(), interval_output) != data.size() ||
      std::fflush(interval_output) != 0)
    throw std::runtime_error("cannot write or flush interval trace aggregates");
}

void intervaltrace_t::write_record(unsigned kind, unsigned flags, uint64_t epoch,
                                   const std::vector<uint64_t> &w) {
  ++record_id;
  FILE *file = kind == 1 ? bb_output : kind == 2 ? pmu_output : epoch_output;
  std::fprintf(file, "%" PRIu64 ",%" PRIu64 ",", record_id, epoch);
  if (kind == 1) {
    std::fprintf(file, "0x%" PRIx64 ",0x%" PRIx64 ",%" PRIu64 ",%" PRIu64 ",%u,%u,%u,%u,%u\n",
                 w[0], w[1], w[2], w[3], !!(flags & 1), !!(flags & 2), !!(flags & 4), flags >> 13, flags);
  } else if (kind == 2) {
    std::fprintf(file, "%" PRIu64 ",%s,%" PRIu64 ",%" PRIu64 ",%" PRIu64 ",%u",
                 w[0], flags & 8 ? "baseline" : flags & 16 ? "final" : "nominal", w[1], w[2], w[3], flags);
    for (size_t i = 4; i < w.size(); ++i) std::fprintf(file, ",%" PRIu64, w[i]);
    std::fputc('\n', file);
  } else if (kind == 3) {
    std::fprintf(file, "start,%" PRIu64 ",%" PRIu64 ",%" PRIu64 ",,,,%u\n", w[1], w[2], w[3], flags);
  } else {
    std::fprintf(file, "end,,,,%" PRIu64 ",%" PRIu64 ",%" PRIu64 ",%u\n", w[1], w[2], w[3], flags);
  }
  if (std::ferror(file)) throw std::runtime_error("cannot write interval trace CSV record");
}

void intervaltrace_t::tick() {
  if (!enabled || failed || finished)
    return;
  try {
    if (read(addrs.config_error))
      throw std::runtime_error("invalid bridge interval configuration");
    drain(STREAM_WIDTH_BYTES);
  } catch (const std::exception &e) {
    fail(e.what());
  }
}

void intervaltrace_t::finish() {
  if (finished)
    return;
  if (enabled && !failed) {
    try {
      // MMIO stop flushes the widget without requesting another target cycle.
      write(addrs.capture_enable, 0);
      unsigned polls = 0;
      while (!read(addrs.drained)) {
        drain(0);
        if (++polls == 1000000)
          throw std::runtime_error("bridge did not drain after capture stop");
      }
      pull_flush(stream_idx);
      while (drain(0)) {}
      decoder.finish();
    } catch (const std::exception &e) {
      fail(e.what());
    }
  }
  for (FILE **file : {&block_catalog_output, &interval_output, &raw_output, &bb_output, &pmu_output, &epoch_output}) {
    if (*file && std::fclose(*file) != 0)
      fail("cannot close interval trace output");
    *file = nullptr;
  }
  if (enabled && !failed &&
      std::rename((prefix + "-intervals.jsonl.partial").c_str(),
                  (prefix + "-intervals.jsonl").c_str()) != 0)
    fail("cannot publish interval trace aggregates");
  finished = true;
  if (enabled && !failed) {
    const auto &stats = decoder.stats();
    if (stats.basic_blocks == 0)
      std::fprintf(stderr, "IntervalTrace %s: warning: empty capture (no retired instructions recorded)\n",
                   prefix.c_str());
    std::fprintf(stderr,
                 "IntervalTrace %s: epochs=%" PRIu64 ", intervals=%" PRIu64
                 ", BBs=%" PRIu64 " (partial=%" PRIu64 "), resets=%" PRIu64
                 ", instructions=%" PRIu64 ", cycles=%" PRIu64 ", stream_bytes=%" PRIu64 "\n",
                 prefix.c_str(), stats.epochs, stats.intervals, stats.basic_blocks,
                 stats.partial_basic_blocks, stats.resets, stats.instructions, stats.cycles, bytes_received);
  }
  // FireSim captures simulation_run's exit code before calling finish(). A late
  // validation failure must escape here rather than leaving a successful result.
  if (failed)
    throw std::runtime_error("interval trace capture failed; see diagnostic above");
}
