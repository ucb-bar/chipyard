ifndef RISCV
$(error RISCV is unset)
else
$(info Running with RISCV=$(RISCV))
endif

PREFIX ?= $RISCV/

default: libgeneric.so

libgeneric.so: rocc.cc
	g++ -L $(RISCV)/lib -Wl,-rpath,$(RISCV)/lib -shared -o $@ -std=c++17 -I $(RISCV)/include -fPIC $^ -lriscv

.PHONY: install
install: libgeneric.so
	cp libgeneric.so $(RISCV)/lib

clean:
	rm -rf *.o *.so
