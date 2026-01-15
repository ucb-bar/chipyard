// See LICENSE

package chipyard

import firrtl.AnnotationSeq
import firrtl.options.OptionsView

package object stage {

  implicit object ChipyardOptionsView extends OptionsView[ChipyardOptions] {

    def view(annotations: AnnotationSeq): ChipyardOptions = annotations
      .collect { case a: ChipyardOption => a }
      .foldLeft(new ChipyardOptions()){ (c, x) =>
        x match {
          case TopModuleAnnotation(a)         => c.copy(topModule = Some(a))
          case ConfigsAnnotation(a)           => c.copy(configNames = Some(a))
          case OutputBaseNameAnnotation(a)    => c.copy(outputBaseName = Some(a))
        }
      }

  }

}
