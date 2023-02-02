#include <riscv/simif.h>
#include <riscv/processor.h>
#include <riscv/log_file.h>
#include <fesvr/context.h>
#include <map>
#include <sstream>
#include <vpi_user.h>
#include <svdpi.h>

enum transfer_t {
  NToB,
  NToT,
  BToT
};

enum cache_state_t {
  NONE,
  BRANCH,
  TRUNK,
  DIRTY
};

struct cache_line_t {
  cache_state_t state;
  uint64_t addr;
  uint64_t data[8];
};

struct mem_region_t {
  uint64_t base;
  uint64_t size;
};

struct stq_entry_t {
  uint64_t addr;
  uint64_t bytes;
  size_t len;
};

struct cache_miss_t {
  bool valid;
  uint64_t addr;
  size_t way;
  transfer_t type;
};

struct writeback_t {
  cache_line_t line;
  cache_state_t desired;
  uint64_t sourceid;
  bool voluntary;
};


class chipyard_simif_t : public simif_t
{
public:
  char* addr_to_mem(reg_t addr) override { return NULL; };
  bool reservable(reg_t addr) override;
  bool mmio_fetch(reg_t addr, size_t len, uint8_t* bytes) override;
  bool mmio_load(reg_t addr, size_t len, uint8_t* bytes) override;
  bool mmio_store(reg_t addr, size_t len, const uint8_t* bytes) override;
  void proc_reset(unsigned id) override { };
  const char* get_symbol(uint64_t addr) override { return nullptr; };

  bool icache_a(uint64_t *address, uint64_t *source);
  void icache_d(uint64_t sourceid, uint64_t data[8]);

  bool mmio_a(uint64_t *address, uint64_t* data, unsigned char* store, int* size);
  void mmio_d(uint64_t data);

  bool dcache_a(uint64_t *address, uint64_t* source, unsigned char* state_old, unsigned char* state_new);
  void dcache_b(uint64_t address, uint64_t source, int param);
  bool dcache_c(uint64_t *address, uint64_t* source, int* param, unsigned char* voluntary, unsigned char* has_data, uint64_t* data[8]);
  void dcache_d(uint64_t sourceid, uint64_t data[8], unsigned char has_data, unsigned char grantack);
  void drain_stq();
  bool stq_empty() { return st_q.size() == 0; };
  
  ~chipyard_simif_t() { };
  chipyard_simif_t(size_t icache_ways,
                   size_t icache_sets,
                   size_t dcache_ways,
                   size_t dcache_sets,
                   char* cacheable,
                   char* uncacheable,
                   char* readonly_uncacheable,
                   char* executable,
                   size_t icache_sourceids,
                   size_t dcache_sourceids);
  uint64_t cycle;
  bool use_stq;
private:
  bool handle_cache_access(reg_t addr, size_t len,
                           uint8_t* load_bytes,
                           const uint8_t* store_bytes,
                           access_type type);
  void handle_mmio_access(reg_t addr, size_t len,
                          uint8_t* load_bytes,
                          const uint8_t* store_bytes,
                          access_type type,
                          bool readonly);

  size_t icache_ways;
  size_t icache_sets;
  size_t dcache_ways;
  size_t dcache_sets;

  std::vector<mem_region_t> cacheables;
  std::vector<mem_region_t> uncacheables;
  std::vector<mem_region_t> readonly_uncacheables;
  std::vector<mem_region_t> executables;

  std::vector<std::vector<cache_line_t>> dcache;
  std::vector<std::vector<cache_line_t>> icache;
  std::vector<size_t> icache_sourceids;
  std::vector<size_t> dcache_a_sourceids;
  std::vector<size_t> dcache_c_sourceids;
  std::vector<size_t> dcache_mmio_sourceids;

  std::vector<cache_miss_t> dcache_miss_q;
  std::vector<cache_miss_t> icache_miss_q;
  std::vector<cache_miss_t> icache_inflight;
  std::vector<cache_miss_t> dcache_inflight;
  std::vector<writeback_t> wb_q;
  std::vector<stq_entry_t> st_q;

