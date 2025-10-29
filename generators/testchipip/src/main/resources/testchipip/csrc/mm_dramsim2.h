// See LICENSE for license details.

#ifndef _MM_EMULATOR_DRAMSIM2_H
#define _MM_EMULATOR_DRAMSIM2_H

#include "mm.h"
#include <DRAMSim.h>
#include <map>
#include <queue>
#include <list>
#include <stdint.h>

struct mm_req_t {
  uint64_t id;
  uint64_t size;
  uint64_t len;
  uint64_t addr;

  mm_req_t(uint64_t id, uint64_t size, uint64_t len, uint64_t addr)
  {
    this->id = id;
    this->size = size;
    this->len = len;
    this->addr = addr;
  }

  mm_req_t()
  {
    this->id = 0;
    this->size = 0;
    this->len = 0;
    this->addr = 0;
  }
};

class mm_dramsim2_t : public mm_t
{
 public:
  mm_dramsim2_t(size_t mem_base, size_t mem_sz, size_t word_sz, size_t line_sz, backing_data_t& dat, std::string memory_ini, std::string system_ini, std::string ini_dir, int axi4_ids, size_t clock_hz);

  virtual bool ar_ready();
  virtual bool aw_ready();
  virtual bool w_ready() { return store_inflight; }
  virtual bool b_valid() { return !bresp.empty(); }
  virtual uint64_t b_resp() { return 0; }
  virtual uint64_t b_id() { return b_valid() ? bresp.front() : 0; }
  virtual bool r_valid() { return !rresp.empty(); }
  virtual uint64_t r_resp() { return 0; }
  virtual uint64_t r_id() { return r_valid() ? rresp.front().id: 0; }
  virtual void *r_data() { return r_valid() ? (void*) &rresp.front().data[0] : data; }
  virtual bool r_last() { return r_valid() ? rresp.front().last : false; }

  virtual void tick
  (
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
    bool b_ready
  );


 protected:
  DRAMSim::MultiChannelMemorySystem *mem;
  uint64_t cycle;

  bool store_inflight = false;
  uint64_t store_addr;
  uint64_t store_id;
  uint64_t store_size;
  uint64_t store_count;
  std::queue<uint64_t> bresp;

  // Keep a FIFO of IDs that made reads to an address since Dramsim2 doesn't
  // track it. Reads or writes to the same address from different IDs can
  // collide
  std::map<uint64_t, std::queue<uint64_t>> wreq;
  std::map<uint64_t, std::queue<mm_req_t>> rreq;
  std::queue<mm_rresp_t> rresp;
  //std::map<uint64_t, std::queue<mm_rresp_t> > rreq;


  // Track inflight requests by putting indexes to their positions in the
  // stimulus vector in queues for each AXI channel
  std::vector<bool> read_id_busy;
  std::vector<bool> write_id_busy;
  std::list<mm_req_t> rreq_queue;

  uint64_t clock_hz = 0;

  void read_complete(unsigned id, uint64_t address, uint64_t clock_cycle);
  void write_complete(unsigned id, uint64_t address, uint64_t clock_cycle);
};

#endif
