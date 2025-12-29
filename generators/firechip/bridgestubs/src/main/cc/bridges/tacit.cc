// See LICENSE for license details

#include "tacit.h"
#include "core/simif.h"

#include <fcntl.h>
#include <sys/stat.h>

#include <stdio.h>
#include <stdlib.h>
#include <array>
#include <errno.h>
#include <string.h>
#include <unistd.h>

// #define DEBUG

char tacit_t::KIND;

class tacit_handler_impl final : public tacit_handler {
public:
  tacit_handler_impl(int tacitno, int tacitlogfd);
  ~tacit_handler_impl() override;

protected:
  int tacitlogfd;
  static constexpr size_t BUFFER_SIZE = 4096;
  std::array<uint8_t, BUFFER_SIZE> buffer{};
  size_t buffered_bytes = 0;

  void put(uint8_t data) override;
  void flush_buffer();
};

static void fatal_io_error(int fd, const char *operation) {
  fprintf(stderr,
          "TACIT log fd %d: failed to %s: %s\n",
          fd,
          operation,
          strerror(errno));
  abort();
}

tacit_handler_impl::tacit_handler_impl(int tacitno, int tacitlogfd) {
  (void)tacitno;
  this->tacitlogfd = tacitlogfd;
}

tacit_handler_impl::~tacit_handler_impl() {
  if (buffered_bytes) {
    flush_buffer();
  }
  while (::close(tacitlogfd) == -1) {
    if (errno == EINTR) {
      continue;
    }
    fatal_io_error(tacitlogfd, "close");
  }
}

void tacit_handler_impl::put(uint8_t data) {
  buffer[buffered_bytes++] = data;
  if (buffered_bytes == BUFFER_SIZE) {
    flush_buffer();
  }
  #ifdef DEBUG
    fprintf(stderr, "TACIT%d: %02x\n", tacitlogfd, data);
  #endif
}

void tacit_handler_impl::flush_buffer() {
  size_t offset = 0;
  while (offset < buffered_bytes) {
    ssize_t bytes_written =
        ::write(tacitlogfd, buffer.data() + offset, buffered_bytes - offset);
    if (bytes_written > 0) {
      offset += static_cast<size_t>(bytes_written);
      continue;
    }
    if (bytes_written == -1 && errno == EINTR) {
      continue;
    }
    fatal_io_error(tacitlogfd, "write");
  }
  buffered_bytes = 0;
}

static std::unique_ptr<tacit_handler>
create_handler(const std::vector<std::string> &args, int tacitno) {
  // open a new file for dumping the bytes
  std::string tacitlogname = std::string("tacit") + std::to_string(tacitno) + ".out";
  // create if non-existent, truncate if exists
  int tacitlogfd = open(tacitlogname.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0644);
  if (tacitlogfd == -1) {
    fprintf(stderr, "Failed to open TACIT%d log file: %s\n", tacitno, tacitlogname.c_str());
    return nullptr;
  }
  return std::make_unique<tacit_handler_impl>(tacitno, tacitlogfd);
}

tacit_t::tacit_t(simif_t &simif,
                 StreamEngine &stream,
                 int tacitno,
                 const std::vector<std::string> &args,
                 int stream_idx,
                 int stream_depth)
    : streaming_bridge_driver_t(simif, stream, &KIND),
      handler(create_handler(args, tacitno)),
      stream_idx(stream_idx),
      stream_depth(stream_depth) {}

tacit_t::~tacit_t() = default;

void tacit_t::tick() {
  drain_stream(STREAM_WIDTH_BYTES);
}

void tacit_t::finish() {
  pull_flush(stream_idx);
  drain_stream(0);
  handler->flush_buffer();
}

void tacit_t::drain_stream(size_t minimum_batch_bytes) {
  const size_t max_batch_bytes =
      static_cast<size_t>(stream_depth) * STREAM_WIDTH_BYTES;
  if (!handler || stream_depth == 0 || max_batch_bytes == 0) {
    return;
  }

  page_aligned_sized_array(outbuf, STREAM_WIDTH_BYTES * stream_depth);

  size_t min_bytes = minimum_batch_bytes;
  while (true) {
    const size_t bytes_received =
        pull(stream_idx, outbuf, max_batch_bytes, min_bytes);
    if (bytes_received == 0) {
      break;
    }

    auto *byte_buf = reinterpret_cast<uint8_t *>(outbuf);
    for (size_t i = 0; i < bytes_received; ++i) {
      handler->put(byte_buf[i]);
    }

    if (bytes_received < max_batch_bytes && min_bytes == 0) {
      break;
    }

    min_bytes = 0;
  }
}
