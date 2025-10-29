#include <vector>
#include <string>
#include <vpi_user.h>
#include <svdpi.h>

#include "cospike_impl.h"

extern "C" void cospike_set_sysinfo_wrapper(char* isa, char* priv, int pmpregions, int maxpglevels,
					    long long int mem0_base, long long int mem0_size,
					    long long int mem1_base, long long int mem1_size,
                                            long long int mem2_base, long long int mem2_size,
					    int nharts,
					    char* bootrom
					    )
{
  s_vpi_vlog_info vinfo;
  if (!vpi_get_vlog_info(&vinfo))
    abort();
  std::vector<std::string> args;
  for (int i = 1; i < vinfo.argc; i++) {
    std::string arg(vinfo.argv[i]);
    args.push_back(arg);
  }

  cospike_set_sysinfo(
    isa,
    priv,
    pmpregions,
    maxpglevels,
    mem0_base,
    mem0_size,
    mem1_base,
    mem1_size,
    mem2_base,
    mem2_size,
    nharts,
    bootrom,
    args
  );
}

extern "C" void cospike_cosim_wrapper(long long int cycle,
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
  int rval = cospike_cosim(
    cycle,
    hartid,
    has_wdata,
    valid,
    iaddr,
    insn,
    raise_exception,
    raise_interrupt,
    cause,
    wdata,
    priv
  );
  if (rval) exit(rval);
}

extern "C" void cospike_register_memory_wrapper(long long int base,
                                                long long int size)
{
  cospike_register_memory(base, size);
}
