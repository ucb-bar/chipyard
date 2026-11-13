default: bin.trace bin.trace.interleaved

vmlinux.dump: boards/default/linux/vmlinux
	riscv64-unknown-elf-objdump -D $< > $@

busybox.dump: wlutil/busybox/busybox_unstripped
	riscv64-unknown-linux-gnu-objdump -D -S $< > $@

# userspace binary
USERSPACE_BIN ?=
bin.dump: $(USERSPACE_BIN)
	riscv64-unknown-elf-objdump -D -S $< > $@

# logfile from spike or CY RTL sim.
IN_LOG ?=
bin.trace: vmlinux.dump busybox.dump bin.dump $(IN_LOG)
	./create-func-trace.py $^
	mv $(IN_LOG).functrace $@

bin.trace.interleaved: vmlinux.dump busybox.dump bin.dump $(IN_LOG)
	./create-func-trace-interleaved.py $^
	mv $(IN_LOG).functrace $@

.PHONY: dumps
dumps: busybox.dump bin.dump vmlinux.dump

.PHONY: clean
clean:
	rm -rf *.trace*
