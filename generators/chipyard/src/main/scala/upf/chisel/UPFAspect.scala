// See LICENSE for license details
package chipyard.upf.chisel

import chisel3.experimental.{BaseModule}
import chisel3.aop.Aspect
import firrtl.{AnnotationSeq}
import chipyard.TestHarness
import freechips.rocketchip.diplomacy.LazyModule
import firrtl.annotations.Annotation
import chipyard.upf.firrtl.NameUPFAnnotation

abstract class UPFAspect[T <: TestHarness](upf: UPFFunc.UPFFunction) extends Aspect[T] {

  final override def toAnnotation(top: T): AnnotationSeq = {
    upf(top.dut)
    AnnotationSeq(ChiselUPFElement().getAnnotations()) // noop
  }
  
}

object UPFFunc {
  type UPFFunction = PartialFunction[BaseModule, Unit]
}

abstract class ChiselNameUPFElement() {
    def getAnnotations(): Seq[Annotation] = {
        return Seq(NameUPFAnnotation())
    }
}

case class ChiselUPFElement() extends ChiselNameUPFElement()
