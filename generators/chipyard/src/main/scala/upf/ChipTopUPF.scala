// See LICENSE for license details
package chipyard.upf

import chipyard.{TestHarness, ChipTopLazyRawModuleImp, DigitalTop}
import freechips.rocketchip.diplomacy.LazyModule
import chisel.{UPFAspect, UPFFunc, ChiselUPFElement}

import scala.collection.mutable.ListBuffer

object ChipTopUPF {
  def default: UPFFunc.UPFFunction = {
    case top: ChipTopLazyRawModuleImp => {      
      val modulesList = createModulesList(top)
      var (g, pdList) = createPowerDomains(modulesList)
      g = connectPDHierarchy(g, pdList)
      g.bfsVisitor(g.rootObj, UPFGenerator.generateUPF)
    }
  }

  def createModulesList(top: ChipTopLazyRawModuleImp): ListBuffer[LazyModule] = {
    var modulesList = ListBuffer[LazyModule]()
    modulesList.append(top.outer.lazySystem)
    val tiles = top.outer.lazySystem match {
      case t: DigitalTop => t.tiles.map(x => x)
      case _ => throw new Exception("Unsupported BuildSystem type")
    }
    for (tile <- tiles) {
      modulesList.append(tile)
    }
    modulesList ++= top.outer.lazySystem.getChildren
    return modulesList
  }

  def createPowerDomains(modulesList: ListBuffer[LazyModule]): (PowerGraph, ListBuffer[PowerDomain]) = {
    var pdList = ListBuffer[PowerDomain]()
    var g = new PowerGraph()
    for (pdInput <- UPFInputs.upfInfo) {
      var pdModules = ListBuffer[LazyModule]()
      for (moduleName <- pdInput.moduleList) {
        val module = modulesList.filter(_.module.name == moduleName)
        if (module.length == 1) { // filter returns a collection
          pdModules.append(module(0))
        } else {
          throw new Exception("PowerDomainInput module list doesn't exist in design.")
        }
      }
      val pd = new PowerDomain(name=pdInput.name, modules=pdModules, 
                                isTop=pdInput.isTop, isGated=pdInput.isGated, 
                                highVoltage=pdInput.highVoltage, lowVoltage=pdInput.lowVoltage)
      pdList.append(pd)
    }
    return (g, pdList)
  }

  def connectPDHierarchy(g: PowerGraph, pdList: ListBuffer[PowerDomain]): PowerGraph = {
    for (pd <- pdList) {
      if (pd.isTop) {
        g.createGraph(pd)
      }
      val pdInput = UPFInputs.upfInfo.filter(_.name == pd.name)(0)
      val childPDs = pdList.filter(x => pdInput.childrenPDs.contains(x.name))
      for (childPD <- childPDs) {
        g.addChild(pd, childPD)
      }
    }
    return g
  }

}

case object ChipTopUPFAspect extends UPFAspect[chipyard.TestHarness](ChipTopUPF.default)