// See LICENSE for license details
package chipyard.upf.chisel

import chisel3.{RawModule}
import chisel3.aop.{Aspect, Select}
import firrtl.{AnnotationSeq}

abstract class UPFAspect[T <: RawModule](upf: UPFFunction) extends Aspect[T] {

  final override def toAnnotation(top: T): AnnotationSeq = {
    AnnotationSeq(Select.collectDeep(top)(upf).toList.flatten.flatMap(_.getAnnotations()))
  }
  
}