  std::map<std::pair<uint64_t, size_t>, uint64_t> readonly_cache;

  bool mmio_valid;
  bool mmio_inflight;
  uint64_t mmio_addr;
  bool mmio_st;
  uint64_t mmio_stdata;
  size_t mmio_len;
  uint64_t mmio_lddata;
};

class tile_t {
public:
  tile_t(processor_t* p, chipyard_simif_t* s);
  processor_t* proc;
  chipyard_simif_t* simif;
  size_t max_insns;
  context_t spike_context;
  context_t stq_context;
};

context_t *host;
std::map<int, tile_t*> tiles;
std::ostream sout(nullptr);
log_file_t* log_file;

extern "C" void spike_tile_reset(int hartid)
{
  if (tiles.find(hartid) != tiles.end()) {
    tiles[hartid]->proc->reset();
  }
}

extern "C" void spike_tile(int hartid, char* isa,
                           int pmpregions,
                           int icache_sets, int icache_ways,
                           int dcache_sets, int dcache_ways,
                           char* cacheable, char* uncacheable, char* readonly_uncacheable, char* executable,
                           int icache_sourceids, int dcache_sourceids,
                           long long int reset_vector,
                           long long int ipc,
                           long long int cycle,
                           long long int* insns_retired,

                           char debug,
                           char mtip, char msip, char meip,
                           char seip,

                           unsigned char icache_a_ready,
                           unsigned char* icache_a_valid,
                           long long int* icache_a_address,
                           long long int* icache_a_sourceid,

                           unsigned char icache_d_valid,
                           long long int icache_d_sourceid,
                           long long int icache_d_data_0,
                           long long int icache_d_data_1,
                           long long int icache_d_data_2,
                           long long int icache_d_data_3,
                           long long int icache_d_data_4,
                           long long int icache_d_data_5,
                           long long int icache_d_data_6,
                           long long int icache_d_data_7,

                           unsigned char dcache_a_ready,
                           unsigned char* dcache_a_valid,
                           long long int* dcache_a_address,
                           long long int* dcache_a_sourceid,
                           unsigned char* dcache_a_state_old,
                           unsigned char* dcache_a_state_new,

                           unsigned char dcache_b_valid,
                           long long int dcache_b_address,
                           long long int dcache_b_source,
                           int dcache_b_param,

                           unsigned char dcache_c_ready,
                           unsigned char* dcache_c_valid,
                           long long int* dcache_c_address,
                           long long int* dcache_c_source,
                           int* dcache_c_param,
                           unsigned char* dcache_c_voluntary,
                           unsigned char* dcache_c_has_data,
                           long long int* dcache_c_data_0,
                           long long int* dcache_c_data_1,
                           long long int* dcache_c_data_2,
                           long long int* dcache_c_data_3,
                           long long int* dcache_c_data_4,
                           long long int* dcache_c_data_5,
                           long long int* dcache_c_data_6,
                           long long int* dcache_c_data_7,

                           unsigned char dcache_d_valid,
                           unsigned char dcache_d_has_data,
                           unsigned char dcache_d_grantack,
                           long long int dcache_d_sourceid,
                           long long int dcache_d_data_0,
                           long long int dcache_d_data_1,
                           long long int dcache_d_data_2,
                           long long int dcache_d_data_3,
                           long long int dcache_d_data_4,
                           long long int dcache_d_data_5,
                           long long int dcache_d_data_6,
                           long long int dcache_d_data_7,

                           unsigned char mmio_a_ready,
                           unsigned char* mmio_a_valid,
                           long long int* mmio_a_address,
                           long long int* mmio_a_data,
                           unsigned char* mmio_a_store,
                           int* mmio_a_size,

                           unsigned char mmio_d_valid,
                           long long int mmio_d_data
                           )
{
  if (!host) {
    host = context_t::current();
    sout.rdbuf(std::cerr.rdbuf());
    log_file = new log_file_t(nullptr);
  }
  if (tiles.find(hartid) == tiles.end()) {
    printf("Constructing spike processor_t\n");
    isa_parser_t *isa_parser = new isa_parser_t(isa, "MSU");
    chipyard_simif_t* simif = new chipyard_simif_t(icache_ways, icache_sets,
                                                   dcache_ways, dcache_sets,
                                                   cacheable, uncacheable, readonly_uncacheable, executable,
                                                   icache_sourceids, dcache_sourceids);
    std::string* isastr = new std::string(isa);
    cfg_t* cfg = new cfg_t(std::make_pair(0, 0),
                           nullptr,
                           isastr->c_str(),
                           "MSU",
                           "vlen:128,elen:64",
                           false,
                           endianness_little,
                           pmpregions,
                           std::vector<mem_cfg_t>(),
                           std::vector<int>(),
                           false,
                           0);
    processor_t* p = new processor_t(isa_parser,
                                     cfg,
                                     simif,
                                     hartid,
                                     false,
                                     log_file->get(),
                                     sout);

    p->enable_log_commits();

    s_vpi_vlog_info vinfo;
    if (!vpi_get_vlog_info(&vinfo))
      abort();
    for (int i = 1; i < vinfo.argc; i++) {
      std::string arg(vinfo.argv[i]);
      if (arg == "+spike-debug") {
        p->set_debug(true);
      }
      if (arg == "+spike-stq") {
        simif->use_stq = true;
      }
    }

    p->reset();
    p->get_state()->pc = reset_vector;
    tiles[hartid] = new tile_t(p, simif);
    printf("Done constructing spike processor\n");
  }
  tile_t* tile = tiles[hartid];
  chipyard_simif_t* simif = tile->simif;
  processor_t* proc = tile->proc;

  simif->cycle = cycle;
  if (debug) {
    proc->halt_request = proc->HR_REGULAR;
  }

  proc->get_state()->mip->backdoor_write_with_mask(MIP_MTIP, mtip ? MIP_MTIP : 0);
  proc->get_state()->mip->backdoor_write_with_mask(MIP_MSIP, msip ? MIP_MSIP : 0);
  proc->get_state()->mip->backdoor_write_with_mask(MIP_MEIP, meip ? MIP_MEIP : 0);
  proc->get_state()->mip->backdoor_write_with_mask(MIP_SEIP, seip ? MIP_SEIP : 0);

  tile->max_insns = ipc;
  uint64_t pre_insns = proc->get_state()->minstret->read();
  tile->spike_context.switch_to();
  *insns_retired = proc->get_state()->minstret->read() - pre_insns;
  if (simif->use_stq) {
    tile->stq_context.switch_to();
  }

  *icache_a_valid = 0;
  if (icache_a_ready) {
    *icache_a_valid = simif->icache_a((uint64_t*)icache_a_address,
                                      (uint64_t*)icache_a_sourceid);
  }

  if (icache_d_valid) {
    uint64_t data[8] = {icache_d_data_0, icache_d_data_1, icache_d_data_2, icache_d_data_3,
                        icache_d_data_4, icache_d_data_5, icache_d_data_6, icache_d_data_7};
    simif->icache_d(icache_d_sourceid, data);
  }

  *dcache_a_valid = 0;
  if (dcache_a_ready) {
    *dcache_a_valid = simif->dcache_a((uint64_t*)dcache_a_address,
                                      (uint64_t*)dcache_a_sourceid,
                                      dcache_a_state_old, dcache_a_state_new);
  }
  if (dcache_b_valid) {
    simif->dcache_b(dcache_b_address, dcache_b_source, dcache_b_param);
  }
  *dcache_c_valid = 0;
  if (dcache_c_ready) {
    uint64_t* data[8] = {(uint64_t*)dcache_c_data_0, (uint64_t*)dcache_c_data_1, (uint64_t*)dcache_c_data_2, (uint64_t*)dcache_c_data_3,
                         (uint64_t*)dcache_c_data_4, (uint64_t*)dcache_c_data_5, (uint64_t*)dcache_c_data_6, (uint64_t*)dcache_c_data_7};
    *dcache_c_valid = simif->dcache_c((uint64_t*)dcache_c_address, (uint64_t*)dcache_c_source, (int*)dcache_c_param,
                                      dcache_c_voluntary, dcache_c_has_data, data);
  }
  if (dcache_d_valid) {
    uint64_t data[8] = {dcache_d_data_0, dcache_d_data_1, dcache_d_data_2, dcache_d_data_3,
                        dcache_d_data_4, dcache_d_data_5, dcache_d_data_6, dcache_d_data_7};
    simif->dcache_d(dcache_d_sourceid, data, dcache_d_has_data, dcache_d_grantack);
  }

  *mmio_a_valid = 0;
  if (mmio_a_ready) {
    *mmio_a_valid = simif->mmio_a((uint64_t*)mmio_a_address, (uint64_t*) mmio_a_data,
                                  mmio_a_store, mmio_a_size);
  }
  if (mmio_d_valid) {
    simif->mmio_d(mmio_d_data);
  }
}


