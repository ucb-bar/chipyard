// See LICENSE for license details
package chipyard.upf

import chisel3.aop.Aspect
import firrtl.{AnnotationSeq}
import chipyard.TestHarness
import freechips.rocketchip.stage.phases.TargetDirKey
import freechips.rocketchip.diplomacy.LazyModule

abstract class UPFAspect[T <: TestHarness](upf: UPFFunc.UPFFunction) extends Aspect[T] {

  final override def toAnnotation(top: T): AnnotationSeq = {
    UPFFunc.UPFPath = top.p(TargetDirKey) + "/upf"
    upf(top.lazyDut)
    AnnotationSeq(Seq()) // noop
  }

}

object UPFFunc {
  type UPFFunction = PartialFunction[LazyModule, Unit]
  var UPFPath = "" // output dir path
}
