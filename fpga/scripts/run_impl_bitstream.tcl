#### Command line arguments to this script
# argv[0] = absolute path to post_synth checkpoint file
# argv[1] = part
# argv[2] = output directory
# argv[3] = common fpga brand tcl

set synth_checkpoint_file [lindex $argv 0]
set board [lindex $argv 1]
set wrkdir [lindex $argv 2]

set scriptdir [lindex $argv 3]

# Set the variable for all the common files
set commondir [file dirname $scriptdir]

# Set the variable that points to board specific files
set boarddir [file join [file dirname $commondir] $board]
source [file join $boarddir tcl board.tcl]

# Set the project part to the part passed into this script
set_part $part_fpga

# Create output directories if they doesn't exist
file mkdir $wrkdir
set rptdir [file join $wrkdir report]
file mkdir $rptdir

# Load synthesis checkpoint
open_checkpoint $synth_checkpoint_file

# opt
opt_design
write_checkpoint -force [file join $wrkdir post_opt]

# place
place_design
phys_opt_design
write_checkpoint -force [file join $wrkdir post_place]

report_timing_summary -file [file join $rptdir post_place_timing_summary.rpt]
report_drc -file [file join $rptdir post_place_drc.rpt]

# route
route_design
write_checkpoint -force [file join $wrkdir post_route]
report_timing_summary -file [file join $rptdir post_route_timing_summary.rpt]
report_timing -sort_by group -max_paths 100 -path_type summary -file [file join $rptdir post_route_timing.rpt]
report_clock_utilization -file [file join $rptdir post_route_clock_utilization.rpt]
report_utilization -file [file join $rptdir post_route_utilization.rpt]
report_drc -file [file join $rptdir post_route_drc.rpt]
report_cdc -details -file [file join $rptdir post_route_cdc.rpt]
report_clock_interaction -file [file join $rptdir post_route_clock_interaction.rpt]
report_bus_skew -file [file join $rptdir post_route_bus_skew.rpt]
report_design_analysis -logic_level_distribution -of_timing_paths [get_timing_paths -max_paths 1000 -slack_lesser_than 0] -file [file join $rptdir post_route_timing_violations.rpt]

# bitstream
write_verilog -force [file join $wrkdir post_route.v]
write_xdc -no_fixed_only -force [file join $wrkdir post_route.xdc]
write_bitstream -force [file join $wrkdir top.bit]
write_debug_probes -force [file join $wrkdir debug_nets.ltx]
