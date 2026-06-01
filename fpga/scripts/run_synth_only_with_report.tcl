# Synth-only flow + hierarchical utilization report.
# Mirrors fpga/fpga-shells/xilinx/common/tcl/synth-only.tcl + report.tcl but
# does NOT call route/place/bitstream, and skips timing/DRC checks (those
# need routed netlists).  Output:
#   $wrkdir/post_synth.dcp
#   $wrkdir/report/utilization.txt           (default depth)
#   $wrkdir/report/utilization_h_flat.txt    (full hierarchy expanded)
#
# All inputs come via the standard prologue.tcl argv parser:
#   -top-module / -F / -board / -ip-vivado-tcls
# (same as the bitstream flow).

# Locate fpga-shells common tcl dir so we can source its scripts unchanged.
set fpga_dir   [file normalize [file join [file dirname [info script]] .. fpga-shells]]
set scriptdir  [file join $fpga_dir xilinx common tcl]

# Parse args + open project (prologue.tcl reads -top-module/-F/-board/etc).
source [file join $scriptdir "prologue.tcl"]

# Set up IP catalog + create the harness PLL and other IPs in $ipdir.
source [file join $scriptdir "init.tcl"]

# Phase-1 fit-check support: when a chipyard FPGA harness has no
# peripheral pin binders (no real DDR/UART/JTAG pinned out), Vivado's
# DCE proves the whole SoC has no observable effect at the top-level
# pins and deletes it during synth_design.  Pre-synth, inject the
# `(* dont_touch = "yes" *)` Verilog attribute on the key module
# declarations (ChipTop, RocketTile, SaturnRocketUnit) -- that tells
# synth to preserve their cells through optimization.  Cheap; safe to
# leave in for any flow.
foreach _sv [list ChipTop.sv RocketTile.sv RocketTile_1.sv SaturnRocketUnit.sv SaturnRocketUnit_1.sv Gemmini.sv MeshWithDelays.sv] {
  # $wrkdir = [pwd]/obj; gen-collateral lives at [pwd]/gen-collateral (one level up)
  set _path [file join [file dirname $wrkdir] gen-collateral $_sv]
  if {[file exists $_path]} {
    set _fh [open $_path r]; set _txt [read $_fh]; close $_fh
    set _mod [file rootname $_sv]
    if {![regexp "\\(\\* keep_hierarchy = \"yes\" \\*\\)\\s+module $_mod" $_txt]} {
      regsub "module $_mod" $_txt "(* keep_hierarchy = \"yes\" *)\nmodule $_mod" _txt
      set _fh [open $_path w]; puts -nonewline $_fh $_txt; close $_fh
      puts "synth-only-with-report: injected keep_hierarchy on $_mod ($_sv)"
    }
  }
}

# Force DSP48E2 inference on Gemmini's MAC unit. The default Chisel/CIRCT
# emit is a purely combinational 8x8 signed multiply + 20-bit add, and
# Vivado's DSP-inference heuristic punts it to LUTs (~123 LUTs per MAC x
# 256 PEs = 31K wasted LUTs at 16x16). Inject `(* use_dsp = "yes" *)` on
# (a) the MacUnit module declaration and (b) the actual multiplier wire
# so Vivado maps to one DSP48E2 per MacUnit instance.
#
# NOTE: this is currently post-Verilog SED rather than a Chisel-side
# annotation. Chisel 6 + CIRCT firtool (chipyard's pipeline) does not
# expose a stable API for emitting arbitrary SV attributes on a module
# declaration -- the legacy firrtl.AttributeAnnotation is only read by
# the SFC AddDescriptionNodes pass, which CIRCT firtool doesn't run.
# A real Chisel-side fix would require BlackBox-with-setInline (loses
# typeclass generics) or rerouting the build through SFC. The SED
# pattern is reliable + idempotent + reversible, and it sits next to
# the keep_hierarchy injection that already uses the same mechanism.
# See agents/notes/gemmini_lut_optimization.md for the area-impact
# data this fix produces.
set _mac_path [file join [file dirname $wrkdir] gen-collateral MacUnit.sv]
if {[file exists $_mac_path]} {
  set _fh [open $_mac_path r]; set _txt [read $_fh]; close $_fh
  if {![regexp "\\(\\* use_dsp = \"yes\" \\*\\)\\s+module MacUnit" $_txt]} {
    regsub "module MacUnit" $_txt "(* use_dsp = \"yes\" *)\nmodule MacUnit" _txt
    regsub -line {(\s*)wire \[15:0\] _io_out_d_T} $_txt "\\1(* use_dsp = \"yes\" *) wire \[15:0\] _io_out_d_T" _txt
    set _fh [open $_mac_path w]; puts -nonewline $_fh $_txt; close $_fh
    puts "synth-only-with-report: injected use_dsp=yes on MacUnit (module + multiplier wire)"
  } else {
    puts "synth-only-with-report: MacUnit already has use_dsp=yes -- skipping injection"
  }
}

