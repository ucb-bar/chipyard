// See LICENSE for license details

#include "cospike.h"
#include "bridges/cospike/thread_pool.h"
#include "cospike_impl.h"

#include <assert.h>
#include <filesystem>
#include <iostream>
#include <limits.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <zlib.h>

/* #define DEBUG */
#define THROUGHPUT_TESTING

char cospike_t::KIND;

/**
 * Constructor for cospike
 */
cospike_t::cospike_t(simif_t &sim,
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
                     uint32_t stream_depth)
    : streaming_bridge_driver_t(sim, stream, &KIND), args(args), _isa(isa),
      _priv(priv), _pmp_regions(pmp_regions), _maxpglevels(maxpglevels),
      _mem0_base(mem0_base), _mem0_size(mem0_size), _mem1_base(mem1_base),
      _mem1_size(mem1_size), _mem2_base(mem2_base), _mem2_size(mem2_size),
      _nharts(nharts), _bootrom(bootrom), _hartid(hartid),
      _num_commit_insts(num_commit_insts), _bits_per_trace(bits_per_trace),
      stream_idx(stream_idx), stream_depth(stream_depth) {

  this->_trace_cfg.init(8,
                        1,
                        TO_BYTES(iaddr_width),
                        TO_BYTES(insn_width),
                        1,
                        1,
                        TO_BYTES(cause_width),
                        TO_BYTES(wdata_width),
                        1,
                        bits_per_trace,
                        hartid);
  this->cospike_failed = false;
  this->cospike_exit_code = 0;

  const std::string cospiketrace_arg = std::string("+cospike-trace=");
  for (auto &arg : args) {
    if (arg.find(cospiketrace_arg) == 0) {
      char *str = const_cast<char *>(arg.c_str()) + cospiketrace_arg.length();
      int num_threads = atol(str);
      this->_trace_printers.start(num_threads);

      size_t max_input_bytes = stream_depth * STREAM_WIDTH_BYTES;
      size_t buffer_bytes =
          num_threads * max_input_bytes; // based on perf experiments
      this->_trace_mempool =
          new mempool_t(num_threads, buffer_bytes, max_input_bytes);

      std::filesystem::create_directory("COSPIKE-TRACES");

      FILE *config_file = fopen("COSPIKE-CONFIG", "w");
      fprintf(config_file,
              "num_threads: %d uncompressed_buffer_bytes: %lu\n",
              num_threads,
              buffer_bytes);
      fclose(config_file);

      FILE *bootrom_file = fopen("FIRESIM-BOOTROM", "w");
      fprintf(bootrom_file, "%s\n", bootrom);
      fclose(bootrom_file);
    }
  }
}

/**
 * Setup simulation and initialize cospike cosimulation
 */
void cospike_t::init() {
  printf("[INFO] Cospike: Attached cospike to a single instruction trace with "
         "%d instructions.\n",
         this->_num_commit_insts);

  cospike_set_sysinfo((char *)this->_isa,
                      (char *)this->_priv,
                      this->_pmp_regions,
                      this->_maxpglevels,
                      this->_mem0_base,
                      this->_mem0_size,
                      this->_mem1_base,
                      this->_mem1_size,
                      this->_mem2_base,
                      this->_mem2_size,
                      this->_nharts,
                      (char *)this->_bootrom,
                      this->args);
}

/**
 * Call cospike co-sim functions with an aligned buffer.
 * This returns the return code of the co-sim functions.
 */
int cospike_t::invoke_cospike(uint8_t *buf) {
  trace_cfg_t &cfg = this->_trace_cfg;
  uint64_t time = EXTRACT_ALIGNED(
      int64_t, uint64_t, buf, cfg._time_width, cfg._time_offset);
  bool valid = buf[cfg._valid_offset];
  // this crazy to extract the right value then sign extend within the size
  uint64_t iaddr = EXTRACT_ALIGNED(int64_t,
                                   uint64_t,
                                   buf,
                                   cfg._iaddr_width,
                                   cfg._iaddr_offset); // aka the pc
  uint32_t insn = EXTRACT_ALIGNED(
      int32_t, uint32_t, buf, cfg._insn_width, cfg._insn_offset);
  bool exception = buf[cfg._exception_offset];
  bool interrupt = buf[cfg._interrupt_offset];
  uint64_t cause = EXTRACT_ALIGNED(
      int64_t, uint64_t, buf, cfg._cause_width, cfg._cause_offset);
  uint64_t wdata =
      cfg._wdata_width != 0
          ? EXTRACT_ALIGNED(
                int64_t, uint64_t, buf, cfg._wdata_width, cfg._wdata_offset)
          : 0;
  uint8_t priv = buf[cfg._priv_offset];

#ifdef DEBUG
  fprintf(stderr,
          "C[%d] V(%d) PC(0x%lx) Insn(0x%x) EIC(%d:%d:%ld) Wdata(%d:0x%lx) "
          "Priv(%d)\n",
          this->_hartid,
          valid,
          iaddr,
          insn,
          exception,
          interrupt,
          cause,
          (this->_wdata_width != 0),
          wdata,
          priv);
#endif

  if (valid || exception || cause) {
    return cospike_cosim(time, // TODO: No cycle given
                         this->_hartid,
                         (cfg._wdata_width != 0),
                         valid,
                         iaddr,
                         insn,
                         exception,
                         interrupt,
                         cause,
                         wdata,
                         priv);
  } else {
    return 0;
  }
}

