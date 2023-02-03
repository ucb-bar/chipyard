#include <vector>
#include <string>
#include <riscv/sim.h>
#include <vpi_user.h>
#include <svdpi.h>
#include <sstream>
#include <set>

typedef struct system_info_t {
  std::string isa;
  int pmpregions;
  uint64_t mem0_base;
  uint64_t mem0_size;
  int nharts;
  std::vector<char> bootrom;
};

system_info_t* info = NULL;
sim_t* sim = NULL;
reg_t tohost_addr = 0;
reg_t fromhost_addr = 0;
std::set<reg_t> magic_addrs;
cfg_t* cfg;

static std::vector<std::pair<reg_t, mem_t*>> make_mems(const std::vector<mem_cfg_t> &layout)
{
  std::vector<std::pair<reg_t, mem_t*>> mems;
  mems.reserve(layout.size());
  for (const auto &cfg : layout) {
    mems.push_back(std::make_pair(cfg.get_base(), new mem_t(cfg.get_size())));
  }
  return mems;
}

extern "C" void cospike_set_sysinfo(char* isa, int pmpregions,
				    long long int mem0_base, long long int mem0_size,
				    int nharts,
				    char* bootrom
				    ) {
  if (!info) {
    info = new system_info_t;
    info->isa = std::string(isa);
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
			      unsigned long long int wdata)
{
  assert(info);
  if (!sim) {
    printf("Configuring spike cosim\n");
    std::vector<mem_cfg_t> mem_cfg;
    std::vector<int> hartids;
    mem_cfg.push_back(mem_cfg_t(info->mem0_base, info->mem0_size));
    for (int i = 0; i < info->nharts; i++)
      hartids.push_back(i);

    cfg = new cfg_t(std::make_pair(0, 0),
                    nullptr,
                    info->isa.c_str(),
                    "MSU",
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
    rom_device_t *boot_rom = new rom_device_t(info->bootrom);
    mem_t *boot_addr_reg = new mem_t(0x1000);
    uint64_t default_boot_addr = 0x80000000;
    std::vector<std::pair<reg_t, abstract_device_t*>> plugin_devices;
    boot_addr_reg->store(0, 8, (const uint8_t*)(&default_boot_addr));
    plugin_devices.push_back(std::pair(0x10000, boot_rom));
    plugin_devices.push_back(std::pair(0x4000, boot_addr_reg));

    s_vpi_vlog_info vinfo;
    if (!vpi_get_vlog_info(&vinfo))
      abort();
    std::vector<std::string> htif_args;
    bool in_permissive = false;
    bool cospike_debug = false;
    for (int i = 1; i < vinfo.argc; i++) {
      std::string arg(vinfo.argv[i]);
      if (arg == "+permissive") {
	in_permissive = true;
      } else if (arg == "+permissive-off") {
	in_permissive = false;
      } else if (arg == "+cospike_debug") {
        cospike_debug = true;
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

    printf("%s\n", info->isa.c_str());
    for (int i = 0; i < htif_args.size(); i++) {
      printf("%s\n", htif_args[i].c_str());
    }

    sim = new sim_t(cfg, false,
		    mems,
		    plugin_devices,
		    htif_args,
		    dm_config,
		    nullptr,
		    false,
		    nullptr,
		    false,
		    nullptr
		    );

    sim->configure_log(true, true);
    // Use our own reset vector
    for (int i = 0; i < info->nharts; i++) {
      sim->get_core(hartid)->get_state()->pc = 0x10040;
    }
    sim->set_debug(cospike_debug);
    printf("Setting up htif for spike cosim\n");
    ((htif_t*)sim)->start();
    printf("Spike cosim started\n");
    tohost_addr = ((htif_t*)sim)->get_tohost_addr();
    fromhost_addr = ((htif_t*)sim)->get_fromhost_addr();
    printf("Tohost  : %lx\n", tohost_addr);
    printf("Fromhost: %lx\n", fromhost_addr);
  }

  processor_t* p = sim->get_core(hartid);
  state_t* s = p->get_state();
  uint64_t s_pc = s->pc;
  if (raise_interrupt) {
    printf("%d interrupt %lx\n", cycle, cause);
    uint64_t interrupt_cause = cause & 0x7FFFFFFFFFFFFFFF;
    if (interrupt_cause == 3) {
      s->mip->backdoor_write_with_mask(MIP_MSIP, MIP_MSIP);
    } else {
      printf("Unknown interrupt %lx\n", interrupt_cause);
    }
  }
  if (raise_exception)
    printf("%d exception %lx\n", cycle, cause);
  if (valid) {
    printf("%d Cosim: %lx", cycle, iaddr);
    if (has_wdata) {
      printf(" %lx", wdata);
    }
    printf("\n");
  }
  if (valid || raise_interrupt || raise_exception)
    p->step(1);

  if (valid) {
    if (s_pc != iaddr) {
      printf("%d PC mismatch %lx != %lx\n", cycle, s_pc, iaddr);
      exit(1);
    }

    // Try to remember magic_mem addrs, and ignore these in the future
    auto& mem_write = s->log_mem_write;
    if (!mem_write.empty() && tohost_addr && std::get<0>(mem_write[0]) == tohost_addr) {
      reg_t wdata = std::get<1>(mem_write[0]);
      if (wdata >= info->mem0_base && wdata < (info->mem0_base + info->mem0_size)) {
	printf("Probable magic mem %x\n", wdata);
	magic_addrs.insert(wdata);
      }
    }

    if (has_wdata) {
      auto& log = s->log_reg_write;
      auto& mem_read = s->log_mem_read;

      for (auto regwrite : log) {
	int rd = regwrite.first >> 4;
	int type = regwrite.first & 0xf;
	// 0 => int
	// 1 => fp
	// 2 => vec
	// 3 => vec hint
	// 4 => csr
	if ((rd != 0 && type == 0) || type == 1) {
	  // Override reads from some CSRs
          if ((insn & 0xfff0007f) == 0xf1300073 || // mimpid
              (insn & 0xfff0007f) == 0xf1200073 || // marchid
              (insn & 0xfff0007f) == 0xf1100073 || // mvendorid
	      (insn & 0xfff0007f) == 0xb0000073 || // mcycle
	      (insn & 0xfff0007f) == 0xb0200073    // minstret
              ) {
            printf("CSR override\n");
            s->XPR.write(rd, wdata);
	  } else if (!mem_read.empty() &&
		     ((magic_addrs.count(std::get<0>(mem_read[0])) ||
                       tohost_addr && std::get<0>(mem_read[0]) == tohost_addr) ||
		      (fromhost_addr && std::get<0>(mem_read[0]) == fromhost_addr))) {
	    // Don't check reads from tohost, or reads from magic memory
	    // Technically this could be buggy because log_mem_read only reports vaddrs, but
	    // no software ever should access tohost/fromhost with vaddrs anyways
	    printf("To/From host read override\n");
	    s->XPR.write(rd, wdata);
          } else if (wdata != regwrite.second.v[0]) {
	    printf("%d wdata mismatch reg %d %lx != %lx\n", cycle, rd, regwrite.second.v[0], wdata);
	    exit(1);
	  }
	}
      }
    }
  }
}
// }
