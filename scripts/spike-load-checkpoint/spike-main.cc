#include <cstdint>
#include <dlfcn.h>
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

static std::vector<std::pair<reg_t, abstract_mem_t*>> make_mems(const std::vector<mem_cfg_t> &layout) {
  std::vector<std::pair<reg_t, abstract_mem_t*>> mems;
  mems.reserve(layout.size());
  for (const auto &cfg : layout) {
    mems.push_back(std::make_pair(cfg.get_base(), new mem_t(cfg.get_size())));
  }
  return mems;
}

reg_t read_priv(std::string priv_str) {
  reg_t prv;
  if (priv_str == "U") {
    prv = 0;
  } else if (priv_str == "S") {
    prv = 1;
  } else if (priv_str == "M") {
    prv = 3;
  } else {
    fprintf(stderr, "loadarch illegal privilege mode %s\n", priv_str.c_str());
    abort();
  }
  return prv;
}

struct loadarch_state_t {
  reg_t pc;
  reg_t prv;

  reg_t fcsr;

  reg_t vstart;
  reg_t vxsat;
  reg_t vxrm;
  reg_t vcsr;
  reg_t vtype;

  reg_t stvec;
  reg_t sscratch;
  reg_t sepc;
  reg_t scause;
  reg_t stval;
  reg_t satp;

  reg_t mstatus;
  reg_t medeleg;
  reg_t mideleg;
  reg_t mie;
  reg_t mtvec;
  reg_t mscratch;
  reg_t mepc;
  reg_t mcause;
  reg_t mtval;
  reg_t mip;

  reg_t mcycle;
  reg_t minstret;
  reg_t mtime;
  reg_t mtimecmp;

  reg_t misa;
  reg_t mcounteren;
  reg_t scounteren;
  reg_t mcountinhibit;
  reg_t tselect;

  reg_t XPR[32];
  reg_t FPR[32];

  reg_t VLEN;
  reg_t ELEN;
  unsigned char* VPR[32];

  bool has_vector;
};

