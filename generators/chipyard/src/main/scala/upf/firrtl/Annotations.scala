// See LICENSE for license details
package chipyard.upf.firrtl

import firrtl.annotations._
import firrtl.stage.{RunFirrtlTransformAnnotation}

trait UPFAnnotation extends Annotation {
  val name: String
}

case class NameUPFAnnotation(target: Target, name: String) extends SingleTargetAnnotation[Target] with UPFAnnotation {
  def apply(target: Target, name: String): NameUPFAnnotation = NameUPFAnnotation(target, name)
  def duplicate(t: Target) = this.copy(t, name)
}