// See LICENSE.SiFive for license details.
//VCS coverage exclude_file
`ifndef RESET_DELAY
 `define RESET_DELAY 777.7
`endif
`ifndef MODEL
 `define MODEL TestHarness
`endif

typedef struct {
  longint unsigned pc;
  longint unsigned prv;

  longint unsigned fcsr;

  longint unsigned vstart;
  longint unsigned vxsat;
  longint unsigned vxrm;
  longint unsigned vcsr;
  longint unsigned vtype;

  longint unsigned stvec;
  longint unsigned sscratch;
  longint unsigned sepc;
  longint unsigned scause;
  longint unsigned stval;
  longint unsigned satp;

  longint unsigned mstatus;
  longint unsigned medeleg;
  longint unsigned mideleg;
  longint unsigned mie;
  longint unsigned mtvec;
  longint unsigned mscratch;
  longint unsigned mepc;
  longint unsigned mcause;
  longint unsigned mtval;
  longint unsigned mip;

  longint unsigned mcycle;
  longint unsigned minstret;
  longint unsigned mtime;
  longint unsigned mtimecmp;

  longint unsigned XPR[32];
  longint unsigned FPR[32];

  longint unsigned VLEN;
  longint unsigned ELEN;
  string VPR[32];
} loadarch_state_t;

import "DPI-C" function void loadarch_from_file
(
  input string loadarch_file,
  output loadarch_state_t loadarch_state
);

module TestDriver;

  reg clock = 1'b0;
  reg reset = 1'b1;

  always #(`CLOCK_PERIOD/2.0) clock = ~clock;
  initial #(`RESET_DELAY) reset = 0;

  // Read input arguments and initialize
  reg verbose = 1'b0;
  wire printf_cond = verbose && !reset;
  reg [63:0] max_cycles = 0;
  reg [63:0] dump_start = 0;
  reg [63:0] trace_count = 0;
  reg [2047:0] fsdbfile = 0;
  reg [2047:0] vcdplusfile = 0;
  reg [2047:0] vcdfile = 0;
  int unsigned rand_value;
  loadarch_state_t loadarch_state;
  string loadarch_file;
  initial
  begin
    void'($value$plusargs("max-cycles=%d", max_cycles));
    void'($value$plusargs("dump-start=%d", dump_start));
    verbose = $test$plusargs("verbose");

    // do not delete the lines below.
    // $random function needs to be called with the seed once to affect all
    // the downstream $random functions within the Chisel-generated Verilog
    // code.
    // $urandom is seeded via cmdline (+ntb_random_seed in VCS) but that
    // doesn't seed $random.
    rand_value = $urandom;
    rand_value = $random(rand_value);
    if (verbose) begin
`ifdef VCS
      $fdisplay(stderr, "testing $random %0x seed %d", rand_value, unsigned'($get_initial_random_seed));
`else
      $fdisplay(stderr, "testing $random %0x", rand_value);
`endif
    end

`ifdef DEBUG

    if ($value$plusargs("vcdplusfile=%s", vcdplusfile))
    begin
`ifdef VCS
      $vcdplusfile(vcdplusfile);
`else
      $fdisplay(stderr, "Error: +vcdplusfile is VCS-only; use +vcdfile instead or recompile with VCS=1");
      $fatal;
`endif
    end

    if ($value$plusargs("fsdbfile=%s", fsdbfile))
    begin
`ifdef FSDB
      $fsdbDumpfile(fsdbfile);
      $fsdbDumpvars("+all");
      //$fsdbDumpSVA;
`else
      $fdisplay(stderr, "Error: +fsdbfile is FSDB-only; use +vcdfile/+vcdplus instead or recompile with FSDB=1");
      $fatal;
`endif
    end

    if ($value$plusargs("vcdfile=%s", vcdfile))
    begin
      $dumpfile(vcdfile);
      $dumpvars(0, testHarness);
    end

    if ($value$plusargs("loadarch=%s", loadarch_file))
    begin
      $display("Reading loadarch file: %s", loadarch_file);
      loadarch_from_file(loadarch_file, loadarch_state);
      $display("Loadarch struct: %p", loadarch_state);
    end

`ifdef FSDB
`define VCDPLUSON $fsdbDumpon;
`define VCDPLUSCLOSE $fsdbDumpoff;
`elsif VCS
`define VCDPLUSON $vcdpluson(0); $vcdplusmemon(0);
`define VCDPLUSCLOSE $vcdplusclose; $dumpoff;
`else
`define VCDPLUSON $dumpon;
`define VCDPLUSCLOSE $dumpoff;
`endif
`else
  // No +define+DEBUG
`define VCDPLUSON
`define VCDPLUSCLOSE

    if ($test$plusargs("vcdplusfile=") || $test$plusargs("vcdfile=") || $test$plusargs("fsdbfile="))
    begin
      $fdisplay(stderr, "Error: +vcdfile, +vcdplusfile, or +fsdbfile requested but compile did not have +define+DEBUG enabled");
      $fatal;
    end

`endif

    if (dump_start == 0)
    begin
      // Start dumping before first clock edge to capture reset sequence in waveform
      `VCDPLUSON
    end
  end

`ifdef TESTBENCH_IN_UVM
  // UVM library has its own way to manage end-of-simulation.
  // A UVM-based testbench will raise an objection, watch this signal until this goes 1, then drop the objection.
  reg finish_request = 1'b0;
`endif
  reg [255:0] reason = "";
  reg failure = 1'b0;
  wire success;
  integer stderr = 32'h80000002;
  always @(posedge clock)
  begin
`ifdef GATE_LEVEL
    if (verbose)
    begin
      $fdisplay(stderr, "C: %10d", trace_count);
    end
`endif

    trace_count = trace_count + 1;

    if (trace_count == dump_start)
    begin
      `VCDPLUSON
    end

    if (!reset)
    begin
      if (max_cycles > 0 && trace_count > max_cycles)
      begin
        reason = " (timeout)";
        failure = 1'b1;
      end

      if (failure)
      begin
        $fdisplay(stderr, "*** FAILED ***%s after %d simulation cycles", reason, trace_count);
        `VCDPLUSCLOSE
        $fatal;
      end

      if (success)
      begin
        if (verbose)
          $fdisplay(stderr, "*** PASSED *** Completed after %d simulation cycles", trace_count);
        `VCDPLUSCLOSE
`ifdef TESTBENCH_IN_UVM
        finish_request = 1;
`else
        $finish;
`endif
      end
    end
  end

  `MODEL testHarness(
    .clock(clock),
    .reset(reset),
    .io_success(success)
  );

  initial begin
    $display("Starting force");
    //force testHarness.chiptop0.system.tile_prci_domain.tile_reset_domain_tile.frontend.s1_pc = 40'h8000_0010;
    // s1_pc isn't reset, so I think I only have to drive s2_pc
    force testHarness.chiptop0.system.tile_prci_domain.tile_reset_domain_tile.frontend.s2_pc = 40'h8000_0000;
    //force testHarness.chiptop0.system.tile_prci_domain.tile_reset_domain_tile.frontend.s2_pc = 40'h10044;
    @(negedge testHarness.chiptop0.system.tile_prci_domain.tile_reset_domain_tile.core.reset);
    //release testHarness.chiptop0.system.tile_prci_domain.tile_reset_domain_tile.frontend.s1_pc;
    release testHarness.chiptop0.system.tile_prci_domain.tile_reset_domain_tile.frontend.s2_pc;
    $display("Released after negedge reset");
  end

endmodule
