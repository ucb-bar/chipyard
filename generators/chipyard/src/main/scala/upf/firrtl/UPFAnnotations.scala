// See LICENSE for license details
package chipyard.upf.firrtl

import firrtl.annotations._
import firrtl.stage.{RunFirrtlTransformAnnotation}

trait UPFAnnotation extends Annotation {
  val name: String
}

case class NameUPFAnnotation() extends NoTargetAnnotation {
  def apply(): NameUPFAnnotation = NameUPFAnnotation()
  def duplicate() = this.copy()
}