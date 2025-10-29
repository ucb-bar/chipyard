package org.chipsalliance.diplomacy.lazymodule

import scala.language.reflectiveCalls

trait ModuleValue[T] {
  def getWrappedValue: T
}

/** Used to inject code snippets to be evaluated in [[LazyModuleImp.instantiate]] in the current [[LazyModule.scope]].
  *
  * It can be used to create additional hardware outside of the [[LazyModule.children]], connections other than the
  * internal [[BaseNode]] connections, or additional IOs aside from the [[AutoBundle]]
  */
object InModuleBody {
  def apply[T](body: => T): ModuleValue[T] = {
    require(LazyModule.scope.isDefined, s"InModuleBody invoked outside a LazyModule")
    val scope = LazyModule.scope.get
    // a wrapper to [[body]], being able to extract result after `execute`.
    val out   = new ModuleValue[T] {
      var result: Option[T] = None

      def execute(): Unit = {
        result = Some(body)
      }

      def getWrappedValue: T = {
        require(result.isDefined, s"InModuleBody contents were requested before module was evaluated!")
        result.get
      }
    }

    // Prepend [[out.execute]] to [[scope.inModuleBody]],
    // it is a val with type of `() => Unit`, which will be executed in [[LazyModuleImp.instantiate]].
    scope.inModuleBody = out.execute _ +: scope.inModuleBody
    out
  }
}
