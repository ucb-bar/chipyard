// See LICENSE.SiFive for license details.
// See LICENSE.Berkeley for license details.

#if VM_TRACE
#include <memory>
#if CY_FST_TRACE
#include "verilated_fst_c.h"
#else
#include "verilated.h"
#include "verilated_vcd_c.h"
#endif // CY_FST_TRACE
#endif // VM_TRACE
#include <fesvr/dtm.h>
#include <fesvr/tsi.h>
#include "remote_bitbang.h"
#include <iostream>
#include <fcntl.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <getopt.h>

// For option parsing, which is split across this file, Verilog, and
// FESVR's HTIF, a few external files must be pulled in. The list of
// files and what they provide is enumerated:
//
// $RISCV/include/fesvr/htif.h:
//   defines:
//     - HTIF_USAGE_OPTIONS
//     - HTIF_LONG_OPTIONS_OPTIND
//     - HTIF_LONG_OPTIONS
// $(ROCKETCHIP_DIR)/generated-src(-debug)?/$(CONFIG).plusArgs:
//   defines:
//     - PLUSARG_USAGE_OPTIONS
//   variables:
//     - static const char * verilog_plusargs

extern tsi_t* tsi;
extern dtm_t* dtm;
extern remote_bitbang_t * jtag;

static uint64_t trace_count = 0;
bool verbose = false;
bool done_reset = false;

void handle_sigterm(int sig)
{
  dtm->stop();
}

double sc_time_stamp()
{
  return trace_count;
}

static void usage(const char * program_name)
{
  printf("Usage: %s [EMULATOR OPTION]... [VERILOG PLUSARG]... [HOST OPTION]... BINARY [TARGET OPTION]...\n",
         program_name);
  fputs("\
Run a BINARY on the Rocket Chip emulator.\n\
\n\
Mandatory arguments to long options are mandatory for short options too.\n\
\n\
EMULATOR OPTIONS\n\
  -c, --cycle-count        Print the cycle count before exiting\n\
       +cycle-count\n\
  -h, --help               Display this help and exit\n\
  -m, --max-cycles=CYCLES  Kill the emulation after CYCLES\n\
       +max-cycles=CYCLES\n\
  -s, --seed=SEED          Use random number seed SEED\n\
  -r, --rbb-port=PORT      Use PORT for remote bit bang (with OpenOCD and GDB) \n\
                           If not specified, a random port will be chosen\n\
                           automatically.\n\
  -V, --verbose            Enable all Chisel printfs (cycle-by-cycle info)\n\
       +verbose\n\
", stdout);
#if VM_TRACE == 0
  fputs("\
\n\
EMULATOR DEBUG OPTIONS (only supported in debug build -- try `make debug`)\n",
        stdout);
#endif
  fputs("\
  -v, --vcd=FILE,          Write vcd trace to FILE (or '-' for stdout)\n\
  -x, --dump-start=CYCLE   Start VCD tracing at CYCLE\n\
       +dump-start\n\
", stdout);
  fputs("\n" PLUSARG_USAGE_OPTIONS, stdout);
  fputs("\n" HTIF_USAGE_OPTIONS, stdout);
  printf("\n"
"EXAMPLES\n"
"  - run a bare metal test:\n"
"    %s $RISCV/riscv64-unknown-elf/share/riscv-tests/isa/rv64ui-p-add\n"
"  - run a bare metal test showing cycle-by-cycle information:\n"
"    %s +verbose $RISCV/riscv64-unknown-elf/share/riscv-tests/isa/rv64ui-p-add 2>&1 | spike-dasm\n"
#if VM_TRACE
"  - run a bare metal test to generate a VCD waveform:\n"
"    %s -v rv64ui-p-add.vcd $RISCV/riscv64-unknown-elf/share/riscv-tests/isa/rv64ui-p-add\n"
#endif
"  - run an ELF (you wrote, called 'hello') using the proxy kernel:\n"
"    %s pk hello\n",
         program_name, program_name, program_name
#if VM_TRACE
         , program_name
#endif
         );
}

