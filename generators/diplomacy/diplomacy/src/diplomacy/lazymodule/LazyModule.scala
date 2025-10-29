// See LICENSE.SiFive for license details.

package org.chipsalliance.diplomacy.lazymodule

import chisel3.experimental.{SourceInfo, UnlocatableSourceInfo}
import org.chipsalliance.cde.config.Parameters

import org.chipsalliance.diplomacy.{sourceLine, ValName}
import org.chipsalliance.diplomacy.nodes.{BaseNode, Dangle, RenderedEdge}

import scala.collection.immutable.SortedMap

/** While the [[freechips.rocketchip.diplomacy]] package allows fairly abstract parameter negotiation while constructing
  * a DAG, [[LazyModule]] builds on top of the DAG annotated with the negotiated parameters and leverage's Scala's lazy
  * evaluation property to split Chisel module generation into two phases:
  *
  *   - Phase 1 (diplomatic) states parameters, hierarchy, and connections:
  *     - [[LazyModule]] and [[BaseNode]] instantiation.
  *     - [[BaseNode]] binding.
  *   - Phase 2 (lazy) generates [[chisel3]] Modules:
  *     - Parameters are negotiated across [[BaseNode]]s.
  *     - Concrete [[Bundle]]s are created along [[BaseNode]]s and connected
  *     - [[AutoBundle]] are automatically connected along [[Edges]], punching IO as necessary though module hierarchy
  *     - [[LazyModuleImpLike]] generates [[chisel3.Module]]s.
  */
