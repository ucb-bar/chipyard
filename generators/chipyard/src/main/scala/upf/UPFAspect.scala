// See LICENSE for license details
package chipyard.upf

import chisel3.aop.{Aspect}
import firrtl.{AnnotationSeq}
import chipyard.harness.{TestHarness}
import chipyard.stage.phases.{TargetDirKey}
import freechips.rocketchip.diplomacy.{LazyModule}

abstract class UPFAspect[T <: TestHarness](upf: UPFFunc.UPFFunction) extends Aspect[T] {

  final override def toAnnotation(top: T): AnnotationSeq = {
    UPFFunc.UPFPath = top.p(TargetDirKey) + "/upf"
    require(top.lazyDuts.length == 1) // currently only supports 1 chiptop
    upf(top.lazyDuts.head)
    AnnotationSeq(Seq()) // noop
  }

}

object UPFFunc {
  type UPFFunction = PartialFunction[LazyModule, Unit]
  var UPFPath = "" // output dir path
}
