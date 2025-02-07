ifndef RISCV
$(error $$(RISCV) not defined)
endif

srcdir := $(PWD)/..

CXX ?= g++
AR ?= ar
CXXFLAGS := -O2 -std=c++11 -pedantic -Wall -I $(RISCV)/include -I $(srcdir) -g
LDFLAGS := -L$(RISCV)/lib -l:libdwarf.so -l:libelf.so -lz
tests := dwarftest elftest tracervproc

.PHONY: all
all: $(tests)

libtracerv := libtracerv.a

libtracerv_srcs := \
	$(srcdir)/tracerv_dwarf.cc \
	$(srcdir)/tracerv_elf.cc \
	$(srcdir)/../tracerv_processing.cc

libtracerv_hdrs := $(libtracerv_srcs:.cc=.h)
libtracerv_objs := $(libtracerv_srcs:.cc=.o)

$(libtracerv): $(libtracerv_objs)
	$(AR) rvs $@ $^

$(libtracerv_objs): %.o: %.cc $(libtracerv_hdrs)
	$(CXX) $(CXXFLAGS) -c -o $@ $<

$(tests): %: %.cc $(libtracerv)
	$(CXX) $(CXXFLAGS) -o $@ $^ $(LDFLAGS)

.PHONY: clean
clean:
	rm -rf -- $(libtracerv) $(libtracerv_objs) $(tests)

