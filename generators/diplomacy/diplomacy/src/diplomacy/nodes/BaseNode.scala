package org.chipsalliance.diplomacy.nodes

import chisel3.Data
import chisel3.experimental.SourceInfo
import org.chipsalliance.cde.config.Parameters

import org.chipsalliance.diplomacy.ValName
import org.chipsalliance.diplomacy.lazymodule.LazyModule
import org.chipsalliance.diplomacy.sourceLine

import scala.collection.immutable
import scala.collection.mutable.ListBuffer

/** [[BaseNode]] is the abstract base class of the type hierarchy of diplomacy node classes.
  *
  * @param valName
  *   [[ValName]] of this node, used by naming inference.
  */
abstract class BaseNode(
  implicit val valName: ValName) {

  /** All subclasses of [[BaseNode]]s are expected to be instantiated only within [[LazyModule]]s.
    *
    * Sometimes one wants to view the entire diplomacy graph in a way where you do not care about the specific types of
    * the edges. [[BaseNode]]s are type-erased and provide this view.
    *
    * @return
    *   The [[LazyModule]] which contains this Node.
    */
  val scope: Option[LazyModule] = LazyModule.scope

  /** @return The index for this node in the containing [[LazyModule]]/[[LazyScope]]'s list of [[BaseNode]]s */
  val index: Int = scope.map(_.nodes.size).getOrElse(0)

  /** @return The [[LazyModule]] which contains this [[BaseNode]] */
  def lazyModule: LazyModule = scope.get

  // Prepend this node to the current [[LazyModule]]'s list of nodes
  scope.foreach { lm => lm.nodes = this :: lm.nodes }

  /** @return The serial number for this node in the global list of [[BaseNode]]s. */
  val serial: Int = BaseNode.serial

  BaseNode.serial = BaseNode.serial + 1

  /** Instantiate this node.
    *
    * This happens after all nodes connections have been made and we are ready to perform parameter negotiation. This
    * also determines which connections need to leave this node's LazyScope and cross hierarchical boundaries. That
    * information is captured in [[Dangle]]s which are returned from this function.
    *
    * @return
    *   A sequence of [[Dangle]]s from this node that leave this [[BaseNode]]'s [[LazyScope]].
    */
  protected[diplomacy] def instantiate(): Seq[Dangle]

  /** Determine the [[Dangle]]'s for connections without instantiating the node, or any child nodes
    *
    * @return
    *   A sequence of [[Dangle]]s from this node that leave this [[BaseNode]]'s [[LazyScope]].
    */
  protected[diplomacy] def cloneDangles(): Seq[Dangle]

  /** @return name of this node. */
  def name: String = scope.map(_.name).getOrElse("TOP") + "." + valName.value

  /** Determines whether or not this node will be excluded from the graph visualization.
    *
    * By default, if this node has neither inputs nor outputs it will be excluded.
    */
  def omitGraphML: Boolean = outputs.isEmpty && inputs.isEmpty

  /** Debug string of this node, used in [[LazyModule.graphML]]. */
  lazy val nodedebugstring: String = ""

  /** Mark whether this node represents a circuit "identity" that outputs its inputs unchanged.
    *
    * This information may be used to elide monitors or inline the parent module.
    */
  def circuitIdentity: Boolean = false

  /** @return A sequence of [[LazyModule]] up to and including Top. */
  def parents: Seq[LazyModule] = scope.map(lm => lm +: lm.parents).getOrElse(Nil)

  /** @return The context string for debug. */
  def context: String = {
    s"""$description $name node:
       |parents: ${parents.map(_.name).mkString("/")}
       |locator: ${scope.map(_.line).getOrElse("<undef>")}
       |""".stripMargin
  }

  /** Determines the name to be used in elements of auto-punched bundles.
    *
    * It takes the name of the node as determined from [[valName]], converts camel case into snake case, and strips
    * "Node" or "NodeOpt" suffixes.
    */
  def wirePrefix: String = {
    val camelCase = "([a-z])([A-Z])".r
    val decamel   = camelCase.replaceAllIn(valName.value, _ match { case camelCase(l, h) => l + "_" + h })
    val name      = decamel.toLowerCase.stripSuffix("_opt").stripSuffix("node").stripSuffix("_")
    if (name.isEmpty) ""
    else name + "_"
  }

  /** @return
    *   [[BaseNode]] description, which should be defined by subclasses and is generally expected to be a constant.
    */
  def description: String

  /** @return
    *   [[BaseNode]] instance description, which can be overridden with more detailed information about each node.
    */
  def formatNode: String = ""

  /** @return Metadata to visualize inward edges into this node. */
  def inputs: Seq[(BaseNode, RenderedEdge)]

  /** @return Metadata to visualize outward edges from this node. */
  def outputs: Seq[(BaseNode, RenderedEdge)]

  /** @return
    *   Whether this node can handle [[BIND_FLEX]] type connections on either side.
    *
    * For example, a node `b` will have [[flexibleArityDirection]] be `true` if both are legal: `a :*=* b :*= c`, which
    * resolves to `a :*= b :*= c` or `a :=* b :*=* c`, which resolves to `a :=* b :=* c`
    *
    * If this is `false`, the node can only support `:*=*` if it connects to a node with `flexibleArityDirection = true`
    */
  protected[diplomacy] def flexibleArityDirection: Boolean = false

  /** @return
    *   The sink cardinality.
    *
    * How many times is this node used as a sink.
    */
  protected[diplomacy] val sinkCard: Int

  /** @return
    *   The source cardinality.
    *
    * How many times is this node used as a source.
    */
  protected[diplomacy] val sourceCard: Int

  /** @return
    *   The "flex" cardinality.
    *
    * How many times is this node used in a way that could be either source or sink, depending on final directional
    * determination.
    */
  protected[diplomacy] val flexes: Seq[BaseNode]

  /** Resolves the flex to be either source or sink.
    *
    * @return
    *   A value >= 0 if it is sink cardinality, a negative value for source cardinality. The magnitude of the value does
    *   not matter.
    */
  protected[diplomacy] val flexOffset: Int
}

