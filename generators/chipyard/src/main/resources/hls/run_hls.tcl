open_project -reset proj_gcd_example
add_files HLSAccel.cpp
set_top HLSGCDAccelBlackBox
open_solution -reset "solution1"

# Specify FPGA board and clock frequency
set_part {xcu200-fsgd2104-2-e}
create_clock -period 10

csynth_design
exit