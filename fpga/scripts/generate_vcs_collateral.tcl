#### Command line arguments to this script
# argv[0] = fpga family
# argv[1] = build directory
# argv[2] = chipyard model definition

set family [lindex $argv 0]
set build_dir [lindex $argv 1]
set model [lindex $argv 2]

# compile the simulation libraries for vcs
compile_simlib -directory $build_dir/$model.cache/compile_simlib -family $family -simulator vcs_mx -library all
# set the top level of the design
set_property top ArtyTestDriver [current_fileset -simset]
# generate other vcs simulation collateral
export_simulation -force -simulator vcs -ip_user_files_dir $build_dir/$model.ip_user_files -lib_map_path $build_dir/$model.cache/compile_simlib -use_ip_compiled_libs -directory $build_dir/export_sim
# Add vivado library mapping to synopsys_sim.setup file
set synopsys_libraries [open $build_dir/synopsys_sim.setup a]
puts $synopsys_libraries "xil_defaultlib : vcs_lib/xil_defaultlib"
close $synopsys_libraries
#generate separate lists of verilog and vhdl sim sources
set fpga_sim_verilog_sources [open $build_dir/fpga_sim_verilog_sources.f w]
foreach source [get_files -compile_order sources -used_in simulation -filter {FILE_TYPE == Verilog}] {puts $fpga_sim_verilog_sources $source}
close $fpga_sim_verilog_sources
set fpga_sim_vhdl_sources [open $build_dir/fpga_sim_vhdl_sources.f w]
foreach source [get_files -compile_order sources -used_in simulation -filter {FILE_TYPE == VHDL}] {puts $fpga_sim_vhdl_sources $source}
close $fpga_sim_vhdl_sources
