#include <vpi_user.h>
#include <svdpi.h>
#include <cstdlib>
#include <cstring>
#include <cinttypes>
#include <cstdint>
#include <cstdio>
#include <fcntl.h>
#include <unistd.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <fesvr/context.h>

#include "plusarg_file_mem.h"

// We are returning the pointer to the PlusargFileMem object so that we can have
// multiple plusarg memories in one sim.
extern "C" long long plusarg_file_mem_init(const char *filename, unsigned char writeable, int addr_bits, int data_bytes)
{
  PlusargFileMem *mem = new PlusargFileMem(filename, (bool)writeable, (1 << addr_bits), data_bytes);
  return (long long)mem;
}

extern "C" void plusarg_file_mem_read(long long mem, long long address, long long *data)
{
  // Cast the pointer back to PlusargFileMem pointer
  ((PlusargFileMem *)mem)->do_read((uint64_t)address, (uint64_t *)data);
}

extern "C" void plusarg_file_mem_write(long long mem, long long address, long long data)
{
  // Cast the pointer back to PlusargFileMem pointer
  ((PlusargFileMem *)mem)->do_write((uint64_t)address, (uint64_t)data);
}

PlusargFileMem::PlusargFileMem(const char *filename, bool writeable, uint64_t capacity_bytes, uint8_t data_bytes)
{

  _capacity_bytes = capacity_bytes;
  _data_bytes = data_bytes;
  _writeable = writeable;

  // open the file
  int fh = open(filename, (_writeable ? O_RDWR : O_RDONLY));

  // get the file size
  struct stat sb;
  fstat(fh, &sb);
  _filesize = (uint64_t)sb.st_size;

  if (_writeable && (_filesize < _capacity_bytes))
  {
    fprintf(stderr, "Error: image file %s is not large enough to store 0x%x bytes.\n", filename, capacity_bytes);
    close(fh);
    abort();
  }

  uint32_t mapsize = _capacity_bytes;
  if (_filesize < _capacity_bytes) mapsize = _filesize;
  _memblk = mmap(NULL, mapsize, PROT_READ | (_writeable ? PROT_WRITE : 0), MAP_SHARED, fh, 0);

  // close the file after memory mapping
  close(fh);

  if (_memblk == MAP_FAILED)
  {
    fprintf(stderr, "Error mmapping file %s\n", filename);
    abort();
  }
}

PlusargFileMem::~PlusargFileMem(void)
{
  munmap(_memblk, _capacity_bytes);
}

void PlusargFileMem::do_read(uint64_t address, uint64_t *data)
{
  if (address >= _filesize)
  {
    *data = 0;
    return;
  }
  // fill with zeroes
  memset(data, 0, sizeof(*data));
  memcpy(data, _memblk + address, _data_bytes);
}

void PlusargFileMem::do_write(uint64_t address, uint64_t data)
{
  if (_writeable && (address < _capacity_bytes))
  {
    memcpy(_memblk + address, &data, _data_bytes);
  }
}