chipyard_simif_t::chipyard_simif_t(size_t icache_ways,
                                   size_t icache_sets,
                                   size_t dcache_ways,
                                   size_t dcache_sets,
                                   char* cacheable,
                                   char* uncacheable,
                                   char* readonly_uncacheable,
                                   char* executable,
                                   size_t ic_sourceids,
                                   size_t dc_sourceids
                                   ) :
  cycle(0),
  use_stq(false),
  icache_ways(icache_ways),
  icache_sets(icache_sets),
  dcache_ways(dcache_ways),
  dcache_sets(dcache_sets),
  mmio_valid(false),
  mmio_inflight(false)
{

  icache.resize(icache_ways);
  for (auto &w : icache) {
    w.resize(icache_sets);
    for (size_t i = 0; i < icache_sets; i++) w[i].state = NONE;
  }

  dcache.resize(dcache_ways);
  for (auto &w : dcache) {
    w.resize(dcache_sets);
    for (size_t i = 0; i < dcache_sets; i++) w[i].state = NONE;
  }
  for (int i = 0; i < ic_sourceids; i++) {
    icache_sourceids.push_back(i);
    icache_inflight.push_back(cache_miss_t { 0, 0, 0, NToB });
  }
  for (int i = 0; i < dc_sourceids; i++) {
    dcache_a_sourceids.push_back(i);
    dcache_c_sourceids.push_back(i);
    dcache_inflight.push_back(cache_miss_t { 0, 0, 0, NToB });
  }

  std::stringstream css(cacheable);
  std::stringstream uss(uncacheable);
  std::stringstream rss(readonly_uncacheable);
  std::stringstream xss(executable);
  std::string base;
  std::string size;
  while (css >> base) {
    css >> size;
    uint64_t base_int = std::stoul(base);
    uint64_t size_int = std::stoul(size);
    cacheables.push_back(mem_region_t { base_int, size_int });
  }
  while (uss >> base) {
    uss >> size;
    uint64_t base_int = std::stoul(base);
    uint64_t size_int = std::stoul(size);
    uncacheables.push_back(mem_region_t { base_int, size_int });
  }
  while (rss >> base) {
    rss >> size;
    uint64_t base_int = std::stoul(base);
    uint64_t size_int = std::stoul(size);
    readonly_uncacheables.push_back(mem_region_t { base_int, size_int });
  }
  while (xss >> base) {
    xss >> size;
    uint64_t base_int = std::stoul(base);
    uint64_t size_int = std::stoul(size);
    executables.push_back(mem_region_t { base_int, size_int });
  }
}

