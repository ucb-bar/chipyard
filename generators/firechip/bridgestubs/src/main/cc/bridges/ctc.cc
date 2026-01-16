// See LICENSE for license details

#include "ctc.h"
#include "core/simif.h"

#include <fcntl.h>
#include <sys/stat.h>

#include <cassert>

#include <stdio.h>
#include <stdlib.h>

#include <unistd.h>

char ctc_t::KIND;

#define TOKENS_PER_BIGTOKEN 7

#define SIMLATENCY_BT (this->LINKLATENCY / TOKENS_PER_BIGTOKEN)

#define BUFWIDTH streaming_bridge_driver_t::STREAM_WIDTH_BYTES
#define BUFBYTES (SIMLATENCY_BT * BUFWIDTH)
#define EXTRABYTES 1 // Taken from NIC, leaving for future error checking

ctc_t::ctc_t(simif_t &simif,
              StreamEngine &stream,
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

  printf("[CTC] CHIP%d: got fifo0 path %s\n", chip_id, fifo0_path.c_str());
  printf("[CTC] CHIP%d: got fifo1 path %s\n", chip_id, fifo1_path.c_str());

  printf("[CTC] Link latency = %d\n", this->LINKLATENCY);

  fifo0_path = fifo0_path + std::string("fifo") + std::to_string(chip_id);
  fifo1_path = fifo1_path + std::string("fifo") + chip1no;

  printf("[CTC] CHIP%d: got fifo0 file %s\n", chip_id, fifo0_path.c_str());
  printf("[CTC] CHIP%d: got fifo1 file %s\n", chip_id, fifo1_path.c_str());

  mkfifo(fifo0_path.c_str(), 0666);

  // For storing data that is pushed/pulled from the stream
  buf = static_cast<char*>(aligned_alloc(64, BUFBYTES + EXTRABYTES));
  memset(buf, 0, BUFBYTES + EXTRABYTES);
}

ctc_t::~ctc_t() {
  free(buf);
}

void ctc_t::init() {
  // Switch order between chips to prevent deadlock
  if (chip_id > chip1_id) {
    fifo0_fd = open(fifo0_path.c_str(), O_RDONLY);
    printf("[CTC] CHIP%d: opened rd fifo0\n", chip_id);
    fifo1_fd = open(fifo1_path.c_str(), O_WRONLY);
    printf("[CTC] CHIP%d: opened wr fifo1\n", chip_id);
  } else {
    fifo1_fd = open(fifo1_path.c_str(), O_WRONLY);
    printf("[CTC] CHIP%d: opened wr fifo1\n", chip_id);
    fifo0_fd = open(fifo0_path.c_str(), O_RDONLY);
    printf("[CTC] CHIP%d: opened rd fifo0\n", chip_id);
  }

  assert(fifo0_fd != -1 && "fifofile0 couldn't be opened\n");
  assert(fifo1_fd != -1 && "fifofile1 couldn't be opened\n");

  // Taken from NIC
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
  while(true) {
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
      return;
    }
    if (token_bytes_from_target != requested_token_bytes) {
      printf("[CTC] Pulling from stream failed. Read %d bytes, expected %d bytes.\n", token_bytes_from_target, requested_token_bytes);
      exit(1);
    }

    // Write entire out buffer to the other chips's fifo
    int bytes_written = ::write(fifo1_fd, buf, BUFBYTES + EXTRABYTES);
    if (bytes_written != BUFBYTES + EXTRABYTES) {
      printf("[CTC] Writing to fifo failed.\n");
      exit(1);
    }

    // Read my own fifo until I read all the "in" chars
    int bytes_read = ::read(fifo0_fd, buf, BUFBYTES + EXTRABYTES);
    if (bytes_read != BUFBYTES + EXTRABYTES) {
      printf("[CTC] Reading from fifo failed.\n");
      exit(1); 
    }

    // Push to the stream
    uint32_t token_bytes_to_target = 0;
    token_bytes_to_target =
      push(stream_from_cpu_idx,
        buf,
        BUFWIDTH * SIMLATENCY_BT,
        BUFWIDTH * SIMLATENCY_BT);

    if (token_bytes_to_target != BUFWIDTH * SIMLATENCY_BT) {
      printf("[CTC] Pushing to stream failed. Wrote %d bytes, expected %d bytes.\n", token_bytes_to_target, BUFWIDTH * SIMLATENCY_BT);
      exit(1);
    }
  }
}

void ctc_t::finish() {
  // Close the fifos
  close(fifo0_fd);
  close(fifo1_fd);
}


