// See LICENSE for license details
package chipyard.upf

import java.io.{FileWriter}
import java.nio.file.{Paths, Files}
import scala.collection.mutable.{ListBuffer}
import scalax.collection.mutable.{Graph}
import scalax.collection.GraphPredef._, scalax.collection.GraphEdge._

import freechips.rocketchip.diplomacy.{LazyModule}

case class PowerDomain (val name: String, val modules: ListBuffer[LazyModule],
                        val isTop: Boolean, val isGated: Boolean,
                        val highVoltage: Double, val lowVoltage: Double) {
    val mainVoltage = isGated match {
        case true => highVoltage // gated nets should have access to high voltage rail (since they are being gated to optimize power)
        case false => lowVoltage // currently assuming non-gated nets are on low voltage rail
    }
}

object UPFGenerator {

    def generateUPF(pd: PowerDomain, g: Graph[PowerDomain, DiEdge]): Unit = {
        val node = g.get(pd)
        val children = node.diSuccessors.map(x => x.toOuter).toList
        val pdList = g.nodes.map(x => x.toOuter).toList
        val filePath = UPFFunc.UPFPath
        val fileName = s"${pd.name}.upf"
        writeFile(filePath, fileName, createMessage(pd, children, pdList))
    }

    def createMessage(pd: PowerDomain, children: List[PowerDomain], pdList: List[PowerDomain]): String = {
        var message = ""
        message += loadUPF(pd, children)
        message += createPowerDomains(pd)
        message += createSupplyPorts(pd)
        message += createSupplyNets(pd)
        message += connectSupplies(pd)
        message += setDomainNets(pd)
        message += createPowerSwitches(pd)
        message += createPowerStateTable(pd, getPorts(pd, children))
        message += createLevelShifters(pd, pdList)
        return message
    }

    def writeFile(filePath: String, fileName: String, message: String): Unit = {
        if (!Files.exists(Paths.get(filePath))) {
            Files.createDirectories(Paths.get(filePath))
        }
        val fw = new FileWriter(s"${filePath}/${fileName}", false)
        fw.write(message)
        fw.close()
    }

    def getPorts(pd: PowerDomain, children: List[PowerDomain]): ListBuffer[String] = {
        var portsList = ListBuffer[String]()
        portsList += "VDDH"
        portsList += "VDDL"
        if (pd.isGated) {
            portsList += s"VDD_${pd.name}"
        }
        for (child <- children) {
            if (child.isGated) {
                portsList += s"VDD_${child.name}"
            }
        }
        return portsList
    }

    def loadUPF(pd: PowerDomain, children: List[PowerDomain]): String = {
        var message = "##### Set Scope and Load UPF #####\n"
        var subMessage = s"set_scope /${pd.modules(0).module.name}\n" //
        children.foreach{
            child => {
                subMessage += s"load_upf ${child.name}.upf -scope ${child.modules(0).module.name}\n"
            }
        }
        message += subMessage
        message += "\n"
        return message
    }

    def createPowerDomains(pd: PowerDomain): String = {
        var message = "##### Create Power Domains #####\n"
        var subMessage = ""
        pd.isTop match {
            case true => subMessage += s"create_power_domain ${pd.name} -include_scope\n"
            case false => {
                subMessage += s"create_power_domain ${pd.name} -elements { "
                for (module <- pd.modules) {
                    subMessage += s"${module.module.name} "
                }
                subMessage += "}\n"
            }
        }
        message += subMessage
        message += "\n"
        return message
    }

    def createSupplyPorts(pd: PowerDomain): String = {
        if (!pd.isTop) {
            return ""
        }
        var message = "##### Create Supply Ports #####\n"
        var subMessage = pd.isTop match {
            case true => {
                s"create_supply_port VDDH -direction in -domain ${pd.name}\n" +
                s"create_supply_port VDDL -direction in -domain ${pd.name}\n" +
                s"create_supply_port VSS -direction in -domain ${pd.name}\n"
            }
            case false => ""
        }
        message += subMessage
        message += "\n"
        return message
    }

    def createSupplyNets(pd: PowerDomain): String = {
        var message = "##### Create Supply Nets #####\n"
        var subMessage = pd.isTop match {
            case true => {
                s"create_supply_net VDDH -domain ${pd.name}\n" +
                s"create_supply_net VDDL -domain ${pd.name}\n" +
                s"create_supply_net VSS -domain ${pd.name}\n"
            }
            case false => {
                s"create_supply_net VDDH -domain ${pd.name} -reuse\n" +
                s"create_supply_net VDDL -domain ${pd.name} -reuse\n" +
                s"create_supply_net VSS -domain ${pd.name} -reuse\n"
            }
        }
        if (pd.isGated) {
            subMessage += s"create_supply_net VDD_${pd.name} -domain ${pd.name}\n"
        }
        message += subMessage
        message += "\n"
        return message
    }

