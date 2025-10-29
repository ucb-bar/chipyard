package org.chipsalliance.diplomacy.lazymodule

import org.chipsalliance.cde.config.Parameters

import org.chipsalliance.diplomacy.ValName

/** Allows dynamic creation of [[Module]] hierarchy and "shoving" logic into a [[LazyModule]]. */
trait LazyScope {
  this: LazyModule =>
  override def toString: String = s"LazyScope named $name"

  /** Evaluate `body` in the current [[LazyModule.scope]] */
  def apply[T](body: => T): T = {
    // Preserve the previous value of the [[LazyModule.scope]], because when calling [[apply]] function,
    // [[LazyModule.scope]] will be altered.
    val saved = LazyModule.scope
    // [[LazyModule.scope]] stack push.
    LazyModule.scope = Some(this)
    // Evaluate [[body]] in the current `scope`, saving the result to [[out]].
    val out   = body
    // Check that the `scope` after evaluating `body` is the same as when we started.
    require(LazyModule.scope.isDefined, s"LazyScope $name tried to exit, but scope was empty!")
    require(
      LazyModule.scope.get eq this,
      s"LazyScope $name exited before LazyModule ${LazyModule.scope.get.name} was closed"
    )
    // [[LazyModule.scope]] stack pop.
    LazyModule.scope = saved
    out
  }
}

/** Used to automatically create a level of module hierarchy (a [[SimpleLazyModule]]) within which [[LazyModule]]s can
  * be instantiated and connected.
  *
  * It will instantiate a [[SimpleLazyModule]] to manage evaluation of `body` and evaluate `body` code snippets in this
  * scope.
  */
object LazyScope {

  /** Create a [[LazyScope]] with an implicit instance name.
    *
    * @param body
    *   code executed within the generated [[SimpleLazyModule]].
    * @param valName
    *   instance name of generated [[SimpleLazyModule]].
    * @param p
    *   [[Parameters]] propagated to [[SimpleLazyModule]].
    */
  def apply[T](
    body:             => T
  )(
    implicit valName: ValName,
    p:                Parameters
  ): T = {
    apply(valName.value, "SimpleLazyModule", None)(body)(p)
  }

  /** Create a [[LazyScope]] with an explicitly defined instance name.
    *
    * @param name
    *   instance name of generated [[SimpleLazyModule]].
    * @param body
    *   code executed within the generated `SimpleLazyModule`
    * @param p
    *   [[Parameters]] propagated to [[SimpleLazyModule]].
    */
  def apply[T](
    name:       String
  )(body:       => T
  )(
    implicit p: Parameters
  ): T = {
    apply(name, "SimpleLazyModule", None)(body)(p)
  }

  /** Create a [[LazyScope]] with an explicit instance and class name, and control inlining.
    *
    * @param name
    *   instance name of generated [[SimpleLazyModule]].
    * @param desiredModuleName
    *   class name of generated [[SimpleLazyModule]].
    * @param overrideInlining
    *   tell FIRRTL that this [[SimpleLazyModule]]'s module should be inlined.
    * @param body
    *   code executed within the generated `SimpleLazyModule`
    * @param p
    *   [[Parameters]] propagated to [[SimpleLazyModule]].
    */
  def apply[T](
    name:              String,
    desiredModuleName: String,
    overrideInlining:  Option[Boolean] = None
  )(body:              => T
  )(
    implicit p:        Parameters
  ): T = {
    val scope = LazyModule(new SimpleLazyModule with LazyScope {
      override lazy val desiredName = desiredModuleName
      override def shouldBeInlined  = overrideInlining.getOrElse(super.shouldBeInlined)
    }).suggestName(name)
    scope {
      body
    }
  }

  /** Create a [[LazyScope]] to temporarily group children for some reason, but tell Firrtl to inline it.
    *
    * For example, we might want to control a set of children's clocks but then not keep the parent wrapper.
    *
    * @param body
    *   code executed within the generated `SimpleLazyModule`
    * @param p
    *   [[Parameters]] propagated to [[SimpleLazyModule]].
    */
  def inline[T](
    body:       => T
  )(
    implicit p: Parameters
  ): T = {
    apply("noname", "ShouldBeInlined", Some(false))(body)(p)
  }
}
