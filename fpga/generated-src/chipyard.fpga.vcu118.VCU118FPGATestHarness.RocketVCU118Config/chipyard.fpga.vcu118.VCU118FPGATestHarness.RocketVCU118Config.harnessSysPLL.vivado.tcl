set scriptPath [info script]
      set scriptDir [file dirname $scriptPath]
      set dirName [file tail $scriptDir]
  	  set newFolderPath [file join $scriptDir "ip"]
	    file mkdir $newFolderPath
      puts "scriptPath name: $scriptPath"
      puts "scriptDir name: $scriptDir"
      puts "Directory name: $dirName"
  	  set currentDir [pwd]
	    set ipdir [file join $currentDir $dirName $newFolderPath]
	    puts "ipdir Path: $ipdir"
    create_ip -name clk_wiz -vendor xilinx.com -library ip -module_name \
 harnessSysPLL -dir $ipdir -force
set_property -dict [list \
 CONFIG.CLK_IN1_BOARD_INTERFACE {Custom} \
 CONFIG.PRIM_SOURCE {No_buffer} \
 CONFIG.NUM_OUT_CLKS {1} \
 CONFIG.PRIM_IN_FREQ {250.0} \
 CONFIG.CLKIN1_JITTER_PS {50.0} \
 CONFIG.CLKOUT1_USED {true} \
 CONFIG.CLKOUT2_USED {false} \
 CONFIG.CLKOUT3_USED {false} \
 CONFIG.CLKOUT4_USED {false} \
 CONFIG.CLKOUT5_USED {false} \
 CONFIG.CLKOUT6_USED {false} \
 CONFIG.CLKOUT7_USED {false} \
 CONFIG.CLKOUT1_REQUESTED_OUT_FREQ {100.0} \
 CONFIG.CLKOUT1_REQUESTED_PHASE {0.0} \
 CONFIG.CLKOUT1_REQUESTED_DUTY_CYCLE {50.0} \
] [get_ips harnessSysPLL]
set mult [get_property CONFIG.MMCM_CLKFBOUT_MULT_F [get_ips harnessSysPLL]]
set div1 [get_property CONFIG.MMCM_DIVCLK_DIVIDE [get_ips harnessSysPLL]]
set jitter [get_property CONFIG.CLKOUT1_JITTER [get_ips harnessSysPLL]]
if {$jitter > 300.0} {
  puts "Output jitter $jitter ps exceeds required limit of 300.0"
  exit 1
}
set phase [get_property CONFIG.MMCM_CLKOUT0_PHASE [get_ips harnessSysPLL]]
if {$phase < -5.0 || $phase > 5.0} {
  puts "Achieved phase $phase degrees is outside tolerated range -5.0-5.0"
  exit 1
}
set div2 [get_property CONFIG.MMCM_CLKOUT0_DIVIDE_F [get_ips harnessSysPLL]]
set freq [expr { 250.0 * $mult / $div1 / $div2 }]
if {$freq < 99.0 || $freq > 101.0} {
  puts "Achieved frequency $freq MHz is outside tolerated range 99.0-101.0"
  exit 1
}
puts "Achieve frequency $freq MHz phase $phase degrees jitter $jitter ps"
