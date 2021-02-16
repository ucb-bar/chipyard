########################################################################
# CS152 Hacks
########################################################################

# Update blackbox resources without rerunning Chisel elaboration
rocketchip_csrc_dir := $(ROCKETCHIP_DIR)/src/main/resources/csrc
rocketchip_csrcs := $(wildcard $(rocketchip_csrc_dir)/*.cc $(rocketchip_csrc_dir)/*.h)
rocketchip_csrcs := $(filter-out %/emulator.cc, $(rocketchip_csrcs))

sim_csrcs := $(addprefix $(build_dir)/,$(notdir $(rocketchip_csrcs)))

$(sim_csrcs): $(build_dir)/%: $(rocketchip_csrc_dir)/% $(sim_vsrcs)
	cp $< $@

EXTRA_SIM_REQS += $(sim_csrcs)

# Local ivy2 and sbt caches
JAVA_OPTS += \
	-Dsbt.ivy.home=$(base_dir)/.ivy2 \
	-Dsbt.global.base=$(base_dir)/.sbt \
	-Dsbt.boot.directory=$(base_dir)/.sbt/boot/