    def connectSupplies(pd: PowerDomain): String = {
        var message = "##### Connect Supply Nets and Ports #####\n"
        var subMessage = "connect_supply_net VDDH -ports VDDH\n" +
                         "connect_supply_net VDDL -ports VDDL\n" +
                         "connect_supply_net VSS -ports VSS\n"
        message += subMessage
        message += "\n"
        return message
    }

    def setDomainNets(pd: PowerDomain): String = {
        var message = "##### Set Domain Supply Nets #####\n"
        var subMessage = pd.isGated match {
            case true => s"set_domain_supply_net ${pd.name} -primary_power_net VDD_${pd.name} -primary_ground_net VSS\n"
            case false => s"set_domain_supply_net ${pd.name} -primary_power_net VDDL -primary_ground_net VSS\n"
        }
        message += subMessage
        message += "\n"
        return message
    }

    def createPowerSwitches(pd: PowerDomain): String = {
        if (!pd.isGated) {
            return ""
        }
        var message = "##### Power Switches #####\n"
        var subMessage = pd.isGated match {
            case true => s"""create_power_switch sw_${pd.name} -domain ${pd.name} -input_supply_port "psw_VDDH VDDH" """ +
                         s"""-output_supply_port "psw_VDD_${pd.name} VDD_${pd.name}" """ +
                         s"""-control_port "psw_${pd.name}_en ${pd.modules(0).module.name}/${pd.modules(0).module.name}_en" """ +
                         s"""-on_state "psw_${pd.name}_ON psw_VDDH { !psw_${pd.name}_en }"""" + "\n"
            case false => ""
        }
        message += subMessage
        message += "\n"
        return message
    }

    def createPowerStateTable(pd: PowerDomain, portsList: ListBuffer[String]): String = {
        if (!pd.isTop) {
            return ""
        }
        var message = "##### Power State Table #####\n"
        var portStates = ""
        var createPST = "create_pst pst_table -supplies { "

        for (port <- portsList) {
            createPST += s"${port} "
            if (port == "VDDH") {
                portStates += s"add_port_state ${port} -state { HighVoltage ${pd.highVoltage} }\n"
            } else if (port == "VDDL") {
                portStates += s"add_port_state ${port} -state { LowVoltage ${pd.lowVoltage} }\n"
            } else { // gated
                portStates += s"add_port_state ${port} -state { HighVoltage ${pd.highVoltage } -state { ${port}_OFF off }\n"
            }
        }
        portStates += "\n"
        createPST += "}\n\n"

        var pstStates = ""
        for (state <- UPFInputs.states.keys) {
            val stateVal = getStateVal(pd, state)
            pstStates += s"add_pst_state ${state} -pst pst_table -state { "
            for (port <- portsList) {
                if (port == "VDDH") {
                    pstStates += s"HighVoltage "
                } else if (port == "VDDL") {
                    pstStates += s"LowVoltage "
                } else { // gated
                    stateVal match {
                        case 0 => pstStates += s"${port}_OFF "
                        case 1 => pstStates += s"HighVoltage "
                    }
                }
            }
            pstStates += "}\n"
        }
        message += portStates
        message += createPST
        message += pstStates
        message += "\n"
        return message
    }

    def getStateVal(pd: PowerDomain, state: String): Int = {
        val stateVals = UPFInputs.states(state).split(",").map(_.trim.toInt)
        val index = UPFInputs.domains.indexOf(pd.name)
        return stateVals(index)
    }

    // current strategy: for each power domain, create level shifters for outputs going to all other pds
    // not creating level shifters for inputs since every pd will already shift its outputs
    // creating level shifters going to every other pd since not sure how to check if there is communication or not between any 2
    def createLevelShifters(pd: PowerDomain, pdList: List[PowerDomain]): String = {
        var message = "##### Level Shifters #####\n"
        for (pd2 <- pdList) {
            if (pd != pd2) {
                val voltage1 = pd.mainVoltage
                val voltage2 = pd2.mainVoltage
                var subMessage = voltage1 match {
                    case x if x < voltage2 => {
                        s"set_level_shifter LtoH_${pd.name}_to_${pd2.name} " +
                        s"-domain ${pd.name} " +
                        "-applies_to outputs " +
                        "rule low_to_high " +
                        "-location self\n"
                    }
                    case y if y > voltage2 => {
                        s"set_level_shifter HtoL_${pd.name}_to_${pd2.name} " +
                        s"-domain ${pd.name} " +
                        "-applies_to outputs " +
                        "rule high_to_low " +
                        "-location self\n"
                    }
                    case _ => ""
                }
                message += subMessage
            }
        }
        message += "\n"
        return message
    }

}
