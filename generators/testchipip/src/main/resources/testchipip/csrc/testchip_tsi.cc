#include "testchip_tsi.h"
#include <stdexcept>

testchip_tsi_t::testchip_tsi_t(int argc, char** argv, bool can_have_loadmem) : tsi_t(argc, argv)
{
  has_loadmem = false;
  init_accesses = std::vector<init_access_t>();
  write_hart0_msip = true;
  is_loadmem = false;
  cflush_addr = 0;
  std::vector<std::string> args(argv + 1, argv + argc);
  for (auto& arg : args) {
    if (arg.find("+loadmem=") == 0)
      has_loadmem = can_have_loadmem;
    if (arg.find("+cflush_addr=0x") == 0)
      cflush_addr = strtoull(arg.substr(15).c_str(), 0, 16);
  }

  testchip_htif_t::parse_htif_args(args);
}

void testchip_tsi_t::flush_cache_lines(addr_t taddr, size_t nbytes) {
  if (!cflush_addr) return;
  static size_t cblock_bytes = 64;
  addr_t base = taddr & ~(cblock_bytes-1);
  while (base < taddr + nbytes) {
    uint32_t data[2] { (uint32_t)base, (uint32_t)(base >> 32) };
    tsi_t::write_chunk(cflush_addr, 8, data);
    base += cblock_bytes;
  }
}

void testchip_tsi_t::write_chunk(addr_t taddr, size_t nbytes, const void* src)
{
  if (is_loadmem) {
    load_mem_write(taddr, nbytes, src);
  } else {
    flush_cache_lines(taddr, nbytes);
    tsi_t::write_chunk(taddr, nbytes, src);
  }
}

void testchip_tsi_t::read_chunk(addr_t taddr, size_t nbytes, void* dst)
{
  if (is_loadmem) {
    load_mem_read(taddr, nbytes, dst);
  } else {
    flush_cache_lines(taddr, nbytes);
    tsi_t::read_chunk(taddr, nbytes, dst);
  }
}

void testchip_tsi_t::reset()
{
  testchip_htif_t::perform_init_accesses();
  if (write_hart0_msip)
    tsi_t::reset();
}
