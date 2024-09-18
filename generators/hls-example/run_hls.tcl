open_project -reset proj_gcd_example
add_files accel/HLSAccel.cpp
set_top HLSAccelBlackBox
open_solution -reset "solution1"
set_part {xcvu9p-flgb2104-2-i}
create_clock -period 10
csynth_design
exit