std::vector<loadarch_state_t> get_loadarch_state(std::string loadarch_file) {
  std::string line;
  std::ifstream in(loadarch_file);

  if (!in.is_open()) {
    fprintf(stderr, "loadarch could not load architectural state file %s\n", loadarch_file.c_str());
    abort();
  }

  std::vector<std::string> lines;
  while (std::getline(in, line)) {
    lines.push_back(line);
  }

  size_t id = 0;
  size_t nharts = std::stoull(lines[id++], nullptr, 0);
  id++; // skip past garbage ':' character

  std::vector<loadarch_state_t> loadarch_state;
  loadarch_state.resize(nharts);

  for (size_t hartsel = 0; hartsel < nharts; hartsel++) {
    loadarch_state_t &state = loadarch_state[hartsel];
    state.pc       = std::stoull(lines[id++], nullptr, 0);
    state.prv      = read_priv(lines[id++]);

    state.fcsr     = std::stoull(lines[id++], nullptr, 0);

    // pending excption
    //

    state.vstart   = std::stoull(lines[id++], nullptr, 0);
    state.vxsat    = std::stoull(lines[id++], nullptr, 0);
    state.vxrm     = std::stoull(lines[id++], nullptr, 0);
    state.vcsr     = std::stoull(lines[id++], nullptr, 0);
    state.vtype    = std::stoull(lines[id++], nullptr, 0);

    state.stvec    = std::stoull(lines[id++], nullptr, 0);
    state.sscratch = std::stoull(lines[id++], nullptr, 0);
    state.sepc     = std::stoull(lines[id++], nullptr, 0);
    state.scause   = std::stoull(lines[id++], nullptr, 0);
    state.stval    = std::stoull(lines[id++], nullptr, 0);
    state.satp     = std::stoull(lines[id++], nullptr, 0);

    state.mstatus  = std::stoull(lines[id++], nullptr, 0);
    state.medeleg  = std::stoull(lines[id++], nullptr, 0);
    state.mideleg  = std::stoull(lines[id++], nullptr, 0);
    state.mie      = std::stoull(lines[id++], nullptr, 0);
    state.mtvec    = std::stoull(lines[id++], nullptr, 0);
    state.mscratch = std::stoull(lines[id++], nullptr, 0);
    state.mepc     = std::stoull(lines[id++], nullptr, 0);
    state.mcause   = std::stoull(lines[id++], nullptr, 0);
    state.mtval    = std::stoull(lines[id++], nullptr, 0);
    state.mip      = std::stoull(lines[id++], nullptr, 0);

    state.misa     = std::stoull(lines[id++], nullptr, 0);
    state.mcounteren    = std::stoull(lines[id++], nullptr, 0);
    state.scounteren    = std::stoull(lines[id++], nullptr, 0);
    state.mcountinhibit = std::stoull(lines[id++], nullptr, 0);
    state.tselect = std::stoull(lines[id++], nullptr, 0);

    state.mcycle   = std::stoull(lines[id++], nullptr, 0);
    state.minstret = std::stoull(lines[id++], nullptr, 0);

    state.mtime    = std::stoull(lines[id++], nullptr, 0);
    state.mtimecmp = std::stoull(lines[id++], nullptr, 0);

    for (size_t i = 0; i < 32; i++) {
      // Spike prints 128b-wide floats, which this doesn't support
      state.FPR[i] = std::stoull(lines[id++].substr(18), nullptr, 16);
    }
    reg_t regs[32];
    for (size_t i = 0; i < 32; i++) {
      state.XPR[i] = std::stoull(lines[id++], nullptr, 0);
    }

    state.has_vector = false;
    if (lines[id].find("VLEN") != std::string::npos) {
      state.has_vector = true;
      std::string vlen = lines[id].substr(lines[id].find("VLEN="));
      vlen = vlen.substr(0, vlen.find(" ")).substr(strlen("VLEN="));
      std::string elen = lines[id].substr(lines[id].find("ELEN="));
      elen = elen.substr(0, elen.find(" ")).substr(strlen("ELEN="));

      state.VLEN = std::stoull(vlen, nullptr, 0);
      state.ELEN = std::stoull(elen, nullptr, 0);
      id++;
#define MAX_VLEN (8 * 32) // Limited by debug module capacity
      if (state.VLEN > MAX_VLEN) {
        fprintf(stderr, "Loadarch VLEN %d > %d. Aborting\n", state.VLEN, MAX_VLEN);
        abort();
      }

      for (size_t i = 0; i < 32; i++) {
        state.VPR[i] = (unsigned char*) malloc(state.VLEN / 8);
        std::string elems_s = lines[id].substr(lines[id].find("0x"));
        for (size_t j = state.VLEN / state.ELEN; j-- > 0;) {
          reg_t elem = std::stoull(elems_s.substr(0, elems_s.find(' ')), nullptr, 0);
          if (j > 0)
            elems_s = elems_s.substr(elems_s.find("0x", 2));
          memcpy(state.VPR[i] + j * (state.ELEN / 8), &elem, state.ELEN / 8);
        }
        id++;
      }
    } else {
      id++;
    }
  }
  assert(id == lines.size());

  return loadarch_state;
}

