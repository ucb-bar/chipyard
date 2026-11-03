// See LICENSE for license details

#include "ctc.h"
#include "core/simif.h"

#include <fcntl.h>
#include <poll.h>
#include <sys/stat.h>

#include <cassert>
#include <cerrno>
#include <cstring>
#include <utility>

#include <stdio.h>
#include <stdlib.h>

#include <unistd.h>

char ctc_t::KIND;

#define TOKENS_PER_BIGTOKEN 7

#define SIMLATENCY_BT (this->LINKLATENCY / TOKENS_PER_BIGTOKEN)

#define BUFWIDTH streaming_bridge_driver_t::STREAM_WIDTH_BYTES
#define BUFBYTES (SIMLATENCY_BT * BUFWIDTH)
#define EXTRABYTES 1 // Taken from NIC, leaving for future error checking

namespace {
void set_nonblocking(int fd) {
  const int flags = fcntl(fd, F_GETFL);
  if (flags == -1 || fcntl(fd, F_SETFL, flags | O_NONBLOCK) == -1) {
    perror("[CTC] fcntl");
    exit(1);
  }
}

int open_fifo(const std::string &path, int flags) {
  for (;;) {
    const int fd = open(path.c_str(), flags);
    if (fd >= 0) return fd;
    if (errno == EINTR) continue;
    fprintf(stderr, "[CTC] cannot open FIFO %s: %s\n", path.c_str(), strerror(errno));
    exit(1);
  }
}

int open_fifo_writer(const std::string &path) {
  // A reader can open a FIFO without a writer when O_NONBLOCK is set. Wait for
  // the peer to do so, instead of making both chips depend on open order.
  for (unsigned attempt = 0; attempt < 300000; ++attempt) {
    const int fd = open(path.c_str(), O_WRONLY | O_NONBLOCK);
    if (fd >= 0) return fd;
    if (errno == EINTR) continue;
    if (errno == ENOENT || errno == ENXIO) {
      usleep(1000);
      continue;
    }
    fprintf(stderr, "[CTC] cannot open FIFO %s: %s\n", path.c_str(), strerror(errno));
    exit(1);
  }
  fprintf(stderr, "[CTC] timed out waiting for FIFO reader %s\n", path.c_str());
  exit(1);
}

// Both peers send a batch before consuming the other peer's batch. Progress
// both directions together so the transfer works even when a FIFO is smaller
// than the batch, or a read/write returns only part of the requested bytes.
bool exchange_batch(int send_fd, int recv_fd, const char *send_buf,
                    char *recv_buf, size_t bytes) {
  size_t sent = 0;
  size_t received = 0;
  while (sent != bytes || received != bytes) {
    struct pollfd fds[2] = {
        {send_fd, static_cast<short>(sent != bytes ? POLLOUT : 0), 0},
        {recv_fd, static_cast<short>(received != bytes ? POLLIN : 0), 0},
    };
    if (poll(fds, 2, -1) == -1) {
      if (errno == EINTR) continue;
      perror("[CTC] poll");
      exit(1);
    }
    if (fds[0].revents & POLLOUT) {
      const ssize_t n = write(send_fd, send_buf + sent, bytes - sent);
      if (n > 0) sent += n;
      else if (n == -1 && errno == EPIPE) return false;
      else if (n == -1 && errno != EINTR && errno != EAGAIN) {
        perror("[CTC] write");
        exit(1);
      }
    }
    if (fds[1].revents & POLLIN) {
      const ssize_t n = read(recv_fd, recv_buf + received, bytes - received);
      if (n > 0) received += n;
      else if (n == 0) return false;
      else if (errno != EINTR && errno != EAGAIN) {
        perror("[CTC] read");
        exit(1);
      }
    }
    if ((sent != bytes && (fds[0].revents & (POLLERR | POLLHUP | POLLNVAL))) ||
        (received != bytes && (fds[1].revents & (POLLERR | POLLHUP | POLLNVAL)))) {
      return false;
    }
  }
  return true;
}
} // namespace