int main(int argc, char** argv)
{
  unsigned random_seed = (unsigned)time(NULL) ^ (unsigned)getpid();
  uint64_t max_cycles = -1;
  int ret = 0;
  bool print_cycles = false;
  // Port numbers are 16 bit unsigned integers.
  uint16_t rbb_port = 0;
#if VM_TRACE
  const char* vcdfile_name = NULL;
  FILE * vcdfile = NULL;
  uint64_t start = 0;
#endif
  int verilog_plusargs_legal = 1;

  int verilated_argc = 1;
  char** verilated_argv = new char*[argc];
  verilated_argv[0] = argv[0];

  opterr = 1;

  while (1) {
    static struct option long_options[] = {
      {"cycle-count",     no_argument,       0, 'c' },
      {"help",            no_argument,       0, 'h' },
      {"max-cycles",      required_argument, 0, 'm' },
      {"seed",            required_argument, 0, 's' },
      {"rbb-port",        required_argument, 0, 'r' },
      {"verbose",         no_argument,       0, 'V' },
      {"permissive",      no_argument,       0, 'p' },
      {"permissive-off",  no_argument,       0, 'o' },
#if VM_TRACE
      {"vcd",             required_argument, 0, 'v' },
      {"dump-start",      required_argument, 0, 'x' },
#endif
      HTIF_LONG_OPTIONS
    };
    int option_index = 0;
#if VM_TRACE
    int c = getopt_long(argc, argv, "-chm:s:r:v:Vx:po", long_options, &option_index);
#else
    int c = getopt_long(argc, argv, "-chm:s:r:Vpo", long_options, &option_index);
#endif
    if (c == -1) break;
 retry:
    switch (c) {
      // Process long and short EMULATOR options
      case '?': usage(argv[0]);             return 1;
      case 'c': print_cycles = true;        break;
      case 'h': usage(argv[0]);             return 0;
      case 'm': max_cycles = atoll(optarg); break;
      case 's': random_seed = atoi(optarg); break;
      case 'r': rbb_port = atoi(optarg);    break;
      case 'V': verbose = true;             break;
      case 'p': opterr = 0;                 break;
      case 'o': opterr = 1;                 break;
#if VM_TRACE
      case 'v': {
        vcdfile_name = optarg;
        vcdfile = strcmp(optarg, "-") == 0 ? stdout : fopen(optarg, "w");
        if (!vcdfile) {
          std::cerr << "Unable to open " << optarg << " for VCD write\n";
          return 1;
        }
        break;
      }
      case 'x': start = atoll(optarg);      break;
#endif
      // Process legacy '+' EMULATOR arguments by replacing them with
      // their getopt equivalents
      case 1: {
        std::string arg = optarg;
        if (arg.substr(0, 1) != "+") {
          optind--;
          goto done_processing;
        }
        if (arg == "+verbose")
          c = 'V';
        else if (arg.substr(0, 12) == "+max-cycles=") {
          c = 'm';
          optarg = optarg+12;
        }
#if VM_TRACE
        else if (arg.substr(0, 12) == "+dump-start=") {
          c = 'x';
          optarg = optarg+12;
        }
#endif
        else if (arg.substr(0, 12) == "+cycle-count")
          c = 'c';
        else if (arg == "+permissive")
        {
          c = 'p';
          verilated_argv[verilated_argc++] = optarg;
        }
        else if (arg == "+permissive-off")
        {
          c = 'o';
          verilated_argv[verilated_argc++] = optarg;
        }
        // If we don't find a legacy '+' EMULATOR argument, it still could be
        // a VERILOG_PLUSARG and not an error.
        else if (verilog_plusargs_legal) {
          const char ** plusarg = &verilog_plusargs[0];
          int legal_verilog_plusarg = 0;
          while (*plusarg && (legal_verilog_plusarg == 0)){
            if (arg.substr(1, strlen(*plusarg)) == *plusarg) {
              legal_verilog_plusarg = 1;
            }
            plusarg ++;
          }
          if (!legal_verilog_plusarg) {
            verilog_plusargs_legal = 0;
          } else {
            c = 'P';
          }
          goto retry;
        }
        // If we STILL don't find a legacy '+' argument, it still could be
        // an HTIF (HOST) argument and not an error. If this is the case, then
        // we're done processing EMULATOR and VERILOG arguments.
        else {
          static struct option htif_long_options [] = { HTIF_LONG_OPTIONS };
          struct option * htif_option = &htif_long_options[0];
          while (htif_option->name) {
            if (arg.substr(1, strlen(htif_option->name)) == htif_option->name) {
              optind--;
              goto done_processing;
            }
            htif_option++;
          }
          if(opterr) {
            std::cerr << argv[0] << ": invalid plus-arg (Verilog or HTIF) \""
                      << arg << "\"\n";
            c = '?';
          } else {
            c = 'P';
          }
        }
        goto retry;
      }
      case 'P': // Verilog PlusArg, add to the argument list for verilator environment
        verilated_argv[verilated_argc++] = optarg;
        break;
      // Realize that we've hit HTIF (HOST) arguments or error out
      default:
        if (c >= HTIF_LONG_OPTIONS_OPTIND) {
          optind--;
          goto done_processing;
        }
        c = '?';
        goto retry;
    }
  }

done_processing:
  if (optind == argc) {
    std::cerr << "No binary specified for emulator\n";
    usage(argv[0]);
    return 1;
  }

  // Copy remaining HTIF arguments (if any) and the binary file name into the verilator argument stack
  while (optind < argc) verilated_argv[verilated_argc++] = argv[optind++];

  if (verbose)
    fprintf(stderr, "using random seed %u\n", random_seed);

  srand(random_seed);
  srand48(random_seed);

  Verilated::randReset(2);
  Verilated::commandArgs(verilated_argc, verilated_argv);
  TEST_HARNESS *tile = new TEST_HARNESS;

#if VM_TRACE
  Verilated::traceEverOn(true); // Verilator must compute traced signals
#if CY_FST_TRACE
  std::unique_ptr<VerilatedFstC> tfp(new VerilatedFstC);
#else
  std::unique_ptr<VerilatedVcdFILE> vcdfd(new VerilatedVcdFILE(vcdfile));
  std::unique_ptr<VerilatedVcdC> tfp(new VerilatedVcdC(vcdfd.get()));
#endif // CY_FST_TRACE
  if (vcdfile_name) {
    tile->trace(tfp.get(), 99);  // Trace 99 levels of hierarchy
    tfp->open(vcdfile_name);
  }
#endif // VM_TRACE

  // RocketChip currently only supports RBB port 0, so this needs to stay here
  jtag = new remote_bitbang_t(rbb_port);

  signal(SIGTERM, handle_sigterm);

  bool dump;
  // start reset off low so a rising edge triggers async reset
  tile->reset = 0;
  tile->clock = 0;
  tile->eval();
  // reset for several cycles to handle pipelined reset
  for (int i = 0; i < 100; i++) {
    tile->reset = 1;
    tile->clock = 0;
    tile->eval();
#if VM_TRACE
    dump = tfp && trace_count >= start;
    if (dump)
      tfp->dump(static_cast<vluint64_t>(trace_count * 2));
#endif
    tile->clock = 1;
    tile->eval();
#if VM_TRACE
    if (dump)
      tfp->dump(static_cast<vluint64_t>(trace_count * 2 + 1));
#endif
    trace_count ++;
  }
  tile->reset = 0;
  done_reset = true;

  do {
    tile->clock = 0;
    tile->eval();
#if VM_TRACE
    dump = tfp && trace_count >= start;
    if (dump)
      tfp->dump(static_cast<vluint64_t>(trace_count * 2));
#endif

    tile->clock = 1;
    tile->eval();
#if VM_TRACE
    if (dump)
      tfp->dump(static_cast<vluint64_t>(trace_count * 2 + 1));
#endif
    trace_count++;
  }
  // for verilator multithreading. need to do 1 loop before checking if
  // tsi exists, since tsi is created by verilated thread on the first
  // serial_tick.
  while ((!dtm || !dtm->done()) &&
         (!jtag || !jtag->done()) &&
         (!tsi || !tsi->done()) &&
         !tile->io_success && trace_count < max_cycles);

#if VM_TRACE
  if (tfp)
    tfp->close();
  if (vcdfile)
    fclose(vcdfile);
#endif

  if (dtm && dtm->exit_code())
  {
    fprintf(stderr, "*** FAILED *** via dtm (code = %d, seed %d) after %ld cycles\n", dtm->exit_code(), random_seed, trace_count);
    ret = dtm->exit_code();
  }
  else if (tsi && tsi->exit_code())
  {
    fprintf(stderr, "*** FAILED *** (code = %d, seed %d) after %ld cycles\n", tsi->exit_code(), random_seed, trace_count);
    ret = tsi->exit_code();
  }
  else if (jtag && jtag->exit_code())
  {
    fprintf(stderr, "*** FAILED *** via jtag (code = %d, seed %d) after %ld cycles\n", jtag->exit_code(), random_seed, trace_count);
    ret = jtag->exit_code();
  }
  else if (trace_count == max_cycles)
  {
    fprintf(stderr, "*** FAILED *** via trace_count (timeout, seed %d) after %ld cycles\n", random_seed, trace_count);
    ret = 2;
  }
  else if (verbose || print_cycles)
  {
    fprintf(stderr, "*** PASSED *** Completed after %ld cycles\n", trace_count);
  }

  if (dtm) delete dtm;
  if (tsi) delete tsi;
  if (jtag) delete jtag;
  if (tile) delete tile;
  if (verilated_argv) delete[] verilated_argv;
  return ret;
}
