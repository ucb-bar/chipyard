// See LICENSE for license details
#ifndef __DMIBRIDGE_H
#define __DMIBRIDGE_H

#include "bridges/serial_data.h"
#include "core/bridge_driver.h"

class loadmem_t;
class firesim_dtm_t;
class firesim_loadmem_t;

struct DMIBRIDGEMODULE_struct {
  uint64_t in_bits_addr;
  uint64_t in_bits_data;
  uint64_t in_bits_op;
  uint64_t in_valid;
  uint64_t in_ready;
  uint64_t out_bits_data;
  uint64_t out_bits_resp;
  uint64_t out_valid;
  uint64_t out_ready;
  uint64_t step_size;
  uint64_t done;
  uint64_t start;
};

class dmibridge_t : public bridge_driver_t {
public:
  /// The identifier for the bridge type used for casts.
  static char KIND;

  dmibridge_t(simif_t &simif,
              loadmem_t &loadmem_widget,
              const DMIBRIDGEMODULE_struct &mmio_addrs,
              int dmino,
              const std::vector<std::string> &args,
              bool has_mem,
              int64_t mem_host_offset);
  ~dmibridge_t();
  virtual void init();
  virtual void tick();
  virtual bool terminate();
  virtual int exit_code();

private:
  const DMIBRIDGEMODULE_struct mmio_addrs;
  loadmem_t &loadmem_widget;

  firesim_dtm_t *fesvr;
  bool has_mem;
  // host memory offset based on the number of memory models and their size
  int64_t mem_host_offset;
  // Number of target cycles between fesvr interactions
  uint32_t step_size;
  // Same as step_size but value during initial programing phase
  uint32_t loading_step_size;
  // During the initial program phase speed up when FESVR is called
  // (i.e. speed up program loading when loadmem isn't/can't be used)
  bool fast_fesvr;
  // Delay n ticks to avoid race-condition where target reset resets the bridge
  // state and drops xacts
  uint32_t wait_ticks;

  // Arguments passed to firesim_dtm.
  char **dmi_argv = nullptr;
  int dmi_argc;

  // Tell the widget to start enqueuing tokens
  void go();

  void handle_loadmem_read(firesim_loadmem_t loadmem);
  void handle_loadmem_write(firesim_loadmem_t loadmem);
  void dmi_bypass_via_loadmem();
};

#endif // __DMIBRIDGE_H