/** Trait that enables iterating over a [[BaseNode]]'s edges to produce a formatted string representation.
  *
  * In practice this is generally GraphML metadata.
  */
trait FormatNode[I <: FormatEdge, O <: FormatEdge] extends BaseNode {
  def edges: Edges[I, O]

  /** Format the edges of the [[BaseNode]] for emission (generally in GraphML). */
  override def formatNode =
    if (circuitIdentity) ""
    else {
      edges.out.map(currEdge => "On Output Edge:\n\n" + currEdge.formatEdge).mkString +
        "\n---------------------------------------------\n\n" +
        edges.in.map(currEdge => "On Input Edge:\n\n" + currEdge.formatEdge).mkString
    }
}

/** A Node that defines inward behavior, meaning that it can have edges coming into it and be used on the left side of
  * binding expressions.
  */
trait InwardNode[DI, UI, BI <: Data] extends BaseNode {

  /** accumulates input connections. */
  private val accPI = ListBuffer[(Int, OutwardNode[DI, UI, BI], NodeBinding, Parameters, SourceInfo)]()

  /** Initially `false`, set to `true` once [[iBindings]] has been evaluated. */
  private var iRealized = false

  /** @return debug information of [[iBindings]]. */
  def iBindingInfo: String =
    s"""${iBindings.size} inward nodes bound: [${iBindings.map(n => s"${n._3}-${n._2.name}").mkString(",")}]"""

  /** The accumulated number of input connections. */
  protected[diplomacy] def iPushed: Int = accPI.size

  /** Accumulate an input connection.
    *
    * Can only be called before [[iBindings]] is accessed.
    *
    * @param index
    *   index of this [[InwardNode]] in that [[OutwardNode]].
    * @param node
    *   the [[OutwardNode]] to bind to this [[InwardNode]].
    * @param binding
    *   [[NodeBinding]] type.
    */
  protected[diplomacy] def iPush(
    index:      Int,
    node:       OutwardNode[DI, UI, BI],
    binding:    NodeBinding
  )(
    implicit p: Parameters,
    sourceInfo: SourceInfo
  ): Unit = {
    val info = sourceLine(sourceInfo, " at ", "")
    require(
      !iRealized,
      s"""Diplomacy has detected a problem in your code:
         |The following node was incorrectly connected as a sink to ${node.name} after its .module was evaluated at $info.
         |$context
         |$iBindingInfo
         |""".stripMargin
    )
    accPI += ((index, node, binding, p, sourceInfo))
  }

  /** Ends the binding accumulation stage and returns all the input bindings to this node.
    *
    * Evaluating this lazy val will mark the inwards bindings as frozen, preventing subsequent bindings from being
    * created via [[iPush]].
    *
    * The bindings are each a tuple of:
    *   - numeric index of this binding in the other end of [[OutwardNode]].
    *   - [[OutwardNode]] on the other end of this binding.
    *   - [[NodeBinding]] describing the type of binding.
    *   - A view of [[Parameters]] where the binding occurred.
    *   - [[SourceInfo]] for source-level error reporting.
    */
  protected[diplomacy] lazy val iBindings
    : immutable.Seq[(Int, OutwardNode[DI, UI, BI], NodeBinding, Parameters, SourceInfo)] = {
    iRealized = true; accPI.result()
  }

  /** resolved [[BIND_STAR]] binding of inward nodes: how many connections the star represents. */
  protected[diplomacy] val iStar: Int

  /** A mapping to convert Node binding index to port range.
    *
    * @return
    *   a sequence of tuple of mapping, the item in each a tuple of:
    *   - index: the index of connected [[OutwardNode]]
    *   - element: port range of connected [[OutwardNode]]
    */
  protected[diplomacy] val iPortMapping: Seq[(Int, Int)]

