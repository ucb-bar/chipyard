// See LICENSE for license details
#include "firesim_dtm.h"
#include <inttypes.h>
#include <stdio.h>

#define fprintf(stdout, fmt, ...) (0)

firesim_dtm_t::firesim_dtm_t(int argc, char **argv, bool can_have_loadmem)
    : testchip_dtm_t(argc, argv, can_have_loadmem), is_busy(false),
      is_loaded_in_host(false), is_loaded_in_target(false) {
  idle_counts = 10;
  std::vector<std::string> args(argv + 1, argv + argc);
  for (auto &arg : args) {
    if (arg.find("+idle-counts=") == 0)
      idle_counts = atoi(arg.c_str() + 13);
  }

  // always use loadmem if the target supports it
  has_loadmem = can_have_loadmem;
}

void firesim_dtm_t::idle() {
  is_busy = false;
  for (size_t i = 0; i < idle_counts; i++)
    switch_to_target();
  is_busy = true;
}

void firesim_dtm_t::send_loadmem_word(uint32_t word) {
  loadmem_out_data.push_back(word);
}

void firesim_dtm_t::load_mem_write(addr_t addr,
                                   size_t nbytes,
                                   const void *src) {
  fprintf(stdout,
          "firesim_dtm_t::load_mem_write addr: %" PRIx64 " nbytes: %" PRIu64
          "\n",
          addr,
          nbytes);
  fflush(stdout);

  loadmem_write_reqs.push_back(firesim_loadmem_t(addr, nbytes));
  loadmem_write_data.insert(
      loadmem_write_data.end(), (const char *)src, (const char *)src + nbytes);
}

void firesim_dtm_t::load_mem_read(addr_t addr, size_t nbytes, void *dst) {
  fprintf(stdout,
          "firesim_dtm_t::load_mem_read addr: %" PRIx64 " nbytes: %" PRIu64
          "\n",
          addr,
          nbytes);
  fflush(stdout);

  while (!loadmem_write_reqs.empty())
    switch_to_target();
  loadmem_read_reqs.push_back(firesim_loadmem_t(addr, nbytes));

  uint32_t *result = static_cast<uint32_t *>(dst);
  size_t len = nbytes / sizeof(uint32_t);
  for (size_t i = 0; i < len; i++) {
    while (loadmem_out_data.empty())
      switch_to_target();
    result[i] = loadmem_out_data.front();
    loadmem_out_data.pop_front();
  }
}

void firesim_dtm_t::reset() {
  // after program loading, this function is called and spins until the target
  // thread/bridge has synced/drained all in-flight fesvr xacts
  is_loaded_in_host = true;
  while (!is_loaded_in_target)
    switch_to_target();
  fprintf(
      stdout,
      "firesim_dtm_t::reset done loading program. sending reset signal(s)\n");
  fflush(stdout);
  testchip_dtm_t::reset();
}

bool firesim_dtm_t::has_loadmem_reqs() {
  return (!loadmem_write_reqs.empty() || !loadmem_read_reqs.empty());
}

bool firesim_dtm_t::recv_loadmem_write_req(firesim_loadmem_t &loadmem) {
  if (loadmem_write_reqs.empty())
    return false;
  auto r = loadmem_write_reqs.front();
  loadmem.addr = r.addr;
  loadmem.size = r.size;
  loadmem_write_reqs.pop_front();
  return true;
}

bool firesim_dtm_t::recv_loadmem_read_req(firesim_loadmem_t &loadmem) {
  if (loadmem_read_reqs.empty())
    return false;
  auto r = loadmem_read_reqs.front();
  loadmem.addr = r.addr;
  loadmem.size = r.size;
  loadmem_read_reqs.pop_front();
  return true;
}

void firesim_dtm_t::recv_loadmem_data(void *buf, size_t len) {
  std::copy(loadmem_write_data.begin(),
            loadmem_write_data.begin() + len,
            (char *)buf);
  loadmem_write_data.erase(loadmem_write_data.begin(),
                           loadmem_write_data.begin() + len);
}
