// See LICENSE for license details

#include "simplenic.h"

#include <cassert>
#include <cstdio>
#include <cstring>

#include <iostream>

#include <fcntl.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

#include <sys/mman.h>

char simplenic_t::KIND;

// DO NOT MODIFY PARAMS BELOW THIS LINE
#define TOKENS_PER_BIGTOKEN 7

#define SIMLATENCY_BT (this->LINKLATENCY / TOKENS_PER_BIGTOKEN)

#define BUFWIDTH streaming_bridge_driver_t::STREAM_WIDTH_BYTES
#define BUFBYTES (SIMLATENCY_BT * BUFWIDTH)
#define EXTRABYTES 1

#define FLIT_BITS 64
#define PACKET_MAX_FLITS 190
#define BITTIME_PER_QUANTA 512
#define CYCLES_PER_QUANTA (BITTIME_PER_QUANTA / FLIT_BITS)

static void simplify_frac(int n, int d, int *nn, int *dd) {
  int a = n, b = d;

  // compute GCD
  while (b > 0) {
    int t = b;
    b = a % b;
    a = t;
  }

  *nn = n / a;
  *dd = d / a;
}

#define niclog_printf(...)                                                     \
  if (this->niclog) {                                                          \
    fprintf(this->niclog, __VA_ARGS__);                                        \
    fflush(this->niclog);                                                      \
  }

