// See LICENSE for license details

#include "tsibridge.h"
#include "bridges/loadmem.h"
#include "core/simif.h"
#include "fesvr/firesim_tsi.h"

#include <cassert>
#include <gmp.h>

char tsibridge_t::KIND;

tsibridge_t::tsibridge_t(simif_t &simif,
                         loadmem_t &loadmem_widget,
                         const TSIBRIDGEMODULE_struct &mmio_addrs,
                         int tsino,
                         const std::vector<std::string> &args,
                         bool has_mem,
                         int64_t mem_host_offset)
    : bridge_driver_t(simif, &KIND), mmio_addrs(mmio_addrs),
      loadmem_widget(loadmem_widget), has_mem(has_mem),
      mem_host_offset(mem_host_offset) {

  const std::string num_equals = std::to_string(tsino) + std::string("=");
  const std::string prog_arg = std::string("+prog") + num_equals;
  std::vector<std::string> args_vec;
  args_vec.push_back("firesim_tsi");

  // This particular selection is vestigial. You may change it freely.
  step_size = 2004765L;

  // During the initial program phase speed up when FESVR is called
  // (i.e. speed up program loading when loadmem isn't/can't be used)
  // (disabled by default)
  fast_fesvr = false;

  // This particular selection is correlated to the amount of reset cycles.
  // It should be larger than the reset period.
  wait_ticks = 8;

  // This particular selection is vestigial. You may change it freely.
  // This * wait_ticks is should be larger than the reset period.
  loading_step_size = fast_fesvr ? 8 : step_size;

  for (auto &arg : args) {
    if (arg.find("+fesvr-step-size=") == 0) {
      step_size = atoi(arg.c_str() + 17);
    }
    if (arg.find("+fesvr-enable-early-fast") == 0) {
      fast_fesvr = true;
    }
    if (arg.find("+fesvr-wait-ticks=") == 0) {
      wait_ticks = atoi(arg.c_str() + 18);
    }
    if (arg.find(prog_arg) == 0) {
      std::string clean_target_args =
          const_cast<char *>(arg.c_str()) + prog_arg.length();

      std::istringstream ss(clean_target_args);
      std::string token;
      while (std::getline(ss, token, ' ')) {
        args_vec.push_back(token);
      }
    } else if (arg.find(std::string("+prog")) == 0) {
      // Eliminate arguments for other fesvrs
    } else {
      args_vec.push_back(arg);
    }
  }

  int argc_count = args_vec.size() - 1;
  tsi_argv = new char *[args_vec.size()];
  for (size_t i = 0; i < args_vec.size(); ++i) {
    tsi_argv[i] = new char[args_vec[i].size() + 1];
    std::strcpy(tsi_argv[i], args_vec[i].c_str());
  }

  // debug for command line arguments
  printf("command line for program %d. argc=%d:\n", tsino, argc_count);
  for (int i = 0; i < argc_count; i++) {
    printf("%s ", tsi_argv[i + 1]);
  }
  printf("\n");

  tsi_argc = argc_count + 1;
}

tsibridge_t::~tsibridge_t() {
  if (fesvr)
    delete fesvr;
  if (tsi_argv) {
    for (int i = 0; i < tsi_argc; ++i) {
      if (tsi_argv[i])
        delete[] tsi_argv[i];
    }
    delete[] tsi_argv;
  }
}

void tsibridge_t::init() {
  // `ucontext` used by tsi cannot be created in one thread and resumed in
  // another. To ensure that the tsi process is on the correct thread, it is
  // built here, as the bridge constructor may be invoked from a thread other
  // than the one it will run on later in meta-simulations.
  fesvr = new firesim_tsi_t(tsi_argc, tsi_argv, has_mem);
  if (fast_fesvr) {
    printf("tsibridge_t::init set FESVR step-size to %" PRIu32 " initially\n",
           loading_step_size);
    write(mmio_addrs.step_size, loading_step_size);
  } else {
    write(mmio_addrs.step_size, step_size);
    fesvr->set_loaded_in_target(true); // pre-set to unblock fs_tsi_t::reset
  }
  go();
}

void tsibridge_t::go() { write(mmio_addrs.start, 1); }

