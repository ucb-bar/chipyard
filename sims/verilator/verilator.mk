#########################################################################################
# verilator installation makefrag
#########################################################################################

#########################################################################################
# verilator version, binary, and path
#########################################################################################
VERILATOR_VERSION      = 4.028
VERILATOR_INSTALL_DIR ?= verilator_install
VERILATOR_SRCDIR       = $(VERILATOR_INSTALL_DIR)/src/verilator-$(VERILATOR_VERSION)
INSTALLED_VERILATOR    = $(abspath $(VERILATOR_INSTALL_DIR)/install/bin/verilator)

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
	cd $(dir $@) && ./configure --prefix=$(abspath $(VERILATOR_INSTALL_DIR)/install)

$(VERILATOR_SRCDIR)/configure: $(VERILATOR_INSTALL_DIR)/verilator-$(VERILATOR_VERSION).tar.gz
	rm -rf $(dir $@)
	mkdir -p $(dir $@)
	cat $^ | tar -xz --strip-components=1 -C $(dir $@)
	touch $@

$(VERILATOR_INSTALL_DIR)/verilator-$(VERILATOR_VERSION).tar.gz:
	mkdir -p $(dir $@)
	wget https://www.veripool.org/ftp/verilator-$(VERILATOR_VERSION).tgz -O $@