simplenic_t::simplenic_t(simif_t &sim,
                         StreamEngine &stream,
                         const SIMPLENICBRIDGEMODULE_struct &mmio_addrs,
                         int simplenicno,
                         const std::vector<std::string> &args,
                         const int stream_to_cpu_idx,
                         const int stream_to_cpu_depth,
                         const int stream_from_cpu_idx,
                         const int stream_from_cpu_depth)
    : streaming_bridge_driver_t(sim, stream, &KIND), mmio_addrs(mmio_addrs),
      stream_to_cpu_idx(stream_to_cpu_idx),
      stream_from_cpu_idx(stream_from_cpu_idx) {
  const char *niclogfile = nullptr;
  const char *shmemportname = nullptr;
  int netbw = MAX_BANDWIDTH, netburst = 8;

  this->loopback = false;
  this->niclog = nullptr;
  this->mac_lendian = 0;
  this->LINKLATENCY = 0;

  // construct arg parsing strings here. We basically append the bridge_driver
  // number to each of these base strings, to get args like +blkdev0 etc.
  std::string num_equals = std::to_string(simplenicno) + std::string("=");
  std::string niclog_arg = std::string("+niclog") + num_equals;
  std::string nicloopback_arg =
      std::string("+nic-loopback") + std::to_string(simplenicno);
  std::string macaddr_arg = std::string("+macaddr") + num_equals;
  std::string netbw_arg = std::string("+netbw") + num_equals;
  std::string netburst_arg = std::string("+netburst") + num_equals;
  std::string linklatency_arg = std::string("+linklatency") + num_equals;
  std::string shmemportname_arg = std::string("+shmemportname") + num_equals;

  for (auto &arg : args) {
    if (arg.find(niclog_arg) == 0) {
      niclogfile = const_cast<char *>(arg.c_str()) + niclog_arg.length();
    }
    if (arg.find(nicloopback_arg) == 0) {
      this->loopback = true;
    }
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

        for (int i = 0; i < 6; i++) {
          mac_lendian |= (((uint64_t)(uint8_t)mac_octets[i]) << (8 * i));
        }
      } else {
        fprintf(stderr, "INVALID MAC ADDRESS SUPPLIED WITH +macaddrN=\n");
      }
    }
    if (arg.find(netbw_arg) == 0) {
      char *str = const_cast<char *>(arg.c_str()) + netbw_arg.length();
      netbw = atoi(str);
    }
    if (arg.find(netburst_arg) == 0) {
      char *str = const_cast<char *>(arg.c_str()) + netburst_arg.length();
      netburst = atoi(str);
    }
    if (arg.find(linklatency_arg) == 0) {
      char *str = const_cast<char *>(arg.c_str()) + linklatency_arg.length();
      this->LINKLATENCY = atoi(str);
    }
    if (arg.find(shmemportname_arg) == 0) {
      shmemportname =
          const_cast<char *>(arg.c_str()) + shmemportname_arg.length();
    }
  }

  if (stream_from_cpu_depth < SIMLATENCY_BT) {
    // Workaround: pick a smaller latency, or up-size the queue.
    std::cerr << "CPU-to-FPGA stream undersized for requested link latency."
              << " Available: " << stream_from_cpu_depth
              << " Required: " << SIMLATENCY_BT << std::endl;
    exit(1);
  }

  if (stream_to_cpu_depth < SIMLATENCY_BT) {
    // Workaround: pick a smaller latency, or up-size the queue.
    std::cerr << "FPGA-to-CPU stream undersized for requested link latency."
              << " Available: " << stream_to_cpu_depth
              << " Required: " << SIMLATENCY_BT << std::endl;
    exit(1);
  }

  assert(this->LINKLATENCY > 0);
  assert(this->LINKLATENCY % TOKENS_PER_BIGTOKEN == 0);
  assert(netbw <= MAX_BANDWIDTH);
  assert(netburst < 256);
  simplify_frac(netbw, MAX_BANDWIDTH, &rlimit_inc, &rlimit_period);
  rlimit_size = netburst;
  pause_threshold = PACKET_MAX_FLITS + this->LINKLATENCY;
  pause_quanta = pause_threshold / CYCLES_PER_QUANTA;
  pause_refresh = this->LINKLATENCY;

  printf("using link latency: %d cycles\n", this->LINKLATENCY);
  printf("using netbw: %d\n", netbw);
  printf("using netburst: %d\n", netburst);

  if (niclogfile) {
    this->niclog = fopen(niclogfile, "w");
    if (!this->niclog) {
      fprintf(stderr, "Could not open NIC log file: %s\n", niclogfile);
      abort();
    }
  }

  char name[257];
  int shmemfd;

  if (!loopback) {
    assert(shmemportname != nullptr);
    for (int j = 0; j < 2; j++) {
      printf("Using non-slot-id associated shmemportname:\n");
      sprintf(name, "/port_nts%s_%d", shmemportname, j);

      printf("opening/creating shmem region\n%s\n", name);
      shmemfd = shm_open(name, O_RDWR | O_CREAT, S_IRWXU);
      ftruncate(shmemfd, BUFBYTES + EXTRABYTES);
      pcis_read_bufs[j] = (char *)mmap(nullptr,
                                       BUFBYTES + EXTRABYTES,
                                       PROT_READ | PROT_WRITE,
                                       MAP_SHARED,
                                       shmemfd,
                                       0);

      printf("Using non-slot-id associated shmemportname:\n");
      sprintf(name, "/port_stn%s_%d", shmemportname, j);

      printf("opening/creating shmem region\n%s\n", name);
      shmemfd = shm_open(name, O_RDWR | O_CREAT, S_IRWXU);
      ftruncate(shmemfd, BUFBYTES + EXTRABYTES);
      pcis_write_bufs[j] = (char *)mmap(nullptr,
                                        BUFBYTES + EXTRABYTES,
                                        PROT_READ | PROT_WRITE,
                                        MAP_SHARED,
                                        shmemfd,
                                        0);
    }
  } else {
    for (int j = 0; j < 2; j++) {
      pcis_read_bufs[j] = (char *)malloc(BUFBYTES + EXTRABYTES);
      pcis_write_bufs[j] = pcis_read_bufs[j];
    }
  }

  printf("BUFBYTES %d\n", BUFBYTES);
}