ctc_t::ctc_t(simif_t &simif,
              HostStreamEngine &stream,
              const CTCBRIDGEMODULE_struct &mmio_addrs,
              int chipno, 
              const std::vector<std::string> &args,
              int stream_to_cpu_idx,
              int stream_to_cpu_depth,
              int stream_from_cpu_idx,
              int stream_from_cpu_depth)
    : streaming_bridge_driver_t(simif, stream, &KIND), mmio_addrs(mmio_addrs),
      stream_to_cpu_idx(stream_to_cpu_idx),
      stream_from_cpu_idx(stream_from_cpu_idx) {

  // Read plusargs
  std::string macaddr_arg = std::string("+macaddr") + std::to_string(chipno) + std::string("=");

  // Use macaddr as chip id for now
  for (auto &arg : args) {
    if (arg.find(macaddr_arg) == 0) {
      int mac_octets[6];
      char *macstring = nullptr;
      macstring = const_cast<char *>(arg.c_str()) + macaddr_arg.length();
      char trailingjunk;

      // convert mac address from string to 48 bit int
      if (6 == sscanf(macstring,
                      "%x:%x:%x:%x:%x:%x%c",
                      &mac_octets[0],
                      &mac_octets[1],
                      &mac_octets[2],
                      &mac_octets[3],
                      &mac_octets[4],
                      &mac_octets[5],
                      &trailingjunk)) {
        chip_id = mac_octets[5] - 2;
      } else {
        fprintf(stderr, "INVALID MAC ADDRESS SUPPLIED WITH +macaddrN=\n");
        chip_id = 0;
      }
    }
  }

  if (chip_id < 0) {
    fprintf(stderr, "[CTC] missing +macaddr%d for chip identity\n", chipno);
    exit(1);
  }
  const std::string num_equals = std::to_string(chip_id) + std::string("=");
  const std::string chip1_arg = std::string("+connectid") + num_equals;
  std::string chip1no = "";

  for (auto &arg : args) {
    if(arg.find(chip1_arg) == 0) {
      printf("[CTC] CHIP%d: got connectid argstr%s\n", chip_id, arg.c_str());
      chip1no = const_cast<char *>(arg.c_str()) + chip1_arg.length();
      printf("[CTC] CHIP%d: got connectid %s\n", chip_id, chip1no.c_str());
      chip1_id = std::stoi(chip1no, nullptr, 0);
    }
  }

  const std::string chip0fifo_arg = std::string("+fifofile") + num_equals;
  const std::string chip1fifo_arg = std::string("+fifofile") + chip1no + std::string("=");
  const std::string latency_arg = std::string("+ctclatency") + num_equals;

  fifo0_path = "";
  fifo1_path = "";

  for (auto &arg : args) {
    if(arg.find(chip0fifo_arg) == 0) {
      fifo0_path = const_cast<char *>(arg.c_str()) + chip0fifo_arg.length();
    }
    if(arg.find(chip1fifo_arg) == 0) {
      fifo1_path = const_cast<char *>(arg.c_str()) + chip1fifo_arg.length();
    }
    if(arg.find(latency_arg) == 0) {
      char *str = const_cast<char *>(arg.c_str()) + latency_arg.length();
      this->LINKLATENCY = atoi(str);
    }
  }

  if (chip1_id < 0 || fifo0_path.empty() || fifo1_path.empty() ||
      LINKLATENCY <= 0 || LINKLATENCY % TOKENS_PER_BIGTOKEN != 0 ||
      SIMLATENCY_BT > stream_from_cpu_depth ||
      SIMLATENCY_BT > stream_to_cpu_depth) {
    fprintf(stderr, "[CTC] invalid peer/FIFO/latency arguments for chip %d\n", chip_id);
    exit(1);
  }

  printf("[CTC] CHIP%d: got fifo0 path %s\n", chip_id, fifo0_path.c_str());
  printf("[CTC] CHIP%d: got fifo1 path %s\n", chip_id, fifo1_path.c_str());

  printf("[CTC] Link latency = %d\n", this->LINKLATENCY);

  fifo0_path = fifo0_path + std::string("fifo") + std::to_string(chip_id);
  fifo1_path = fifo1_path + std::string("fifo") + chip1no;

  printf("[CTC] CHIP%d: got fifo0 file %s\n", chip_id, fifo0_path.c_str());
  printf("[CTC] CHIP%d: got fifo1 file %s\n", chip_id, fifo1_path.c_str());

  if (mkfifo(fifo0_path.c_str(), 0666) == -1 && errno != EEXIST) {
    perror("[CTC] mkfifo");
    exit(1);
  }

  // For storing data that is pushed/pulled from the stream
  if (posix_memalign(reinterpret_cast<void **>(&buf), 64, BUFBYTES + EXTRABYTES) != 0 ||
      posix_memalign(reinterpret_cast<void **>(&rx_buf), 64, BUFBYTES + EXTRABYTES) != 0) {
    fprintf(stderr, "[CTC] buffer allocation failed\n");
    exit(1);
  }
  memset(buf, 0, BUFBYTES + EXTRABYTES);
  memset(rx_buf, 0, BUFBYTES + EXTRABYTES);
}

