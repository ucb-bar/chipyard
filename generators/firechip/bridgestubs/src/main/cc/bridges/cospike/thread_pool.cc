#include "thread_pool.h"
#include <algorithm>
#include <inttypes.h>
#include <zlib.h>

void print_insn_logs(trace_t trace, const std::string &oname) {
  gzFile trace_file = gzopen(oname.c_str(), "wb");
  trace_cfg_t &cfg = trace.cfg;
  uint8_t *buf = trace.buf->get_data();
  size_t buf_bytes = trace.buf->bytes();

  const size_t bytes_per_trace = cfg._bits_per_trace / 8;

  for (uint32_t offset = 0; offset < buf_bytes; offset += bytes_per_trace) {
    uint8_t *cur_buf = buf + offset;
    uint64_t time = EXTRACT_ALIGNED(
        int64_t, uint64_t, cur_buf, cfg._time_width, cfg._time_offset);
    bool valid = cur_buf[cfg._valid_offset];
    // this crazy to extract the right value then sign extend within the size
    uint64_t iaddr = EXTRACT_ALIGNED(int64_t,
                                     uint64_t,
                                     cur_buf,
                                     cfg._iaddr_width,
                                     cfg._iaddr_offset); // aka the pc
    uint32_t insn = EXTRACT_ALIGNED(
        int32_t, uint32_t, cur_buf, cfg._insn_width, cfg._insn_offset);
    bool exception = cur_buf[cfg._exception_offset];
    bool interrupt = cur_buf[cfg._interrupt_offset];
    uint64_t cause = EXTRACT_ALIGNED(
        int64_t, uint64_t, cur_buf, cfg._cause_width, cfg._cause_offset);
    bool has_w = cfg._wdata_width != 0;
    uint64_t wdata = cfg._wdata_width != 0 ? EXTRACT_ALIGNED(int64_t,
                                                             uint64_t,
                                                             cur_buf,
                                                             cfg._wdata_width,
                                                             cfg._wdata_offset)
                                           : 0;
    uint8_t priv = cur_buf[cfg._priv_offset];

    if (valid || exception || cause) {
      gzprintf(trace_file,
               "%ld %lu %lx %d %d %d %d %d %lx\n",
               cfg._hartid,
               time,
               iaddr,
               valid,
               exception,
               interrupt,
               (cfg._wdata_width != 0),
               (int)cause,
               wdata);
    }
  }
  gzclose(trace_file);
  trace.buf->clear();
}

void print_buf(buffer_t *buf, const std::string &ofname) {
  FILE *fp = fopen(ofname.c_str(), "w");
  uint64_t *data = (uint64_t *)buf->get_data();
  for (size_t i = 0; i < buf->bytes() / 8; i += 16) {
    for (size_t j = 0; j < std::min(buf->bytes() / 8 - i, (size_t)16); j++) {
      fprintf(fp, "%3" PRIu64 ",", data[i + j]);
    }
    fprintf(fp, "\n");
  }
  fclose(fp);
  buf->clear();
}