# Same DSP-inference attribute on hardfloat's MulAddRecFNPipe modules
# (Saturn's vector FPFMAPipe + Rocket's scalar FPU FMA path). Tested
# 2026-05-15 on V128D128 robotMpc KU040: it's a NO-OP today -- Vivado
# already maps each hardfloat mantissa multiplier
# `({1'h0, {zext,A}} * {1'h0, {zext,B}}) + {1'h0,C}` to one DSP48E2
# without any hint, because the hardfloat emit is well-structured
# enough to satisfy the inference heuristic (unlike Gemmini's MacUnit
# where the small input width + BOTH-dataflow muxes defeated it).
#
# We keep the injection anyway as a defensive idempotent safety net:
# if a future Vivado/Chisel update regresses hardfloat DSP inference,
# this attribute keeps the DSPs in place without needing manual
# intervention. Costs ~0 -- the regex check is fast, and module-level
# `(* use_dsp = "yes" *)` is a hint, not a mandate; if Vivado has
# better information it ignores it.
#
# Module names: MulAddRecFNPipe_l2_e5_s11.sv (Saturn vector, 2-stage,
# FP16), MulAddRecFNPipe_l3_e5_s11.sv (Rocket scalar, 3-stage), and
# similar for e8_s24 (FP32) and e11_s53 (FP64) in non-FP16-only builds.
foreach _fma_sv [glob -nocomplain [file join [file dirname $wrkdir] gen-collateral MulAddRecFNPipe_*.sv]] {
  set _fh [open $_fma_sv r]; set _txt [read $_fh]; close $_fh
  set _mod [file rootname [file tail $_fma_sv]]
  if {![regexp "\\(\\* use_dsp = \"yes\" \\*\\)\\s+module $_mod" $_txt]} {
    regsub "module $_mod" $_txt "(* use_dsp = \"yes\" *)\nmodule $_mod" _txt
    set _fh [open $_fma_sv w]; puts -nonewline $_fh $_txt; close $_fh
    puts "synth-only-with-report: injected use_dsp=yes on $_mod (hardfloat FMA mantissa mul)"
  }
}

