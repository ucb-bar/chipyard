470_lbm_src_dir := $(spec_dir)/470.lbm/src
470_lbm_build_dir := $(spec_dir)/470.lbm/build.$(TARGET)
470_lbm_CFLAGS := -DSPEC_CPU
470_lbm_LIBS := -lm

470_lbm_c_srcs := $(wildcard $(470_lbm_src_dir)/*.c)
470_lbm_c_objs := $(addprefix $(470_lbm_build_dir)/,$(patsubst %.c,%.o,$(notdir $(470_lbm_c_srcs))))

$(470_lbm_build_dir):
	mkdir -p $@

$(470_lbm_c_objs): $(470_lbm_build_dir)/%.o: $(470_lbm_src_dir)/%.c | $(470_lbm_build_dir)
	$(CC) $(CFLAGS) $(470_lbm_CFLAGS) -o $@ -c $<

$(build_dir)/470.lbm: $(470_lbm_c_objs) | $(build_dir)
	$(CC) -o $@ $(LDFLAGS) $^ $(LIBS) $(470_lbm_LIBS)

$(build_dir)/470.lbm.out: $(build_dir)/470.lbm $(TARGET_DEPENDS)
	$(TARGET_RUN) $< 1 /dev/null 0 1 $(470_lbm_src_dir)/../data/test/input/100_100_130_cf_a.of $(TARGET_REDIRECT) $@

.PHONY: run-lbm
run-lbm: $(build_dir)/470.lbm.out

.PHONY: clean
clean::
	rm -rf -- $(470_lbm_build_dir)
