// See LICENSE for license details

#include "dmibridge.h"
#include "bridges/loadmem.h"
#include "core/simif.h"
#include "fesvr/firesim_dtm.h"

#include <cassert>
#include <gmp.h>

char dmibridge_t::KIND;

dmibridge_t::dmibridge_t(simif_t &simif,
                         loadmem_t &loadmem_widget,
                         const DMIBRIDGEMODULE_struct &mmio_addrs,
                         int dmino,
                         const std::vector<std::string> &args,
                         bool has_mem,
                         int64_t mem_host_offset)
    : bridge_driver_t(simif, &KIND), mmio_addrs(mmio_addrs),
      loadmem_widget(loadmem_widget), has_mem(has_mem),
      mem_host_offset(mem_host_offset) {

  std::string num_equals = std::to_string(dmino) + std::string("=");
  std::string prog_arg = std::string("+prog") + num_equals;
  std::vector<std::string> args_vec;
  args_vec.push_back("firesim_dtm");

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
  dmi_argv = new char *[args_vec.size()];
  for (size_t i = 0; i < args_vec.size(); ++i) {
    dmi_argv[i] = new char[args_vec[i].size() + 1];
    std::strcpy(dmi_argv[i], args_vec[i].c_str());
  }

  // debug for command line arguments
  printf("command line for program %d. argc=%d:\n", dmino, argc_count);
  for (int i = 0; i < argc_count; i++) {
    printf("%s ", dmi_argv[i + 1]);
  }
  printf("\n");

  dmi_argc = argc_count + 1;
}

dmibridge_t::~dmibridge_t() {
  if (fesvr)
    delete fesvr;
  if (dmi_argv) {
    for (int i = 0; i < dmi_argc; ++i) {
      if (dmi_argv[i])
        delete[] dmi_argv[i];
    }
    delete[] dmi_argv;
  }
}

void dmibridge_t::init() {
  // `ucontext` used by dmi cannot be created in one thread and resumed in
  // another. To ensure that the dmi process is on the correct thread, it is
  // built here, as the bridge constructor may be invoked from a thread other
  // than the one it will run on later in meta-simulations.
  fesvr = new firesim_dtm_t(dmi_argc, dmi_argv, has_mem);
  if (fast_fesvr) {
    printf("dmibridge_t::init set FESVR step-size to %" PRIu32 " initially\n",
           loading_step_size);
    write(mmio_addrs.step_size, loading_step_size);
  } else {
    write(mmio_addrs.step_size, step_size);
    fesvr->set_loaded_in_target(true); // pre-set to unblock fs_dtm_t::reset
  }
  go();
}

void dmibridge_t::go() { write(mmio_addrs.start, 1); }

void dmibridge_t::handle_loadmem_read(firesim_loadmem_t loadmem) {
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
  fesvr->switch_to_host();
}

void dmibridge_t::handle_loadmem_write(firesim_loadmem_t loadmem) {
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

void dmibridge_t::dmi_bypass_via_loadmem() {
  firesim_loadmem_t loadmem;

  while (fesvr->has_loadmem_reqs()) {
    // Check for reads first as they preceed a narrow write;
    if (fesvr->recv_loadmem_read_req(loadmem))
      handle_loadmem_read(loadmem);
    if (fesvr->recv_loadmem_write_req(loadmem))
      handle_loadmem_write(loadmem);
  }
}

void dmibridge_t::tick() {
  // First, check to see step_size tokens have been enqueued
  if (!read(mmio_addrs.done))
    return;

  if (wait_ticks != 0) {
    wait_ticks -= 1;
    printf("dmibridge_t::tick skipping tick\n");
    go();
    return;
  }

  // req from the host, resp from the target
  // in(to) the target, out from the target

  const auto resp_valid = read(mmio_addrs.out_valid);
  dtm_t::resp out_resp;
  if (resp_valid) {
    // NOTE: these are equivalent to recv() in tsibridge
    out_resp.resp = read(mmio_addrs.out_bits_resp);
    out_resp.data = read(mmio_addrs.out_bits_data);
    // printf("DEBUG: Resp read: resp(0x%x) data(0x%x)\n", out_resp.resp,
    //  out_resp.data);
    write(mmio_addrs.out_ready, 1);
  }

  // non-overloaded dtm_t tick that sync's data + switches to host
  fesvr->tick(read(mmio_addrs.in_ready), resp_valid, out_resp);

  if (fesvr->has_loadmem_reqs()) {
    dmi_bypass_via_loadmem();
  }

  if (!terminate()) {
    if (fesvr->req_valid() && read(mmio_addrs.in_ready)) {
      dtm_t::req in_req = fesvr->req_bits();
      // printf("DEBUG: Req sent: addr(0x%x) op(0x%x) data(0x%x)\n",
      //  in_req.addr, in_req.op, in_req.data);

      // NOTE: these are equivalent to send() in tsibridge
      write(mmio_addrs.in_bits_addr, in_req.addr);
      write(mmio_addrs.in_bits_op, in_req.op);
      write(mmio_addrs.in_bits_data, in_req.data);
      write(mmio_addrs.in_valid, 1);
    }

    if (fast_fesvr) {
      if (fesvr->loaded_in_host()) {
        if (!fesvr->req_valid()) {
          fesvr->set_loaded_in_target(true); // done w/ firesim loading
          printf("dmibridge_t::tick reverting FESVR step-size to %" PRIu32 "\n",
                 step_size);
          write(mmio_addrs.step_size, step_size);
          fast_fesvr = false; // only write this user-defined step size once
        }
      }
    }

    // Move forward step_size iterations
    go();
  }
}

bool dmibridge_t::terminate() { return fesvr->done(); }
int dmibridge_t::exit_code() { return fesvr->exit_code(); }
