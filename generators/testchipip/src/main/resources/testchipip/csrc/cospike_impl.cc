#include "cospike_impl.h"

#include <cstdint>
#include <vector>
#include <string>
#include <riscv/sim.h>
#include <riscv/mmu.h>
#include <riscv/encoding.h>
#include <sstream>
#include <iterator>
#include <set>
#include <sys/types.h>
#include <sys/mman.h>
#include <sys/types.h>
#include <unistd.h>
#include <sys/syscall.h>
#include <fcntl.h>

#ifndef FIRESIM
#if __has_include ("cospike_dtm.h")
#define COSPIKE_DTM
#include "testchip_dtm.h"
extern testchip_dtm_t* dtm;
bool spike_loadarch_done = false;
#endif

#if __has_include ("mm.h")
#define COSPIKE_SIMDRAM
#include "mm.h"
extern std::vector<std::map<unsigned long long int, backing_data_t>> backing_mem_data;
#endif
#endif

#define _DEFAULT_BOOT_ADDR (0x80000000)
#define _BOOT_ADDR_BASE (0x1000)
#define _CLINT_BASE (0x2000000)
#define _CLINT_SIZE (0x10000)
#define _UART_BASE (0x10020000)
#define _UART_SIZE (0x1000)
#define _PLIC_BASE (0xc000000)
#define _PLIC_SIZE (0x4000000)

// address of the PC register after reset
#define _RESET_VECTOR (0x10000)

#define COSPIKE_PRINTF(...) {                   \
  fprintf(stderr, "Cosim: " __VA_ARGS__);                 \
  }

struct system_info_t {
  std::string isa;
  int pmpregions;
  int maxpglevels;
  uint64_t mem0_base;
  uint64_t mem0_size;
  uint64_t mem1_base;
  uint64_t mem1_size;
  uint64_t mem2_base;
  uint64_t mem2_size;
  int nharts;
  std::vector<char> bootrom;
  std::string priv;
  std::vector<std::string> htif_args;
};

class read_override_device_t : public abstract_device_t {
public:
  read_override_device_t(std::string n, reg_t sz) : was_read_from(false), extent(sz), name(n) { };
  virtual bool load(reg_t addr, size_t len, uint8_t* bytes) override {
    if (addr + len > extent) return false;
    COSPIKE_PRINTF("Read from device %s at %" PRIx64 "\n", name.c_str(), addr);
    was_read_from = true;
    return true;
  }
  virtual bool store(reg_t addr, size_t len, const uint8_t* bytes) override {
    COSPIKE_PRINTF("Store to device %s at %" PRIx64 "\n", name.c_str(), addr);
    return (addr + len <= extent);
  }
  bool was_read_from;
  reg_t size() override { return extent; }
private:
  reg_t extent;
  std::string name;
};

system_info_t* info = NULL;
std::vector<std::pair<uint64_t, uint64_t>> mem_info;
sim_t* sim = NULL;
bool cospike_debug;
bool cospike_enable = true;
bool cospike_printf = true;
reg_t tohost_addr = 0;
reg_t fromhost_addr = 0;
reg_t cospike_timeout = 0;
std::set<reg_t> magic_addrs;
cfg_t* cfg;
std::vector<std::shared_ptr<read_override_device_t>> read_override_devices;
std::vector<std::pair<reg_t, std::shared_ptr<abstract_device_t>>> devices;

static std::vector<std::pair<reg_t, abstract_mem_t*>> make_mems(const std::vector<mem_cfg_t> &layout)
{
  std::vector<std::pair<reg_t, abstract_mem_t*>> mems;
  mems.reserve(layout.size());
  for (const auto &cfg : layout) {
    mems.push_back(std::make_pair(cfg.get_base(), new mem_t(cfg.get_size())));
  }
  return mems;
}

bool mem_already_exists(uint64_t base, uint64_t size) {
  return std::any_of(mem_info.begin(), mem_info.end(), [base, size](const std::pair<uint64_t, uint64_t>& p) {
    bool match = p.first == base;
    if (match && p.second != size) {
      COSPIKE_PRINTF("Conflicting mem config with legacy API (%lx, %lx) != (%lx, %lx)\n",
                     p.first, p.second, base, size);
      exit(1);
    }
    return match;
  });
}

