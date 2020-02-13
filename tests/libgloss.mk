# Handle libgloss-htif dependency
ifndef libgloss

ifndef GCC
$(error GCC is not defined)
endif

libgloss_specs := htif_nano.specs

# Test whether libgloss-htif is globally installed and usable
# Define BUILD_LIBGLOSS=1 to unconditionally force a local build
BUILD_LIBGLOSS ?= $(shell { echo 'int main(void) { return 0; }' | \
	$(GCC) -xc -specs=$(libgloss_specs) -o /dev/null - 2> /dev/null ; } || \
	echo "$$?")

ifneq ($(BUILD_LIBGLOSS),)
$(info libgloss-htif: Using local build)

libgloss_srcdir := ../toolchains/libgloss
libgloss_builddir := libgloss
libgloss_specs := $(libgloss_srcdir)/util/$(libgloss_specs)
libgloss_lib := $(libgloss_builddir)/libgloss_htif.a
libgloss := $(libgloss_lib) $(libgloss_specs) htif.ld

LDFLAGS += -L libgloss

$(libgloss_builddir)/Makefile: $(libgloss_srcdir)/configure
	mkdir -p $(dir $@)
	cd $(dir $@) && $(realpath $<) \
		--prefix=$(shell $(GCC) -print-sysroot) \
		--host=$(TARGET) \
		--disable-multilib

$(libgloss_lib): $(libgloss_builddir)/Makefile
	$(MAKE) -C $(dir $^)

.PHONY: libgloss
libgloss: $(libgloss)

else

$(info libgloss-htif: Using global install)
libgloss :=  # No additional prerequisites

endif

CFLAGS += -specs=$(libgloss_specs)
LDFLAGS += -specs=$(libgloss_specs)

endif # libgloss
