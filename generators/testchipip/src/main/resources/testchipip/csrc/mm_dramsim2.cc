// See LICENSE for license details.

#include "mm_dramsim2.h"
#include "mm.h"
#include <iostream>
#include <fstream>
#include <list>
#include <queue>
#include <cstring>
#include <cstdlib>
#include <cassert>

//#define DEBUG_DRAMSIM2

using namespace DRAMSim;

void mm_dramsim2_t::read_complete(unsigned id, uint64_t address, uint64_t clock_cycle)
{
  assert(!rreq[address].empty());
  auto req = rreq[address].front();
  uint64_t start_addr = (req.addr / word_size) * word_size;
  for (size_t i = 0; i < req.len; i++) {
    auto dat = read(start_addr + i * word_size);
    rresp.push(mm_rresp_t(req.id, dat, (i == req.len - 1)));
  }
  read_id_busy[req.id] = false;
  rreq[address].pop();
}

void mm_dramsim2_t::write_complete(unsigned id, uint64_t address, uint64_t clock_cycle)
{
  assert(!wreq[address].empty());
  auto b_id = wreq[address].front();
  bresp.push(b_id);
  write_id_busy[b_id] = false;
  wreq[address].pop();
}

void power_callback(double a, double b, double c, double d)
{
    //fprintf(stderr, "power callback: %0.3f, %0.3f, %0.3f, %0.3f\n",a,b,c,d);
}


mm_dramsim2_t::mm_dramsim2_t(size_t mem_base, size_t mem_sz, size_t word_sz, size_t line_sz, backing_data_t& dat, std::string memory_ini, std::string system_ini, std::string ini_dir, int axi4_ids, size_t clock_hz) :
  mm_t(mem_base, mem_sz, word_sz, line_sz, dat),
  read_id_busy(axi4_ids, false),
  write_id_busy(axi4_ids, false) {

  assert(line_sz == 64); // assumed by dramsim2
  assert(mem_sz % (1024*1024) == 0);
  mem = getMemorySystemInstance(memory_ini, system_ini, ini_dir, "results", mem_size/(1024*1024));
  mem->setCPUClockSpeed(clock_hz);
  TransactionCompleteCB *read_cb = new Callback<mm_dramsim2_t, void, unsigned, uint64_t, uint64_t>(this, &mm_dramsim2_t::read_complete);
  TransactionCompleteCB *write_cb = new Callback<mm_dramsim2_t, void, unsigned, uint64_t, uint64_t>(this, &mm_dramsim2_t::write_complete);
  mem->RegisterCallbacks(read_cb, write_cb, power_callback);
};

bool mm_dramsim2_t::ar_ready() {
  return mem->willAcceptTransaction();
}

bool mm_dramsim2_t::aw_ready() {
  return mem->willAcceptTransaction() && !store_inflight;
}

void mm_dramsim2_t::tick(
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
  bool w_fire = !reset && w_valid && w_ready();
  bool r_fire = !reset && r_valid() && r_ready;
  bool b_fire = !reset && b_valid() && b_ready;

  if (mem->willAcceptTransaction()) {
    for (auto it = rreq_queue.begin(); it != rreq_queue.end(); it++) {
      if (!read_id_busy[it->id]) {
        read_id_busy[it->id] = true;
        auto transaction = *it;
        rreq[transaction.addr].push(transaction);
        mem->addTransaction(false, transaction.addr);
        rreq_queue.erase(it);
        break;
      }
    }
  }

  if (ar_fire) {
    rreq_queue.push_back(mm_req_t(ar_id, 1 << ar_size, ar_len + 1, ar_addr));
  }

  if (aw_fire) {
    store_addr = aw_addr;
    store_id = aw_id;
    store_count = aw_len + 1;
    store_size = 1 << aw_size;
    store_inflight = true;
  }

  if (w_fire) {
    write(store_addr, (uint8_t*)w_data, w_strb, store_size);
    store_addr += store_size;
    store_count--;

    if (store_count == 0) {
      store_inflight = false;
      mem->addTransaction(true, store_addr);
      wreq[store_addr].push(store_id);
      assert(w_last);
    }
  }

  if (b_fire)
    bresp.pop();

  if (r_fire)
    rresp.pop();

  mem->update();
  cycle++;

  if (reset) {
    while (!bresp.empty()) bresp.pop();
    while (!rresp.empty()) rresp.pop();
    cycle = 0;
  }
}
