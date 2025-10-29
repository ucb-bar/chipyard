#ifndef __COSPIKE_IMPL_H
#define __COSPIKE_IMPL_H

#include <vector>
#include <string>

void cospike_set_sysinfo(
  char* isa,
  char* priv,
  int pmpregions,
  int maxpglevels,
  unsigned long long int mem0_base,
  unsigned long long int mem0_size,
  unsigned long long int mem1_base,
  unsigned long long int mem1_size,
  unsigned long long int mem2_base,
  unsigned long long int mem2_size,
  int nharts,
  char* bootrom,
  std::vector<std::string> &args);

int cospike_cosim(
  unsigned long long int cycle,
  unsigned long long int hartid,
  int has_wdata,
  int valid,
  unsigned long long int iaddr,
  unsigned long int insn,
  int raise_exception,
  int raise_interrupt,
  unsigned long long int cause,
  unsigned long long int wdata,
  int priv);

void cospike_register_memory(
  unsigned long long int base,
  unsigned long long int size);

#endif // __COSPIKE_IMPL_H