  /** "Forward" an input connection through this node so that the node can be removed from the graph.
    *
    * @return
    *   None if no forwarding is needing.
    */
  protected[diplomacy] def iForward(x: Int): Option[(Int, InwardNode[DI, UI, BI])] = None

  /** Downward-flowing inward parameters.
    *
    * Generated from the nodes connected to the inward side of this node and sent downstream to this node.
    */
  protected[diplomacy] val diParams: Seq[DI]

  /** Upward-flowing inward parameters.
    *
    * Generated by this node and sent upstream to the nodes connected to the inward side of this node.
    */
  protected[diplomacy] val uiParams: Seq[UI]

  /** Create a binding from this node to an [[OutwardNode]].
    *
    * @param h
    *   The [[OutwardNode]] to bind to.
    * @param binding
    *   [[NodeBinding]] the type of binding.
    */
  protected[diplomacy] def bind(
    h:          OutwardNode[DI, UI, BI],
    binding:    NodeBinding
  )(
    implicit p: Parameters,
    sourceInfo: SourceInfo
  ): Unit
}

/** A Node that defines outward behavior, meaning that it can have edges coming out of it. */
trait OutwardNode[DO, UO, BO <: Data] extends BaseNode {

  /** Accumulates output connections. */
  private val accPO = ListBuffer[(Int, InwardNode[DO, UO, BO], NodeBinding, Parameters, SourceInfo)]()

  /** Initially set to `true`, this is set to false once [[oBindings]] is referenced. */
  private var oRealized = false

  /** @return debug information of [[oBindings]]. */
  def oBindingInfo: String =
    s"""${oBindings.size} outward nodes bound: [${oBindings.map(n => s"${n._3}-${n._2.name}").mkString(",")}]"""

  /** The accumulated number of output connections of this node. */
  protected[diplomacy] def oPushed: Int = accPO.size

  /** Accumulate an output connection.
    *
    * Can only be called before [[oBindings]] is accessed.
    *
    * @param index
    *   Index of this [[OutwardNode]] in that [[InwardNode]].
    * @param node
    *   [[InwardNode]] to bind to.
    * @param binding
    *   Binding type.
    */
  protected[diplomacy] def oPush(
    index:      Int,
    node:       InwardNode[DO, UO, BO],
    binding:    NodeBinding
  )(
    implicit p: Parameters,
    sourceInfo: SourceInfo
  ): Unit = {
    val info = sourceLine(sourceInfo, " at ", "")
    require(
      !oRealized,
      s"""Diplomacy has detected a problem in your code:
         |The following node was incorrectly connected as a source to ${node.name} after its .module was evaluated at $info.
         |$context
         |$oBindingInfo
         |""".stripMargin
    )
    accPO += ((index, node, binding, p, sourceInfo))
  }

  /** Ends the binding accumulation stage and returns all the output bindings to this node.
    *
    * Evaluating this lazy val will mark the outward bindings as frozen, preventing subsequent bindings from being
    * created via [[oPush]].
    *
    * The bindings are each a tuple of:
    *   - numeric index of this binding in the other end of [[InwardNode]].
    *   - [[InwardNode]] on the other end of this binding
    *   - [[NodeBinding]] describing the type of binding
    *   - A view of [[Parameters]] where the binding occurred.
    *   - [[SourceInfo]] for source-level error reporting
    */
  protected[diplomacy] lazy val oBindings: Seq[(Int, InwardNode[DO, UO, BO], NodeBinding, Parameters, SourceInfo)] = {
    oRealized = true; accPO.result()
  }

  /** resolved [[BIND_STAR]] binding of outward nodes: how many connections the star represents. */
  protected[diplomacy] val oStar: Int

  /** A mapping to convert Node binding index to port range.
    *
    * @return
    *   a sequence of tuple of mapping, the item in each a tuple of:
    *   - index: the index of connected [[InwardNode]]
    *   - element: port range of connected [[InwardNode]]
    */
  protected[diplomacy] val oPortMapping: Seq[(Int, Int)]

  /** "Forward" an output connection through this node so that the node can be removed from the graph.
    *
    * @return
    *   None if no forwarding is needed.
    */
  protected[diplomacy] def oForward(x: Int): Option[(Int, OutwardNode[DO, UO, BO])] = None

  /** Upward-flowing outward parameters.
    *
    * Generated from the nodes connected to the outward side of this node and sent upstream to this node.
    */
  protected[diplomacy] val uoParams: Seq[UO]

  /** Downward-flowing outward parameters.
    *
    * Generated by this node and sent downstream to the nodes connected to the outward side of this node.
    */
  protected[diplomacy] val doParams: Seq[DO]
}

/** Trait that enables a string representation of an edge. */
trait FormatEdge {
  def formatEdge: String
}

/** Companion object for [[BaseNode]], which is only used to hold the the global serial number of all [[BaseNode]]s. */
object BaseNode {
  protected[diplomacy] var serial = 0
}