void tsibridge_t::send() {
  while (fesvr->data_available() && read(mmio_addrs.in_ready)) {
    write(mmio_addrs.in_bits, fesvr->recv_word());
    write(mmio_addrs.in_valid, 1);
  }
}

void tsibridge_t::recv() {
  while (read(mmio_addrs.out_valid)) {
    fesvr->send_word(read(mmio_addrs.out_bits));
    write(mmio_addrs.out_ready, 1);
  }
}

void tsibridge_t::handle_loadmem_read(firesim_loadmem_t loadmem) {
  assert(loadmem.size % sizeof(uint32_t) == 0);
  assert(has_mem);
  // Loadmem reads are in granularities of the width of the FPGA-DRAM bus
  mpz_t buf;
  mpz_init(buf);
  while (loadmem.size > 0) {
    loadmem_widget.read_mem(loadmem.addr + mem_host_offset, buf);

    // If the read word is 0; mpz_export seems to return an array with length 0
    size_t beats_requested =
        (loadmem.size / sizeof(uint32_t) > loadmem_widget.get_mem_data_chunk())
            ? loadmem_widget.get_mem_data_chunk()
            : loadmem.size / sizeof(uint32_t);
    // The number of beats exported from buf; may be less than beats requested.
    size_t non_zero_beats;
    uint32_t *data = (uint32_t *)mpz_export(
        NULL, &non_zero_beats, -1, sizeof(uint32_t), 0, 0, buf);
    for (size_t j = 0; j < beats_requested; j++) {
      if (j < non_zero_beats) {
        fesvr->send_loadmem_word(data[j]);
      } else {
        fesvr->send_loadmem_word(0);
      }
    }
    loadmem.size -= beats_requested * sizeof(uint32_t);
  }
  mpz_clear(buf);
  // Switch back to fesvr for it to process read data
  fesvr->tick();
}

void tsibridge_t::handle_loadmem_write(firesim_loadmem_t loadmem) {
  assert(loadmem.size <= 4096);
  assert(has_mem);
  static char buf[4096]; // size chosen empirically based on chunk sizes
  fesvr->recv_loadmem_data(buf, loadmem.size);
  mpz_t data;
  mpz_init(data);
  mpz_import(data,
             (loadmem.size + sizeof(uint32_t) - 1) / sizeof(uint32_t),
             -1,
             sizeof(uint32_t),
             0,
             0,
             buf);
  loadmem_widget.write_mem_chunk(
      loadmem.addr + mem_host_offset, data, loadmem.size);
  mpz_clear(data);
}

void tsibridge_t::tsi_bypass_via_loadmem() {
  firesim_loadmem_t loadmem;
  while (fesvr->has_loadmem_reqs()) {
    // Check for reads first as they preceed a narrow write;
    if (fesvr->recv_loadmem_read_req(loadmem))
      handle_loadmem_read(loadmem);
    if (fesvr->recv_loadmem_write_req(loadmem))
      handle_loadmem_write(loadmem);
  }
}

void tsibridge_t::tick() {
  // First, check to see step_size tokens have been enqueued
  if (!read(mmio_addrs.done))
    return;
  if (wait_ticks != 0) {
    wait_ticks -= 1;
    printf("tsibridge_t::tick skipping tick\n");
    go();
    return;
  }
  // Collect all the responses from the target
  this->recv();
  // Punt to FESVR
  if (!fesvr->data_available()) {
    fesvr->tick();
  }
  if (fesvr->has_loadmem_reqs()) {
    tsi_bypass_via_loadmem();
  }
  if (!terminate()) {
    // Write all the requests to the target
    this->send();
    if (fast_fesvr) {
      if (fesvr->loaded_in_host()) {
        if (!fesvr->data_available()) {
          fesvr->set_loaded_in_target(true); // done w/ firesim loading
          printf("tsibridge_t::tick reverting FESVR step-size to %" PRIu32 "\n",
                 step_size);
          write(mmio_addrs.step_size, step_size);
          fast_fesvr = false; // only write this user-defined step size once
        }
      }
    }
    go();
  }
}

bool tsibridge_t::terminate() { return fesvr->done(); }
int tsibridge_t::exit_code() { return fesvr->exit_code(); }
