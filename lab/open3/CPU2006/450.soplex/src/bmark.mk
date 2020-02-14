450_soplex_src_dir := $(spec_dir)/450.soplex/src
450_soplex_build_dir := $(spec_dir)/450.soplex/build.$(TARGET)
450_soplex_CFLAGS :=
450_soplex_LIBS := 

450_soplex_c_srcs := $(wildcard $(450_soplex_src_dir)/*.cc)
450_soplex_c_objs := $(addprefix $(450_soplex_build_dir)/,$(patsubst %.cc,%.o,$(notdir $(450_soplex_c_srcs))))

$(450_soplex_build_dir):
	mkdir -p $@

$(450_soplex_c_objs): $(450_soplex_build_dir)/%.o: $(450_soplex_src_dir)/%.cc | $(450_soplex_build_dir)
	$(CXX) $(CXXFLAGS) $(450_soplex_CFLAGS) -o $@ -c $<

$(build_dir)/450.soplex: $(450_soplex_c_objs) | $(build_dir)
	$(CXX) -o $@ $(LDFLAGS) $^ $(LIBS) $(450_soplex_LIBS)

$(build_dir)/450.soplex.out: $(build_dir)/450.soplex $(TARGET_DEPENDS)
	$(TARGET_RUN) $< -s2 $(450_soplex_src_dir)/../data/test/input/test.mps $(TARGET_REDIRECT) $@

.PHONY: run-soplex
run-soplex: $(build_dir)/450.soplex.out

.PHONY: clean
clean::
	rm -rf -- $(450_soplex_build_dir)