bool chipyard_simif_t::reservable(reg_t addr) {
  for (auto& r: cacheables) {
    if (addr >= r.base && addr < r.base + r.size) {
      return true;
    }
  }
  return false;
}

bool chipyard_simif_t::mmio_fetch(reg_t addr, size_t len, uint8_t* bytes) {
  bool executable = false;

  for (auto& r: executables) {
    if (addr >= r.base && addr + len <= r.base + r.size) {
      executable = true;
      break;
    }
  }
  if (!executable) {
    return false;
  }

  while (!handle_cache_access(addr, len, bytes, nullptr, FETCH)) {
    host->switch_to();
  }
  return true;
}

bool chipyard_simif_t::mmio_load(reg_t addr, size_t len, uint8_t* bytes) {
  bool found = false;
  bool cacheable = false;
  bool readonly = false;
  for (auto& r: cacheables) {
    if (addr >= r.base && addr + len <= r.base + r.size) {
      cacheable = true;
      found = true;
      break;
    }
  }
  if (!found) {
    for (auto& r: uncacheables) {
      if (addr >= r.base && addr + len <= r.base + r.size) {
        cacheable = false;
        found = true;
        break;
      }
    }
    for (auto& r: readonly_uncacheables) {
      if (addr >= r.base && addr + len <= r.base + r.size) {
        readonly = true;
        break;
      }
    }
  }
  
  if (!found) {
    return false;
  }

  if (cacheable) {
    while (!handle_cache_access(addr, len, bytes, nullptr, LOAD)) {
      host->switch_to();
    }
  } else {
    handle_mmio_access(addr, len, bytes, nullptr, LOAD, readonly);
  }

  return true;
}