abstract class LazyModule(
)(
  implicit val p: Parameters) {

  /** Contains sub-[[LazyModule]]s; can be accessed by [[getChildren]]. */
  protected[diplomacy] var children: List[LazyModule] = List[LazyModule]()

  /** Contains the [[BaseNode]]s instantiated within this instance. */
  protected[diplomacy] var nodes: List[BaseNode] = List[BaseNode]()

  /** Stores [[SourceInfo]] of this instance.
    *
    * The companion object factory method will set this to the correct value.
    */
  protected[diplomacy] var info: SourceInfo = UnlocatableSourceInfo

  /** Parent of this LazyModule. If this instance is at the top of the hierarchy, this will be [[None]]. */
  protected[diplomacy] val parent: Option[LazyModule] = LazyModule.scope

  /** If set, the LazyModule this LazyModule will be a clone of Note that children of a cloned module will also have
    * this set
    */
  protected[diplomacy] var cloneProto: Option[LazyModule] = None

  /** Code snippets from [[InModuleBody]] injection. */
  protected[diplomacy] var inModuleBody: List[() => Unit] = List[() => Unit]()

  /** Sequence of ancestor LazyModules, starting with [[parent]]. */
  def parents: Seq[LazyModule] = parent match {
    case None    => Nil
    case Some(x) => x +: x.parents
  }

  // Push this instance onto the [[LazyModule.scope]] stack.
  LazyModule.scope = Some(this)
  parent.foreach(p => p.children = this :: p.children)

  /** Accumulates Some(names), taking the final one. `None`s are ignored. */
  private var suggestedNameVar: Option[String] = None

  /** Suggests instance name for [[LazyModuleImpLike]] module. */
  def suggestName(x: String): this.type = suggestName(Some(x))

  def suggestName(x: Option[String]): this.type = {
    x.foreach { n => suggestedNameVar = Some(n) }
    this
  }

  /** Finds the name of the first non-anonymous Scala class while walking up the class hierarchy. */
  private def findClassName(c: Class[_]): String = {
    val n = c.getName.split('.').last
    if (n.contains('$')) findClassName(c.getSuperclass)
    else n
  }

  /** Scala class name of this instance. */
  lazy val className: String = findClassName(getClass)

  /** Suggested instance name. Defaults to [[className]]. */
  lazy val suggestedName: String = suggestedNameVar.getOrElse(className)

  /** Suggested module name. Defaults to [[className]]. */
  lazy val desiredName: String = className // + hashcode?

  /** Return instance name. */
  def name: String = suggestedName // className + suggestedName ++ hashcode ?
  /** Return source line that defines this instance. */
  def line: String = sourceLine(info)

  // Accessing these names can only be done after circuit elaboration!
  /** Module name in verilog, used in GraphML. For cloned lazyModules, this is the name of the prototype
    */
  lazy val moduleName: String = cloneProto.map(_.module.name).getOrElse(module.name)

  /** Hierarchical path of this instance, used in GraphML. For cloned modules, construct this manually (since
    * this.module should not be evaluated)
    */
  lazy val pathName: String = cloneProto
    .map(p => s"${parent.get.pathName}.${p.instanceName}")
    .getOrElse(module.pathName)

  /** Instance name in verilog. Should only be accessed after circuit elaboration. */
  lazy val instanceName: String = pathName.split('.').last

  /** [[chisel3]] hardware implementation of this [[LazyModule]].
    *
    * Subclasses should define this function as `lazy val`s for lazy evaluation. Generally, the evaluation of this marks
    * the beginning of phase 2.
    */
  def module: LazyModuleImpLike

  /** Recursively traverse all child LazyModules and Nodes of this LazyModule to construct the set of empty [[Dangle]]'s
    * that are this module's top-level IO This is effectively doing the same thing as [[LazyModuleImp.instantiate]], but
    * without constructing any [[Module]]'s
    */
  protected[diplomacy] def cloneDangles(): List[Dangle] = {
    children.foreach(c => require(c.cloneProto.isDefined, s"${c.info}, ${c.parent.get.info}"))
    val childDangles = children.reverse.flatMap { c => c.cloneDangles() }
    val nodeDangles  = nodes.reverse.flatMap(n => n.cloneDangles())
    val allDangles   = nodeDangles ++ childDangles
    val pairing      = SortedMap(allDangles.groupBy(_.source).toSeq: _*)
    val done         = Set() ++ pairing.values.filter(_.size == 2).map {
      case Seq(a, b) =>
        require(a.flipped != b.flipped)
        a.source
      case _         => None
    }
    val forward      = allDangles.filter(d => !done(d.source))
    val dangles      = forward.map { d =>
      d.copy(name = suggestedName + "_" + d.name)
    }
    dangles
  }

  /** Whether to omit generating the GraphML for this [[LazyModule]].
    *
    * Recursively checks whether all [[BaseNode]]s and children [[LazyModule]]s should omit GraphML generation.
    */
  def omitGraphML: Boolean = nodes.forall(_.omitGraphML) && children.forall(_.omitGraphML)

  /** Whether this [[LazyModule]]'s module should be marked for in-lining by FIRRTL.
    *
    * The default heuristic is to inline any parents whose children have been inlined and whose nodes all produce
    * identity circuits.
    */
  def shouldBeInlined: Boolean = nodes.forall(_.circuitIdentity) && children.forall(_.shouldBeInlined)

  /** GraphML representation for this instance.
    *
    * This is a representation of the Nodes, Edges, LazyModule hierarchy, and any other information that is added in by
    * implementations. It can be converted to an image with various third-party tools.
    */
  lazy val graphML: String = parent.map(_.graphML).getOrElse {
    val buf = new StringBuilder
    buf ++= "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
    buf ++= "<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\" xmlns:y=\"http://www.yworks.com/xml/graphml\">\n"
    buf ++= "  <key for=\"node\" id=\"n\" yfiles.type=\"nodegraphics\"/>\n"
    buf ++= "  <key for=\"edge\" id=\"e\" yfiles.type=\"edgegraphics\"/>\n"
    buf ++= "  <key for=\"node\" id=\"d\" attr.name=\"Description\" attr.type=\"string\"/>\n"
    buf ++= "  <graph id=\"G\" edgedefault=\"directed\">\n"
    nodesGraphML(buf, "    ")
    edgesGraphML(buf, "    ")
    buf ++= "  </graph>\n"
    buf ++= "</graphml>\n"
    buf.toString
  }

  /** A globally unique [[LazyModule]] index for this instance. */
  private val index = {
    LazyModule.index = LazyModule.index + 1
    LazyModule.index
  }

  /** Generate GraphML fragment for nodes.
    *
    * @param buf
    *   String buffer to write to.
    * @param pad
    *   Padding as prefix for indentation purposes.
    */
  private def nodesGraphML(buf: StringBuilder, pad: String): Unit = {
    buf ++= s"""$pad<node id=\"$index\">\n"""
    buf ++= s"""$pad  <data key=\"n\"><y:ShapeNode><y:NodeLabel modelName=\"sides\" modelPosition=\"w\" rotationAngle=\"270.0\">$instanceName</y:NodeLabel><y:BorderStyle type=\"${if (
        shouldBeInlined
      ) "dotted"
      else "line"}\"/></y:ShapeNode></data>\n"""
    buf ++= s"""$pad  <data key=\"d\">$moduleName ($pathName)</data>\n"""
    buf ++= s"""$pad  <graph id=\"$index::\" edgedefault=\"directed\">\n"""
    nodes.filter(!_.omitGraphML).foreach { n =>
      buf ++= s"""$pad    <node id=\"$index::${n.index}\">\n"""
      buf ++= s"""$pad      <data key=\"n\"><y:ShapeNode><y:Shape type="ellipse"/><y:Fill color="#FFCC00" transparent=\"${n.circuitIdentity}\"/></y:ShapeNode></data>\n"""
      buf ++= s"""$pad      <data key=\"d\">${n.formatNode}, \n${n.nodedebugstring}</data>\n"""
      buf ++= s"""$pad    </node>\n"""
    }
    children.filter(!_.omitGraphML).foreach(_.nodesGraphML(buf, pad + "    "))
    buf ++= s"""$pad  </graph>\n"""
    buf ++= s"""$pad</node>\n"""
  }

  /** Generate GraphML fragment for edges.
    *
    * @param buf
    *   String buffer to write to.
    * @param pad
    *   Padding as prefix for indentation purposes.
    */
  private def edgesGraphML(buf: StringBuilder, pad: String): Unit = {
    nodes.filter(!_.omitGraphML).foreach { n =>
      n.outputs.filter(!_._1.omitGraphML).foreach { case (o, edge) =>
        val RenderedEdge(colour, label, flipped) = edge
        buf ++= pad
        buf ++= "<edge"
        if (flipped) {
          buf ++= s""" target=\"$index::${n.index}\""""
          buf ++= s""" source=\"${o.lazyModule.index}::${o.index}\">"""
        } else {
          buf ++= s""" source=\"$index::${n.index}\""""
          buf ++= s""" target=\"${o.lazyModule.index}::${o.index}\">"""
        }
        buf ++= s"""<data key=\"e\"><y:PolyLineEdge>"""
        if (flipped) {
          buf ++= s"""<y:Arrows source=\"standard\" target=\"none\"/>"""
        } else {
          buf ++= s"""<y:Arrows source=\"none\" target=\"standard\"/>"""
        }
        buf ++= s"""<y:LineStyle color=\"$colour\" type=\"line\" width=\"1.0\"/>"""
        buf ++= s"""<y:EdgeLabel modelName=\"centered\" rotationAngle=\"270.0\">$label</y:EdgeLabel>"""
        buf ++= s"""</y:PolyLineEdge></data></edge>\n"""
      }
    }
    children.filter(!_.omitGraphML).foreach { c => c.edgesGraphML(buf, pad) }
  }

  /** Call function on all of this [[LazyModule]]'s [[children]].
    *
    * @param iterfunc
    *   Function to call on each descendant.
    */
  def childrenIterator(iterfunc: LazyModule => Unit): Unit = {
    iterfunc(this)
    children.foreach(_.childrenIterator(iterfunc))
  }

  /** Call function on all of this [[LazyModule]]'s [[nodes]].
    *
    * @param iterfunc
    *   Function to call on each descendant.
    */
  def nodeIterator(iterfunc: BaseNode => Unit): Unit = {
    nodes.foreach(iterfunc)
    childrenIterator(_.nodes.foreach(iterfunc))
  }

  /** Accessor for [[children]]. */
  def getChildren: List[LazyModule] = children

  /** Accessor for [[nodes]]. */
  def getNodes: List[BaseNode] = nodes

  /** Accessor for [[info]]. */
  def getInfo: SourceInfo = info

  /** Accessor for [[parent]]. */
  def getParent: Option[LazyModule] = parent
}

