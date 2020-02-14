401_bzip2_src_dir := $(spec_dir)/401.bzip2/src
401_bzip2_build_dir := $(spec_dir)/401.bzip2/build.$(TARGET)
401_bzip2_CFLAGS := -DSPEC_CPU -Wno-pointer-to-int-cast
401_bzip2_LIBS := 

401_bzip2_c_srcs := $(wildcard $(401_bzip2_src_dir)/*.c)
401_bzip2_c_objs := $(addprefix $(401_bzip2_build_dir)/,$(patsubst %.c,%.o,$(notdir $(401_bzip2_c_srcs))))

$(401_bzip2_build_dir):
	mkdir -p $@

$(401_bzip2_c_objs): $(401_bzip2_build_dir)/%.o: $(401_bzip2_src_dir)/%.c | $(401_bzip2_build_dir)
	$(CC) $(CFLAGS) $(401_bzip2_CFLAGS) -o $@ -c $<

$(build_dir)/401.bzip2: $(401_bzip2_c_objs) | $(build_dir)
	$(CC) -o $@ $(LDFLAGS) $^ $(LIBS) $(401_bzip2_LIBS)

$(build_dir)/401.bzip2.out: $(build_dir)/401.bzip2 $(TARGET_DEPENDS)
	$(TARGET_RUN) $< $(401_bzip2_src_dir)/../data/test/input/dryer.jpg $(TARGET_REDIRECT) $@

.PHONY: run-bzip2
run-bzip2: $(build_dir)/401.bzip2.out

.PHONY: clean
clean::
	rm -rf -- $(401_bzip2_build_dir)
