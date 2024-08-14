rv64ui-p-asm-tests = \
	rv64ui-p-simple \
	rv64ui-p-add \
	rv64ui-p-addi \
	rv64ui-p-and \
	rv64ui-p-andi \
	rv64ui-p-auipc \
	rv64ui-p-beq \
	rv64ui-p-bge \
	rv64ui-p-bgeu \
	rv64ui-p-blt \
	rv64ui-p-bltu \
	rv64ui-p-bne \
	rv64ui-p-fence_i \
	rv64ui-p-jal \
	rv64ui-p-jalr \
	rv64ui-p-lb \
	rv64ui-p-lbu \
	rv64ui-p-lh \
	rv64ui-p-lhu \
	rv64ui-p-lui \
	rv64ui-p-lw \
	rv64ui-p-or \
	rv64ui-p-ori \
	rv64ui-p-sb \
	rv64ui-p-sh \
	rv64ui-p-sw \
	rv64ui-p-sll \
	rv64ui-p-slli \
	rv64ui-p-slt \
	rv64ui-p-slti \
	rv64ui-p-sra \
	rv64ui-p-srai \
	rv64ui-p-srl \
	rv64ui-p-srli \
	rv64ui-p-sub \
	rv64ui-p-xor \
	rv64ui-p-xori \
	rv64ui-p-addw \
	rv64ui-p-addiw \
	rv64ui-p-ld \
	rv64ui-p-lwu \
	rv64ui-p-sd \
	rv64ui-p-slliw \
	rv64ui-p-sllw \
	rv64ui-p-sltiu \
	rv64ui-p-sltu \
	rv64ui-p-sraiw \
	rv64ui-p-sraw \
	rv64ui-p-srliw \
	rv64ui-p-srlw \
	rv64ui-p-subw

$(addprefix $(output_dir)/, $(addsuffix .hex, $(rv64ui-p-asm-tests))): $(output_dir)/%.hex: $(RISCV)/riscv64-unknown-elf/share/riscv-tests/isa/%.hex
	mkdir -p $(output_dir)
	ln -fs $< $@

$(addprefix $(output_dir)/, $(rv64ui-p-asm-tests)): $(output_dir)/%: $(RISCV)/riscv64-unknown-elf/share/riscv-tests/isa/%
	mkdir -p $(output_dir)
	ln -fs $< $@

