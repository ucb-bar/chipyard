# ------------------------- Base Clocks --------------------
create_clock -name sys_clock -period 4.0 [get_ports {sys_clock_p}]
set_input_jitter sys_clock 0.5
# ------------------------- Clock Groups -------------------
set_clock_groups -asynchronous \
  -group [list [get_clocks -of_objects [get_pins { \
      mig/island/blackbox/c0_ddr4_ui_clk \
    }]]] \
  -group [list [get_clocks -of_objects [get_pins { \
      harnessSysPLL/clk_out1 \
    }]]]
# ------------------------- False Paths --------------------
set_false_path -through [get_pins {fpga_power_on/power_on_reset}]
# ------------------------- IO Timings ---------------------

