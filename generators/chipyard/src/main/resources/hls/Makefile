base_dir=$(abspath ../../../..)
hls_dir=$(abspath .)
hls_vlog_gendir=$(hls_dir)/proj_gcd_example/solution1/syn/verilog
vsrc_dir=$(base_dir)/src/main/resources/vsrc

.PHONY: default run-hls clean

HLS_CMD = vitis_hls 
TCL_SCRIPT = run_hls.tcl
ACCEL_C = HLSAccel.cpp

default: run-hls

run-hls: $(ACCEL_C) $(TCL_SCRIPT)
	$(HLS_CMD) $(TCL_SCRIPT)
	cp -r $(hls_vlog_gendir)/. $(vsrc_dir)

clean:
	rm -rf $(hls_dir)/proj_gcd_example
	rm -f $(hls_dir)/vitis_hls.log
	rm -f $(vsrc_dir)/HLSGCDAccelBlackBox*