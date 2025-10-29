package org.chipsalliance.diplomacy.nodes

import chisel3.Data
import chisel3.experimental.SourceInfo
import org.chipsalliance.cde.config.Parameters

/** A Handle with no explicitly defined binding functionality.
  *
  * A [[NoHandle]] is at the top of the Handle type hierarchy, but it does not define any binding operators, so by
  * itself a [[NoHandle]] cannot be used on either side of a bind operator.
  *
  * For example, a source node connected directly to a sink node produces a [[NoHandle]], because there are no further
  * bindings that could be applied to either side of the pair of nodes.
  *
  * The other Handle types extend this type and bestow actual binding semantics. They can always be used wherever a
  * [[NoHandle]] is expected because a [[NoHandle]] doesn't provide any guaranteed behavior.
  *
  * Handle algebra:
  *
  * "x---x" [[NoHandle]] "x---<" [[InwardNodeHandle]] "<---x" [[OutwardNodeHandle]] "<---<" (Full) [[NodeHandle]]
  *
  * "<" can be bound to (arrow points in the direction of binding). "x" cannot be bound to.
  *
  * The left side is outer, the right side is inner.
  *
  * Two Handles can be bound if their adjacent ends are both "<".
  */
trait NoHandle
case object NoHandleObject extends NoHandle

/** A Handle that can be used on either side of a bind operator. */
trait NodeHandle[DI, UI, EI, BI <: Data, DO, UO, EO, BO <: Data]
    extends InwardNodeHandle[DI, UI, EI, BI]
    with OutwardNodeHandle[DO, UO, EO, BO] {

  /** Connects two full nodes handles => full node handle.
    *
    * <---< := <---< == <---< This and that node are both [[BIND_ONCE]].
    *
    * @param h
    *   A source node also with sink handle.
    * @return
    *   A [[NodeHandle]] with that node as `inwardNode`, this node as `outwardNode`.
    */
  override def :=[DX, UX, EX, BX <: Data, EY](
    h:          NodeHandle[DX, UX, EX, BX, DI, UI, EY, BI]
  )(
    implicit p: Parameters,
    sourceInfo: SourceInfo
  ): NodeHandle[DX, UX, EX, BX, DO, UO, EO, BO] = { bind(h, BIND_ONCE); NodeHandle(h, this) }

  /** Connects two full nodes handles => full node handle.
    *
    * <---< :*= <---< == <---< [[BIND_STAR]] this node as sink, [[BIND_QUERY]] that node as source.
    *
    * @param h
    *   A source node also with sink handle.
    * @return
    *   A [[NodeHandle]] with that node as `InwardNode`, this node as `OutwardNode`.
    */
  override def :*=[DX, UX, EX, BX <: Data, EY](
    h:          NodeHandle[DX, UX, EX, BX, DI, UI, EY, BI]
  )(
    implicit p: Parameters,
    sourceInfo: SourceInfo
  ): NodeHandle[DX, UX, EX, BX, DO, UO, EO, BO] = { bind(h, BIND_STAR); NodeHandle(h, this) }

  /** Connects two full nodes handles => full node handle.
    *
    * <---< :=* <---< == <---< [[BIND_QUERY]] this node as sink, [[BIND_STAR]] that node as source.
    *
    * @param h
    *   A source node also with sink handle.
    * @return
    *   A [[NodeHandle]] with that node as `InwardNode`, this node as `OutwardNode`.
    */
  override def :=*[DX, UX, EX, BX <: Data, EY](
    h:          NodeHandle[DX, UX, EX, BX, DI, UI, EY, BI]
  )(
    implicit p: Parameters,
    sourceInfo: SourceInfo
  ): NodeHandle[DX, UX, EX, BX, DO, UO, EO, BO] = { bind(h, BIND_QUERY); NodeHandle(h, this) }

  /** Connects two full nodes handles => full node handle.
    *
    * <---< :*=* <---< == <---< [[BIND_FLEX]] this node as sink, [[BIND_FLEX]] that node as source.
    *
    * @param h
    *   A source node also with sink handle.
    * @return
    *   A [[NodeHandle]] with that node as `inwardNode`, this node as `outwardNode`.
    */
  override def :*=*[DX, UX, EX, BX <: Data, EY](
    h:          NodeHandle[DX, UX, EX, BX, DI, UI, EY, BI]
  )(
    implicit p: Parameters,
    sourceInfo: SourceInfo
  ): NodeHandle[DX, UX, EX, BX, DO, UO, EO, BO] = { bind(h, BIND_FLEX); NodeHandle(h, this) }

  /** Connects a full node with an output node => an output handle.
    *
    * <---< := <---x == <---x [[BIND_ONCE]] this node as sink, [[BIND_ONCE]] that node as source.
    *
    * @param h
    *   A source node also without sink handle.
    * @return
    *   A [[OutwardNodeHandle]] with this node as `outwardNode`.
    */
  override def :=[EY](
    h:          OutwardNodeHandle[DI, UI, EY, BI]
  )(
    implicit p: Parameters,
    sourceInfo: SourceInfo
  ): OutwardNodeHandle[DO, UO, EO, BO] = { bind(h, BIND_ONCE); this }

  /** Connects a full node with an output node => an output handle.
    *
    * <---< :*= <---x == <---x [[BIND_STAR]] this node as sink, [[BIND_QUERY]] that node as source.
    *
    * @param h
    *   A source node also without sink handle.
    * @return
    *   A [[OutwardNodeHandle]] with this node as `outwardNode`.
    */
  override def :*=[EY](
    h:          OutwardNodeHandle[DI, UI, EY, BI]
  )(
    implicit p: Parameters,
    sourceInfo: SourceInfo
  ): OutwardNodeHandle[DO, UO, EO, BO] = { bind(h, BIND_STAR); this }

  /** Connects a full node with an output => an output.
    *
    * <---< :=* <---x == <---x [[BIND_QUERY]] this node as sink, [[BIND_STAR]] that node as source.
    *
    * @param h
    *   A source node also without sink handle.
    * @return
    *   A [[OutwardNodeHandle]] with this node as `outwardNode`.
    */
  override def :=*[EY](
    h:          OutwardNodeHandle[DI, UI, EY, BI]
  )(
    implicit p: Parameters,
    sourceInfo: SourceInfo
  ): OutwardNodeHandle[DO, UO, EO, BO] = { bind(h, BIND_QUERY); this }

  /** Connects a full node with an output => an output.
    *
    * <---< :*=* <---x == <---x [[BIND_FLEX]] this node as sink, [[BIND_FLEX]] that node as source.
    *
    * @param h
    *   A source node also without sink handle.
    * @return
    *   A [[OutwardNodeHandle]] with this node as `outwardNode`.
    */
  override def :*=*[EY](
    h:          OutwardNodeHandle[DI, UI, EY, BI]
  )(
    implicit p: Parameters,
    sourceInfo: SourceInfo
  ): OutwardNodeHandle[DO, UO, EO, BO] = { bind(h, BIND_FLEX); this }
}

