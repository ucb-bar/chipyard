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
