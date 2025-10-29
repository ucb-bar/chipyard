package testchipip.soc

import scala.collection.immutable.ListSet
import org.chipsalliance.cde.config._
import freechips.rocketchip.subsystem.{BaseSubsystem}

// Defines Keys which can be used to lookup functions that inject arbitrary code into the Subsystem
// This is intended to be used to inject devices without requiring the cake pattern

abstract class SubsystemInjector(inject: (Parameters, BaseSubsystem) => Unit) extends Field[(Parameters, BaseSubsystem) => Unit](inject)
case object SubsystemInjectorKey extends Field[ListSet[SubsystemInjector]](ListSet.empty)

trait CanHaveSubsystemInjectors { this: BaseSubsystem =>
  // Inject all the functions collected through the SubsystemInjector
  p(SubsystemInjectorKey).foreach { k => p(k)(p, this) }
}
