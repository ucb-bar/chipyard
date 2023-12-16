// See LICENSE.SiFive for license details.
//VCS coverage exclude_file
`ifndef RESET_DELAY
 `define RESET_DELAY 777.7
`endif
`ifndef MODEL
 `define MODEL TestHarness
`endif

`define TILE testHarness.chiptop0.system.tile_prci_domain.tile_reset_domain_tile
`define ROCKET `TILE.core
`define CSR_FILE `ROCKET.csr
`define CORE_RESET (`ROCKET.reset)
`define CORE_CLOCK (`ROCKET.clock)
`define TRACE_VALID (`ROCKET._csr_io_trace_0_valid)
`define TRACE_EXCEPTION (`ROCKET._csr_io_trace_0_exception)
`define TRACE_TIME (`ROCKET._csr_io_time)
`define INSTRET (!(`CORE_RESET) && `TRACE_VALID && !(`TRACE_EXCEPTION))
`define DCACHE_DATA_ARRAY_ROOT `TILE.dcache.data.data_arrays_0.data_arrays_0_ext
`define DCACHE_DATA_ARRAY(bank) `DCACHE_DATA_ARRAY_ROOT.mem_0_``bank
`define DCACHE_TAG_ARRAY_ROOT `TILE.dcache.tag_array.tag_array_ext
`define DCACHE_TAG_ARRAY(way) `DCACHE_TAG_ARRAY_ROOT.mem_0_``way.ram

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
  chandle VPR[32];
  // unsigned char* = SystemVerilog string
  // so VPR should be a string
  // BUT, a string in a SV struct makes it 'dynamic' and then any reference to any member of the struct is illegal as the RHS of a force
  // but chandle seems OK lol
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

  // Loadarch
  static loadarch_state_t loadarch_state;
  string loadarch_file;
  event loadarch_struct_ready;
  bit do_loadarch = 0;

  // Performance metric extraction
  int unsigned cycles = 0;
  int unsigned instret = 0;
  int unsigned sample_period = 1000;
  int max_instructions = -1; // -1 = no maximum
  int unsigned total_instret = 0;
  string perf_file;
  int perf_file_fd;

  function void dump_perf_stats();
    if (perf_file != "") begin
      $fwrite(perf_file_fd, "%0d,%0d\n", cycles, instret);
    end
  endfunction

  // DCache functional warmup
  localparam integer physical_address_bits = 32;
  localparam integer dcache_block_size = 64;
  localparam integer dcache_sets = 64;
  localparam integer dcache_size = 16384;
  localparam integer dcache_ways = dcache_size / (dcache_sets * dcache_block_size);
  localparam integer dcache_offset_bits = $clog2(dcache_block_size);
  localparam integer dcache_set_bits = $clog2(dcache_sets);
  localparam integer dcache_raw_tag_bits = physical_address_bits - dcache_set_bits - dcache_offset_bits;
  localparam integer dcache_tag_bits = dcache_raw_tag_bits + 2; // 2 bits for coherency metadata
  bit dcache_tag_array [0:dcache_ways-1][0:dcache_sets-1][dcache_tag_bits-1:0];

  always @(posedge `CORE_CLOCK) begin
    if (!reset && !`CORE_RESET) begin
      cycles = cycles + 1;
      if (`INSTRET) begin
        instret = instret + 1;
        total_instret = total_instret + 1;
      end
    end
    if (instret == sample_period) begin
      dump_perf_stats();
      cycles = 0;
      instret = 0;
    end
  end

  initial
  begin
    // Performance metric extration related plusargs
    if ($value$plusargs("perf-file=%s", perf_file))
    begin
      perf_file_fd = $fopen(perf_file, "w");
      $display("Dumping performance metrics to file: %s", perf_file);
      $fwrite(perf_file_fd, "cycles,instret\n");
    end
    void'($value$plusargs("perf-sample-period=%d", sample_period));
    void'($value$plusargs("max-instructions=%d", max_instructions));

    // Rest of plusargs
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

    if ($value$plusargs("loadarch=%s", loadarch_file))
    begin
      do_loadarch = 1;
      $display("Reading loadarch file: %s", loadarch_file);
      loadarch_from_file(loadarch_file, loadarch_state);
      $display("Loadarch struct: %p", loadarch_state);
    end

    if (do_loadarch) begin
      ->loadarch_struct_ready;
      $display("Starting state injection via forces");
      // mtime and mtimecmp to CLINT
      force testHarness.chiptop0.system.clint.time_0 = loadarch_state.mtime;
      force testHarness.chiptop0.system.clint.timecmp_0 = loadarch_state.mtimecmp;

      // similar to testchip_dtm, set mstatus_fs, mstatus_xs, mstatus_vs
      // TODO: ask Jerry why
      // TODO: mstatus_xs and mstatus_vs are not found in Rocket (no vector unit, no custom instruction unit either). This is probably OK.
      // TODO: why don't we set the other bits of mstatus from loadarch_state?
      force `CSR_FILE.reg_mstatus_fs = 2'b11;

      // forcing fcsr is a bit tough
      // FPU.sv has fcsr_flags_valid/bits as outputs, they come from other state
      // in the FPU. Can't just force I/Os, need to force the register origins.
      // I can set the rounding mode, but not the other flag bits.
      // TODO: figure out how to set FPU flag bits
      // force testHarness.chiptop0.system.tile_prci_domain.tile_reset_domain_tile.fpuOpt.io_fcsr
      force `CSR_FILE.reg_frm = loadarch_state.fcsr[7:5];

      // The vector registers won't be restored, idk how DTM handles restores to
      // non-existent registers... TODO: ask Jerry

      // Restore all the regular CSRs
      force `CSR_FILE.reg_stvec = loadarch_state.stvec;
      force `CSR_FILE.reg_sscratch = loadarch_state.sscratch;
      force `CSR_FILE.reg_sepc = loadarch_state.sepc;
      force `CSR_FILE.reg_stval = loadarch_state.stval;

      // satp
      force `CSR_FILE.reg_satp_mode = loadarch_state.satp[63:60];
      force `CSR_FILE.reg_satp_ppn = loadarch_state.satp[43:0];
      // TODO: register for satp ASID not found (possibly b/c plain Rocket doesn't have split address spaces)

      // mstatus
      // TODO: why is mstatus being set here again? This must be some caveat of DTM...
      force `CSR_FILE.reg_mstatus_fs    = loadarch_state.mstatus[14:13];
      // force `CSR_FILE.reg_mstatus_gva   = loadarch_state.mstatus[];
      // TODO: gva doesn't exist anymore in mstatus? seems like it's hypervisor related
      force `CSR_FILE.reg_mstatus_mie   = loadarch_state.mstatus[3];
      force `CSR_FILE.reg_mstatus_mpie  = loadarch_state.mstatus[7];
      force `CSR_FILE.reg_mstatus_mpp   = loadarch_state.mstatus[12:11];
      force `CSR_FILE.reg_mstatus_mprv  = loadarch_state.mstatus[17];
      force `CSR_FILE.reg_mstatus_mxr   = loadarch_state.mstatus[19];
      // force `CSR_FILE.reg_mstatus_prv   = loadarch_state.mstatus[];
      // TODO: what is prv? It doesn't exist in the spec
      force `CSR_FILE.reg_mstatus_sie   = loadarch_state.mstatus[1];
      force `CSR_FILE.reg_mstatus_spie  = loadarch_state.mstatus[5];
      force `CSR_FILE.reg_mstatus_spp   = loadarch_state.mstatus[8];
      force `CSR_FILE.reg_mstatus_sum   = loadarch_state.mstatus[18];
      force `CSR_FILE.reg_mstatus_tsr   = loadarch_state.mstatus[22];
      force `CSR_FILE.reg_mstatus_tvm   = loadarch_state.mstatus[20];
      force `CSR_FILE.reg_mstatus_tw    = loadarch_state.mstatus[21];

      // other CSRs
      force `CSR_FILE.reg_medeleg = loadarch_state.medeleg;
      force `CSR_FILE.reg_mideleg = loadarch_state.mideleg;
      force `CSR_FILE.reg_mie     = loadarch_state.mie;
      force `CSR_FILE.reg_mtvec   = loadarch_state.mtvec;
      force `CSR_FILE.reg_mscratch = loadarch_state.mscratch;
      force `CSR_FILE.reg_mepc    = loadarch_state.mepc;
      force `CSR_FILE.reg_mcause  = loadarch_state.mcause;
      force `CSR_FILE.reg_mtval   = loadarch_state.mtval;
      // TODO: missing machine mode interrupt registers
      force `CSR_FILE.reg_mip_seip = loadarch_state.mip[9];
      force `CSR_FILE.reg_mip_ssip = loadarch_state.mip[1];
      force `CSR_FILE.reg_mip_stip = loadarch_state.mip[5];
      // TODO: can't find mcycle/minstret registers in Rocket
      // force `CSR_FILE.mcycle      = loadarch_state.mcycle;
      // force `CSR_FILE.minstret    = loadarch_state.minstret;

      // prv (TODO: this is a guess)
      force `CSR_FILE.reg_mstatus_prv = loadarch_state.prv;
      // pc
      force testHarness.chiptop0.system.tile_prci_domain.tile_reset_domain_tile.frontend.s2_pc = loadarch_state.pc;

      // PMPs
      force `CSR_FILE.reg_pmp_0_addr = 'h1f_ffff_ffff_ffff;
      force `CSR_FILE.reg_pmp_0_cfg_a = 2'b11;
      force `CSR_FILE.reg_pmp_0_cfg_x = 1'b1;
      force `CSR_FILE.reg_pmp_0_cfg_w = 1'b1;
      force `CSR_FILE.reg_pmp_0_cfg_r = 1'b1;

      $display("Forcing complete, waiting for reset to fall");
      @(negedge `CORE_RESET) begin end

      $display("Releasing all forced registers after negedge reset");
      release testHarness.chiptop0.system.clint.time_0;
      release testHarness.chiptop0.system.clint.timecmp_0;
      release `CSR_FILE.reg_mstatus_fs;
      release `CSR_FILE.reg_frm;

      release `CSR_FILE.reg_stvec;
      release `CSR_FILE.reg_sscratch;
      release `CSR_FILE.reg_sepc;
      release `CSR_FILE.reg_stval;

      // satp
      release `CSR_FILE.reg_satp_mode;
      release `CSR_FILE.reg_satp_ppn;

      // mstatus
      release `CSR_FILE.reg_mstatus_fs;
      // release `CSR_FILE.reg_mstatus_gva;
      release `CSR_FILE.reg_mstatus_mie;
      release `CSR_FILE.reg_mstatus_mpie;
      release `CSR_FILE.reg_mstatus_mpp;
      release `CSR_FILE.reg_mstatus_mprv;
      release `CSR_FILE.reg_mstatus_mxr;
      release `CSR_FILE.reg_mstatus_sie;
      release `CSR_FILE.reg_mstatus_spie;
      release `CSR_FILE.reg_mstatus_spp;
      release `CSR_FILE.reg_mstatus_sum;
      release `CSR_FILE.reg_mstatus_tsr;
      release `CSR_FILE.reg_mstatus_tvm;
      release `CSR_FILE.reg_mstatus_tw;

      // other CSRs
      release `CSR_FILE.reg_medeleg;
      release `CSR_FILE.reg_mideleg;
      release `CSR_FILE.reg_mie;
      release `CSR_FILE.reg_mtvec;
      release `CSR_FILE.reg_mscratch;
      release `CSR_FILE.reg_mepc;
      release `CSR_FILE.reg_mcause;
      release `CSR_FILE.reg_mtval;
      release `CSR_FILE.reg_mip_seip;
      release `CSR_FILE.reg_mip_ssip;
      release `CSR_FILE.reg_mip_stip;
      // release `CSR_FILE.mcycle;
      // release `CSR_FILE.minstret;

      // prv
      release `CSR_FILE.reg_mstatus_prv;
      // pc
      release testHarness.chiptop0.system.tile_prci_domain.tile_reset_domain_tile.frontend.s2_pc;

      release `CSR_FILE.reg_pmp_0_addr;
      release `CSR_FILE.reg_pmp_0_cfg_a;
      release `CSR_FILE.reg_pmp_0_cfg_x;
      release `CSR_FILE.reg_pmp_0_cfg_w;
      release `CSR_FILE.reg_pmp_0_cfg_r;
      $display("Finished releasing all registers");
    end
  end

  // LHS of a force statement must be static and genvar loops aren't allowed
  // inside initial blocks
  // Always reload FPRs, just cause. TODO: ask Jerry why he gates loading FPRs based on state.mstatus, may be related to DTM configuration
  for (genvar i_fpr=0; i_fpr < 32; i_fpr++) begin
    initial begin
      wait(loadarch_struct_ready.triggered) begin end
      force testHarness.chiptop0.system.tile_prci_domain.tile_reset_domain_tile.fpuOpt.regfile_ext.Memory[31-i_fpr] = loadarch_state.FPR[i_fpr];
      @(negedge `CORE_RESET) begin end
      release testHarness.chiptop0.system.tile_prci_domain.tile_reset_domain_tile.fpuOpt.regfile_ext.Memory[31-i_fpr];
    end
  end

  // XPRs
  for (genvar i_xpr=1; i_xpr < 32; i_xpr++) begin // Don't attempt to restore register x0 (only 31 registers)
    initial begin
      wait (loadarch_struct_ready.triggered) begin end
      //$display("Loadarch struct is ready");
      //$display("Forcing XPR %d with value %x", i_xpr, loadarch_state.XPR[i_xpr]);
      force testHarness.chiptop0.system.tile_prci_domain.tile_reset_domain_tile.core.rf_ext.Memory[30-i_xpr+1] = loadarch_state.XPR[i_xpr];
      //$display("Waiting for reset negedge");
      @(negedge `CORE_RESET) begin end
      //$display("Got reset negedge");
      release testHarness.chiptop0.system.tile_prci_domain.tile_reset_domain_tile.core.rf_ext.Memory[30-i_xpr+1];
      //$display("Releasing XPR %d", i_xpr);
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
        $fdisplay(stderr, "*** FAILED ***%s after %d simulation cycles and %d instructions", reason, trace_count, total_instret);
        dump_perf_stats();
        `VCDPLUSCLOSE
        $fatal;
      end

      if (success)
      begin
        if (verbose)
          $fdisplay(stderr, "*** PASSED *** Completed after %d simulation cycles and %d instructions", trace_count, total_instret);
        dump_perf_stats();
        `VCDPLUSCLOSE
`ifdef TESTBENCH_IN_UVM
        finish_request = 1;
`else
        $finish;
`endif
      end

      if ((max_instructions != -1) && (total_instret >= max_instructions)) begin
        $display("TERMINATING after %d instructions and %d cycles", total_instret, trace_count);
        dump_perf_stats();
        $finish;
      end
    end
  end

  `MODEL testHarness(
    .clock(clock),
    .reset(reset),
    .io_success(success)
  );

endmodule
