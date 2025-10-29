// See LICENSE for license details.

#include "mm.h"
#include <iostream>
#include <fstream>
#include <cstdlib>
#include <cstring>
#include <cassert>
#include <sys/mman.h>
#include <fesvr/memif.h>
#include <fesvr/elfloader.h>

void mm_t::write(uint64_t faddr, uint8_t *data, uint64_t strb, uint64_t size)
{
  uint64_t addr = faddr - this->mem_base;
  assert(addr < this->mem_size);
  auto max_strb_bytes = sizeof(uint64_t) * 8;
  assert(size <= max_strb_bytes); // Ensure the strb is wide enough to support the desired transaction
  if (size != max_strb_bytes) {
    strb &= (((uint64_t) (1ULL << size)) - 1ULL) << (uint64_t) (addr % word_size);
  }

  uint8_t *base = this->data + (addr / word_size) * word_size;
  for (int i = 0; i < word_size; i++) {
    if ((uint64_t) (strb & 1ULL))
      base[i] = data[i];
    strb >>= 1ULL;
  }
}

std::vector<char> mm_t::read(uint64_t faddr)
{
  uint64_t addr = faddr - this->mem_base;
  assert(addr < this->mem_size);
  uint8_t *base = this->data + addr;
  return std::vector<char>(base, base + word_size);
}

mm_t::~mm_t()
{
  munmap(data, this->mem_size);
}

void mm_magic_t::tick(
  bool reset,

  bool ar_valid,
  uint64_t ar_addr,
  uint64_t ar_id,
  uint64_t ar_size,
  uint64_t ar_len,

  bool aw_valid,
  uint64_t aw_addr,
  uint64_t aw_id,
  uint64_t aw_size,
  uint64_t aw_len,

  bool w_valid,
  uint64_t w_strb,
  void *w_data,
  bool w_last,

  bool r_ready,
  bool b_ready)
{
  bool ar_fire = !reset && ar_valid && ar_ready();
  bool aw_fire = !reset && aw_valid && aw_ready();
  bool w_fire  = !reset && w_valid && w_ready();
  bool r_fire  = !reset && r_valid() && r_ready;
  bool b_fire  = !reset && b_valid() && b_ready;

  if (ar_fire) {
    uint64_t start_addr = (ar_addr / word_size) * word_size;
    for (int i = 0; i <= ar_len; i++) {
      auto dat = read(start_addr + i * word_size);
      rresp.push(mm_rresp_t(ar_id, dat, i == ar_len));
    }
  }

  if (aw_fire) {
    store_addr = aw_addr;
    store_id = aw_id;
    store_count = aw_len + 1;
    store_size = 1 << aw_size;
    store_inflight = true;
  }

  if (w_fire) {
    write(store_addr, (uint8_t *) w_data, w_strb, store_size);
    store_addr += store_size;
    store_count--;

    if (store_count == 0) {
      store_inflight = false;
      bresp.push(store_id);
      assert(w_last);
    }
  }

  if (b_fire)
    bresp.pop();

  if (r_fire)
    rresp.pop();

  cycle++;

  if (reset) {
    while (!bresp.empty()) bresp.pop();
    while (!rresp.empty()) rresp.pop();
    cycle = 0;
  }
}