int main(void) {
  // TODO: modify this
  std::string loadarch_file = "../encrypt-measure-checkpoint-bin.0x80000000.0x18013.0.customdts.loadarch/loadarch";
  std::string elf_file = "../encrypt-measure-checkpoint-bin.0x80000000.0x18013.0.customdts.loadarch/mem.elf";
  std::string dtb_file = "../encrypt-measure-checkpoint-bin.0x80000000.0x18013.0.customdts.loadarch/tmp.dtb";
  std::string extlib = "libspikedevices.so";
  std::string device = "iceblk,img=<ABSPATHTOFILE>/software/firemarshal/images/firechip/encrypt-measure-checkpoint/encrypt-measure-checkpoint.img";

  void *lib = dlopen(extlib.c_str(), RTLD_NOW | RTLD_GLOBAL);
  if (lib == NULL) {
    fprintf(stderr, "Unable to load extlib '%s': %s\n", extlib.c_str(), dlerror());
    exit(-1);
  }

  std::vector<std::pair<const device_factory_t*, std::vector<std::string>>> plugin_device_factories;
  std::vector<std::string> parsed_args;
  std::stringstream sstr(device);
  while (sstr.good()) {
    std::string substr;
    getline(sstr, substr, ',');
    parsed_args.push_back(substr);
  }
  if (parsed_args.empty()) throw std::runtime_error("Plugin argument is empty.");

  const std::string name = parsed_args[0];
  if (name.empty()) throw std::runtime_error("Plugin name is empty.");

  auto it = mmio_device_map().find(name);
  if (it == mmio_device_map().end()) throw std::runtime_error("Plugin \"" + name + "\" not found in loaded extlibs.");

  parsed_args.erase(parsed_args.begin());
  plugin_device_factories.push_back(std::make_pair(it->second, parsed_args));

  cfg_t* cfg = new cfg_t();
  cfg->isa = "rv64imafdcbzicsr_zifencei_zihpm_zfh_zba_zbb_zbs";
  cfg->initrd_bounds = std::make_pair(0, 0);
  cfg->bootargs = nullptr;
  cfg->misaligned = false;
  cfg->endianness = endianness_little;
  std::vector<mem_cfg_t> mem_cfg;
  mem_cfg.push_back(mem_cfg_t(2147483648UL, 268435456UL));
  cfg->mem_layout = mem_cfg;
  cfg->pmpregions = 0;
  cfg->explicit_hartids = false;
  cfg->trigger_count = 0;
  std::vector<size_t> hartids;
  hartids.push_back(0);
  cfg->hartids = hartids;

  std::vector<std::pair<reg_t, abstract_mem_t*>> mems = make_mems(cfg->mem_layout);

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

  std::vector<std::string> htif_args;
  htif_args.push_back(elf_file);

  sim_t* sim = new sim_t(
    cfg,
    false,
    mems,
    plugin_device_factories,
    htif_args,
    dm_config,
    nullptr,
    true,
    dtb_file.c_str(),
    false,
    nullptr
  );

  processor_t* p = sim->get_core(0);
  state_t* s = p->get_state();
  std::vector<loadarch_state_t> ls_all = get_loadarch_state(loadarch_file);
  loadarch_state_t ls = ls_all[0];

  // copy the loadarch state into the cosim
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

  if (ls.mstatus & MSTATUS_FS) {
    RESTORE(CSR_FCSR     , fcsr);
    for (size_t i = 0; i < 32; i++) {
      s->XPR.write(i, ls.XPR[i]);
      s->FPR.write(i, { (uint64_t)ls.FPR[i], (uint64_t)-1 });
    }
  }
  for (size_t i = 0; i < 32; i++) {
    s->XPR.write(i, ls.XPR[i]);
  }

  if (ls.VLEN != p->VU.VLEN) {
    abort();
  }
  if (ls.ELEN != p->VU.ELEN) {
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

  fprintf(stderr, "done loading loadarch\n");

  sim->clint->store(0xbff8, 8, (const uint8_t*)&ls.mtime);
  sim->clint->store(0x4000, 8, (const uint8_t*)&ls.mtimecmp);
  // p->get_mmu()->store<uint64_t>(0x2000000 + 0xbff8, ls.mtime);
  // p->get_mmu()->store<uint64_t>(0x2000000 + 0x4000 + (0 * 8), ls.mtimecmp);

#define PLIC_BASE (0xc000000)
  uint32_t prio = 1;
  sim->plic->store(0x4, 4, (const uint8_t*)&prio);
  uint32_t thresh = 0;
  sim->plic->store(0x201000, 4, (const uint8_t*)&thresh);
  uint32_t enable = 0;
  sim->plic->store(0x2080, 4, (const uint8_t*)&enable);
  enable = 2;
  sim->plic->store(0x2080, 4, (const uint8_t*)&enable);

  fprintf(stderr, "wrote clint\n");

  // sim->set_debug(true);
  // sim->set_procs_debug(true);
  //sim->configure_log(true, true);

  auto return_code = sim->run();

  for (auto& mem : mems)
    delete mem.second;

  delete sim;

  return return_code;
}
