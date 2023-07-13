// See LICENSE for license details
package chipyard.upf

// outputs are dumped in vlsi/generated-src/upf
object UPFInputs {

    /**
     * UPF info
     * each PowerDomainInput represents a desired power domain
     * each input will contain all the necessary info to describe a power domain in UPF, including hierarchy
     */
    val upfInfo = List(
        PowerDomainInput(name="PD_top", isTop=true, moduleList=List("DigitalTop"),
                         parentPD="", childrenPDs=List("PD_RocketTile1", "PD_RocketTile2"),
                         isGated=false, highVoltage=3.9, lowVoltage=3.4),
        PowerDomainInput(name="PD_RocketTile1", isTop=false, moduleList=List("tile_prci_domain"),
                         parentPD="PD_top", childrenPDs=List(),
                         isGated=false, highVoltage=3.9, lowVoltage=3.1),
        PowerDomainInput(name="PD_RocketTile2", isTop=false, moduleList=List("tile_prci_domain_1"),
                         parentPD="PD_top", childrenPDs=List(),
                         isGated=false, highVoltage=3.9, lowVoltage=3.2),
    )


    /**
      * PST info
      * experimental Power State Table input, used to gate power domains based on specified power states
      * place names of all power domains to be gated in the domains list
      * states will map different keywords (arbitrary strings) to a binary on or off (1 or 0) to form a power state
      * order of domains in list corresponds to order of values in each states mapping
      */
    val domains = List("PD_top", "PD_RocketTile1", "PD_RocketTile2")
    val states = Map(
        "ON" -> "1, 1, 1",
        "OFF" -> "0, 0, 0"
    )

}

/**
  * Representation of a power domain used to generate UPF.
  *
  * @param name name of the power domain.
  * @param isTop if the power domain is the top level or not.
  * @param moduleList refers to all the Verilog modules belonging to this power domain. Can be module name, instance name, or full path name.
  * @param parentPD the name of the parent power domain to this one.
  * @param childrenPDs names of all the children power domains to this one.
  * @param isGated if the power domain is gated or not.
  * @param highVoltage voltage value of the high voltage rail (currently, gated nets have access to high voltage since they are optimized to save power).
  * @param lowVoltage voltage value of the low voltage rail (currently, non-gated nets default to the low voltage rail).
  */
case class PowerDomainInput(name: String, isTop: Boolean, moduleList: List[String],
                            parentPD: String, childrenPDs: List[String],
                            isGated: Boolean, highVoltage: Double, lowVoltage: Double)