void chipyard_simif_t::handle_mmio_access(reg_t addr, size_t len,
                                          uint8_t* load_bytes,
                                          const uint8_t* store_bytes,
                                          access_type type,
                                          bool readonly) {
  if (type == LOAD && readonly) {
    auto it = readonly_cache.find(std::make_pair(addr, len));
    if (it != readonly_cache.end()) {
      memcpy(load_bytes, &(it->second), len);
      return;
    }
  }

  mmio_valid = true;
  mmio_inflight = false;
  mmio_addr = addr;
  mmio_st = type == STORE;
  if (type == STORE) {
    assert(len <= 8);
    memcpy(&mmio_stdata, store_bytes, len);
  }
  mmio_len = len;

  while (mmio_valid) {
    host->switch_to();
  }
  if (type == LOAD) {
    memcpy(load_bytes , &mmio_lddata, len);
  }
  if (type == LOAD && readonly) {
    readonly_cache[std::make_pair(addr, len)] = mmio_lddata;
  }
}

bool chipyard_simif_t::handle_cache_access(reg_t addr, size_t len,
                                           uint8_t* load_bytes,
                                           const uint8_t* store_bytes,
                                           access_type type) {
  uint64_t stdata = 0;
  if (type == STORE) {
    assert(len <= 8);
    memcpy(&stdata, store_bytes, len);
  }

  // no stores to icache
  std::vector<std::vector<cache_line_t>> *cache = &icache;
  std::vector<cache_miss_t> *missq = &icache_miss_q;
  std::vector<cache_miss_t> *inflight = &icache_inflight;
  size_t n_sets = icache_sets;
  size_t n_ways = icache_ways;
  if (type != FETCH) {
    cache = &dcache;
    missq = &dcache_miss_q;
    inflight = &dcache_inflight;
    n_sets = dcache_sets;
    n_ways = dcache_ways;
  }
  if (type == LOAD) {
    for (auto& s : st_q) {
      if (addr == s.addr && len < s.len) {
        // Forwarding
        memcpy(load_bytes, &(s.bytes), len);
        return true;
      }
      if (addr < s.addr && addr + len > s.addr) {
        return false;
      }
      if (s.addr < addr && s.addr + s.len > addr) {
        return false;
      }
    }
  }
  
#define SETIDX(ADDR) ((ADDR >> 6) & (n_sets - 1))
  uint64_t setidx = SETIDX(addr);
  uint64_t offset = addr & (64 - 1);
  bool cache_hit = false;
  size_t hit_way = 0;
  for (int i = 0; i < n_ways; i++) {
    bool addr_match = ((*cache)[i][setidx].addr >> 6) == (addr >> 6);
    if (addr_match && (*cache)[i][setidx].state != NONE) {
      assert(!cache_hit);
      cache_hit = true;
      hit_way = i;
    }
  }

  if (type != STORE) {
    if (cache_hit) {
      memcpy(load_bytes, (uint8_t*)((*cache)[hit_way][setidx].data) + offset, len);
      return true;
    }
  } else {
    for (int i = 0; i < icache_ways; i++) {
      if ((icache[i][setidx].addr >> 6) == addr >> 6) {
        icache[i][setidx].state = NONE;
      }
    }
    if (cache_hit && dcache[hit_way][setidx].state != BRANCH) {
      dcache[hit_way][setidx].state = DIRTY;
      memcpy((uint8_t*)(dcache[hit_way][setidx].data) + offset, store_bytes, len);
      return true;
    }
  }

  for (auto& e : wb_q) {
    cache_line_t& cl = e.line;
    if (cl.addr >> 6 == addr >> 6) {
      return false;
    }
  }

  for (cache_miss_t& cl : *missq) {
    if (cl.addr >> 6 == addr >> 6) {
      return false;
    }
  }

  for (cache_miss_t& cl : *inflight) {
    if (cl.addr >> 6 == addr >> 6 && cl.valid) {
      return false;
    }
  }


  size_t repl_way = rand() % n_ways;
  transfer_t upgrade;
  size_t upgrade_way;
  bool do_repl;
  if (type == STORE) {
    if (cache_hit && (*cache)[hit_way][setidx].state != NONE) {
      upgrade = BToT;
      upgrade_way = hit_way;
      do_repl = false;
    } else {
      upgrade = NToT;
      upgrade_way = repl_way;
      do_repl = true;
    }
  } else {
    upgrade = NToB;
    upgrade_way = repl_way;
    do_repl = true;
  }
  if (do_repl) {
    for (auto& e : *missq) {
      if (SETIDX(e.addr) == setidx) {
        return false;
      }
    }
    for (auto& e : *inflight) {
      if (e.valid && SETIDX(e.addr) == setidx) {
        return false;
      }
    }
  }

  missq->push_back(cache_miss_t { true, addr, upgrade_way, upgrade });

  cache_line_t repl_cl = (*cache)[repl_way][setidx];
  if (do_repl) {
    if (repl_cl.state == DIRTY) {
      wb_q.push_back(writeback_t { repl_cl, NONE, 0, true});
    }
    (*cache)[repl_way][setidx].state = NONE;
  }
  (*cache)[upgrade_way][setidx].state = NONE;

  return false;
}

