458_sjeng_src_dir := $(spec_dir)/458.sjeng/src
458_sjeng_build_dir := $(spec_dir)/458.sjeng/build.$(TARGET)
458_sjeng_CFLAGS := -DSPEC_CPU
458_sjeng_LIBS := 

458_sjeng_c_srcs := $(wildcard $(458_sjeng_src_dir)/*.c)
458_sjeng_c_objs := $(addprefix $(458_sjeng_build_dir)/,$(patsubst %.c,%.o,$(notdir $(458_sjeng_c_srcs))))

$(458_sjeng_build_dir):
	mkdir -p $@

$(458_sjeng_c_objs): $(458_sjeng_build_dir)/%.o: $(458_sjeng_src_dir)/%.c | $(458_sjeng_build_dir)
	$(CC) $(CFLAGS) $(458_sjeng_CFLAGS) -o $@ -c $<

$(build_dir)/458.sjeng: $(458_sjeng_c_objs)
	$(CC) -o $@ $(LDFLAGS) $^ $(LIBS) $(458_sjeng_LIBS)

$(build_dir)/458.sjeng.out: $(build_dir)/458.sjeng $(TARGET_DEPENDS)
	$(TARGET_RUN) $< $(458_sjeng_src_dir)/../data/test/input/test.txt $(TARGET_REDIRECT) $@

.PHONY: run-sjeng
run-sjeng: $(build_dir)/458.sjeng.out

.PHONY: clean
clean::
	rm -rf -- $(458_sjeng_build_dir)
