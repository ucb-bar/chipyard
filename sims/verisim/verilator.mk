#########################################################################################
# verilator installation makefrag
#########################################################################################

#########################################################################################
# verilator version, binary, and path
#########################################################################################
VERILATOR_VERSION=4.016
VERILATOR_SRCDIR=verilator/src/verilator-$(VERILATOR_VERSION)
INSTALLED_VERILATOR=$(abspath verilator/install/bin/verilator)

#########################################################################################
# build and install our own verilator to work around versioning issues
#########################################################################################
$(INSTALLED_VERILATOR): $(VERILATOR_SRCDIR)/bin/verilator
	$(MAKE) -C $(VERILATOR_SRCDIR) installbin installdata
	touch $@

.PHONY:
verilator_install: $(INSTALLED_VERILATOR)

$(VERILATOR_SRCDIR)/bin/verilator: $(VERILATOR_SRCDIR)/Makefile
	$(MAKE) -C $(VERILATOR_SRCDIR) verilator_bin
	touch $@

$(VERILATOR_SRCDIR)/Makefile: $(VERILATOR_SRCDIR)/configure
	mkdir -p $(dir $@)
	cd $(dir $@) && ./configure --prefix=$(abspath verilator/install)

$(VERILATOR_SRCDIR)/configure: verilator/verilator-$(VERILATOR_VERSION).tar.gz
	rm -rf $(dir $@)
	mkdir -p $(dir $@)
	cat $^ | tar -xz --strip-components=1 -C $(dir $@)
	touch $@

verilator/verilator-$(VERILATOR_VERSION).tar.gz:
	mkdir -p $(dir $@)
	wget http://www.veripool.org/ftp/verilator-$(VERILATOR_VERSION).tgz -O $@

#########################################################################################
# verilator binary and flags
#########################################################################################
VERILATOR := $(INSTALLED_VERILATOR) --cc --exe
CXXFLAGS := $(CXXFLAGS) -O1 -std=c++11 -I$(RISCV)/include -D__STDC_FORMAT_MACROS
VERILATOR_FLAGS := --top-module $(VLOG_MODEL) \
	+define+PRINTF_COND=\$$c\(\"verbose\",\"\&\&\"\,\"done_reset\"\) \
	+define+STOP_COND=\$$c\(\"done_reset\"\) --assert \
	--output-split 20000 \
	-Wno-STMTDLY --x-assign unique \
	-O3 -CFLAGS "$(CXXFLAGS) -DTEST_HARNESS=V$(VLOG_MODEL) -DVERILATOR"
