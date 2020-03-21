########################################################################
# CS152 Lab 3: Open-Ended Problem 1                                    #
########################################################################

workdir := $(dir $(realpath $(firstword $(MAKEFILE_LIST))))
workdir := $(workdir:/=)
basedir := $(workdir)/../..

# Autograder submission

zip_obj := upload.zip
zip_srcs := $(basedir)/generators/boom/src/main/resources/csrc/predictor_sw.cc

.PHONY: zip
zip: $(zip_srcs)
	rm -f -- $(zip_obj)
	zip -j $(zip_obj) $^


.SUFFIXES: # Disable built-in suffix rules