void cospike_set_sysinfo(char* isa, char* priv, int pmpregions, int maxpglevels,
			 unsigned long long int mem0_base, unsigned long long int mem0_size,
			 unsigned long long int mem1_base, unsigned long long int mem1_size,
                         unsigned long long int mem2_base, unsigned long long int mem2_size,
			 int nharts,
			 char* bootrom,
			 std::vector<std::string> &args
			 ) {
  if (!info) {
    info = new system_info_t;
    // technically the targets aren't zicntr compliant, but they implement the zicntr registers
    info->isa = std::string(isa) + "_zicntr";
    info->priv = std::string(priv);
    info->maxpglevels = maxpglevels;
    info->pmpregions = pmpregions;
    info->mem0_base = mem0_base;
    info->mem0_size = mem0_size;
    info->mem1_base = mem1_base;
    info->mem1_size = mem1_size;
    info->mem2_base = mem2_base;
    info->mem2_size = mem2_size;
    info->nharts = nharts;
    std::stringstream ss(bootrom);
    std::string s;
    while (ss >> s) {
      info->bootrom.push_back(std::stoi(s));
    }

    bool in_permissive = false;
    cospike_debug = false;
    for (const auto& arg : args) {
      if (arg == "+permissive") {
        in_permissive = true;
      } else if (arg == "+permissive-off") {
        in_permissive = false;
      } else if (arg == "+cospike_debug" || arg == "+cospike-debug") {
        cospike_debug = true;
      } else if (arg.find("+cospike-timeout=") == 0) {
        cospike_timeout = strtoull(arg.substr(17).c_str(), 0, 10);
      } else if (arg.find("+cospike-printf=") == 0) {
	cospike_printf = strtoull(arg.substr(16).c_str(), 0, 10) != 0;
      } else if (arg.find("+cospike-enable=") == 0) {
	cospike_enable = strtoull(arg.substr(16).c_str(), 0, 10) != 0;
      } else if (!in_permissive) {
        info->htif_args.push_back(arg);
      }
    }
  }
}

void cospike_register_memory(unsigned long long int base,
                             unsigned long long int size)
{
  if (sim) {
    COSPIKE_PRINTF("Memories must be registered prior to sim execution\n");
    exit(1);
  }
  mem_info.push_back(std::make_pair(base, size));
}

