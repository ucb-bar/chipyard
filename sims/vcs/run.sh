#!/bin/bash


DUMMY_TILE_DIR=generated-src/chipyard.TestHarness.DummyTileConfig
TILE_ONLY_DIR=generated-src/chipyard.TileOnlyTestHarness.TileOnlyRocketConfig

# rm -rf generated-src/*-copy

# ./replace-dummy.py  \
# --tile-generated-src    $TILE_ONLY_DIR \
# --tile-module           TileOnlyDigitalTop \
# --tile-mod-hier-json    $TILE_ONLY_DIR/model_module_hierarchy.json \
# --subsys-generated-src  $DUMMY_TILE_DIR \
# --subsys-module         DummyTile \
# --subsys-mod-hier-json  $DUMMY_TILE_DIR/model_module_hierarchy.json


MODEL=TestHarness
CONFIG=DummyTileConfig
LONG_NAME=chipyard.$MODEL.$CONFIG-copy
BINARY_NAME=simv-chipyard-MergedRocketConfig-debug

vcs -full64 \
  -CFLAGS " -O3 -std=c++17 -I/scratch/joonho.whangbo/coding/tile-only-config-chipyard/.conda-env/riscv-tools/include -I/scratch/joonho.whangbo/coding/tile-only-config-chipyard/tools/DRAMSim2 -I/scratch/joonho.whangbo/coding/tile-only-config-chipyard/sims/vcs/generated-src/$LONG_NAME/gen-collateral " \
  -LDFLAGS "-L/scratch/joonho.whangbo/coding/tile-only-config-chipyard/.conda-env/riscv-tools/lib -Wl,-rpath,/scratch/joonho.whangbo/coding/tile-only-config-chipyard/.conda-env/riscv-tools/lib -L/scratch/joonho.whangbo/coding/tile-only-config-chipyard/sims/vcs -L/scratch/joonho.whangbo/coding/tile-only-config-chipyard/tools/DRAMSim2" \
  -lriscv -lfesvr -ldramsim -notice -line +lint=all,noVCDE,noONGS,noUI -error=PCWM-L -error=noZMMCM -timescale=1ns/10ps -quiet -q +rad +vcs+lic+wait \
  +vc+list -f /scratch/joonho.whangbo/coding/tile-only-config-chipyard/sims/vcs/generated-src/$LONG_NAME/sim_files.common.f \
  -sverilog +systemverilogext+.sv+.svi+.svh+.svt -assert svaext +libext+.sv +v2k +verilog2001ext+.v95+.vt+.vp +libext+.v -debug_pp \
  +incdir+/scratch/joonho.whangbo/coding/tile-only-config-chipyard/sims/vcs/generated-src/$LONG_NAME/gen-collateral \
  +define+VCS +define+CLOCK_PERIOD=1.0 +define+RESET_DELAY=777.7 +define+PRINTF_COND=TestDriver.printf_cond +define+STOP_COND=!TestDriver.reset \
  +define+MODEL=$MODEL +define+RANDOMIZE_MEM_INIT +define+RANDOMIZE_REG_INIT +define+RANDOMIZE_GARBAGE_ASSIGN +define+RANDOMIZE_INVALID_ASSIGN +define+FSDB  \
  -o /scratch/joonho.whangbo/coding/tile-only-config-chipyard/sims/vcs/$BINARY_NAME \
  -Mdir=/scratch/joonho.whangbo/coding/tile-only-config-chipyard/sims/vcs/generated-src/$LONG_NAME/$LONG_NAME.debug  \
  +define+DEBUG -debug_access+all -kdb -lca

mkdir -p /scratch/joonho.whangbo/coding/tile-only-config-chipyard/sims/vcs/output/chipyard.$MODEL.$CONFIG

(set -o pipefail &&  /scratch/joonho.whangbo/coding/tile-only-config-chipyard/sims/vcs/$BINARY_NAME +permissive +dramsim +dramsim_ini_dir=/scratch/joonho.whangbo/coding/tile-only-config-chipyard/generators/testchipip/src/main/resources/dramsim2_ini +max-cycles=10000000  +ntb_random_seed_automatic +verbose +fsdbfile=/scratch/joonho.whangbo/coding/tile-only-config-chipyard/sims/vcs/output/chipyard.$MODEL.$CONFIG/hello-world.fsdb +permissive-off ../../../etc/hello-world.riscv </dev/null 2> >(spike-dasm > /scratch/joonho.whangbo/coding/tile-only-config-chipyard/sims/vcs/output/chipyard.$MODEL.$CONFIG/hello-world.out) | tee /scratch/joonho.whangbo/coding/tile-only-config-chipyard/sims/vcs/output/chipyard.$MODEL.$CONFIG/hello-world.log)