simplenic_t::~simplenic_t() {
  if (this->niclog)
    fclose(this->niclog);
  if (loopback) {
    for (auto &pcis_read_buf : pcis_read_bufs)
      if (pcis_read_buf)
        free(pcis_read_buf);
  } else {
    for (int j = 0; j < 2; j++) {
      if (pcis_read_bufs[j])
        munmap(pcis_read_bufs[j], BUFBYTES + EXTRABYTES);
      if (pcis_write_bufs[j])
        munmap(pcis_write_bufs[j], BUFBYTES + EXTRABYTES);
    }
  }
}

#define ceil_div(n, d) (((n)-1) / (d) + 1)

void simplenic_t::init() {
  write(mmio_addrs.macaddr_upper, (mac_lendian >> 32) & 0xFFFF);
  write(mmio_addrs.macaddr_lower, mac_lendian & 0xFFFFFFFF);
  write(mmio_addrs.rlimit_settings,
        (rlimit_inc << 16) | ((rlimit_period - 1) << 8) | rlimit_size);
  write(mmio_addrs.pause_threshold, pause_threshold);
  write(mmio_addrs.pause_times,
        (pause_refresh << 16) | (pause_quanta & 0xffff));

  // In lieu of reading "count", check that the stream is empty by doing a pull.
  // To make this work under alveo we'd almost definitely need to call flush
  // first.
  auto bytes_received = this->pull(
      stream_to_cpu_idx, pcis_read_bufs[0], SIMLATENCY_BT * BUFWIDTH, 0);
  if ((bytes_received != 0)) {
    printf("FAIL. Exactly 1 tokens should be present in the cpu-bound stream "
           "on init");
    exit(1);
  }

  // Enqueue SIMLATENCY_BT beats into the from-cpu stream. This permits the
  // FPGA-hosted part of the simulator to execute SIMLATENCY cycles in the
  // NIC-local clock domain before requiring additional interaction from the
  // driver.
  auto token_bytes_to_send = SIMLATENCY_BT * BUFWIDTH;
  // Set the threshold here to 0 as a proxy for checking the stream capacity.
  // If we cannot enqueue the full payload, the stream is likely undersized
  // for our desired latency or the FPGA has not been properly reset /
  // reprogrammed.
  auto token_bytes_produced = this->push(
      stream_from_cpu_idx, pcis_write_bufs[1], token_bytes_to_send, 0);

  if (token_bytes_produced != token_bytes_to_send) {
    printf("FAIL. Could not enqueue big tokens to support the desired sim "
           "latency on init. Required %d, enqueued %lu\n",
           SIMLATENCY_BT,
           token_bytes_produced / BUFWIDTH);
    exit(1);
  }
}

// #define TOKENVERIFY

