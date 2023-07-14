#include <cstdint>
#include <vector>
#include <string>
#include <riscv/sim.h>
#include <riscv/mmu.h>
#include <riscv/encoding.h>
#include <vpi_user.h>
#include <svdpi.h>
#include <sstream>
#include <set>
#include <sys/types.h>
#include <sys/mman.h>
#include <sys/types.h>
#include <unistd.h>
#include <sys/syscall.h>
#include <fcntl.h>

#if __has_include ("cospike_dtm.h")
#define COSPIKE_DTM
#include "testchip_dtm.h"
extern testchip_dtm_t* dtm;
bool spike_loadarch_done = false;
#endif

#if __has_include ("mm.h")
#define COSPIKE_SIMDRAM
#include "mm.h"
extern std::map<long long int, backing_data_t> backing_mem_data;
#endif

#define CLINT_BASE (0x2000000)
#define CLINT_SIZE (0x10000)
#define UART_BASE (0x54000000)
#define UART_SIZE (0x1000)
#define PLIC_BASE (0xc000000)
#define PLIC_SIZE (0x4000000)

#define COSPIKE_PRINTF(...) {                   \
  printf(__VA_ARGS__);                          \
  fprintf(stderr, __VA_ARGS__);                 \
  }

typedef struct system_info_t {
  std::string isa;
  int pmpregions;
  uint64_t mem0_base;
  uint64_t mem0_size;
  int nharts;
  std::vector<char> bootrom;
  std::string priv;
};

class read_override_device_t : public abstract_device_t {
public:
  read_override_device_t(std::string n, reg_t sz) : was_read_from(false), size(sz), name(n) { };
  virtual bool load(reg_t addr, size_t len, uint8_t* bytes) override {
    if (addr + len > size) return false;
    COSPIKE_PRINTF("Read from device %s at %lx\n", name.c_str(), addr);
    was_read_from = true;
    return true;
  }
  virtual bool store(reg_t addr, size_t len, const uint8_t* bytes) override {
    COSPIKE_PRINTF("Store to device %s at %lx\n", name.c_str(), addr);
    return (addr + len <= size);
  }
  bool was_read_from;
private:
  reg_t size;
  std::string name;
};

system_info_t* info = NULL;
sim_t* sim = NULL;
bool cospike_debug;
reg_t tohost_addr = 0;
reg_t fromhost_addr = 0;
reg_t cospike_timeout = 0;
std::set<reg_t> magic_addrs;
cfg_t* cfg;
std::vector<std::shared_ptr<read_override_device_t>> read_override_devices;

static std::vector<std::pair<reg_t, mem_t*>> make_mems(const std::vector<mem_cfg_t> &layout)
{
  std::vector<std::pair<reg_t, mem_t*>> mems;
  mems.reserve(layout.size());
  for (const auto &cfg : layout) {
    mems.push_back(std::make_pair(cfg.get_base(), new mem_t(cfg.get_size())));
  }
  return mems;
}

extern "C" void cospike_set_sysinfo(char* isa, char* priv, int pmpregions,
                                    long long int mem0_base, long long int mem0_size,
                                    int nharts,
                                    char* bootrom
                                    ) {
  if (!info) {
    info = new system_info_t;
    // technically the targets aren't zicntr compliant, but they implement the zicntr registers
    info->isa = std::string(isa) + "_zicntr";
    info->priv = std::string(priv);
    info->pmpregions = pmpregions;
    info->mem0_base = mem0_base;
    info->mem0_size = mem0_size;
    info->nharts = nharts;
    std::stringstream ss(bootrom);
    std::string s;
    while (ss >> s) {
      info->bootrom.push_back(std::stoi(s));
    }
  }
}