object LazyModule {

  /** Current [[LazyModule]] scope. The scope is a stack of [[LazyModule]]/[[LazyScope]]s.
    *
    * Each call to [[LazyScope.apply]] or [[LazyModule.apply]] will push that item onto the current scope.
    */
  protected[diplomacy] var scope: Option[LazyModule] = None

  /** Accessor for [[scope]]. */
  def getScope: Option[LazyModule] = scope

  /** Global index of [[LazyModule]]. Note that there is no zeroth module. */
  private var index = 0

  /** Wraps a [[LazyModule]], handling bookkeeping of scopes.
    *
    * This method manages the scope and index of the [[LazyModule]]s. All [[LazyModule]]s must be wrapped exactly once.
    *
    * @param bc
    *   [[LazyModule]] instance to be wrapped.
    * @param valName
    *   [[ValName]] used to name this instance, it can be automatically generated by [[ValName]] macro, or specified
    *   manually.
    * @param sourceInfo
    *   [[SourceInfo]] information about where this [[LazyModule]] is being generated
    */
  def apply[T <: LazyModule](
    bc:               T
  )(
    implicit valName: ValName,
    sourceInfo:       SourceInfo
  ): T = {
    // Make sure the user puts [[LazyModule]] around modules in the correct order.
    require(
      scope.isDefined,
      s"LazyModule() applied to ${bc.name} twice ${sourceLine(sourceInfo)}. Ensure that descendant LazyModules are instantiated with the LazyModule() wrapper and that you did not call LazyModule() twice."
    )
    require(scope.get eq bc, s"LazyModule() applied to ${bc.name} before ${scope.get.name} ${sourceLine(sourceInfo)}")
    // Pop from the [[LazyModule.scope]] stack.
    scope = bc.parent
    bc.info = sourceInfo
    if (bc.suggestedNameVar.isEmpty) bc.suggestName(valName.value)
    bc
  }
}
