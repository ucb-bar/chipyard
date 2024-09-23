base_dir=$(abspath .)
hls_gendir=$(base_dir)/proj_gcd_example/solution1/syn/verilog
vsrc_dir=$(base_dir)/src/main/resources/vsrc

.PHONY: default run-hls clean

HLS_CMD = vitis_hls 
TCL_SCRIPT = run_hls.tcl
ACCEL_C = accel/HLSAccel.cpp

default: run-hls

run-hls: $(ACCEL_C) $(TCL_SCRIPT)
	$(HLS_CMD) $(TCL_SCRIPT)
	mkdir -p $(vsrc_dir)
	cp -r $(hls_gendir)/. $(vsrc_dir)

clean:
	rm -rf proj_gcd_example
	rm -f vitis_hls.log
	rm -f $(vsrc_dir)/*.v