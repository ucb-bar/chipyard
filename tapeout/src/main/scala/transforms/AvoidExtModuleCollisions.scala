// See LICENSE for license details.

package barstools.tapeout.transforms

import firrtl._
import firrtl.ir._
import firrtl.annotations._

case class LinkExtModulesAnnotation(mustLink: Seq[ExtModule]) extends NoTargetAnnotation

class AvoidExtModuleCollisions extends Transform {
  def inputForm = HighForm
  def outputForm = HighForm
  def execute(state: CircuitState): CircuitState = {
    val mustLink = state.annotations.flatMap {
      case LinkExtModulesAnnotation(mustLink) => mustLink
      case _ => Nil
    }
    val newAnnos = state.annotations.filterNot(_.isInstanceOf[LinkExtModulesAnnotation])
    state.copy(circuit = state.circuit.copy(modules = state.circuit.modules ++ mustLink), annotations = newAnnos)
  }
}