int cospike_cosim(unsigned long long int cycle,
                  unsigned long long int hartid,
                  int has_wdata,
                  int valid,
                  unsigned long long int iaddr,
                  unsigned long int insn,
                  int raise_exception,
                  int raise_interrupt,
                  unsigned long long int cause,
                  unsigned long long int wdata,
                  int priv)
{
  assert(info);

  if (!cospike_enable) { return 0; }
  if (unlikely(!sim)) {
#ifdef COSPIKE_SIMDRAM
    // memory_init in SimDRAM.cc needs to run first
    if (backing_mem_data.size() < 1) return 0;
#endif
    COSPIKE_PRINTF("Configuring spike cosim\n");
    std::vector<mem_cfg_t> mem_cfg;
    std::vector<size_t> hartids;
    for (auto &t : mem_info)
      mem_cfg.push_back(mem_cfg_t(t.first, t.second));

    // legacy mem API
    if (info->mem0_base != 0 && !mem_already_exists(info->mem0_base, info->mem0_size))
      mem_cfg.push_back(mem_cfg_t(info->mem0_base, info->mem0_size));
    if (info->mem1_base != 0 && !mem_already_exists(info->mem1_base, info->mem1_size))
      mem_cfg.push_back(mem_cfg_t(info->mem1_base, info->mem1_size));
    if (info->mem2_base != 0 && !mem_already_exists(info->mem2_base, info->mem2_size))
      mem_cfg.push_back(mem_cfg_t(info->mem2_base, info->mem2_size));

    for (int i = 0; i < info->nharts; i++)
      hartids.push_back(i);

    cfg = new cfg_t();
    cfg->initrd_bounds = std::make_pair(0, 0);
    cfg->bootargs = nullptr;
    cfg->isa = info->isa.c_str();
    cfg->priv = info->priv.c_str();
    cfg->misaligned = false;
    cfg->endianness = endianness_little;
    cfg->pmpregions = info->pmpregions;
    cfg->mem_layout = mem_cfg;
    cfg->hartids = hartids;
    cfg->explicit_hartids =  false;
    cfg->trigger_count = 0;

    std::vector<std::pair<reg_t, abstract_mem_t*>> mems = make_mems(cfg->mem_layout);

    size_t default_boot_rom_size = 0x10000;
    size_t default_boot_rom_addr = 0x10000;
    assert(info->bootrom.size() < default_boot_rom_size);
    info->bootrom.resize(default_boot_rom_size);

    std::shared_ptr<rom_device_t> boot_rom = std::make_shared<rom_device_t>(info->bootrom);

    std::shared_ptr<mem_t> boot_addr_reg = std::make_shared<mem_t>(PGSIZE); // must have size of 4KiB (at minimum)
    uint64_t default_boot_addr = _DEFAULT_BOOT_ADDR;
    boot_addr_reg.get()->store(_BOOT_ADDR_BASE - _BOOT_ADDR_BASE, 8, (const uint8_t*)(&default_boot_addr));

    std::shared_ptr<read_override_device_t> clint = std::make_shared<read_override_device_t>("clint", _CLINT_SIZE);
    std::shared_ptr<read_override_device_t> uart = std::make_shared<read_override_device_t>("uart", _UART_SIZE);
    std::shared_ptr<read_override_device_t> plic = std::make_shared<read_override_device_t>("plic", _PLIC_SIZE);

    read_override_devices.push_back(clint);
    read_override_devices.push_back(uart);
    read_override_devices.push_back(plic);

    // The device map is hardcoded here for now
    devices.push_back(std::pair(_BOOT_ADDR_BASE, boot_addr_reg));
    devices.push_back(std::pair(default_boot_rom_addr, boot_rom));
    devices.push_back(std::pair(_CLINT_BASE, clint));
    devices.push_back(std::pair(_UART_BASE, uart));
    devices.push_back(std::pair(_PLIC_BASE, plic));

    debug_module_config_t dm_config = {
      .progbufsize = 2,
      .max_sba_data_width = 0,
      .require_authentication = false,
      .abstract_rti = 0,
      .support_hasel = true,
      .support_abstract_csr_access = true,
      .support_abstract_fpr_access = true,
      .support_haltgroups = true,
      .support_impebreak = true
    };

    COSPIKE_PRINTF("isa string: %s\n", info->isa.c_str());
    COSPIKE_PRINTF("priv: %s\n", info->priv.c_str());
    COSPIKE_PRINTF("pmpregions: %d\n", info->pmpregions);
    COSPIKE_PRINTF("harts: %d\n", info->nharts);

    std::stringstream s;
    std::copy(info->htif_args.begin(), info->htif_args.end(), std::ostream_iterator<std::string>(s, ", "));
    COSPIKE_PRINTF("htif args: %s\n", s.str().erase(s.str().size() - 2).c_str());

    const std::vector<std::pair<const device_factory_t*, std::vector<std::string>>> plugin_device_factories;
    sim = new sim_t(cfg, false,
                    mems,
                    plugin_device_factories,
                    info->htif_args,
                    dm_config,
                    nullptr,
                    false,
                    nullptr,
                    false,
                    nullptr,
                    std::nullopt
                    );
    for (auto &it : devices)
      sim->add_device(it.first, it.second);

#ifdef COSPIKE_SIMDRAM
    // match sim_t's backing memory with the SimDRAM memory
    bus_t temp_mem_bus;
    for (auto& pair : mems) temp_mem_bus.add_device(pair.first, pair.second);

    for (auto& pair : backing_mem_data[0]) {
      size_t base = pair.first;
      size_t size = pair.second.size;
      COSPIKE_PRINTF("Matching spike memory initial state for region %zx-%zx\n", base, base + size);
      if (!temp_mem_bus.store(base, size, pair.second.data)) {
        COSPIKE_PRINTF("Error, unable to match memory at address %zx\n", base);
        abort();
      }
    }
#endif

    assert(info->maxpglevels >= 3 && info->maxpglevels <= 5);
    sim->configure_log(true, true);
    for (int i = 0; i < info->nharts; i++) {
      // Use our own reset vector
      sim->get_core(hartid)->get_state()->pc = _RESET_VECTOR;
      // Set MMU capability
      sim->get_core(hartid)->set_impl(IMPL_MMU_SV48, info->maxpglevels >= 4);
      sim->get_core(hartid)->set_impl(IMPL_MMU_SV57, info->maxpglevels >= 5);
      // targets generally don't support ASIDs
      sim->get_core(hartid)->set_impl(IMPL_MMU_ASID, false);
      // HACKS: Our processor's don't implement zicntr fully, they don't provide time
      sim->get_core(hartid)->get_state()->csrmap.erase(CSR_TIME);
    }
    sim->set_debug(cospike_debug);
    sim->set_histogram(true);
    sim->set_procs_debug(cospike_debug);
    COSPIKE_PRINTF("Setting up htif\n");
    ((htif_t*)sim)->start();
    COSPIKE_PRINTF("Started\n");
    tohost_addr = ((htif_t*)sim)->get_tohost_addr();
    fromhost_addr = ((htif_t*)sim)->get_fromhost_addr();
    COSPIKE_PRINTF("Tohost addr   : %" PRIx64 "\n", tohost_addr);
    COSPIKE_PRINTF("Fromhost addr : %" PRIx64 "\n", fromhost_addr);
    COSPIKE_PRINTF("BootROM base  : %" PRIx64 "\n", default_boot_rom_addr);
    COSPIKE_PRINTF("BootROM size  : %" PRIx64 "\n", boot_rom->contents().size());
    for (auto &cfg : mem_cfg) {
      COSPIKE_PRINTF("Memory base  : %" PRIx64 "\n", cfg.get_base());
      COSPIKE_PRINTF("Memory size  : %" PRIx64 "\n", cfg.get_size());
    }
  }

  if (priv & 0x4) { // debug
    return 0;
  }

  if (cospike_timeout && cycle > cospike_timeout) {
    if (sim) {
      COSPIKE_PRINTF("Reached timeout cycles = %" PRIu64 ", terminating\n", cospike_timeout);
      delete sim;
    }
    return 0;
  }


  processor_t* p = sim->get_core(hartid);
  state_t* s = p->get_state();
#ifdef COSPIKE_DTM
  if (dtm && dtm->loadarch_done && !spike_loadarch_done) {
    COSPIKE_PRINTF("Restoring spike state from testchip_dtm loadarch\n");
    // copy the loadarch state into the cosim
    loadarch_state_t &ls = dtm->loadarch_state[hartid];
    s->pc  = ls.pc;
    s->prv = ls.prv;
    s->csrmap[CSR_MSTATUS]->write(s->csrmap[CSR_MSTATUS]->read() | MSTATUS_VS | MSTATUS_XS | MSTATUS_FS);
#define RESTORE(CSRID, csr) s->csrmap[CSRID]->write(ls.csr);
    RESTORE(CSR_STVEC    , stvec);
    RESTORE(CSR_SSCRATCH , sscratch);
    RESTORE(CSR_SEPC     , sepc);
    RESTORE(CSR_SCAUSE   , scause);
    RESTORE(CSR_STVAL    , stval);
    RESTORE(CSR_SATP     , satp);
    RESTORE(CSR_MSTATUS  , mstatus);
    RESTORE(CSR_MEDELEG  , medeleg);
    RESTORE(CSR_MIDELEG  , mideleg);
    RESTORE(CSR_MIE      , mie);
    RESTORE(CSR_MTVEC    , mtvec);
    RESTORE(CSR_MSCRATCH , mscratch);
    RESTORE(CSR_MEPC     , mepc);
    RESTORE(CSR_MCAUSE   , mcause);
    RESTORE(CSR_MTVAL    , mtval);
    RESTORE(CSR_MIP      , mip);
    RESTORE(CSR_MCYCLE   , mcycle);
    RESTORE(CSR_MINSTRET , minstret);

    // assuming fprs are always present
    RESTORE(CSR_FCSR     , fcsr);
    for (size_t i = 0; i < 32; i++) {
      s->XPR.write(i, ls.XPR[i]);
      s->FPR.write(i, { (uint64_t)ls.FPR[i], (uint64_t)-1 });
    }

    if (ls.VLEN != p->VU.VLEN) {
      COSPIKE_PRINTF("VLEN mismatch loadarch: %" PRIu64 " != spike: %" PRIu64 "\n", ls.VLEN, p->VU.VLEN);
      abort();
    }
    if (ls.ELEN != p->VU.ELEN) {
      COSPIKE_PRINTF("ELEN mismatch loadarch: %" PRIu64 " != spike: %" PRIu64 "\n", ls.ELEN, p->VU.ELEN);
      abort();
    }
    if (ls.VLEN != 0 && ls.ELEN != 0) {
      RESTORE(CSR_VSTART   , vstart);
      RESTORE(CSR_VXSAT    , vxsat);
      RESTORE(CSR_VXRM     , vxrm);
      RESTORE(CSR_VCSR     , vcsr);
      RESTORE(CSR_VTYPE    , vtype);
      for (size_t i = 0; i < 32; i++) {
        memcpy(p->VU.reg_file + i * ls.VLEN / 8, ls.VPR[i], ls.VLEN / 8);
      }
    }

    spike_loadarch_done = true;
    p->clear_waiting_for_interrupt();
    COSPIKE_PRINTF("Done restoring spike state from testchip_dtm loadarch\n");
  }
#endif
  uint64_t s_pc = s->pc;
  uint64_t interrupt_cause = cause & 0x7FFFFFFFFFFFFFFF;
  bool ssip_interrupt = interrupt_cause == 0x1;
  bool msip_interrupt = interrupt_cause == 0x3;
  bool stip_interrupt = interrupt_cause == 0x5;
  bool mtip_interrupt = interrupt_cause == 0x7;
  bool seip_interrupt = interrupt_cause == 0x9;
  bool debug_interrupt = interrupt_cause == 0xe;
  // SEIP interrupts can be triggered by either mip.seip, or an external source
  // We can't model external interrupts properly with Spike, so if it might be external,
  // set mip.seip to trigger the interrupt, but restore
  bool unset_seip = false;
  if (raise_interrupt) {
    COSPIKE_PRINTF("%" PRIu64 " interrupt %" PRIx32 "\n", cycle, cause);

    if (ssip_interrupt || stip_interrupt) {
      // do nothing
    } else if (msip_interrupt) {
      s->mip->backdoor_write_with_mask(MIP_MSIP, MIP_MSIP);
    } else if (mtip_interrupt) {
      s->mip->backdoor_write_with_mask(MIP_MTIP, MIP_MTIP);
    } else if (debug_interrupt) {
      return 0;
    } else if (seip_interrupt) {
      if (s->mip->read() & MIP_SEIP) {
        // mip.seip already set, so do nothing
      } else {
        // mip.seip not set, so set it and remember to restore
        s->mip->backdoor_write_with_mask(MIP_SEIP, MIP_SEIP);
        unset_seip = true;
      }
    } else {
      COSPIKE_PRINTF("Unknown interrupt %" PRIx32 "\n", interrupt_cause);
      return 2;
    }
  }
  if (raise_exception)
    COSPIKE_PRINTF("%" PRIu64 " exception %" PRIx32 "\n", cycle, cause);
  if (valid) {
    p->clear_waiting_for_interrupt();
    if (cospike_printf) {
      COSPIKE_PRINTF("%" PRIu64 " commit: %" PRIx64 "\n", cycle, iaddr);
    }
  }
  if (valid || raise_interrupt || raise_exception) {
    p->clear_waiting_for_interrupt();
    for (auto& e : read_override_devices) e.get()->was_read_from = false;
    p->step(1);
    if (unlikely(cospike_debug)) {
      COSPIKE_PRINTF("spike pc is %" PRIx64 "\n", s->pc);
      COSPIKE_PRINTF("spike mstatus is %" PRIx64 "\n", s->mstatus->read());
      COSPIKE_PRINTF("spike mip is %" PRIx64 "\n", s->mip->read());
      COSPIKE_PRINTF("spike mie is %" PRIx64 "\n", s->mie->read());
      COSPIKE_PRINTF("spike wfi state is %d\n", p->is_waiting_for_interrupt());
    }
  }
  if (unset_seip) {
    s->mip->backdoor_write_with_mask(MIP_SEIP, 0);
  }

  if (valid && !raise_exception) {
    if (s_pc != iaddr) {
      COSPIKE_PRINTF("%" PRIx64 " PC mismatch spike %" PRIx64 " != DUT %" PRIx64 "\n", cycle, s_pc, iaddr);
      if (unlikely(cospike_debug)) {
        COSPIKE_PRINTF("spike mstatus is %" PRIx64 "\n", s->mstatus->read());
        COSPIKE_PRINTF("spike mcause is %" PRIx64 "\n", s->mcause->read());
        COSPIKE_PRINTF("spike mtval is %" PRIx64 "\n" , s->mtval->read());
        COSPIKE_PRINTF("spike mtinst is %" PRIx64 "\n", s->mtinst->read());
      }
      return 1;
    }


    auto& mem_write = s->log_mem_write;
    auto& log = s->log_reg_write;
    auto& mem_read = s->log_mem_read;

    for (auto memwrite : mem_write) {
      reg_t waddr = std::get<0>(memwrite);
      uint64_t w_data = std::get<1>(memwrite);
      if ((waddr == _CLINT_BASE + 4*hartid) && w_data == 0) {
        s->mip->backdoor_write_with_mask(MIP_MSIP, 0);
      }
      if ((waddr == _CLINT_BASE + 0x4000 + 4*hartid)) {
        s->mip->backdoor_write_with_mask(MIP_MTIP, 0);
      }
      // Try to remember magic_mem addrs, and ignore these in the future
      if ( waddr == tohost_addr && w_data >= info->mem0_base && w_data < (info->mem0_base + info->mem0_size)) {
        COSPIKE_PRINTF("Probable magic mem %" PRIx64 "\n", w_data);
        magic_addrs.insert(w_data);
      }
    }

    //bool scalar_wb = false;
    uint32_t vector_cnt = 0;
    std::vector<reg_t> vector_rds;

    for (auto &regwrite : log) {

      //TODO: scaling to multi issue reads?
      reg_t mem_read_addr = mem_read.empty() ? 0 : std::get<0>(mem_read[0]);

      int rd = regwrite.first >> 4;
      int type = regwrite.first & 0xf;

      // 0 => int
      // 1 => fp
      // 2 => vec
      // 3 => vec hint
      // 4 => csr
      bool device_read = false;
      for (auto& e : read_override_devices) if (e.get()->was_read_from) device_read = true;

      bool lr_read = ((insn & MASK_LR_D) == MATCH_LR_D) || ((insn & MASK_LR_W) == MATCH_LR_W);
      bool sc_read = ((insn & MASK_SC_D) == MATCH_SC_D) || ((insn & MASK_SC_W) == MATCH_SC_W);

      bool ignore_read = sc_read || (!mem_read.empty() &&
                          (magic_addrs.count(mem_read_addr) ||
                           device_read ||
                           lr_read ||
                           (tohost_addr && mem_read_addr == tohost_addr) ||
                           (fromhost_addr && mem_read_addr == fromhost_addr)));
      //COSPIKE_PRINTF("register write type %d\n", type);
      // check the type is compliant with writeback first
      //if ((type == 0 || type == 1))
      //  scalar_wb = true;
      if (type == 2) {
        vector_rds.push_back(rd);
      }
      if (type == 3) continue;

      if ((rd != 0 && type == 0) || type == 1) {
        // Override reads from some CSRs
        uint64_t csr_addr = (insn >> 20) & 0xfff;
        bool csr_read = (insn & 0x7f) == 0x73;
        if (csr_read && cospike_printf)
          COSPIKE_PRINTF("CSR read %" PRIx64 "\n", csr_addr);
        if (csr_read && ((csr_addr == 0x301) ||                      // misa
                         (csr_addr == 0x306) ||                      // mcounteren
                         (csr_addr == 0x320) ||                      // mcountinhibit
                         (csr_addr == 0xf13) ||                      // mimpid
                         (csr_addr == 0xf12) ||                      // marchid
                         (csr_addr == 0xf11) ||                      // mvendorid
                         (csr_addr == 0xb00) ||                      // mcycle
                         (csr_addr == 0xb02) ||                      // minstret
                         (csr_addr == 0xc00) ||                      // cycle
                         (csr_addr == 0xc01) ||                      // time
                         (csr_addr == 0xc02) ||                      // instret
                         (csr_addr >= 0xb03 && csr_addr <= 0xb1f) || // mhpmcounters
                         (csr_addr >= 0x323 && csr_addr <= 0x33f) || // mhpmevent
                         (csr_addr >= 0x7a0 && csr_addr <= 0x7aa) || // debug trigger registers
                         (csr_addr >= 0x3b0 && csr_addr <= 0x3ef)    // pmpaddr
                         )) {
          const reg_t old_csr_data = s->XPR[rd];
          s->XPR.write(rd, wdata);
          if (cospike_printf) COSPIKE_PRINTF("CSR override: old=%" PRIx64 " new=%" PRIx64 "\n", old_csr_data, s->XPR[rd]);
        } else if (csr_read && ((csr_addr == 0x100) ||               // sstatus
                                (csr_addr == 0x200) ||               // vsstatus
                                (csr_addr == 0x300) ||               // mstatus
                                (csr_addr == 0x600)                  // hstatus
                                )) {
          if (cospike_printf) COSPIKE_PRINTF("CSR status override\n");
          s->XPR.write(rd, wdata);
          // Always use the DUT's reported settings for these fields
          uint64_t ignore_bits = MSTATUS64_SD | MSTATUS_XS | MSTATUS_FS | MSTATUS_VS;
          uint64_t read_bits = s->csrmap[csr_addr]->read();
          uint64_t write_bits = (read_bits & ~ignore_bits) | (wdata & ignore_bits);
          s->csrmap[csr_addr]->write(write_bits);
          if ((wdata & ~ignore_bits) != (regwrite.second.v[0] & ~ignore_bits)) {
            COSPIKE_PRINTF("%lld wdata mismatch reg %d %lx != %llx\n", cycle, rd,
                           regwrite.second.v[0], wdata);
            return 1;
          }
        } else if (csr_read && ((csr_addr == 0x343) ||
                                (csr_addr == 0x143) ||
                                (csr_addr == 0x243) ||
                                (csr_addr == 0x643))) {
          // Implementations may set tval to zero instead of writing the actual bits
          if (wdata != 0 && wdata != regwrite.second.v[0]) {
            COSPIKE_PRINTF("%" PRIx64 " wdata mismatch reg %" PRId32 " %" PRIx64 " != %" PRIx64 "\n", cycle, rd,
                           regwrite.second.v[0], wdata);
          }
          s->XPR.write(rd, wdata);
        } else if (ignore_read)  {
          // Don't check reads from tohost, reads from magic memory, or reads
          // from clint Technically this could be buggy because log_mem_read
          // only reports vaddrs, but no software ever should access
          // tohost/fromhost/clint with vaddrs anyways
          if (cospike_printf) COSPIKE_PRINTF("Read override %" PRIx64 " = %" PRIx64 "\n", mem_read_addr, wdata);
          s->XPR.write(rd, wdata);
        } else if (wdata != regwrite.second.v[0]) {
          COSPIKE_PRINTF("%" PRIx64 " wdata mismatch reg %" PRId32 " %" PRIx64 " != %" PRIx64 "\n", cycle, rd,
                 regwrite.second.v[0], wdata);
          return 1;
        }
      }

      // TODO FIX: Rocketchip TracedInstruction.wdata should be Valid(UInt)
      // if (scalar_wb ^ has_wdata) {
      //   COSPIKE_PRINTF("Scalar wdata behavior divergence between spike and DUT\n");
      //   exit(-1);
      // }
    }
    // for (auto &a : vector_rds) {
    //   COSPIKE_PRINTF("vector writeback to v%ld\n", a);
    // }
  }

  return 0;
}
