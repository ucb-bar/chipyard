429_mcf_src_dir := $(spec_dir)/429.mcf/src
429_mcf_build_dir := $(spec_dir)/429.mcf/build.$(TARGET)
429_mcf_CFLAGS := 
429_mcf_LIBS := 

429_mcf_c_srcs := $(wildcard $(429_mcf_src_dir)/*.c)
429_mcf_c_objs := $(addprefix $(429_mcf_build_dir)/, $(patsubst %.c, %.o, $(notdir $(429_mcf_c_srcs))))

$(429_mcf_build_dir):
	mkdir -p $@

$(429_mcf_c_objs): $(429_mcf_build_dir)/%.o: $(429_mcf_src_dir)/%.c | $(429_mcf_build_dir)
	$(CC) $(CFLAGS) $(429_mcf_CFLAGS) -o $@ -c $<

$(build_dir)/429.mcf: $(429_mcf_c_objs) | $(build_dir)
	$(CC) -o $@ $(LDFLAGS) $^ $(LIBS) $(429_mcf_LIBS)

$(build_dir)/429.mcf.out: $(build_dir)/429.mcf $(TARGET_DEPENDS)
	$(TARGET_RUN) $< $(429_mcf_src_dir)/../data/test/input/inp.in $(TARGET_REDIRECT) $@

.PHONY: run-mcf
run-mcf: $(build_dir)/429.mcf.out

.PHONY: clean
clean::
	rm -rf -- $(429_mcf_build_dir)
