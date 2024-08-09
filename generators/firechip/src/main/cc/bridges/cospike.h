// See LICENSE for license details
#ifndef __COSPIKE_H
#define __COSPIKE_H

#include "bridges/cospike/mem_pool.h"
#include "bridges/cospike/thread_pool.h"
#include "core/bridge_driver.h"
#include <string>
#include <vector>
#include <zlib.h>

class cospike_t : public streaming_bridge_driver_t {
public:
  /// The identifier for the bridge type used for casts.
  static char KIND;

  cospike_t(simif_t &sim,
            StreamEngine &stream,
            int cospikeno,
            std::vector<std::string> &args,
            uint32_t iaddr_width,
            uint32_t insn_width,
            uint32_t cause_width,
            uint32_t wdata_width,
            uint32_t num_commit_insts,
            uint32_t bits_per_trace,
            const char *isa,
            const char *priv,
            uint32_t pmp_regions,
            uint32_t maxpglevels,
            uint64_t mem0_base,
            uint64_t mem0_size,
            uint64_t mem1_base,
            uint64_t mem1_size,
            uint64_t mem2_base,
            uint64_t mem2_size,
            uint32_t nharts,
            const char *bootrom,
            uint32_t hartid,
            uint32_t stream_idx,
            uint32_t stream_depth);

  ~cospike_t() override = default;

  void init() override;
  void tick() override;
  bool terminate() override { return cospike_failed; };
  int exit_code() override { return (cospike_failed) ? cospike_exit_code : 0; };
  void finish() override { this->flush(); };

private:
  size_t record_trace(size_t max_batch_bytes, size_t min_batch_bytes);
  size_t run_cosim(size_t max_batch_bytes, size_t min_batch_bytes);
  int invoke_cospike(uint8_t *buf);
  size_t process_tokens(int num_beats, size_t minimum_batch_beats);
  void flush();

  std::vector<std::string> args;

  trace_cfg_t _trace_cfg;

  const char *_isa;
  const char *_priv;
  uint32_t _pmp_regions;
  uint32_t _maxpglevels;
  uint64_t _mem0_base;
  uint64_t _mem0_size;
  uint64_t _mem1_base;
  uint64_t _mem1_size;
  uint64_t _mem2_base;
  uint64_t _mem2_size;
  uint32_t _nharts;
  const char *_bootrom;
  uint32_t _hartid;

  // other misc members
  uint32_t _num_commit_insts;
  uint32_t _bits_per_trace;
  bool cospike_failed;
  int cospike_exit_code;

  // stream config
  int stream_idx;
  int stream_depth;

  bool _record_trace = false;
  int _file_idx = 0;
  threadpool_t<trace_t, std::string> _trace_printers;
  mempool_t *_trace_mempool = nullptr;
};

#endif // __COSPIKE_H
