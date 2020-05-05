SBT := java -Xmx16G -jar generators/rocket-chip/sbt-launch.jar ++2.12.10

MACDriver.v:
	cd ../ && $(SBT) "chipyard/runMain power.MACModeling --target-dir power-modeling"

mac: MACDriver.v

.PHONY: mac