bool chipyard_simif_t::icache_a(uint64_t* address, uint64_t* sourceid) {
  if (icache_miss_q.empty() || icache_sourceids.empty()) {
    return false;
  }
  *sourceid = icache_sourceids[0];
  *address = (icache_miss_q[0].addr >> 6) << 6;

  icache_inflight[icache_sourceids[0]] = icache_miss_q[0];

  icache_sourceids.erase(icache_sourceids.begin());
  icache_miss_q.erase(icache_miss_q.begin());

  return true;
}

void chipyard_simif_t::icache_d(uint64_t sourceid, uint64_t data[8]) {
  cache_miss_t& miss = icache_inflight[sourceid];
  uint64_t setidx = (miss.addr >> 6) & (icache_sets - 1);
  icache_inflight[sourceid].valid = false;
  icache[miss.way][setidx].state = BRANCH;
  icache[miss.way][setidx].addr = miss.addr;
  memcpy(icache[miss.way][setidx].data, (void*)data, 64);
  icache_sourceids.push_back(sourceid);
}

bool chipyard_simif_t::mmio_a(uint64_t* address, uint64_t* data, unsigned char* store, int* size) {
  if (!mmio_valid || mmio_inflight) {
    return false;
  }
  mmio_inflight = true;
  *address = mmio_addr;
  *store = mmio_st;
  *data = mmio_stdata;
  *size = mmio_len;
  return true;
}

