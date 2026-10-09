// See LICENSE for license details.
#ifndef INTERVALTRACE_DECODER_H
#define INTERVALTRACE_DECODER_H

#include <cstddef>
#include <cstdint>
#include <deque>
#include <functional>
#include <utility>
#include <stdexcept>
#include <vector>

struct intervaltrace_stats {
  uint64_t epochs = 0;
  uint64_t intervals = 0;
  uint64_t basic_blocks = 0;
  uint64_t partial_basic_blocks = 0;
  uint64_t resets = 0;
  uint64_t instructions = 0;
  uint64_t cycles = 0;
};

/** Incremental framing and interval/snapshot checks, independent of DMA pulls.
 * This validates the raw stream; feature construction belongs to host workers.
 */
class intervaltrace_decoder {
public:
  using record_callback = std::function<void(unsigned, unsigned, uint64_t, const std::vector<uint64_t> &)>;
  intervaltrace_decoder(unsigned counters, uint64_t catalog_id, record_callback callback = {})
      : counters(counters), catalog_id(catalog_id), callback(std::move(callback)) {
    check(counters != 0, "empty PMU catalog");
  }

  void push(const uint8_t *data, size_t size) {
    buffered.insert(buffered.end(), data, data + size);
    size_t offset = 0;
    while (buffered.size() - offset >= 8) {
      const uint64_t header = word(buffered.data() + offset);
      const unsigned kind = header & 255;
      check(((header >> 8) & 255) == 1, "unsupported format version");
      check(kind >= 1 && kind <= 4, "unknown record type");
      const uint64_t payload = header >> 32;
      check(payload == (kind == 2 ? 4ull + counters : 4ull), "invalid payload length");
      const size_t bytes = ((payload + 1 + 7) / 8) * 64;
      if (buffered.size() - offset < bytes)
        break;
      std::vector<uint64_t> words;
      for (size_t i = 0; i < payload; ++i)
        words.push_back(word(buffered.data() + offset + 8 * (i + 1)));
      for (size_t i = (payload + 1) * 8; i < bytes; ++i)
        check(buffered[offset + i] == 0, "nonzero record padding");
      push_record(kind, (header >> 16) & 65535, words);
      offset += bytes;
    }
    buffered.erase(buffered.begin(), buffered.begin() + offset);
  }

  // For an already decoded transport (e.g. CSV). Use either this API or push()
  // for one stream; both execute the same semantic checks and callback.
  void push_record(unsigned kind, unsigned flags, const std::vector<uint64_t> &words) {
    check(kind >= 1 && kind <= 4 && flags <= 65535, "invalid record type or flags");
    check(words.size() == (kind == 2 ? 4ull + counters : 4ull), "invalid payload length");
    consume(kind, flags, words);
    if (callback) callback(kind, flags, epoch, words);
  }

  void finish() const {
    check(buffered.empty(), "truncated final packet");
    check(!in_epoch, "capture missing epoch end");
    check(pending_boundaries.empty(), "unmatched PMU snapshots");
  }
  uint64_t completed_intervals() const { return totals.intervals; }
  const intervaltrace_stats &stats() const { return totals; }

private:
  static void check(bool condition, const char *message) {
    if (!condition)
      throw std::runtime_error(message);
  }
  static uint64_t word(const uint8_t *data) {
    uint64_t result = 0;
    for (unsigned i = 0; i < 8; ++i)
      result |= uint64_t(data[i]) << (8 * i);
    return result;
  }

  void consume(unsigned kind, unsigned flags, const std::vector<uint64_t> &w) {
    if (kind == 3) {
      check(!in_epoch && flags == 0, "invalid epoch start");
      check(w[0] == epoch + 1 && w[1] != 0, "invalid epoch identity or interval length");
      check(w[2] == counters && w[3] == catalog_id, "PMU catalog mismatch");
      epoch = w[0];
      in_epoch = true;
      have_baseline = have_final = false;
      retired = bb_cycles = snapshot_id = snapshot_cycle = snapshot_retired = physical_retired = 0;
      pending_boundaries.clear();
      return;
    }
    check(in_epoch, "record outside an epoch");
    if (kind == 2) {
      check((flags & ~(8u | 16u | 32u)) == 0, "invalid PMU flags");
      check(w[3] >= w[1], "physical retirement endpoint precedes logical boundary");
      if (flags & 8) {
        check(flags == 8 && !have_baseline && w[0] == 0 && w[1] == 0 && w[2] == 0 && w[3] == 0,
              "invalid capture baseline");
        have_baseline = true;
      } else {
        check(have_baseline && !have_final, "snapshot without baseline or after final snapshot");
        check(w[0] == snapshot_id + 1, "nonconsecutive snapshot ID");
        check(w[1] >= snapshot_retired && w[2] >= snapshot_cycle && w[3] >= physical_retired,
              "snapshot endpoints went backwards");
        if (flags & 16) {
          have_final = true;
          final_flags = flags & 32;
        } else {
          check(flags == 0 && w[1] > snapshot_retired, "invalid interval snapshot");
          pending_boundaries.push_back({w[1], w[2]});
        }
      }
      snapshot_id = w[0];
      snapshot_retired = w[1];
      snapshot_cycle = w[2];
      physical_retired = w[3];
    } else if (kind == 1) {
      check(have_baseline, "BB without capture baseline");
      check((flags & ~(1u | 2u | 4u | 32u | 64u | 128u | 256u | (7u << 13))) == 0, "invalid BB flags");
      check(w[2] != 0 && retired + w[2] >= retired, "invalid BB instruction count");
      retired += w[2];
      check(bb_cycles + w[3] >= bb_cycles, "BB cycle count overflow");
      bb_cycles += w[3];
      if (flags & 1) {
        check(!pending_boundaries.empty() && pending_boundaries.front().retired == retired,
              "interval boundary has no matching PMU snapshot");
        check(pending_boundaries.front().cycle == bb_cycles,
              "interval BB and PMU timestamps disagree");
        pending_boundaries.pop_front();
        ++totals.intervals;
      }
      check(pending_boundaries.empty() || pending_boundaries.front().retired > retired,
            "PMU endpoint passed without terminate_interval");
      ++totals.basic_blocks;
      // A record with both partial flags is counted once.
      if (flags & (2u | 4u))
        ++totals.partial_basic_blocks;
    } else {
      check((flags & ~32u) == 0 && flags == final_flags, "invalid epoch end flags");
      check(have_final && pending_boundaries.empty(), "epoch ended with missing snapshots or boundaries");
      check(w[0] == epoch && w[1] == retired && w[1] == snapshot_retired && w[1] == physical_retired,
            "epoch retirement totals disagree");
      check(w[2] == snapshot_cycle && bb_cycles <= w[2] && w[3] == snapshot_id,
            "epoch timing or snapshot totals disagree");
      ++totals.epochs;
      totals.instructions += retired;
      totals.cycles += w[2];
      if (flags & 32u)
        ++totals.resets;
      in_epoch = false;
    }
  }

  intervaltrace_stats totals;
  const unsigned counters;
  const uint64_t catalog_id;
  const record_callback callback;
  std::vector<uint8_t> buffered;
  struct boundary { uint64_t retired, cycle; };
  std::deque<boundary> pending_boundaries;
  bool in_epoch = false, have_baseline = false, have_final = false;
  unsigned final_flags = 0;
  uint64_t epoch = 0, retired = 0, bb_cycles = 0;
  uint64_t snapshot_id = 0, snapshot_cycle = 0, snapshot_retired = 0, physical_retired = 0;
};
#endif
