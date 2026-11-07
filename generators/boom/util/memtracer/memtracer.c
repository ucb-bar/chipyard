#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>

#define MEMSTART 0x80000000
#define MEMSIZE  0x10000000

static uint64_t tohost;
static uint64_t fromhost;

uint64_t size_mask(uint64_t data, int memsize) {
  switch (memsize) {
  case 0:
    return data & 0xff;
  case 1:
    return data & 0xffff;
  case 2:
    return data & 0xffffffff;
  case 3:
    return data & 0xffffffffffffffff;
  default:
    printf("bad memsize %d\n", memsize);
    exit(1);
    return 0;
  }
}

void handle_store(uint8_t* mem, uint64_t* mem_history, char* mem_val,
                  uint64_t tsc, uint64_t addr, uint64_t stdata, uint64_t wbdata,
                  int memsize) {

  for (int i = 0; i < (1 << memsize); i++) {
    /* if (tsc == 0x9cecd) { */
    /*   printf("tsc(%lx) store 0(%lx) = %x %lx\n", */
    /*          tsc, */
    /*          addr + i, */
    /*          (uint8_t)((stdata >> (i << 3)) & 0xff), */
    /*          stdata >> (i << 3)); */
    /* } */
    uint64_t arr_addr = addr - MEMSTART + i;
    mem[arr_addr] = (stdata >> (i << 3)) & 0xff;
    mem_history[arr_addr] = tsc;
    mem_val[arr_addr] = 'Y';
  }
}

int arrcmp(uint8_t* mem, uint64_t addr, uint64_t data, int memsize) {
  for (int i = 0; i < (1 << memsize); i++) {
    if (((data >> (i << 3)) & 0xff) != mem[addr - MEMSTART + i]) {
      return 1;
    }
  }
  return 0;
}

void handle_load(uint8_t* mem, uint64_t* mem_history, char* mem_val,
                 uint64_t tsc, uint64_t addr, uint64_t stdata, uint64_t wbdata,
                 int memsize) {
  if (arrcmp(mem, addr, wbdata, memsize) && addr != fromhost && addr != tohost) {

    /* printf("%lx Load differs %d %lx != %lx at %lx\n", */
    /*        tsc, */
    /*        memsize, */
    /*        size_mask(wbdata, memsize), */
    /*        size_mask(*(uint64_t*)(mem + addr - MEMSTART), memsize), */
    /*        addr); */
    if (strncmp(mem_val + addr - MEMSTART, "YYYYYYYY", 1 << memsize) == 0) {
      printf("tsc(%lx) load %lx at %lx memsize %d\n",
             tsc,
             wbdata,
             addr,
             memsize);
      for (int i = 0; i < (1 << memsize); i++) {
        printf("tsc(%lx) load %x != %x at %lx last modified tsc(%lx)\n",
               tsc,
               (uint8_t)((wbdata >> (i << 3)) & 0xff),
               mem[addr - MEMSTART + i],
               addr + i,
               mem_history[addr - MEMSTART + i]);
      }
    } else {
      handle_store(mem, mem_history, mem_val,
                   0, addr, wbdata, wbdata,
                   memsize);
    }
  } else {
    /* printf("%lx Load matches %d %lx == %lx at %lx\n", */
    /*        tsc, */
    /*        memsize, */
    /*        size_mask(wbdata, memsize), */
    /*        size_mask(*(uint64_t*)(mem + addr - MEMSTART), memsize), */
    /*        addr); */
  }
}

int main(int argc, char** argv) {

  printf("BOOM memtrace utility. 2019. Jerry Zhao\n"
         "\n"
         "Build BOOM with the MEMTRACE_PRINTF option enabled to\n"
         "generate a log of memory operations the LSU commits.\n"
         "\n"
         "This utility will process them and announce loads which\n"
         "do not match with the next youngest store to that address.\n");

  int8_t* mem = malloc(MEMSIZE);
  char* mem_val = malloc(MEMSIZE);
  memset(mem_val, 'N', MEMSIZE);
  uint64_t* mem_history = calloc(MEMSIZE, sizeof(uint64_t));
  uint64_t lct = 0;
  fromhost = strtoul(argv[1], NULL, 16);
  tohost = strtoul(argv[2], NULL, 16);

  char *lpt = NULL;
  size_t size;
  while (getline(&lpt, &size, stdin) != -1) {
    char *line = lpt;
    if (strncmp(line, "MT", 2)) {
      continue;
    }
    line = line + 3;
    uint64_t tsc = strtoul(line, &line, 16);
    int uopc = strtoul(line, &line, 16);
    int memcmd = strtoul(line, &line, 16);
    int memsize = strtoul(line, &line, 16);
    uint64_t addr = strtoul(line, &line, 16);
    uint64_t stdata = strtoul(line, &line, 16);
    uint64_t wbdata = strtoul(line, &line, 16);

    //printf("%lu %d %d %lx %lx %lx\n", tsc, uopc, memsize, addr, stdata, wbdata);

    if (addr > MEMSTART && addr < MEMSTART + MEMSIZE && uopc != 42) {
      switch (memcmd) {
      case 0 :
        // Loads
        handle_load(mem, mem_history, mem_val,
                    tsc, addr, stdata, wbdata,
                    memsize);
        break;
      case 1 :
        // Stores
        handle_store(mem, mem_history, mem_val,
                     tsc, addr, stdata, wbdata,
                     memsize);
        break;
      case 0x4:
        // Swap
        handle_load(mem, mem_history, mem_val,
                    tsc, addr, stdata, wbdata,
                    memsize);
        handle_store(mem, mem_history, mem_val,
                     tsc, addr, stdata, wbdata,
                     memsize);
        break;
      case 0x6:
          // LR
        handle_load(mem, mem_history, mem_val,
                    tsc, addr, stdata, wbdata,
                    memsize);
        break;
      case 0x7:
        // SC
        if (wbdata == 0) {
          handle_store(mem, mem_history, mem_val,
                       tsc, addr, stdata, wbdata,
                       memsize);
        }
        break;
      case 0x8:
        // amoadd
        handle_load(mem, mem_history, mem_val,
                    tsc, addr, stdata, wbdata,
                    memsize);
        handle_store(mem, mem_history, mem_val,
                     tsc, addr, stdata + wbdata, wbdata,
                     memsize);
        break;
      case 0x9:
        // amoxor
        handle_load(mem, mem_history, mem_val,
                    tsc, addr, stdata, wbdata,
                    memsize);
        handle_store(mem, mem_history, mem_val,
                     tsc, addr, stdata ^ wbdata, wbdata,
                     memsize);
        break;
      case 0xa:
          // amoor
        handle_load(mem, mem_history, mem_val,
                    tsc, addr, stdata, wbdata,
                      memsize);
        handle_store(mem, mem_history, mem_val,
                     tsc, addr, stdata | wbdata, wbdata,
                     memsize);
        break;
      case 0xb:
        // amoand
        handle_load(mem, mem_history, mem_val,
                    tsc, addr, stdata, wbdata,
                    memsize);
        handle_store(mem, mem_history, mem_val,
                     tsc, addr, stdata & wbdata, wbdata,
                     memsize);
        break;
      default :
        printf("bad mem_cmd %x\n", memcmd);
        exit(0);
      }
    }
    lct++;
  }
  printf("\n");
  printf("Success\n");
  return 0;
}
