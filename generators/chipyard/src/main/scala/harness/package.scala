package chipyard

import chisel3._
import scala.collection.immutable.ListMap

package object harness
{
  type HarnessBinderFunction = (Any, HasChipyardHarnessInstantiators, Seq[Data]) => Unit
  type HarnessBinderMap = Map[String, HarnessBinderFunction]
  def HarnessBinderMapDefault: HarnessBinderMap = (new ListMap[String, HarnessBinderFunction])
    .withDefaultValue((t: Any, th: HasChipyardHarnessInstantiators, d: Seq[Data]) => ())

  type MultiHarnessBinderFunction = (Any, Any, HasChipyardHarnessInstantiators, Seq[Data], Seq[Data]) => Unit
  type MultiHarnessBinderMap = Map[String, MultiHarnessBinderFunction]
  def MultiHarnessBinderMapDefault: MultiHarnessBinderMap = (new ListMap[String, MultiHarnessBinderFunction])
    .withDefaultValue((_: Any, _: Any, _: HasChipyardHarnessInstantiators, _: Seq[Data], _: Seq[Data]) => ())
}
