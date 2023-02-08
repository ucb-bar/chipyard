package chipyard.upf

import java.io.FileWriter
import scala.collection.mutable.ListBuffer


object UPFGenerator {

    def generateUPF(node: Node): Unit = {
        val pd = node.nodeObj match {
            case o: PowerDomain => o
            case _ => throw new Exception("Power domain cannot be a non-PowerDomain object.")
        }
        val children = node.getChildren().map{
            node => node.nodeObj match {
                case o: PowerDomain => o
                case _ => throw new Exception("Power domain children cannot be non-PowerDomain objects.")
            }
        }   
        val fpath = s"/scratch/s.sridhar/upf4/${pd.name}.upf"
        writeFile(fpath, loadUPF(pd, children))
        writeFile(fpath, createPowerDomains(pd))
        writeFile(fpath, createSupplyPorts(pd))
        writeFile(fpath, createSupplyNets(pd))
        writeFile(fpath, connectSupplies(pd))
        writeFile(fpath, setDomainNets(pd))
        writeFile(fpath, createPowerSwitches(pd))
        writeFile(fpath, createPowerStateTable(pd, getPorts(pd, children)))
    }

    def getPorts(pd: PowerDomain, children: ListBuffer[PowerDomain]): ListBuffer[String] = {
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

    def writeFile(filePath: String, message: String): Unit = {
        val fw = new FileWriter(filePath, true)
        fw.write(message)
        fw.close()
    }

    def loadUPF(pd: PowerDomain, children: ListBuffer[PowerDomain]): String = {
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
        return message
    }

    def getStateVal(pd: PowerDomain, state: String): Int = {
        val stateVals = UPFInputs.states(state).split(",").map(_.trim.toInt)
        val index = UPFInputs.domains.indexOf(pd.name)
        return stateVals(index)
    }

}