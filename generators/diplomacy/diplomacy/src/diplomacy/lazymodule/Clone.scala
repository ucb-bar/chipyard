package org.chipsalliance.diplomacy.lazymodule

import chisel3.experimental.SourceInfo

import org.chipsalliance.diplomacy.{sourceLine, ValName}

object CloneLazyModule {

  /** Constructs a [[LazyModule]], but replaces its [[LazyModuleImp]] with a cloned [[LazyModuleImp]] from another
    * source. The user of [[CloneLazyModule]] must be careful to guarantee that bc and cloneProto have equivalent
    * [[LazyModuleImp]]'s.
    *
    * @param bc
    *   [[LazyModule]] instance to wrap, this instance will not evaluate its own [[LazyModuleImp]]
    * @param cloneProto
    *   [[LazyModule]] instance which will provide the [[LazyModuleImp]] implementation for bc
    */
  def apply[A <: LazyModule, B <: LazyModule](
    bc:               A,
    cloneProto:       B
  )(
    implicit valName: ValName,
    sourceInfo:       SourceInfo
  ): A = {
    require(
      LazyModule.scope.isDefined,
      s"CloneLazyModule ${bc.name} ${sourceLine(sourceInfo)} can only exist as the child of a parent LazyModule"
    )
    LazyModule(bc)
    bc.cloneProto = Some(cloneProto)
    bc
  }
}