void chipyard_simif_t::mmio_d(uint64_t data) {
  mmio_valid = false;
  mmio_inflight = false;
  size_t offset = mmio_addr & 7;
  mmio_lddata = data >> (offset * 8);
}

bool chipyard_simif_t::dcache_a(uint64_t *address, uint64_t* source, unsigned char* state_old, unsigned char* state_new) {
  if (dcache_miss_q.empty() || dcache_a_sourceids.empty()) {
    return false;
  }
  *source = dcache_a_sourceids[0];
  *address = (dcache_miss_q[0].addr >> 6) << 6;
  switch (dcache_miss_q[0].type) {
  case NToB:
    *state_old = 0;
    *state_new = 0;
    break;
  case NToT:
    *state_old = 0;
    *state_new = 1;
    break;
  case BToT:
    *state_old = 1;
    *state_new = 1;
    break;
  }

  dcache_inflight[dcache_a_sourceids[0]] = dcache_miss_q[0];
  dcache_a_sourceids.erase(dcache_a_sourceids.begin());
  dcache_miss_q.erase(dcache_miss_q.begin());
  return true;
}

void chipyard_simif_t::dcache_b(uint64_t address, uint64_t source, int param) {
  uint64_t setidx = (address >> 6) & (dcache_sets - 1);
  uint64_t offset = address & (64 - 1);
  bool cache_hit = false;
  size_t hit_way = 0;
  for (int i = 0; i < dcache_ways; i++) {
    bool addr_match = dcache[i][setidx].addr >> 6 == address >> 6;
    if (addr_match && dcache[i][setidx].state != NONE) {
      cache_hit = true;
      hit_way = i;
    }
  }
  cache_state_t desired;
  switch (param) {
  case 0:
    desired = TRUNK;
    break;
  case 1:
    desired = BRANCH;
    break;
  case 2:
    desired = NONE;
    break;
  }
  if (!cache_hit) {
    cache_line_t miss { NONE, address, {} };
    wb_q.push_back(writeback_t { miss, desired, source, false});
  } else {
    wb_q.push_back(writeback_t { dcache[hit_way][setidx], desired, source, false});
    if (desired == TRUNK && dcache[hit_way][setidx].state == BRANCH) {
      dcache[hit_way][setidx].state = BRANCH;
    } else {
      dcache[hit_way][setidx].state = desired;
    }

  }
}

bool chipyard_simif_t::dcache_c(uint64_t* address, uint64_t* source, int* param, unsigned char* voluntary,
                                unsigned char* has_data,
                                uint64_t* data[8]) {
  if (wb_q.empty())
    return false;

  writeback_t& wb = wb_q[0];
  if (wb.voluntary && dcache_c_sourceids.empty())
    return false;

  *address = (wb.line.addr >> 6) << 6;
  *source = wb.sourceid;
  *voluntary = wb.voluntary;
  if (wb.voluntary) {
    *source = dcache_c_sourceids[0];
    dcache_c_sourceids.erase(dcache_c_sourceids.begin());
  }

#define SHRINK(_desired, _state, _has_data, _param)       \
  if (wb.line.state == _state && wb.desired == _desired) { \
    *has_data = _has_data; \
    *param = _param; \
  }

  SHRINK(TRUNK  , DIRTY  , true , 3);
  SHRINK(TRUNK  , TRUNK  , false, 3);
  SHRINK(TRUNK  , BRANCH , false, 4);
  SHRINK(TRUNK  , NONE   , false, 5);
  SHRINK(BRANCH , DIRTY  , true , 0);
  SHRINK(BRANCH , TRUNK  , false, 0);
  SHRINK(BRANCH , BRANCH , false, 4);
  SHRINK(BRANCH , NONE   , false, 5);
  SHRINK(NONE   , DIRTY  , true , 1);
  SHRINK(NONE   , TRUNK  , false, 1);
  SHRINK(NONE   , BRANCH , false, 2);
  SHRINK(NONE   , NONE   , false, 5);

  for (int i = 0; i < 8; i++) {
    *(data[i]) = wb.line.data[i];
  }
  wb_q.erase(wb_q.begin());
  return true;
}