extern "C" void cospike_cosim(long long int cycle,
                              long long int hartid,
                              int has_wdata,
                              int valid,
                              long long int iaddr,
                              unsigned long int insn,
                              int raise_exception,
                              int raise_interrupt,
                              unsigned long long int cause,
                              unsigned long long int wdata,
                              int priv)
{
  assert(info);

  if (unlikely(!sim)) {
    COSPIKE_PRINTF("Configuring spike cosim\n");
    std::vector<mem_cfg_t> mem_cfg;
    std::vector<size_t> hartids;
    mem_cfg.push_back(mem_cfg_t(info->mem0_base, info->mem0_size));
    for (int i = 0; i < info->nharts; i++)
      hartids.push_back(i);

    cfg = new cfg_t(std::make_pair(0, 0),
                    nullptr,
                    info->isa.c_str(),
                    info->priv.c_str(),
                    "vlen:128,elen:64",
                    false,
                    endianness_little,
                    info->pmpregions,
                    mem_cfg,
                    hartids,
                    false,
                    0
                    );

    std::vector<std::pair<reg_t, mem_t*>> mems = make_mems(cfg->mem_layout());

    size_t default_boot_rom_size = 0x10000;
    size_t default_boot_rom_addr = 0x10000;
    assert(info->bootrom.size() < default_boot_rom_size);
    info->bootrom.resize(default_boot_rom_size);

    std::shared_ptr<rom_device_t> boot_rom = std::make_shared<rom_device_t>(info->bootrom);
    std::shared_ptr<mem_t> boot_addr_reg = std::make_shared<mem_t>(0x1000);
    uint64_t default_boot_addr = 0x80000000;
    boot_addr_reg.get()->store(0, 8, (const uint8_t*)(&default_boot_addr));

    std::shared_ptr<read_override_device_t> clint = std::make_shared<read_override_device_t>("clint", CLINT_SIZE);
    std::shared_ptr<read_override_device_t> uart = std::make_shared<read_override_device_t>("uart", UART_SIZE);
    std::shared_ptr<read_override_device_t> plic = std::make_shared<read_override_device_t>("plic", PLIC_SIZE);

    read_override_devices.push_back(clint);
    read_override_devices.push_back(uart);
    read_override_devices.push_back(plic);

    std::vector<std::pair<reg_t, std::shared_ptr<abstract_device_t>>> devices;
    // The device map is hardcoded here for now
    devices.push_back(std::pair(0x4000, boot_addr_reg));
    devices.push_back(std::pair(default_boot_rom_addr, boot_rom));
    devices.push_back(std::pair(CLINT_BASE, clint));
    devices.push_back(std::pair(UART_BASE, uart));
    devices.push_back(std::pair(PLIC_BASE, plic));

    s_vpi_vlog_info vinfo;
    if (!vpi_get_vlog_info(&vinfo))
      abort();
    std::vector<std::string> htif_args;
    bool in_permissive = false;
    cospike_debug = false;
    for (int i = 1; i < vinfo.argc; i++) {
      std::string arg(vinfo.argv[i]);
      if (arg == "+permissive") {
        in_permissive = true;
      } else if (arg == "+permissive-off") {
        in_permissive = false;
      } else if (arg == "+cospike_debug" || arg == "+cospike-debug") {
        cospike_debug = true;
      } else if (arg.find("+cospike-timeout=") == 0) {
	cospike_timeout = strtoull(arg.substr(17).c_str(), 0, 10);
      } else if (!in_permissive) {
        htif_args.push_back(arg);
      }
    }

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
    COSPIKE_PRINTF("htif args: ");
    for (int i = 0; i < htif_args.size(); i++) {
      COSPIKE_PRINTF("%s", htif_args[i].c_str());
    }
    COSPIKE_PRINTF("\n");

    std::vector<const device_factory_t*> plugin_device_factories;
    sim = new sim_t(cfg, false,
                    mems,
                    plugin_device_factories,
                    htif_args,
                    dm_config,
                    nullptr,
                    false,
                    nullptr,
                    false,
                    nullptr
                    );
    for (auto &it : devices)
      sim->add_device(it.first, it.second);

#ifdef COSPIKE_SIMDRAM
    // match sim_t's backing memory with the SimDRAM memory
    bus_t temp_mem_bus;
    for (auto& pair : mems) temp_mem_bus.add_device(pair.first, pair.second);

    for (auto& pair : backing_mem_data) {
      size_t base = pair.first;
      size_t size = pair.second.size;
      COSPIKE_PRINTF("Matching spike memory initial state for region %lx-%lx\n", base, base + size);
      if (!temp_mem_bus.store(base, size, pair.second.data)) {
        COSPIKE_PRINTF("Error, unable to match memory at address %lx\n", base);
        abort();
      }
    }
#endif

    sim->configure_log(true, true);
    for (int i = 0; i < info->nharts; i++) {
      // Use our own reset vector
      sim->get_core(hartid)->get_state()->pc = 0x10040;
      // Set MMU to support up to sv39, as our normal hw configs do
      sim->get_core(hartid)->set_impl(IMPL_MMU_SV48, false);
      sim->get_core(hartid)->set_impl(IMPL_MMU_SV57, false);

      // HACKS: Our processor's don't implement zicntr fully, they don't provide time
      sim->get_core(hartid)->get_state()->csrmap.erase(CSR_TIME);
    }
    sim->set_debug(cospike_debug);
    sim->set_histogram(true);
    sim->set_procs_debug(cospike_debug);
    COSPIKE_PRINTF("Setting up htif for spike cosim\n");
    ((htif_t*)sim)->start();
    COSPIKE_PRINTF("Spike cosim started\n");
    tohost_addr = ((htif_t*)sim)->get_tohost_addr();
    fromhost_addr = ((htif_t*)sim)->get_fromhost_addr();
    COSPIKE_PRINTF("Tohost  : %lx\n", tohost_addr);
    COSPIKE_PRINTF("Fromhost: %lx\n", fromhost_addr);
    COSPIKE_PRINTF("BootROM base  : %lx\n", default_boot_rom_addr);
    COSPIKE_PRINTF("BootROM size  : %lx\n", boot_rom->contents().size());
    COSPIKE_PRINTF("Memory  base  : %lx\n", info->mem0_base);
    COSPIKE_PRINTF("Memory  size  : %lx\n", info->mem0_size);
  }

  if (priv & 0x4) { // debug
    return;
  }

  if (cospike_timeout && cycle > cospike_timeout) {
    if (sim) {
      COSPIKE_PRINTF("Cospike reached timeout cycles = %ld, terminating\n", cospike_timeout);
      delete sim;
    }
    exit(0);
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
    RESTORE(CSR_FCSR     , fcsr);
    RESTORE(CSR_VSTART   , vstart);
    RESTORE(CSR_VXSAT    , vxsat);
    RESTORE(CSR_VXRM     , vxrm);
    RESTORE(CSR_VCSR     , vcsr);
    RESTORE(CSR_VTYPE    , vtype);
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
    if (ls.VLEN != p->VU.VLEN) {
      COSPIKE_PRINTF("VLEN mismatch loadarch: $d != spike: $d\n", ls.VLEN, p->VU.VLEN);
      abort();
    }
    if (ls.ELEN != p->VU.ELEN) {
      COSPIKE_PRINTF("ELEN mismatch loadarch: $d != spike: $d\n", ls.ELEN, p->VU.ELEN);
      abort();
    }
    for (size_t i = 0; i < 32; i++) {
      s->XPR.write(i, ls.XPR[i]);
      s->FPR.write(i, { (uint64_t)ls.FPR[i], (uint64_t)-1 });
      memcpy(p->VU.reg_file + i * ls.VLEN / 8, ls.VPR[i], ls.VLEN / 8);
    }
    spike_loadarch_done = true;
    p->clear_waiting_for_interrupt();
  }
#endif
  uint64_t s_pc = s->pc;
  uint64_t interrupt_cause = cause & 0x7FFFFFFFFFFFFFFF;
  bool ssip_interrupt = interrupt_cause == 0x1;
  bool msip_interrupt = interrupt_cause == 0x3;
  bool stip_interrupt = interrupt_cause == 0x5;
  bool mtip_interrupt = interrupt_cause == 0x7;
  bool debug_interrupt = interrupt_cause == 0xe;
  if (raise_interrupt) {
    COSPIKE_PRINTF("%d interrupt %lx\n", cycle, cause);

    if (ssip_interrupt || stip_interrupt) {
      // do nothing
    } else if (msip_interrupt) {
      s->mip->backdoor_write_with_mask(MIP_MSIP, MIP_MSIP);
    } else if (mtip_interrupt) {
      s->mip->backdoor_write_with_mask(MIP_MTIP, MIP_MTIP);
    } else if (debug_interrupt) {
      return;
    } else {
      COSPIKE_PRINTF("Unknown interrupt %lx\n", interrupt_cause);
      abort();
    }
  }
  if (raise_exception)
    COSPIKE_PRINTF("%d exception %lx\n", cycle, cause);
  if (valid) {
    p->clear_waiting_for_interrupt();
    COSPIKE_PRINTF("%d Cosim: %lx", cycle, iaddr);
    // if (has_wdata) {
    //   COSPIKE_PRINTF(" s: %lx", wdata);
    // }
    COSPIKE_PRINTF("\n");
  }
  if (valid || raise_interrupt || raise_exception) {
    p->clear_waiting_for_interrupt();
    for (auto& e : read_override_devices) e.get()->was_read_from = false;
    p->step(1);
    if (unlikely(cospike_debug)) {
      COSPIKE_PRINTF("spike pc is %lx\n", s->pc);
      COSPIKE_PRINTF("spike mstatus is %lx\n", s->mstatus->read());
      COSPIKE_PRINTF("spike mip is %lx\n", s->mip->read());
      COSPIKE_PRINTF("spike mie is %lx\n", s->mie->read());
      COSPIKE_PRINTF("spike wfi state is %d\n", p->is_waiting_for_interrupt());
    }
  }

  if (valid && !raise_exception) {
    if (s_pc != iaddr) {
      COSPIKE_PRINTF("%d PC mismatch spike %llx != DUT %llx\n", cycle, s_pc, iaddr);
      if (unlikely(cospike_debug)) {
        COSPIKE_PRINTF("spike mstatus is %lx\n", s->mstatus->read());
        COSPIKE_PRINTF("spike mcause is %lx\n", s->mcause->read());
        COSPIKE_PRINTF("spike mtval is %lx\n" , s->mtval->read());
        COSPIKE_PRINTF("spike mtinst is %lx\n", s->mtinst->read());
      }
      exit(1);
    }


    auto& mem_write = s->log_mem_write;
    auto& log = s->log_reg_write;
    auto& mem_read = s->log_mem_read;

    for (auto memwrite : mem_write) {
      reg_t waddr = std::get<0>(memwrite);
      uint64_t w_data = std::get<1>(memwrite);
      if ((waddr == CLINT_BASE + 4*hartid) && w_data == 0) {
        s->mip->backdoor_write_with_mask(MIP_MSIP, 0);
      }
      if ((waddr == CLINT_BASE + 0x4000 + 4*hartid)) {
        s->mip->backdoor_write_with_mask(MIP_MTIP, 0);
      }
      // Try to remember magic_mem addrs, and ignore these in the future
      if ( waddr == tohost_addr && w_data >= info->mem0_base && w_data < (info->mem0_base + info->mem0_size)) {
        COSPIKE_PRINTF("Probable magic mem %lx\n", w_data);
        magic_addrs.insert(w_data);
      }
    }

    bool scalar_wb = false;
    bool vector_wb = false;
    uint32_t vector_cnt = 0;

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
      // check the type is compliant with writeback first
      if ((type == 0 || type == 1))
        scalar_wb = true;
      if (type == 2) {
        vector_wb = true;
      }
      if (type == 3) continue;


      if ((rd != 0 && type == 0) || type == 1) {
        // Override reads from some CSRs
        uint64_t csr_addr = (insn >> 20) & 0xfff;
        bool csr_read = (insn & 0x7f) == 0x73;
        if (csr_read)
          COSPIKE_PRINTF("CSR read %lx\n", csr_addr);
        if (csr_read && ((csr_addr == 0x301) ||                      // misa
                         (csr_addr == 0x306) ||                      // mcounteren
                         (csr_addr == 0xf13) ||                      // mimpid
                         (csr_addr == 0xf12) ||                      // marchid
                         (csr_addr == 0xf11) ||                      // mvendorid
                         (csr_addr == 0xb00) ||                      // mcycle
                         (csr_addr == 0xb02) ||                      // minstret
                         (csr_addr == 0xc00) ||                      // cycle
                         (csr_addr == 0xc01) ||                      // time
                         (csr_addr == 0xc02) ||                      // instret
                         (csr_addr >= 0x7a0 && csr_addr <= 0x7aa) || // debug trigger registers
                         (csr_addr >= 0x3b0 && csr_addr <= 0x3ef)    // pmpaddr
                         )) {
          COSPIKE_PRINTF("CSR override\n");
          s->XPR.write(rd, wdata);
        } else if (ignore_read)  {
          // Don't check reads from tohost, reads from magic memory, or reads
          // from clint Technically this could be buggy because log_mem_read
          // only reports vaddrs, but no software ever should access
          // tohost/fromhost/clint with vaddrs anyways
          COSPIKE_PRINTF("Read override %lx = %lx\n", mem_read_addr, wdata);
          s->XPR.write(rd, wdata);
        } else if (wdata != regwrite.second.v[0]) {
          COSPIKE_PRINTF("%d wdata mismatch reg %d %lx != %lx\n", cycle, rd,
                 regwrite.second.v[0], wdata);
          exit(1);
        }
      }

      // TODO FIX: Rocketchip TracedInstruction.wdata should be Valid(UInt)
      // if (scalar_wb ^ has_wdata) {
      //   COSPIKE_PRINTF("Scalar wdata behavior divergence between spike and DUT\n");
      //   exit(-1);
      // }
    }
  }
}