run-rv64ui-p-asm-tests: $(addprefix $(output_dir)/, $(addsuffix .out, $(rv64ui-p-asm-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $^ /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'

run-rv64ui-p-asm-tests-debug: $(addprefix $(output_dir)/, $(addsuffix .vpd, $(rv64ui-p-asm-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $(patsubst %.vpd,%.out,$^) /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'

run-rv64ui-p-asm-tests-fst: $(addprefix $(output_dir)/, $(addsuffix .fst, $(rv64ui-p-asm-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $(patsubst %.fst,%.out,$^) /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'

rv64uc-p-asm-tests = \
	rv64uc-p-rvc

$(addprefix $(output_dir)/, $(addsuffix .hex, $(rv64uc-p-asm-tests))): $(output_dir)/%.hex: $(RISCV)/riscv64-unknown-elf/share/riscv-tests/isa/%.hex
	mkdir -p $(output_dir)
	ln -fs $< $@

$(addprefix $(output_dir)/, $(rv64uc-p-asm-tests)): $(output_dir)/%: $(RISCV)/riscv64-unknown-elf/share/riscv-tests/isa/%
	mkdir -p $(output_dir)
	ln -fs $< $@

run-rv64uc-p-asm-tests: $(addprefix $(output_dir)/, $(addsuffix .out, $(rv64uc-p-asm-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $^ /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'

run-rv64uc-p-asm-tests-debug: $(addprefix $(output_dir)/, $(addsuffix .vpd, $(rv64uc-p-asm-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $(patsubst %.vpd,%.out,$^) /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'

run-rv64uc-p-asm-tests-fst: $(addprefix $(output_dir)/, $(addsuffix .fst, $(rv64uc-p-asm-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $(patsubst %.fst,%.out,$^) /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'

rv64ud-p-asm-tests = \
	rv64ud-p-ldst \
	rv64ud-p-move \
	rv64ud-p-fcmp \
	rv64ud-p-fcvt \
	rv64ud-p-fcvt_w \
	rv64ud-p-fclass \
	rv64ud-p-fadd \
	rv64ud-p-fdiv \
	rv64ud-p-fmin \
	rv64ud-p-fmadd \
	rv64ud-p-recoding \
	rv64ud-p-structural

$(addprefix $(output_dir)/, $(addsuffix .hex, $(rv64ud-p-asm-tests))): $(output_dir)/%.hex: $(RISCV)/riscv64-unknown-elf/share/riscv-tests/isa/%.hex
	mkdir -p $(output_dir)
	ln -fs $< $@

$(addprefix $(output_dir)/, $(rv64ud-p-asm-tests)): $(output_dir)/%: $(RISCV)/riscv64-unknown-elf/share/riscv-tests/isa/%
	mkdir -p $(output_dir)
	ln -fs $< $@

run-rv64ud-p-asm-tests: $(addprefix $(output_dir)/, $(addsuffix .out, $(rv64ud-p-asm-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $^ /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'

run-rv64ud-p-asm-tests-debug: $(addprefix $(output_dir)/, $(addsuffix .vpd, $(rv64ud-p-asm-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $(patsubst %.vpd,%.out,$^) /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'

run-rv64ud-p-asm-tests-fst: $(addprefix $(output_dir)/, $(addsuffix .fst, $(rv64ud-p-asm-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $(patsubst %.fst,%.out,$^) /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'

rv64ud-v-asm-tests = \
	rv64ud-v-ldst \
	rv64ud-v-move \
	rv64ud-v-fcmp \
	rv64ud-v-fcvt \
	rv64ud-v-fcvt_w \
	rv64ud-v-fclass \
	rv64ud-v-fadd \
	rv64ud-v-fdiv \
	rv64ud-v-fmin \
	rv64ud-v-fmadd \
	rv64ud-v-recoding \
	rv64ud-v-structural

$(addprefix $(output_dir)/, $(addsuffix .hex, $(rv64ud-v-asm-tests))): $(output_dir)/%.hex: $(RISCV)/riscv64-unknown-elf/share/riscv-tests/isa/%.hex
	mkdir -p $(output_dir)
	ln -fs $< $@

$(addprefix $(output_dir)/, $(rv64ud-v-asm-tests)): $(output_dir)/%: $(RISCV)/riscv64-unknown-elf/share/riscv-tests/isa/%
	mkdir -p $(output_dir)
	ln -fs $< $@

run-rv64ud-v-asm-tests: $(addprefix $(output_dir)/, $(addsuffix .out, $(rv64ud-v-asm-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $^ /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'

run-rv64ud-v-asm-tests-debug: $(addprefix $(output_dir)/, $(addsuffix .vpd, $(rv64ud-v-asm-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $(patsubst %.vpd,%.out,$^) /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'

run-rv64ud-v-asm-tests-fst: $(addprefix $(output_dir)/, $(addsuffix .fst, $(rv64ud-v-asm-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $(patsubst %.fst,%.out,$^) /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'

rv64uc-v-asm-tests = \
	rv64uc-v-rvc

$(addprefix $(output_dir)/, $(addsuffix .hex, $(rv64uc-v-asm-tests))): $(output_dir)/%.hex: $(RISCV)/riscv64-unknown-elf/share/riscv-tests/isa/%.hex
	mkdir -p $(output_dir)
	ln -fs $< $@

$(addprefix $(output_dir)/, $(rv64uc-v-asm-tests)): $(output_dir)/%: $(RISCV)/riscv64-unknown-elf/share/riscv-tests/isa/%
	mkdir -p $(output_dir)
	ln -fs $< $@

run-rv64uc-v-asm-tests: $(addprefix $(output_dir)/, $(addsuffix .out, $(rv64uc-v-asm-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $^ /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'

run-rv64uc-v-asm-tests-debug: $(addprefix $(output_dir)/, $(addsuffix .vpd, $(rv64uc-v-asm-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $(patsubst %.vpd,%.out,$^) /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'

run-rv64uc-v-asm-tests-fst: $(addprefix $(output_dir)/, $(addsuffix .fst, $(rv64uc-v-asm-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $(patsubst %.fst,%.out,$^) /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'

rv64um-p-asm-tests = \
	rv64um-p-mul \
	rv64um-p-mulh \
	rv64um-p-mulhsu \
	rv64um-p-mulhu \
	rv64um-p-div \
	rv64um-p-divu \
	rv64um-p-rem \
	rv64um-p-remu \
	rv64um-p-divuw \
	rv64um-p-divw \
	rv64um-p-mulw \
	rv64um-p-remuw \
	rv64um-p-remw

$(addprefix $(output_dir)/, $(addsuffix .hex, $(rv64um-p-asm-tests))): $(output_dir)/%.hex: $(RISCV)/riscv64-unknown-elf/share/riscv-tests/isa/%.hex
	mkdir -p $(output_dir)
	ln -fs $< $@

$(addprefix $(output_dir)/, $(rv64um-p-asm-tests)): $(output_dir)/%: $(RISCV)/riscv64-unknown-elf/share/riscv-tests/isa/%
	mkdir -p $(output_dir)
	ln -fs $< $@

run-rv64um-p-asm-tests: $(addprefix $(output_dir)/, $(addsuffix .out, $(rv64um-p-asm-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $^ /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'

run-rv64um-p-asm-tests-debug: $(addprefix $(output_dir)/, $(addsuffix .vpd, $(rv64um-p-asm-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $(patsubst %.vpd,%.out,$^) /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'

run-rv64um-p-asm-tests-fst: $(addprefix $(output_dir)/, $(addsuffix .fst, $(rv64um-p-asm-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $(patsubst %.fst,%.out,$^) /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'

rv64um-v-asm-tests = \
	rv64um-v-mul \
	rv64um-v-mulh \
	rv64um-v-mulhsu \
	rv64um-v-mulhu \
	rv64um-v-div \
	rv64um-v-divu \
	rv64um-v-rem \
	rv64um-v-remu \
	rv64um-v-divuw \
	rv64um-v-divw \
	rv64um-v-mulw \
	rv64um-v-remuw \
	rv64um-v-remw

$(addprefix $(output_dir)/, $(addsuffix .hex, $(rv64um-v-asm-tests))): $(output_dir)/%.hex: $(RISCV)/riscv64-unknown-elf/share/riscv-tests/isa/%.hex
	mkdir -p $(output_dir)
	ln -fs $< $@

$(addprefix $(output_dir)/, $(rv64um-v-asm-tests)): $(output_dir)/%: $(RISCV)/riscv64-unknown-elf/share/riscv-tests/isa/%
	mkdir -p $(output_dir)
	ln -fs $< $@

run-rv64um-v-asm-tests: $(addprefix $(output_dir)/, $(addsuffix .out, $(rv64um-v-asm-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $^ /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'

run-rv64um-v-asm-tests-debug: $(addprefix $(output_dir)/, $(addsuffix .vpd, $(rv64um-v-asm-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $(patsubst %.vpd,%.out,$^) /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'

run-rv64um-v-asm-tests-fst: $(addprefix $(output_dir)/, $(addsuffix .fst, $(rv64um-v-asm-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $(patsubst %.fst,%.out,$^) /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'

rv64si-p-asm-tests = \
	rv64si-p-csr \
	rv64si-p-ma_fetch \
	rv64si-p-scall \
	rv64si-p-sbreak \
	rv64si-p-wfi \
	rv64si-p-dirty \
	rv64si-p-icache-alias

$(addprefix $(output_dir)/, $(addsuffix .hex, $(rv64si-p-asm-tests))): $(output_dir)/%.hex: $(RISCV)/riscv64-unknown-elf/share/riscv-tests/isa/%.hex
	mkdir -p $(output_dir)
	ln -fs $< $@

$(addprefix $(output_dir)/, $(rv64si-p-asm-tests)): $(output_dir)/%: $(RISCV)/riscv64-unknown-elf/share/riscv-tests/isa/%
	mkdir -p $(output_dir)
	ln -fs $< $@

run-rv64si-p-asm-tests: $(addprefix $(output_dir)/, $(addsuffix .out, $(rv64si-p-asm-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $^ /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'

run-rv64si-p-asm-tests-debug: $(addprefix $(output_dir)/, $(addsuffix .vpd, $(rv64si-p-asm-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $(patsubst %.vpd,%.out,$^) /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'

run-rv64si-p-asm-tests-fst: $(addprefix $(output_dir)/, $(addsuffix .fst, $(rv64si-p-asm-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $(patsubst %.fst,%.out,$^) /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'

rv64ua-v-asm-tests = \
	rv64ua-v-amoadd_w \
	rv64ua-v-amoand_w \
	rv64ua-v-amoor_w \
	rv64ua-v-amoxor_w \
	rv64ua-v-amoswap_w \
	rv64ua-v-amomax_w \
	rv64ua-v-amomaxu_w \
	rv64ua-v-amomin_w \
	rv64ua-v-amominu_w \
	rv64ua-v-lrsc \
	rv64ua-v-amoadd_d \
	rv64ua-v-amoand_d \
	rv64ua-v-amoor_d \
	rv64ua-v-amoxor_d \
	rv64ua-v-amoswap_d \
	rv64ua-v-amomax_d \
	rv64ua-v-amomaxu_d \
	rv64ua-v-amomin_d \
	rv64ua-v-amominu_d

$(addprefix $(output_dir)/, $(addsuffix .hex, $(rv64ua-v-asm-tests))): $(output_dir)/%.hex: $(RISCV)/riscv64-unknown-elf/share/riscv-tests/isa/%.hex
	mkdir -p $(output_dir)
	ln -fs $< $@

$(addprefix $(output_dir)/, $(rv64ua-v-asm-tests)): $(output_dir)/%: $(RISCV)/riscv64-unknown-elf/share/riscv-tests/isa/%
	mkdir -p $(output_dir)
	ln -fs $< $@

run-rv64ua-v-asm-tests: $(addprefix $(output_dir)/, $(addsuffix .out, $(rv64ua-v-asm-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $^ /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'

run-rv64ua-v-asm-tests-debug: $(addprefix $(output_dir)/, $(addsuffix .vpd, $(rv64ua-v-asm-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $(patsubst %.vpd,%.out,$^) /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'

run-rv64ua-v-asm-tests-fst: $(addprefix $(output_dir)/, $(addsuffix .fst, $(rv64ua-v-asm-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $(patsubst %.fst,%.out,$^) /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'

rv64uf-v-asm-tests = \
	rv64uf-v-ldst \
	rv64uf-v-move \
	rv64uf-v-fcmp \
	rv64uf-v-fcvt \
	rv64uf-v-fcvt_w \
	rv64uf-v-fclass \
	rv64uf-v-fadd \
	rv64uf-v-fdiv \
	rv64uf-v-fmin \
	rv64uf-v-fmadd \
	rv64uf-v-recoding

$(addprefix $(output_dir)/, $(addsuffix .hex, $(rv64uf-v-asm-tests))): $(output_dir)/%.hex: $(RISCV)/riscv64-unknown-elf/share/riscv-tests/isa/%.hex
	mkdir -p $(output_dir)
	ln -fs $< $@

$(addprefix $(output_dir)/, $(rv64uf-v-asm-tests)): $(output_dir)/%: $(RISCV)/riscv64-unknown-elf/share/riscv-tests/isa/%
	mkdir -p $(output_dir)
	ln -fs $< $@

run-rv64uf-v-asm-tests: $(addprefix $(output_dir)/, $(addsuffix .out, $(rv64uf-v-asm-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $^ /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'

run-rv64uf-v-asm-tests-debug: $(addprefix $(output_dir)/, $(addsuffix .vpd, $(rv64uf-v-asm-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $(patsubst %.vpd,%.out,$^) /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'

run-rv64uf-v-asm-tests-fst: $(addprefix $(output_dir)/, $(addsuffix .fst, $(rv64uf-v-asm-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $(patsubst %.fst,%.out,$^) /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'

rv64uf-p-asm-tests = \
	rv64uf-p-ldst \
	rv64uf-p-move \
	rv64uf-p-fcmp \
	rv64uf-p-fcvt \
	rv64uf-p-fcvt_w \
	rv64uf-p-fclass \
	rv64uf-p-fadd \
	rv64uf-p-fdiv \
	rv64uf-p-fmin \
	rv64uf-p-fmadd \
	rv64uf-p-recoding

$(addprefix $(output_dir)/, $(addsuffix .hex, $(rv64uf-p-asm-tests))): $(output_dir)/%.hex: $(RISCV)/riscv64-unknown-elf/share/riscv-tests/isa/%.hex
	mkdir -p $(output_dir)
	ln -fs $< $@

$(addprefix $(output_dir)/, $(rv64uf-p-asm-tests)): $(output_dir)/%: $(RISCV)/riscv64-unknown-elf/share/riscv-tests/isa/%
	mkdir -p $(output_dir)
	ln -fs $< $@

run-rv64uf-p-asm-tests: $(addprefix $(output_dir)/, $(addsuffix .out, $(rv64uf-p-asm-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $^ /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'

run-rv64uf-p-asm-tests-debug: $(addprefix $(output_dir)/, $(addsuffix .vpd, $(rv64uf-p-asm-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $(patsubst %.vpd,%.out,$^) /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'

run-rv64uf-p-asm-tests-fst: $(addprefix $(output_dir)/, $(addsuffix .fst, $(rv64uf-p-asm-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $(patsubst %.fst,%.out,$^) /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'

rv64ua-p-asm-tests = \
	rv64ua-p-amoadd_w \
	rv64ua-p-amoand_w \
	rv64ua-p-amoor_w \
	rv64ua-p-amoxor_w \
	rv64ua-p-amoswap_w \
	rv64ua-p-amomax_w \
	rv64ua-p-amomaxu_w \
	rv64ua-p-amomin_w \
	rv64ua-p-amominu_w \
	rv64ua-p-lrsc \
	rv64ua-p-amoadd_d \
	rv64ua-p-amoand_d \
	rv64ua-p-amoor_d \
	rv64ua-p-amoxor_d \
	rv64ua-p-amoswap_d \
	rv64ua-p-amomax_d \
	rv64ua-p-amomaxu_d \
	rv64ua-p-amomin_d \
	rv64ua-p-amominu_d

$(addprefix $(output_dir)/, $(addsuffix .hex, $(rv64ua-p-asm-tests))): $(output_dir)/%.hex: $(RISCV)/riscv64-unknown-elf/share/riscv-tests/isa/%.hex
	mkdir -p $(output_dir)
	ln -fs $< $@

$(addprefix $(output_dir)/, $(rv64ua-p-asm-tests)): $(output_dir)/%: $(RISCV)/riscv64-unknown-elf/share/riscv-tests/isa/%
	mkdir -p $(output_dir)
	ln -fs $< $@

run-rv64ua-p-asm-tests: $(addprefix $(output_dir)/, $(addsuffix .out, $(rv64ua-p-asm-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $^ /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'

run-rv64ua-p-asm-tests-debug: $(addprefix $(output_dir)/, $(addsuffix .vpd, $(rv64ua-p-asm-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $(patsubst %.vpd,%.out,$^) /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'

run-rv64ua-p-asm-tests-fst: $(addprefix $(output_dir)/, $(addsuffix .fst, $(rv64ua-p-asm-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $(patsubst %.fst,%.out,$^) /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'

rv64mi-p-asm-tests = \
	rv64mi-p-csr \
	rv64mi-p-mcsr \
	rv64mi-p-illegal \
	rv64mi-p-ma_addr \
	rv64mi-p-ma_fetch \
	rv64mi-p-sbreak \
	rv64mi-p-scall \
	rv64mi-p-breakpoint \
	rv64mi-p-lh-misaligned \
	rv64mi-p-lw-misaligned \
	rv64mi-p-sh-misaligned \
	rv64mi-p-sw-misaligned \
	rv64mi-p-zicntr \
	rv64mi-p-access

$(addprefix $(output_dir)/, $(addsuffix .hex, $(rv64mi-p-asm-tests))): $(output_dir)/%.hex: $(RISCV)/riscv64-unknown-elf/share/riscv-tests/isa/%.hex
	mkdir -p $(output_dir)
	ln -fs $< $@

$(addprefix $(output_dir)/, $(rv64mi-p-asm-tests)): $(output_dir)/%: $(RISCV)/riscv64-unknown-elf/share/riscv-tests/isa/%
	mkdir -p $(output_dir)
	ln -fs $< $@

run-rv64mi-p-asm-tests: $(addprefix $(output_dir)/, $(addsuffix .out, $(rv64mi-p-asm-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $^ /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'

run-rv64mi-p-asm-tests-debug: $(addprefix $(output_dir)/, $(addsuffix .vpd, $(rv64mi-p-asm-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $(patsubst %.vpd,%.out,$^) /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'

run-rv64mi-p-asm-tests-fst: $(addprefix $(output_dir)/, $(addsuffix .fst, $(rv64mi-p-asm-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $(patsubst %.fst,%.out,$^) /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'

rv64ui-v-asm-tests = \
	rv64ui-v-simple \
	rv64ui-v-add \
	rv64ui-v-addi \
	rv64ui-v-and \
	rv64ui-v-andi \
	rv64ui-v-auipc \
	rv64ui-v-beq \
	rv64ui-v-bge \
	rv64ui-v-bgeu \
	rv64ui-v-blt \
	rv64ui-v-bltu \
	rv64ui-v-bne \
	rv64ui-v-fence_i \
	rv64ui-v-jal \
	rv64ui-v-jalr \
	rv64ui-v-lb \
	rv64ui-v-lbu \
	rv64ui-v-lh \
	rv64ui-v-lhu \
	rv64ui-v-lui \
	rv64ui-v-lw \
	rv64ui-v-or \
	rv64ui-v-ori \
	rv64ui-v-sb \
	rv64ui-v-sh \
	rv64ui-v-sw \
	rv64ui-v-sll \
	rv64ui-v-slli \
	rv64ui-v-slt \
	rv64ui-v-slti \
	rv64ui-v-sra \
	rv64ui-v-srai \
	rv64ui-v-srl \
	rv64ui-v-srli \
	rv64ui-v-sub \
	rv64ui-v-xor \
	rv64ui-v-xori \
	rv64ui-v-addw \
	rv64ui-v-addiw \
	rv64ui-v-ld \
	rv64ui-v-lwu \
	rv64ui-v-sd \
	rv64ui-v-slliw \
	rv64ui-v-sllw \
	rv64ui-v-sltiu \
	rv64ui-v-sltu \
	rv64ui-v-sraiw \
	rv64ui-v-sraw \
	rv64ui-v-srliw \
	rv64ui-v-srlw \
	rv64ui-v-subw

$(addprefix $(output_dir)/, $(addsuffix .hex, $(rv64ui-v-asm-tests))): $(output_dir)/%.hex: $(RISCV)/riscv64-unknown-elf/share/riscv-tests/isa/%.hex
	mkdir -p $(output_dir)
	ln -fs $< $@

$(addprefix $(output_dir)/, $(rv64ui-v-asm-tests)): $(output_dir)/%: $(RISCV)/riscv64-unknown-elf/share/riscv-tests/isa/%
	mkdir -p $(output_dir)
	ln -fs $< $@

run-rv64ui-v-asm-tests: $(addprefix $(output_dir)/, $(addsuffix .out, $(rv64ui-v-asm-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $^ /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'

run-rv64ui-v-asm-tests-debug: $(addprefix $(output_dir)/, $(addsuffix .vpd, $(rv64ui-v-asm-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $(patsubst %.vpd,%.out,$^) /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'

run-rv64ui-v-asm-tests-fst: $(addprefix $(output_dir)/, $(addsuffix .fst, $(rv64ui-v-asm-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $(patsubst %.fst,%.out,$^) /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'

run-asm-v-tests: $(addprefix $(output_dir)/, $(addsuffix .out, $(rv64ud-v-asm-tests) $(rv64uc-v-asm-tests) $(rv64um-v-asm-tests) $(rv64ua-v-asm-tests) $(rv64uf-v-asm-tests) $(rv64ui-v-asm-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $^ /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'
run-asm-v-tests-debug: $(addprefix $(output_dir)/, $(addsuffix .vpd, $(rv64ud-v-asm-tests) $(rv64uc-v-asm-tests) $(rv64um-v-asm-tests) $(rv64ua-v-asm-tests) $(rv64uf-v-asm-tests) $(rv64ui-v-asm-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $(patsubst %.vpd,%.out,$^) /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'
run-asm-v-tests-fst: $(addprefix $(output_dir)/, $(addsuffix .fst, $(rv64ud-v-asm-tests) $(rv64uc-v-asm-tests) $(rv64um-v-asm-tests) $(rv64ua-v-asm-tests) $(rv64uf-v-asm-tests) $(rv64ui-v-asm-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $(patsubst %.fst,%.out,$^) /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'
run-asm-v-tests-fast: $(addprefix $(output_dir)/, $(addsuffix .run, $(rv64ud-v-asm-tests) $(rv64uc-v-asm-tests) $(rv64um-v-asm-tests) $(rv64ua-v-asm-tests) $(rv64uf-v-asm-tests) $(rv64ui-v-asm-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $^ /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'


run-asm-p-tests: $(addprefix $(output_dir)/, $(addsuffix .out, $(rv64ui-p-asm-tests) $(rv64uc-p-asm-tests) $(rv64ud-p-asm-tests) $(rv64um-p-asm-tests) $(rv64si-p-asm-tests) $(rv64uf-p-asm-tests) $(rv64ua-p-asm-tests) $(rv64mi-p-asm-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $^ /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'
run-asm-p-tests-debug: $(addprefix $(output_dir)/, $(addsuffix .vpd, $(rv64ui-p-asm-tests) $(rv64uc-p-asm-tests) $(rv64ud-p-asm-tests) $(rv64um-p-asm-tests) $(rv64si-p-asm-tests) $(rv64uf-p-asm-tests) $(rv64ua-p-asm-tests) $(rv64mi-p-asm-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $(patsubst %.vpd,%.out,$^) /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'
run-asm-p-tests-fst: $(addprefix $(output_dir)/, $(addsuffix .fst, $(rv64ui-p-asm-tests) $(rv64uc-p-asm-tests) $(rv64ud-p-asm-tests) $(rv64um-p-asm-tests) $(rv64si-p-asm-tests) $(rv64uf-p-asm-tests) $(rv64ua-p-asm-tests) $(rv64mi-p-asm-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $(patsubst %.fst,%.out,$^) /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'
run-asm-p-tests-fast: $(addprefix $(output_dir)/, $(addsuffix .run, $(rv64ui-p-asm-tests) $(rv64uc-p-asm-tests) $(rv64ud-p-asm-tests) $(rv64um-p-asm-tests) $(rv64si-p-asm-tests) $(rv64uf-p-asm-tests) $(rv64ua-p-asm-tests) $(rv64mi-p-asm-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $^ /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'

run-asm-tests: $(addprefix $(output_dir)/, $(addsuffix .out, $(rv64ui-p-asm-tests) $(rv64uc-p-asm-tests) $(rv64ud-p-asm-tests) $(rv64ud-v-asm-tests) $(rv64uc-v-asm-tests) $(rv64um-p-asm-tests) $(rv64um-v-asm-tests) $(rv64si-p-asm-tests) $(rv64ua-v-asm-tests) $(rv64uf-v-asm-tests) $(rv64uf-p-asm-tests) $(rv64ua-p-asm-tests) $(rv64mi-p-asm-tests) $(rv64ui-v-asm-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $^ /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'
run-asm-tests-debug: $(addprefix $(output_dir)/, $(addsuffix .vpd, $(rv64ui-p-asm-tests) $(rv64uc-p-asm-tests) $(rv64ud-p-asm-tests) $(rv64ud-v-asm-tests) $(rv64uc-v-asm-tests) $(rv64um-p-asm-tests) $(rv64um-v-asm-tests) $(rv64si-p-asm-tests) $(rv64ua-v-asm-tests) $(rv64uf-v-asm-tests) $(rv64uf-p-asm-tests) $(rv64ua-p-asm-tests) $(rv64mi-p-asm-tests) $(rv64ui-v-asm-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $(patsubst %.vpd,%.out,$^) /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'
run-asm-tests-fst: $(addprefix $(output_dir)/, $(addsuffix .fst, $(rv64ui-p-asm-tests) $(rv64uc-p-asm-tests) $(rv64ud-p-asm-tests) $(rv64ud-v-asm-tests) $(rv64uc-v-asm-tests) $(rv64um-p-asm-tests) $(rv64um-v-asm-tests) $(rv64si-p-asm-tests) $(rv64ua-v-asm-tests) $(rv64uf-v-asm-tests) $(rv64uf-p-asm-tests) $(rv64ua-p-asm-tests) $(rv64mi-p-asm-tests) $(rv64ui-v-asm-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $(patsubst %.fst,%.out,$^) /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'
run-asm-tests-fast: $(addprefix $(output_dir)/, $(addsuffix .run, $(rv64ui-p-asm-tests) $(rv64uc-p-asm-tests) $(rv64ud-p-asm-tests) $(rv64ud-v-asm-tests) $(rv64uc-v-asm-tests) $(rv64um-p-asm-tests) $(rv64um-v-asm-tests) $(rv64si-p-asm-tests) $(rv64ua-v-asm-tests) $(rv64uf-v-asm-tests) $(rv64uf-p-asm-tests) $(rv64ua-p-asm-tests) $(rv64mi-p-asm-tests) $(rv64ui-v-asm-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $^ /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'

rvi-bmark-tests = \
	median.riscv \
	multiply.riscv \
	qsort.riscv \
	rsort.riscv \
	pmp.riscv \
	towers.riscv \
	vvadd.riscv \
	dhrystone.riscv \
	mt-matmul.riscv

$(addprefix $(output_dir)/, $(addsuffix .hex, $(rvi-bmark-tests))): $(output_dir)/%.hex: $(RISCV)/riscv64-unknown-elf/share/riscv-tests/benchmarks/%.hex
	mkdir -p $(output_dir)
	ln -fs $< $@

$(addprefix $(output_dir)/, $(rvi-bmark-tests)): $(output_dir)/%: $(RISCV)/riscv64-unknown-elf/share/riscv-tests/benchmarks/%
	mkdir -p $(output_dir)
	ln -fs $< $@

run-rvi-bmark-tests: $(addprefix $(output_dir)/, $(addsuffix .out, $(rvi-bmark-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $^ /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'

run-rvi-bmark-tests-debug: $(addprefix $(output_dir)/, $(addsuffix .vpd, $(rvi-bmark-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $(patsubst %.vpd,%.out,$^) /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'

run-rvi-bmark-tests-fst: $(addprefix $(output_dir)/, $(addsuffix .fst, $(rvi-bmark-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $(patsubst %.fst,%.out,$^) /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'

rvd-bmark-tests = \
	mm.riscv \
	spmv.riscv \
	mt-vvadd.riscv

$(addprefix $(output_dir)/, $(addsuffix .hex, $(rvd-bmark-tests))): $(output_dir)/%.hex: $(RISCV)/riscv64-unknown-elf/share/riscv-tests/benchmarks/%.hex
	mkdir -p $(output_dir)
	ln -fs $< $@

$(addprefix $(output_dir)/, $(rvd-bmark-tests)): $(output_dir)/%: $(RISCV)/riscv64-unknown-elf/share/riscv-tests/benchmarks/%
	mkdir -p $(output_dir)
	ln -fs $< $@

run-rvd-bmark-tests: $(addprefix $(output_dir)/, $(addsuffix .out, $(rvd-bmark-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $^ /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'

run-rvd-bmark-tests-debug: $(addprefix $(output_dir)/, $(addsuffix .vpd, $(rvd-bmark-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $(patsubst %.vpd,%.out,$^) /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'

run-rvd-bmark-tests-fst: $(addprefix $(output_dir)/, $(addsuffix .fst, $(rvd-bmark-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $(patsubst %.fst,%.out,$^) /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'

run-bmark-tests: $(addprefix $(output_dir)/, $(addsuffix .out, $(rvi-bmark-tests) $(rvd-bmark-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $^ /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'
run-bmark-tests-debug: $(addprefix $(output_dir)/, $(addsuffix .vpd, $(rvi-bmark-tests) $(rvd-bmark-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $(patsubst %.vpd,%.out,$^) /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'
run-bmark-tests-fst: $(addprefix $(output_dir)/, $(addsuffix .fst, $(rvi-bmark-tests) $(rvd-bmark-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $(patsubst %.fst,%.out,$^) /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'
run-bmark-tests-fast: $(addprefix $(output_dir)/, $(addsuffix .run, $(rvi-bmark-tests) $(rvd-bmark-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $^ /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'

regression-tests = \
rv64ud-v-fcvt \
rv64ud-p-fdiv \
rv64ud-v-fadd \
rv64uf-v-fadd \
rv64um-v-mul \
rv64mi-p-breakpoint \
rv64uc-v-rvc \
rv64ud-v-structural \
rv64si-p-wfi \
rv64um-v-divw \
rv64ua-v-lrsc \
rv64ui-v-fence_i \
rv64ud-v-fcvt_w \
rv64uf-v-fmin \
rv64ui-v-sb \
rv64ua-v-amomax_d \
rv64ud-v-move \
rv64ud-v-fclass \
rv64ua-v-amoand_d \
rv64ua-v-amoxor_d \
rv64si-p-sbreak \
rv64ud-v-fmadd \
rv64uf-v-ldst \
rv64um-v-mulh \
rv64si-p-dirty
run-regression-tests: $(addprefix $(output_dir)/, $(addsuffix .out, $(regression-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $^ /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'
run-regression-tests-debug: $(addprefix $(output_dir)/, $(addsuffix .vpd, $(regression-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $(patsubst %.vpd,%.out,$^) /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'
run-regression-tests-fst: $(addprefix $(output_dir)/, $(addsuffix .fst, $(regression-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $(patsubst %.fst,%.out,$^) /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'
run-regression-tests-fast: $(addprefix $(output_dir)/, $(addsuffix .run, $(regression-tests)))
	@echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if( /\*{3}(.{8})\*{3}(.*)/ || /ASSERTION (FAILED):(.*)/i )' $^ /dev/null | perl -pe 'BEGIN { $$failed = 0 } $$failed = 1 if(/FAILED/i); END { exit($$failed) }'
