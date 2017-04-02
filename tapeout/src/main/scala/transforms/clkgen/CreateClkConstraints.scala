// See license file for details

package barstools.tapeout.transforms.clkgen

import firrtl.passes.clocklist._
import firrtl.annotations._
import firrtl.ir._
import firrtl.Utils._
import barstools.tapeout.transforms._
import scala.collection.immutable.ListMap

// TODO: Really should be moved out of memlib
import firrtl.passes.memlib.AnalysisUtils._
import firrtl.passes._

// TODO: Wait until Albert merges into firrtl
import firrtl.analyses._

class CreateClkConstraints(
    clkModAnnos: Seq[TargetClkModAnnoF], 
    clkPortAnnos: Seq[TargetClkPortAnnoF],
    targetDir: String) extends Pass {

  // TODO: Are annotations only valid on ports?
  
  def run(c: Circuit): Circuit = {

    val top = c.main
    
    // Remove everything from the circuit, unless it has a clock type
    // This simplifies the circuit drastically so InlineInstances doesn't take forever.
    val onlyClockCircuit = RemoveAllButClocks.run(c)

    val instanceGraph = new InstanceGraph(onlyClockCircuit)

    val clkModNames = clkModAnnos.map(x => x.targetName)
    // ** Module name -> Absolute path of (unique) instance
    val clkMods = clkModNames.map { x =>
      // NoDeDup was run so only 1 instance of each module should exist
      val inst = instanceGraph.findInstancesInHierarchy(x)
      require(inst.length == 1, "Clk modules should have not ben dedup-ed")
      // Return map of module name to absolute path as a string
      // Note: absolute path doesn't contain top module + to work with inlineInstances, 
      // delimit with $
      x -> inst.head.tail.map(y => y.name).mkString("$")
    }.toMap

    val clkPortIds = clkPortAnnos.map { a => a.modId }
    require(clkPortIds.distinct.length == clkPortIds.length, "All clk port IDs must be unique!")

    val allModClkPorts = clkModAnnos.map { x =>
      val modClkPorts = x.getAllClkPorts
      require(modClkPorts.intersect(clkPortIds).length == modClkPorts.length, 
        "Clks given relationships via clk modules must have been annotated as clk ports")
      modClkPorts
    }.flatten.distinct

    val clkPortMap = clkPortIds.zip(clkPortAnnos).toMap 
    val clkModMap = clkModNames.zip(clkModAnnos).toMap

    val (clkSinksTemp, clkSrcsTemp) = clkPortAnnos.partition { 
      case TargetClkPortAnnoF(_, ClkPortAnnotation(tag, _)) if tag.nonEmpty  => true
      case _ => false
    }

    def convertClkPortAnnoToMap(annos: Seq[TargetClkPortAnnoF]): ListMap[String, String] = 
      ListMap(annos.map { x =>
        val target = x.target
        val absPath = {
            if (top == target.module.name) LowerName(target.name)
            else Seq(clkMods(target.module.name), LowerName(target.name)).mkString(".")
          }
        x.modId -> absPath
      }.sortBy(_._1): _*)

    // ** clk port -> absolute path
    val clkSinks = convertClkPortAnnoToMap(clkSinksTemp)
    val clkSrcs = convertClkPortAnnoToMap(clkSrcsTemp)

    clkSrcs foreach { case (id, path) => 
      require(allModClkPorts contains id, "All clock source properties must be defined by their respective modules") }

    // Don't inline clock modules
    val modulesToInline = (c.modules.collect { 
      case Module(_, n, _, _) if n != top && !clkModNames.contains(n) => 
        ModuleName(n, CircuitName(top)) 
    }).toSet

    val inlineTransform = new InlineInstances
    val inlinedCircuit = inlineTransform.run(onlyClockCircuit, modulesToInline, Set(), None).circuit

    val topModule = inlinedCircuit.modules.find(_.name == top).getOrElse(throwInternalError)

    // Build a hashmap of connections to use for getOrigins
    val connects = getConnects(topModule)

    // Clk sinks are either inputs to clock modules or top clk inputs --> separate
    // ** clk port -> absolute path
    val (topClks, clkModSinks) = clkSinks.partition { 
      case (modId, absPath) if modId.split("\\.").head == top => true
      case _ => false
    }

    // Must be 1:1 originally!
    def flipMapping(m: ListMap[String, String]): ListMap[String, String] = 
      m.map { case (a, b) => b -> a }

    val clkSrcsFlip = flipMapping(clkSrcs)
    val topClksFlip = flipMapping(topClks)

    // Find origins of clk mod sinks
    val clkModSinkToSourceMap = clkModSinks.map { case (sinkId, sinkAbsPath) =>
      val sourceAbsPath = getOrigin(connects, sinkAbsPath).serialize
      val sourceId = {
        // sources of sinks are generated clks or top level clk inputs
        if (clkSrcsFlip.contains(sourceAbsPath)) clkSrcsFlip(sourceAbsPath)
        else if (topClksFlip.contains(sourceAbsPath)) topClksFlip(sourceAbsPath)
        else throw new Exception(s"Absolute path $sourceAbsPath of clk source for $sinkId not found!")
      }
      sinkId -> sourceId
    }

    c.modules.foreach {
      case mod: DefModule  =>
        mod.ports.foreach {
          case Port(_, n, dir, tpe) 
              if tpe == ClockType && 
              ((dir == Input && mod.name == top) || (dir == Output && clkModNames.contains(mod.name)))  => 
            clkPortAnnos.find(x => 
              // TODO: Not sufficiently general for output clks? Might have forgotten to label a clk module...
              LowerName(x.target.name) == n && x.target.module.name == mod.name).getOrElse(
              throw new Exception(
                s"All top module input clks/clk module output clocks must be sinks/sources! $n not annotated!"))
          case _ =>
        }
    }

    // Find sinks used to derive clk mod sources
    val clkModSourceToSinkMap: Seq[(String, Seq[String])] = clkModAnnos.map(x => {
      val modName = x.targetName
      x.generatedClks.map(y => Seq(modName, y.id).mkString(".") -> y.sources.map(z => Seq(modName, z).mkString(".")))
    } ).flatten
    
    topClks.foreach {x => println(s"top clk: $x")}
    clkModSinks.foreach { x => println(s"clk sink: $x")}
    clkSrcs.foreach { x => println(s"gen clk: $x")}
    clkModSinkToSourceMap.foreach { x => println(s"sink -> src: $x")}
    clkModSourceToSinkMap.foreach { x => println(s"src -> dependent sinks: $x")}
    c
  }
}