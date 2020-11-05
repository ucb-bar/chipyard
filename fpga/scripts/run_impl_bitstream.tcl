#### Command line arguments to this script
# argv[0] = absolute path to post_synth checkpoint file
# argv[1] = part
# argv[2] = output directory

set synth_checkpoint_file [lindex $argv 0]
set part [lindex $argv 1]
set output_dir [lindex $argv 2]

# Set the project part to the part passed into this script
set_part ${part}

# Create output directory if it doesn't exist
file mkdir ${output_dir}
file mkdir ${output_dir}/reports
file mkdir ${output_dir}/outputs

# Load synthesis checkpoint
open_checkpoint ${synth_checkpoint_file}

# Run implementation and save reports as needed
opt_design
place_design
phys_opt_design
write_checkpoint -force ${output_dir}/outputs/post_place
report_timing_summary -file ${output_dir}/reports/post_place_timing_summary.rpt
report_drc -file ${output_dir}/reports/post_place_drc.rpt

route_design
write_checkpoint -force ${output_dir}/outputs/post_route
report_timing_summary -file ${output_dir}/reports/post_route_timing_summary.rpt
report_timing -sort_by group -max_paths 100 -path_type summary -file ${output_dir}/reports/post_route_timing.rpt
report_clock_utilization -file ${output_dir}/reports/post_route_clock_utilization.rpt
report_utilization -file ${output_dir}/reports/post_route_utilization.rpt
report_drc -file ${output_dir}/reports/post_route_drc.rpt
report_cdc -details -file ${output_dir}/reports/post_route_cdc.rpt
report_clock_interaction -file ${output_dir}/reports/post_route_clock_interaction.rpt
report_bus_skew -file ${output_dir}/reports/post_route_bus_skew.rpt
report_design_analysis -logic_level_distribution -of_timing_paths [get_timing_paths -max_paths 1000 -slack_lesser_than 0] -file ${output_dir}/reports/post_route_timing_violations.rpt

write_verilog -force ${output_dir}/outputs/post_route.v
write_xdc -no_fixed_only -force ${output_dir}/outputs/post_route.xdc

write_bitstream -force ${output_dir}/outputs/top.bit
write_debug_probes -force ${output_dir}/outputs/debug_nets.ltx
