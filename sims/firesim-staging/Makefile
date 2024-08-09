#########################################################################################
# firesim-wrapper makefile
#########################################################################################

#########################################################################################
# general path variables
#########################################################################################
base_dir=$(abspath ../..)
sim_dir=$(abspath .)

#########################################################################################
# include shared variables
#########################################################################################
include $(base_dir)/variables.mk

#########################################################################################
# generate firrtl + annos needed for firesim
#########################################################################################
.PHONY: default
default: firrtl

#########################################################################################
# unused
# TODO: remove from main common.mk into simulator specific .mk
#########################################################################################
sim_name = unused
sim_prefix = unused
sim = unused
sim_debug = unused

$(sim_files): | $(build_dir)
	touch $@

#########################################################################################
# import other necessary rules and variables
#########################################################################################
include $(base_dir)/common.mk

#########################################################################################
# testing
#########################################################################################

# expects that chipyard has all env.sh variables set
# requires abs path TARGET_PROJECT_MAKEFRAG and proper name TARGET_PROJECT
export TARGET_PROJECT=bridges
export TARGET_PROJECT_MAKEFRAG=$(base_dir)/generators/firesim-stuff/src/main/makefrag/$(TARGET_PROJECT)
.PHONY: tmp
tmp:
	cd $(sim_dir)/../firesim/sim && \
		source sourceme-manager.sh --skip-ssh-setup \
		make run-verilator

#########################################################################################
# general cleanup rules
#########################################################################################
.PHONY: clean
clean:
	rm -rf $(CLASSPATH_CACHE) $(gen_dir)
