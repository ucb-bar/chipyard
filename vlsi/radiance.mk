TIMEOUT_CYCLES = 0
timeout_cycles = 0
START_TIME = 49000ns
override SIM_FLAGS += +loadmem=$(BINARY) +vcs+initreg+random
override VCS_NONCC_OPTS += +vcs+initreg+random
override EXTRA_SIM_PREPROC_DEFINES += +define+DPI_DISABLE +define+NDEBUG +define+VLSI_SIM +define+SYNTHESIS