size_t cospike_t::record_trace(size_t max_batch_bytes, size_t min_batch_bytes) {
  assert(!_trace_mempool->full());
  size_t bytes_received = pull(stream_idx,
                               _trace_mempool->next_empty(),
                               max_batch_bytes,
                               min_batch_bytes);
  if (bytes_received > 0) {
    _trace_mempool->fill(bytes_received);

    // if the buffer is full, push it to the threadpool
    if (_trace_mempool->full()) {
      while (_trace_mempool->next_buffer_full()) {
        ;
      }
      std::string ofname = "COSPIKE-TRACES/COSPIKE-TRACE-" +
                           std::to_string(this->_hartid) + "-" +
                           std::to_string(this->_file_idx++) + ".gz";
      trace_t trace = {_trace_mempool->cur_buf(), this->_trace_cfg};
      _trace_printers.queue_job(print_insn_logs, trace, ofname);
      _trace_mempool->advance_buffer();
    }
  }
  return bytes_received;
}

size_t cospike_t::run_cosim(size_t max_batch_bytes, size_t min_batch_bytes) {
  // TODO: as opt can mmap file and just load directly into it.
  page_aligned_sized_array(OUTBUF, max_batch_bytes);
  size_t bytes_received =
      pull(stream_idx, OUTBUF, max_batch_bytes, min_batch_bytes);

  const size_t bytes_per_trace = this->_bits_per_trace / 8;

  for (uint32_t offset = 0; offset < bytes_received;
       offset += bytes_per_trace) {
#ifdef DEBUG
    fprintf(stderr,
            "Off(%d/%ld:%lu) token(",
            offset,
            bytes_received,
            offset / bytes_per_trace);

    for (int32_t i = STREAM_WIDTH_BYTES - 1; i >= 0; --i) {
      fprintf(stderr, "%02x", (OUTBUF + offset)[i]);
      if (i == bytes_per_trace)
        fprintf(stderr, " ");
    }
    fprintf(stderr, ")\n");
#endif

    // invoke cospike (requires that buffer is aligned properly)
    int rval = this->invoke_cospike(((uint8_t *)OUTBUF) + offset);
    if (rval) {
      cospike_failed = true;
      cospike_exit_code = rval;
      printf("[ERROR] Cospike: Errored during simulation with %d\n", rval);

#ifdef DEBUG
      fprintf(stderr, "Off(%lu) token(", offset / bytes_per_trace);

      for (int32_t i = STREAM_WIDTH_BYTES - 1; i >= 0; --i) {
        fprintf(stderr, "%02x", (OUTBUF + offset)[i]);
        if (i == bytes_per_trace)
          fprintf(stderr, " ");
      }
      fprintf(stderr, ")\n");

      fprintf(stderr, "get_next_token token(");
      auto next_off = offset + STREAM_WIDTH_BYTES;

      for (auto i = STREAM_WIDTH_BYTES - 1; i >= 0; --i) {
        fprintf(stderr, "%02x", (OUTBUF + next_off)[i]);
        if (i == bytes_per_trace)
          fprintf(stderr, " ");
      }
      fprintf(stderr, ")\n");
#endif

      break;
    }
  }
  return bytes_received;
}

/**
 * Read queue and co-simulate
 */
size_t cospike_t::process_tokens(int num_beats, size_t minimum_batch_beats) {
  const size_t maximum_batch_bytes = num_beats * STREAM_WIDTH_BYTES;
  const size_t minimum_batch_bytes = minimum_batch_beats * STREAM_WIDTH_BYTES;

  size_t bytes_received;
  if (this->_trace_mempool) {
    bytes_received = record_trace(maximum_batch_bytes, minimum_batch_bytes);
  } else {
    bytes_received = run_cosim(maximum_batch_bytes, minimum_batch_bytes);
  }
  return bytes_received;
}

/**
 * Move forward the simulation
 */
void cospike_t::tick() {
  this->process_tokens(this->stream_depth, this->stream_depth);
}

/**
 * Pull in any remaining tokens and use them (if the simulation hasn't already
 * failed)
 */
void cospike_t::flush() {
  // only flush if there wasn't a failure before
  while (!cospike_failed && (this->process_tokens(this->stream_depth, 0) > 0))
    ;

  if (this->_trace_mempool)
    this->_trace_printers.stop();
}
