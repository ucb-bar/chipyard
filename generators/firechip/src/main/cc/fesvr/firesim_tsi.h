// See LICENSE for license details
#ifndef __FIRESIM_TSI_H
#define __FIRESIM_TSI_H

#include "testchip_tsi.h"

struct firesim_loadmem_t {
  firesim_loadmem_t() : addr(0), size(0) {}
  firesim_loadmem_t(size_t addr, size_t size) : addr(addr), size(size) {}
  size_t addr;
  size_t size;
};

class firesim_tsi_t final : public testchip_tsi_t {
public:
  firesim_tsi_t(int argc, char **argv, bool has_loadmem);
  ~firesim_tsi_t() {}

  bool busy() { return is_busy; };
  bool loaded_in_host() { return is_loaded_in_host; };
  void set_loaded_in_target(bool loaded) { is_loaded_in_target = loaded; };

  void tick();
  void tick(bool out_valid, uint32_t out_bits, bool in_ready) { tick(); };

  bool recv_loadmem_write_req(firesim_loadmem_t &loadmem);
  bool recv_loadmem_read_req(firesim_loadmem_t &loadmem);
  void recv_loadmem_data(void *buf, size_t len);
  bool has_loadmem_reqs();

  void send_loadmem_word(uint32_t word);

  void load_program() override;

protected:
  void idle() override;

  void reset() override;

  void load_mem_write(addr_t addr, size_t nbytes, const void *src) override;
  void load_mem_read(addr_t addr, size_t nbytes, void *dst) override;

  // This must be exactly the same as hKey.dataBits in the LoadMem widget
  // See discussion here: https://github.com/firesim/firesim/pull/1401
  // TODO: Avoid hardcoding this value here
  size_t chunk_align() override { return 8; }

  std::deque<firesim_loadmem_t> loadmem_write_reqs;
  std::deque<firesim_loadmem_t> loadmem_read_reqs;
  std::deque<char> loadmem_write_data;

  std::deque<uint32_t> loadmem_out_data;

private:
  size_t idle_counts;
  bool is_busy;
  // program load has completed in the host thread (i.e. all fesvr xacts for
  // program load have been sent by fesvr)
  bool is_loaded_in_host;
  // program load has completed in the target thread (i.e. all in-flight fesvr
  // xacts for program load have been synced/drained by the target
  // thread/bridge)
  bool is_loaded_in_target;
};
#endif // __FIRESIM_TSI_H
