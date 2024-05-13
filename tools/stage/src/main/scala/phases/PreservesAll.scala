package chipyard.stage.phases

import firrtl.AnnotationSeq
import firrtl.options.{Dependency, DependencyManagerException, Phase, PhaseManager}

trait PreservesAll { this: Phase =>
  override def invalidates(phase: Phase) = false
}
