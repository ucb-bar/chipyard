package org.chipsalliance.diplomacy.lazymodule

import org.chipsalliance.cde.config.Parameters

/** Used for a [[LazyModule]] which does not need to define any [[LazyModuleImp]] implementation.
  *
  * It can be used as wrapper that only instantiates and connects [[LazyModule]]s.
  */
class SimpleLazyModule(
  implicit p: Parameters)
    extends LazyModule {
  lazy val module = new LazyModuleImp(this)
}
class SimpleLazyRawModule(
  implicit p: Parameters)
    extends LazyModule {
  lazy val module = new LazyRawModuleImp(this)
}