object NodeHandle {

  /** generate a [[NodeHandle]] by combining an [[InwardNodeHandle]] and an [[OutwardNodeHandle]].
    *
    * @param i
    *   Inward node handle.
    * @param o
    *   Outward node handle.
    * @return
    *   [[NodeHandlePair]] with `inwardNode` of `i`, `outwardNode` of `o`.
    */
  def apply[DI, UI, EI, BI <: Data, DO, UO, EO, BO <: Data](
    i: InwardNodeHandle[DI, UI, EI, BI],
    o: OutwardNodeHandle[DO, UO, EO, BO]
  ) = new NodeHandlePair(i, o)
}

/** A data structure that preserves information about the innermost and outermost Nodes in a [[NodeHandle]]. */
class NodeHandlePair[DI, UI, EI, BI <: Data, DO, UO, EO, BO <: Data](
  inwardHandle:  InwardNodeHandle[DI, UI, EI, BI],
  outwardHandle: OutwardNodeHandle[DO, UO, EO, BO])
    extends NodeHandle[DI, UI, EI, BI, DO, UO, EO, BO] {

  /** @return [[InwardNode]] of [[inwardHandle]]. */
  val inward: InwardNode[DI, UI, BI] = inwardHandle.inward

  /** @return [[OutwardNode]] of [[outwardHandle]]. */
  val outward: OutwardNode[DO, UO, BO] = outwardHandle.outward

  /** @return The innermost [[InwardNodeImp]] of this [[NodeHandlePair]]. */
  def inner: InwardNodeImp[DI, UI, EI, BI] = inwardHandle.inner

  /** @return The outermost [[OutwardNodeImp]] of [[NodeHandlePair]]. */
  def outer: OutwardNodeImp[DO, UO, EO, BO] = outwardHandle.outer
}

/** A handle for an [[InwardNode]], which may appear on the left side of a bind operator. */
trait InwardNodeHandle[DI, UI, EI, BI <: Data] extends NoHandle {

  /** @return [[InwardNode]] of `inwardHandle`. */
  def inward: InwardNode[DI, UI, BI]

  /** @return [[InwardNodeImp]] of `inwardHandle`. */
  def inner: InwardNodeImp[DI, UI, EI, BI]

  /** Bind this node to an [[OutwardNodeHandle]]. */
  protected def bind[EY](
    h:          OutwardNodeHandle[DI, UI, EY, BI],
    binding:    NodeBinding
  )(
    implicit p: Parameters,
    sourceInfo: SourceInfo
  ): Unit = inward.bind(h.outward, binding)

  /** Connect an input node with a full node => inward node handle.
    *
    * x---< := <---< == x---< [[BIND_ONCE]] this node as sink, [[BIND_ONCE]] that node as source.
    *
    * @param h
    *   A source node also with sink handle.
    * @return
    *   A [[NodeHandle]] with that node as `inwardNode`, this node as `outwardNode`.
    */
  def :=[DX, UX, EX, BX <: Data, EY](
    h:          NodeHandle[DX, UX, EX, BX, DI, UI, EY, BI]
  )(
    implicit p: Parameters,
    sourceInfo: SourceInfo
  ): InwardNodeHandle[DX, UX, EX, BX] = { bind(h, BIND_ONCE); h }

