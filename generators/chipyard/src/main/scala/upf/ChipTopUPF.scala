// See LICENSE for license details
package chipyard.upf

import scala.collection.mutable.{ListBuffer}
import scalax.collection.mutable.{Graph}
import scalax.collection.GraphPredef._, scalax.collection.GraphEdge._

import chipyard.harness.{TestHarness}
import freechips.rocketchip.diplomacy.{LazyModule}

object ChipTopUPF {

  def default: UPFFunc.UPFFunction = {
    case top: LazyModule => {
      val modulesList = getLazyModules(top)
      val pdList = createPowerDomains(modulesList)
      val g = connectPDHierarchy(pdList)
      traverseGraph(g, UPFGenerator.generateUPF)
    }
  }

  def getLazyModules(top: LazyModule): ListBuffer[LazyModule] = {
    var i = 0
    var result = new ListBuffer[LazyModule]()
    result.append(top)
    while (i < result.length) {
      val lazyMod = result(i)
      for (child <- lazyMod.getChildren) {
        result.append(child)
      }
      i += 1
    }
    return result
  }

  def createPowerDomains(modulesList: ListBuffer[LazyModule]): ListBuffer[PowerDomain] = {
    var pdList = ListBuffer[PowerDomain]()
    for (pdInput <- UPFInputs.upfInfo) {
      val pd = new PowerDomain(name=pdInput.name, modules=getPDModules(pdInput, modulesList),
                               isTop=pdInput.isTop, isGated=pdInput.isGated,
                               highVoltage=pdInput.highVoltage, lowVoltage=pdInput.lowVoltage)
      pdList.append(pd)
    }
    return pdList
  }

  def getPDModules(pdInput: PowerDomainInput, modulesList: ListBuffer[LazyModule]): ListBuffer[LazyModule] = {
    var pdModules = ListBuffer[LazyModule]()
    for (moduleName <- pdInput.moduleList) {
      var module = modulesList.filter(_.module.name == moduleName)
      if (module.length == 1) { // filter returns a collection
        pdModules.append(module(0))
      } else {
        module = modulesList.filter(_.module.instanceName == moduleName)
        if (module.length == 1) {
          pdModules.append(module(0))
        } else {
          module = modulesList.filter(_.module.pathName == moduleName)
          if (module.length == 1) {
            pdModules.append(module(0))
          } else {
            throw new Exception(s"PowerDomainInput module list doesn't exist in design.")
          }
        }
      }
    }
    return pdModules
  }

  def connectPDHierarchy(pdList: ListBuffer[PowerDomain]): Graph[PowerDomain, DiEdge] = {
    var g = Graph[PowerDomain, DiEdge]()
    for (pd <- pdList) {
      val pdInput = UPFInputs.upfInfo.filter(_.name == pd.name)(0)
      val childPDs = pdList.filter(x => pdInput.childrenPDs.contains(x.name))
      for (childPD <- childPDs) {
        g += (pd ~> childPD) // directed edge from pd to childPD
      }
    }
    return g
  }

  def traverseGraph(g: Graph[PowerDomain, DiEdge], action: (PowerDomain, Graph[PowerDomain, DiEdge]) => Unit): Unit = {
    for (node <- g.nodes.filter(_.diPredecessors.isEmpty)) { // all nodes without parents
      g.outerNodeTraverser(node).foreach(pd => action(pd, g))
    }
  }

}

case object ChipTopUPFAspect extends UPFAspect[chipyard.harness.TestHarness](ChipTopUPF.default)