# Saturn OPU (Outer Product Unit, opu-fp8 branch) — same DSP-inference
# issue as Gemmini's MacUnit. The OuterProductCell.sv body:
#   wire [15:0] prod = io_macc ? {{8{io_in_l[7]}}, io_in_l} * {{8{io_in_t[7]}}, io_in_t} : 16'h0;
#   wire [31:0] _sum_int8_T_1 = sext20(prod) + regs[io_mrf_idx];
# is an 8b x 8b signed mul + 32b accumulator-read add — exactly the
# A*B+C pattern that DSP48E2 can absorb. Vivado defaults to LUTs (256
# cells x ~169 LUTs = ~43 KLUT) because of the sub-9-bit input and the
# `io_macc ? ... : 16'h0` mux. The 256-cell OPU mesh is the largest
# single LUT block in V128D128 + OPU builds.
#
# Inject `(* use_dsp = "yes" *)` on:
#  (a) the OuterProductCell module declaration
#  (b) the `prod` wire that holds the multiplication result
# Even though chipyard uniquifies each cell to OuterProductCell_NNN at
# the instance level, all share the single OuterProductCell.sv source --
# attribute carries through to every uniquified copy.
set _opu_path [file join [file dirname $wrkdir] gen-collateral OuterProductCell.sv]
if {[file exists $_opu_path]} {
  set _fh [open $_opu_path r]; set _txt [read $_fh]; close $_fh
  if {![regexp "\\(\\* use_dsp = \"yes\" \\*\\)\\s+module OuterProductCell" $_txt]} {
    regsub "module OuterProductCell" $_txt "(* use_dsp = \"yes\" *)\nmodule OuterProductCell" _txt
    # Tag the prod wire (the 8x8 mul output) so Vivado's per-wire DSP
    # heuristic gets a direct hint even if module-level inference
    # leaks for some reason.
    regsub -line {(\s*)wire \[15:0\]\s+prod = } $_txt "\\1(* use_dsp = \"yes\" *) wire \[15:0\] prod = " _txt
    set _fh [open $_opu_path w]; puts -nonewline $_fh $_txt; close $_fh
    puts "synth-only-with-report: injected use_dsp=yes on OuterProductCell (module + prod wire)"
  }
}

# NOTE: Saturn's vector register file (vrf_<N>x<W>.sv) currently emits
# as `reg [W-1:0] Memory[0:N-1]` with COMBINATIONAL reads, three read
# ports, and one write port with byte-mask (16 byte-write-enables).
# That gets synthesized to ~9 K LUTs of pure flop-array + read-mux for
# V128D128 robotMpc, instead of the ~150 LUT-as-RAM cells it could fit
# into.
#
# Tried 2026-05-15: injecting `(* ram_style = "distributed" *)` on the
# Memory declaration via SED. Vivado rejected the hint with
# `[Synth 8-7186] ram_style ignored, Memory[N] not inferred as ram
# due to incorrect usage` -- the byte-mask write doesn't fit any
# native Xilinx LUTRAM primitive (RAM32M/RAM16M have ONE write enable
# per cell, not 16 byte-level WEs). To fix this properly the Saturn
# Chisel in generators/saturn/.../RegisterFile.scala would need to
# split the single Memory into 16 separate byte-wide memories each
# with its own write-enable -- significant restructuring. Documented
# in zephyr-chipyard-sw/agents/notes/gemmini_lut_optimization.md.

# Synthesize.  This writes $wrkdir/post_synth.dcp.
source [file join $scriptdir "synth.tcl"]

# Post-synth: apply DONT_TOUCH on every chiptop/RocketTile/Saturn cell
# so opt_design / phys_opt_design in the downstream debug-bitstream
# flow don't DCE the SoC.  Verilog-source `(* dont_touch *)` survives
# synth_design but is sometimes stripped by `-flatten_hierarchy rebuilt`;
# applying the property directly on the synthesized netlist cells is
# the robust path.  Re-write post_synth.dcp so the property persists.
set _dt [list]
foreach _pat {chiptop0 element_reset_domain_rockettile vector_unit core dcache frontend fpuOpt gemmini mesh} {
  set _hits [get_cells -hier -filter "NAME =~ {*${_pat}*} && IS_PRIMITIVE == 0" -quiet]
  set _dt [concat $_dt $_hits]
}
if {[llength $_dt] > 0} {
  set_property DONT_TOUCH true $_dt
  puts "synth-only-with-report: applied DONT_TOUCH on [llength $_dt] cell(s)"
  write_checkpoint -force [file join $wrkdir post_synth]
}

# Hierarchical utilization report (the slice the user actually wants).
set rptdir [file join $wrkdir report]
file mkdir $rptdir
report_utilization -hierarchical                       -file [file join $rptdir utilization.txt]
report_utilization -hierarchical -hierarchical_depth 6 -file [file join $rptdir utilization_h6.txt]
report_utilization                                     -file [file join $rptdir utilization_flat.txt]

puts "=== synth-only-with-report DONE.  Reports in $rptdir"
