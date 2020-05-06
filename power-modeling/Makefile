# TODO: I'm explicitly using /usr/bin/java on eda machines to avoid some pathing issue
SBT := /usr/bin/java -Xmx4G -jar generators/rocket-chip/sbt-launch.jar ++2.12.10
HAMMER_EXEC := hammer-vlsi
OBJ_DIR := mac-build/8_8_19

## RTL-level
mac-rtl: generated-src/8_8_19/MAC.v
generated-src/8_8_19/MAC.v:
	mkdir -p generated-src/8_8_19
	cd ../ && $(SBT) "chipyard/runMain power.MACModeling power-modeling/generated-src/8_8_19 8 8 19"

sim: $(OBJ_DIR)/sim-rundir/sim-output-full.json
$(OBJ_DIR)/sim-rundir/sim-output-full.json: generated-src/8_8_19/MAC.v MACTB.v
	$(HAMMER_EXEC) -e inst-env.yml -p mac.yml -p mac_rtl_sim.yml --obj_dir $(OBJ_DIR) sim

## Syn-level
syn: $(OBJ_DIR)/syn-rundir/syn-output-full.json
$(OBJ_DIR)/syn-rundir/syn-output-full.json: generated-src/8_8_19/MAC.v
	$(HAMMER_EXEC) -e inst-env.yml -p mac.yml --obj_dir $(OBJ_DIR) syn

sim-syn: $(OBJ_DIR)/sim-syn-rundir/sim-output-full.json
$(OBJ_DIR)/sim-syn-rundir/sim-output-full.json: $(OBJ_DIR)/syn-rundir/syn-output-full.json
	$(HAMMER_EXEC) -e inst-env.yml -p mac.yml -p $(OBJ_DIR)/syn-rundir/syn-output-full.json -o $(OBJ_DIR)/sim-syn-input.json --obj_dir $(OBJ_DIR) syn-to-sim
	$(HAMMER_EXEC) -e inst-env.yml -p mac.yml -p mac_gl_sim.yml -p $(OBJ_DIR)/sim-syn-input.json --sim_rundir $(OBJ_DIR)/sim-syn-rundir --obj_dir $(OBJ_DIR) sim

redo-sim-syn:
	$(HAMMER_EXEC) -e inst-env.yml -p mac.yml -p $(OBJ_DIR)/syn-rundir/syn-output-full.json -o $(OBJ_DIR)/sim-syn-input.json --obj_dir $(OBJ_DIR) syn-to-sim
	$(HAMMER_EXEC) -e inst-env.yml -p mac.yml -p mac_gl_sim.yml -p $(OBJ_DIR)/sim-syn-input.json --sim_rundir $(OBJ_DIR)/sim-syn-rundir --obj_dir $(OBJ_DIR) sim

## Par-level
par: $(OBJ_DIR)/par-rundir/par-output-full.json
$(OBJ_DIR)/par-rundir/par-output-full.json: $(OBJ_DIR)/syn-rundir/syn-output-full.json
	$(HAMMER_EXEC) -e inst-env.yml -p $(OBJ_DIR)/syn-rundir/syn-output-full.json -o $(OBJ_DIR)/par-input.json --obj_dir $(OBJ_DIR) syn-to-par
	$(HAMMER_EXEC) -e inst-env.yml -p $(OBJ_DIR)/par-input.json --obj_dir $(OBJ_DIR) par

sim-par: $(OBJ_DIR)/sim-par-rundir/sim-output-full.json
$(OBJ_DIR)/sim-par-rundir/sim-output-full.json: $(OBJ_DIR)/par-rundir/par-output-full.json
	$(HAMMER_EXEC) -e inst-env.yml -p $(OBJ_DIR)/par-rundir/par-output-full.json -o $(OBJ_DIR)/sim-par-input.json --obj_dir $(OBJ_DIR) par-to-sim
	$(HAMMER_EXEC) -e inst-env.yml -p mac.yml -p mac_gl_sim.yml -p $(OBJ_DIR)/sim-par-input.json --sim_rundir $(OBJ_DIR)/sim-par-rundir --obj_dir $(OBJ_DIR) sim

redo-sim-par:
	$(HAMMER_EXEC) -e inst-env.yml -p $(OBJ_DIR)/par-rundir/par-output-full.json -o $(OBJ_DIR)/sim-par-input.json --obj_dir $(OBJ_DIR) par-to-sim
	$(HAMMER_EXEC) -e inst-env.yml -p mac.yml -p mac_gl_sim.yml -p $(OBJ_DIR)/sim-par-input.json --sim_rundir $(OBJ_DIR)/sim-par-rundir --obj_dir $(OBJ_DIR) sim

## Power
power: $(OBJ_DIR)/power-rundir/power-output-full.json
$(OBJ_DIR)/power-rundir/power-output-full.json: $(OBJ_DIR)/sim-par-rundir/sim-output-full.json $(OBJ_DIR)/par-rundir/par-output-full.json
	  /share/instsww/synopsys-new/vcs/P-2019.06/bin/vpd2vcd $(OBJ_DIR)/sim-par-rundir/vcdplus.vpd $(OBJ_DIR)/sim-par-rundir/vcd.vcd
	$(HAMMER_EXEC) -e inst-env.yml -p $(OBJ_DIR)/sim-par-rundir/sim-output-full.json -p mac_power.yml -o $(OBJ_DIR)/power-sim-input.json --obj_dir $(OBJ_DIR) sim-to-power
	$(HAMMER_EXEC) -e inst-env.yml -p $(OBJ_DIR)/par-rundir/par-output-full.json -p mac_power.yml -o $(OBJ_DIR)/power-par-input.json --obj_dir $(OBJ_DIR) par-to-power
	$(HAMMER_EXEC) -e inst-env.yml -p $(OBJ_DIR)/power-sim-input.json -p $(OBJ_DIR)/power-par-input.json -p mac_power.yml --obj_dir $(OBJ_DIR) power

clean-src:
	rm -rf generated-src

clean-build:
	rm -rf mac-build

.PHONY: mac-rtl sim syn sim-syn redo-sim-syn par sim-par power
