SBT		?= sbt
#SBT_FLAGS	?= -Dsbt.log.noformat=true

.PHONY: checkstyle


checkstyle:
	$(SBT) $(SBT_FLAGS) scalastyle test:scalastyle
