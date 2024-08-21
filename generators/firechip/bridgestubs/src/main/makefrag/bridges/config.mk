# See LICENSE for license details.

# These point at the main class of the target's Chisel generator
DESIGN_PACKAGE ?= firechip.bridgestubs
DESIGN ?= BlockDevModule

TARGET_CONFIG_PACKAGE ?= firechip.bridgestubs
TARGET_CONFIG ?= NoConfig

PLATFORM_CONFIG_PACKAGE ?= firesim.configs
PLATFORM_CONFIG ?= DefaultF1Config

# Override project for the target.
TARGET_SBT_PROJECT := firechip_bridgestubs

# The following two are unused by this project's set of makefrags since
# Chipyard's makefile is directly invoked.
TARGET_SBT_DIR :=
TARGET_SOURCE_DIRS :=

# Only used in this projects makefrags
makefile_path := $(abspath $(lastword $(MAKEFILE_LIST)))
makefile_dir := $(patsubst %/,%,$(dir $(makefile_path)))
chipyard_dir := $(abspath $(makefile_dir)/../../../../../../..)
