// See LICENSE for license details.
#ifndef INTERVALTRACE_H
#define INTERVALTRACE_H

#include "core/bridge_driver.h"
#include "intervaltrace_decoder.h"
#include "intervaltrace_aggregate.h"
#include <cstdio>
#include <string>
#include <vector>

struct INTERVALTRACEBRIDGEMODULE_struct {
  uint64_t init_done;
  uint64_t capture_enable;
  uint64_t interval_low;
  uint64_t interval_high;
  uint64_t active;
  uint64_t drained;
  uint64_t config_error;
  uint64_t trigger_selector;
  uint64_t trigger_start_low;
  uint64_t trigger_start_high;
  uint64_t trigger_end_low;
  uint64_t trigger_end_high;
};

class intervaltrace_t final : public streaming_bridge_driver_t {
public:
  static char KIND;
  intervaltrace_t(simif_t &sim, StreamEngine &stream,
                  const INTERVALTRACEBRIDGEMODULE_struct &addrs, int index,
                  const std::vector<std::string> &args, int stream_idx,
                  int stream_depth, unsigned counters, uint64_t catalog_id,
                  const char *manifest_json, const std::vector<std::string> &event_names);
  ~intervaltrace_t() override;
  void init() override;
  void tick() override;
  void finish() override;
  bool terminate() override { return failed; }
  int exit_code() override { return failed ? 1 : 0; }

private:
  size_t drain(size_t minimum_bytes);
  void write_features(const intervaltrace_features &features);
  void fail(const char *message);
  void write_record(unsigned kind, unsigned flags, uint64_t epoch, const std::vector<uint64_t> &words);
  const INTERVALTRACEBRIDGEMODULE_struct addrs;
  const int stream_idx;
  const int stream_depth;
  intervaltrace_aggregator aggregate;
  intervaltrace_decoder decoder;
  uint64_t nominal_interval = 1000000;
  uint64_t bytes_received = 0;
  uint64_t record_id = 0;
  bool dump_raw = false;
  bool dump_csv = false;
  uint32_t trigger_selector = 0;
  uint64_t trigger_start = 0, trigger_end = UINT64_MAX;
  bool enabled = true, failed = false, finished = false;
  std::string prefix;
  std::string manifest;
  const std::vector<std::string> event_names;
  std::map<intervaltrace_block_key, uint64_t> block_ids;
  FILE *block_catalog_output = nullptr;
  FILE *interval_output = nullptr;
  FILE *raw_output = nullptr;
  FILE *bb_output = nullptr;
  FILE *pmu_output = nullptr;
  FILE *epoch_output = nullptr;
};
#endif
