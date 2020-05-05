SBT := java -Xmx4G -jar generators/rocket-chip/sbt-launch.jar ++2.12.10
HAMMER_EXEC := hammer-vlsi

mac-rtl: generated-src/8_8_19/MAC.v
generated-src/8_8_19/MAC.v:
	mkdir -p generated-src/8_8_19
	cd ../ && $(SBT) "chipyard/runMain power.MACModeling power-modeling/generated-src/8_8_19 8 8 19"


sim: mac-build/8_8_19/sim-rundir/sim-output-full.json
mac-build/8_8_19/sim-rundir/sim-output-full.json: generated-src/8_8_19/MAC.v
	$(HAMMER_EXEC) -e inst-env.yml -p mac.yml --obj_dir mac-build/8_8_19/ sim

.PHONY: mac-rtl sim