  /** Connect an input node with a full node => an input node.
    *
    * x---< :*= <---< == x---< [[BIND_STAR]] this node as sink, [[BIND_QUERY]] that node as source.
    *
    * @param h
    *   A Source node also with sink handle.
    * @return
    *   A [[NodeHandle]] with that node as `inwardNode`, this node as `outwardNode`.
    */
  def :*=[DX, UX, EX, BX <: Data, EY](
    h:          NodeHandle[DX, UX, EX, BX, DI, UI, EY, BI]
  )(
    implicit p: Parameters,
    sourceInfo: SourceInfo
  ): InwardNodeHandle[DX, UX, EX, BX] = { bind(h, BIND_STAR); h }

  /** Connect an input node with a full node => an inward node handle.
    *
    * x---< :=* <---< == x---< [[BIND_QUERY]] this node as sink, [[BIND_STAR]] that node as source.
    *
    * @param h
    *   A source node also with sink handle.
    * @return
    *   A [[NodeHandle]] with that node as `inwardNode`, this node as `outwardNode`.
    */
  def :=*[DX, UX, EX, BX <: Data, EY](
    h:          NodeHandle[DX, UX, EX, BX, DI, UI, EY, BI]
  )(
    implicit p: Parameters,
    sourceInfo: SourceInfo
  ): InwardNodeHandle[DX, UX, EX, BX] = { bind(h, BIND_QUERY); h }

  /** Connect an input node with a full node => an input node.
    *
    * x---< :*=* <---< == x---< [[BIND_FLEX]] this node as sink, [[BIND_FLEX]] that node as source.
    *
    * @param h
    *   A source node also with sink handle.
    * @return
    *   A [[NodeHandle]] with that node as `inwardNode`, this node as `outwardNode`.
    */
  def :*=*[DX, UX, EX, BX <: Data, EY](
    h:          NodeHandle[DX, UX, EX, BX, DI, UI, EY, BI]
  )(
    implicit p: Parameters,
    sourceInfo: SourceInfo
  ): InwardNodeHandle[DX, UX, EX, BX] = { bind(h, BIND_FLEX); h }

  /** Connect an input node with output node => no node.
    *
    * x---< := <---x == x---x [[BIND_ONCE]] this node as sink, [[BIND_ONCE]] that node as source.
    *
    * @param h
    *   A source node also without sink handle.
    * @return
    *   A [[NoHandle]] since neither side can bind to a node.
    */
  def :=[EY](
    h:          OutwardNodeHandle[DI, UI, EY, BI]
  )(
    implicit p: Parameters,
    sourceInfo: SourceInfo
  ): NoHandle = {
    bind(h, BIND_ONCE); NoHandleObject
  }

  /** Connect an input node with output node => no node.
    *
    * x---< :*= <---x == x---x [[BIND_STAR]] this node as sink, [[BIND_QUERY]] that node as source.
    *
    * @param h
    *   A source node also without sink handle.
    * @return
    *   A [[NoHandle]] since neither side can bind to a node.
    */
  def :*=[EY](
    h:          OutwardNodeHandle[DI, UI, EY, BI]
  )(
    implicit p: Parameters,
    sourceInfo: SourceInfo
  ): NoHandle = {
    bind(h, BIND_STAR); NoHandleObject
  }

  /** Connect an input node with output node => no node.
    *
    * x---< :=* <---x == x---x [[BIND_QUERY]] this node as sink, [[BIND_STAR]] that node as source.
    *
    * @param h
    *   A source node also without sink handle.
    * @return
    *   A [[NoHandle]] since neither side can bind to another node.
    */
  def :=*[EY](
    h:          OutwardNodeHandle[DI, UI, EY, BI]
  )(
    implicit p: Parameters,
    sourceInfo: SourceInfo
  ): NoHandle = {
    bind(h, BIND_QUERY); NoHandleObject
  }

  /** Connect an input node with output node => no node.
    *
    * x---< :*=* <---x == x---x [[BIND_FLEX]] this node as sink, [[BIND_FLEX]] that node as source.
    *
    * @param h
    *   A source node also without sink handle.
    * @return
    *   A [[NoHandle]] since neither side can bind to another node.
    */
  def :*=*[EY](
    h:          OutwardNodeHandle[DI, UI, EY, BI]
  )(
    implicit p: Parameters,
    sourceInfo: SourceInfo
  ): NoHandle = {
    bind(h, BIND_FLEX); NoHandleObject
  }
}

/** A Handle for OutwardNodes, which may appear on the right side of a bind operator. */
trait OutwardNodeHandle[DO, UO, EO, BO <: Data] extends NoHandle {

  /** @return [[OutwardNode]] of `outwardHandle`. */
  def outward: OutwardNode[DO, UO, BO]

  /** @return [[OutwardNodeImp]] of `inwardHandle`. */
  def outer: OutwardNodeImp[DO, UO, EO, BO]
}