void simplenic_t::tick() {
  /* #define DEBUG_NIC_PRINT */

  while (true) { // break when we don't have 5k tokens
    uint32_t tokens_this_round = SIMLATENCY_BT;

    uint32_t token_bytes_obtained_from_fpga = 0;
    auto requested_token_bytes = BUFWIDTH * tokens_this_round;
    token_bytes_obtained_from_fpga =
        pull(stream_to_cpu_idx,
             pcis_read_bufs[currentround],
             requested_token_bytes,
             requested_token_bytes // Copy only if the stream can provide
                                   // exactly as many bytes as we want
        );

    if (token_bytes_obtained_from_fpga == 0) {
      return;
    }
    // read into read_buffer
    pcis_read_bufs[currentround][BUFBYTES] = 1;

#ifdef DEBUG_NIC_PRINT
    niclog_printf("send pcis_read_bufs[%d][%d]: %d\n",
                  currentround,
                  BUFBYTES,
                  pcis_read_bufs[currentround][BUFBYTES]);
#endif


#ifdef TOKENVERIFY
    // the widget is designed to tag tokens with a 43 bit number,
    // incrementing for each sent token. verify that we are not losing
    // tokens over PCIS
    for (int i = 0; i < tokens_this_round; i++) {
      uint64_t TOKENLRV_AND_COUNT =
          *(((uint64_t *)pcis_read_bufs[currentround]) + i * 8);
      uint8_t LAST;
      for (int token_in_bigtoken = 0; token_in_bigtoken < 7;
           token_in_bigtoken++) {
        if (TOKENLRV_AND_COUNT & (1L << (43 + token_in_bigtoken * 3))) {
          LAST = (TOKENLRV_AND_COUNT >> (45 + token_in_bigtoken * 3)) & 0x1;
          niclog_printf("sending to other node, valid data chunk: "
                        "%016lx, last %x, sendcycle: %016ld\n",
                        *((((uint64_t *)pcis_read_bufs[currentround]) + i * 8) +
                          1 + token_in_bigtoken),
                        LAST,
                        timeelapsed_cycles + i * 7 + token_in_bigtoken);
        }
      }

      //            *((uint64_t*)(pcis_read_buf + i*64)) |= 0x4924900000000000;
      uint32_t thistoken =
          *((uint32_t *)(pcis_read_bufs[currentround] + i * 64));
      if (thistoken != next_token_from_fpga) {
        niclog_printf("FAIL! Token lost on FPGA interface.\n");
        exit(1);
      }
      next_token_from_fpga++;
    }
#endif
    if (token_bytes_obtained_from_fpga != tokens_this_round * BUFWIDTH) {
      printf("ERR MISMATCH! on reading tokens out. actually read %d bytes, "
             "wanted %d bytes.\n",
             token_bytes_obtained_from_fpga,
             BUFWIDTH * tokens_this_round);
      printf("errno: %s\n", strerror(errno));
      exit(1);
    }

#ifdef DEBUG_NIC_PRINT
    niclog_printf("receiving ... round %d\n", currentround);
#endif

#ifdef TOKENVERIFY
    timeelapsed_cycles += LINKLATENCY;
#endif

    if (!loopback) {
      volatile uint8_t *polladdr =
          (uint8_t *)(pcis_write_bufs[currentround] + BUFBYTES);
      while (*polladdr == 0) {
        ;
      }
    }

#ifdef DEBUG_NIC_PRINT
    niclog_printf("done recv round %d\n", currentround);
#endif

#ifdef TOKENVERIFY
    // this does not do tokenverify - it's just printing tokens
    // there should not be tokenverify on this interface
    for (int i = 0; i < tokens_this_round; i++) {
      uint64_t TOKENLRV_AND_COUNT =
          *(((uint64_t *)pcis_write_bufs[currentround]) + i * 8);
      uint8_t LAST;
      for (int token_in_bigtoken = 0; token_in_bigtoken < 7;
           token_in_bigtoken++) {
        if (TOKENLRV_AND_COUNT & (1L << (43 + token_in_bigtoken * 3))) {
          LAST = (TOKENLRV_AND_COUNT >> (45 + token_in_bigtoken * 3)) & 0x1;
          niclog_printf(
              "from other node, valid data chunk: %016lx, "
              "last %x, recvcycle: %016ld\n",
              *((((uint64_t *)pcis_write_bufs[currentround]) + i * 8) + 1 +
                token_in_bigtoken),
              LAST,
              timeelapsed_cycles + i * 7 + token_in_bigtoken);
        }
      }
    }
#endif
    uint32_t token_bytes_sent_to_fpga = 0;
    token_bytes_sent_to_fpga = push(stream_from_cpu_idx,
                                    pcis_write_bufs[currentround],
                                    BUFWIDTH * tokens_this_round,
                                    BUFWIDTH * tokens_this_round);
    pcis_write_bufs[currentround][BUFBYTES] = 0;
    if (token_bytes_sent_to_fpga != tokens_this_round * BUFWIDTH) {
      printf("ERR MISMATCH! on writing tokens in. actually wrote in %d bytes, "
             "wanted %d bytes.\n",
             token_bytes_sent_to_fpga,
             BUFWIDTH * tokens_this_round);
      printf("errno: %s\n", strerror(errno));
      exit(1);
    }

    currentround = (currentround + 1) % 2;
  }
}
