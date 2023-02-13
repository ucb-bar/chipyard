// See LICENSE for license details
package chipyard.upf.chisel

import firrtl.annotations.{Target, Annotation}
import chipyard.upf.firrtl.NameUPFAnnotation

abstract class ChiselNameUPFElement(scope: Target, name: String) {
    def getAnnotations(): Seq[Annotation] = {
        return Seq(NameUPFAnnotation(scope, name))
    }
}

class ChiselUPFElement(scope: Target, name: String) extends ChiselNameUPFElement(scope, name)