ctc_t::~ctc_t() {
  free(buf);
  free(rx_buf);
}

void ctc_t::init() {
  // Both sides open their receive FIFO first. Nonblocking reader open avoids
  // a rendezvous deadlock when the two FPGA drivers start at different times.
  fifo0_fd = open_fifo(fifo0_path, O_RDONLY | O_NONBLOCK);
  printf("[CTC] CHIP%d: opened rd fifo0\n", chip_id);
  fifo1_fd = open_fifo_writer(fifo1_path);
  printf("[CTC] CHIP%d: opened wr fifo1\n", chip_id);

  assert(fifo0_fd != -1 && "fifofile0 couldn't be opened\n");
  assert(fifo1_fd != -1 && "fifofile1 couldn't be opened\n");
  set_nonblocking(fifo0_fd);
  set_nonblocking(fifo1_fd);
  signal(SIGPIPE, SIG_IGN);

  auto token_bytes_to_send = SIMLATENCY_BT * BUFWIDTH;
  auto token_bytes_produced = this->push(
      stream_from_cpu_idx, buf, token_bytes_to_send, 0);

  if (token_bytes_produced != token_bytes_to_send) {
    printf("FAIL. Could not enqueue big tokens to support the desired sim "
           "latency on init. Required %d, enqueued %lu\n",
           SIMLATENCY_BT,
           token_bytes_produced / BUFWIDTH);
    exit(1);
  }

  printf("[CTC] init complete\n");

}

void ctc_t::tick() {
  // The FPGA can produce CTC batches continuously. Bound work per scheduler
  // tick so block device, TSI, and termination bridges still get serviced.
  for (unsigned processed = 0; processed < 4; ++processed) {
    if (pending_push) {
      const auto pushed = push(stream_from_cpu_idx, buf, BUFBYTES, BUFBYTES);
      if (pushed == 0) {
        return; // H2C DMA/queue backpressure; retry next tick.
      }
      if (pushed != BUFBYTES) {
        fprintf(stderr, "[CTC] partial H2C batch: %zu/%d bytes\n", pushed, BUFBYTES);
        exit(1);
      }
      pending_push = false;
    }
    // Pull from the stream
    uint32_t token_bytes_from_target = 0;
    auto requested_token_bytes = BUFWIDTH * SIMLATENCY_BT; //Number of concatenated tokens * bytes per token
    token_bytes_from_target =
      pull(stream_to_cpu_idx,
        buf,
        requested_token_bytes,
        requested_token_bytes // Copy only if the stream can provide
                              // exactly as many bytes as we want
      );
    if (token_bytes_from_target == 0) {
      // The C2H engine normally writes full 4 KiB pages. This batch is not
      // page aligned, so release its final partial page when the producer has
      // stopped at the batch boundary. Mirror the NIC bridge's flush path.
      if (++empty_pull_polls == flush_poll_limit) {
        pull_flush(stream_to_cpu_idx);
        empty_pull_polls = 0;
      }
      return;
    }
    empty_pull_polls = 0;
    if (token_bytes_from_target != requested_token_bytes) {
      printf("[CTC] Pulling from stream failed. Read %d bytes, expected %d bytes.\n", token_bytes_from_target, requested_token_bytes);
      exit(1);
    }
    if (!peer_disconnected &&
        !exchange_batch(fifo1_fd, fifo0_fd, buf, rx_buf, BUFBYTES + EXTRABYTES)) {
      peer_disconnected = true;
      fprintf(stderr, "[CTC] chip %d peer closed; draining local simulation\n", chip_id);
    }
    if (peer_disconnected) memset(rx_buf, 0, BUFBYTES + EXTRABYTES);
    std::swap(buf, rx_buf);
    pending_push = true;
  }
}

void ctc_t::finish() {
  // Close the fifos
  close(fifo0_fd);
  close(fifo1_fd);
}