bool chipyard_simif_t::mmio_store(reg_t addr, size_t len, const uint8_t* bytes) {
  bool found = false;
  bool cacheable = false;
   for (auto& r: cacheables) {
    if (addr >= r.base && addr + len <= r.base + r.size) {
      cacheable = true;
      found = true;
      break;
    }
  }
  for (auto& r: uncacheables) {
    if (addr >= r.base && addr + len <= r.base + r.size) {
      cacheable = false;
      found = true;
      break;
    }
  }
  if (!found) {
    return false;
  }
  if (cacheable) {
    if (use_stq) {
      assert(len <= 8);
      uint64_t stdata;
      memcpy(&stdata, bytes, len);
      st_q.push_back(stq_entry_t { addr, stdata, len });
    } else {
      while (!handle_cache_access(addr, len, nullptr, bytes, STORE)) {
        host->switch_to();
      }
    }
  } else {
    handle_mmio_access(addr, len, nullptr, bytes, STORE, false);
  }

  return true;
}

void chipyard_simif_t::drain_stq() {
  while (true) {
    while (st_q.size() == 0) {
      host->switch_to();
    }
    stq_entry_t store = st_q[0];
    while (!handle_cache_access(store.addr, store.len, nullptr, (uint8_t*)(&(store.bytes)), STORE)) {
      host->switch_to();
    }
    st_q.erase(st_q.begin());
  }
}

void chipyard_simif_t::dcache_d(uint64_t sourceid, uint64_t data[8], unsigned char has_data, unsigned char grantack) {
  if (grantack) {
    cache_miss_t& miss = dcache_inflight[sourceid];
    uint64_t setidx = (miss.addr >> 6) & (dcache_sets - 1);
    if (has_data) {
      memcpy(dcache[miss.way][setidx].data, (void*)data, 64);
    }
    dcache_inflight[sourceid].valid = false;
    if (miss.type == NToB) {
      dcache[miss.way][setidx].state = BRANCH;
    } else {
      dcache[miss.way][setidx].state = TRUNK;
    }
    dcache[miss.way][setidx].addr = miss.addr;
    dcache_a_sourceids.push_back(sourceid);
  } else {
    dcache_c_sourceids.push_back(sourceid);
  }
}

bool insn_should_fence(uint64_t bits) {
  uint8_t opcode = bits & 0x7f;
  return opcode == 0b0101111 || opcode == 0b0001111;
}

void spike_thread_main(void* arg)
{
  tile_t* tile = (tile_t*) arg;
  while (true) {
    while (tile->max_insns == 0) {
      host->switch_to();
    }
    while (tile->max_insns != 0) {
      // TODO: Fences don't work
      // uint64_t last_bits = tile->proc->get_last_bits();
      // if (insn_should_fence(last_bits) && !tile->simif->stq_empty()) {
      //   host->switch_to();
      // }
      tile->proc->step(1);
      tile->max_insns--;
      tile->proc->get_state()->mcycle->write(tile->simif->cycle);
    }
  }
}

void stq_thread_main(void* arg)
{
  tile_t* tile = (tile_t*) arg;
  tile->simif->drain_stq();
}

tile_t::tile_t(processor_t* p, chipyard_simif_t* s) : proc(p), simif(s), max_insns(0) {
  spike_context.init(spike_thread_main, this);
  stq_context.init(stq_thread_main, this);
}
