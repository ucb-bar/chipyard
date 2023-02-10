package chipyard.upf


object UPFInputs {

    val upfInfo = List(
        PowerDomainInput(name="PD_top", isTop=true, moduleList=List("DigitalTop"),
                         parentPD="", childrenPDs=List("PD_RocketTile1", "PD_RocketTile2"),
                         isGated=false, highVoltage=3.9, lowVoltage=3.4),
        PowerDomainInput(name="PD_RocketTile1", isTop=false, moduleList=List("RocketTile"),
                         parentPD="PD_top", childrenPDs=List(),
                         isGated=false, highVoltage=3.9, lowVoltage=3.1),
        PowerDomainInput(name="PD_RocketTile2", isTop=false, moduleList=List("RocketTile_1"),
                         parentPD="PD_top", childrenPDs=List(),
                         isGated=false, highVoltage=3.9, lowVoltage=3.2),
    )

    // PST info
    val domains = List("PD_top", "PD_RocketTile1", "PD_RocketTile2")
    val states = Map(
        "ON" -> "1, 1, 1",
        "OFF" -> "0, 0, 0"
    )

}

case class PowerDomainInput(name: String, isTop: Boolean, moduleList: List[String], 
                            parentPD: String, childrenPDs: List[String], 
                            isGated: Boolean, highVoltage: Double, lowVoltage: Double)
