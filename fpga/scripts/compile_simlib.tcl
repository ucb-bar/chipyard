#### Command line arguments to this script
# argv[0] = fpga family 
# argv[1] = build directory
# argv[2] = chipyard model definition

set family [lindex $argv 0]
set build_dir [lindex $argv 1]
set model [lindex $argv 2]
# Compile the simulation libraries for vcs
compile_simlib -directory $build_dir/$model.cache/compile_simlib -family $family -simulator vcs_mx -library all
