package chipyard

import chisel3._
import scala.collection.immutable.ListMap

package object harness
{
  type HarnessBinderFunction = (Any, HasHarnessInstantiators, Seq[Data]) => Unit
  type HarnessBinderMap = Map[String, HarnessBinderFunction]
  def HarnessBinderMapDefault: HarnessBinderMap = (new ListMap[String, HarnessBinderFunction])
    .withDefaultValue((t: Any, th: HasHarnessInstantiators, d: Seq[Data]) => ())

  type MultiHarnessBinderFunction = (Any, Any, HasHarnessInstantiators, Seq[Data], Seq[Data]) => Unit
  type MultiHarnessBinderMap = Map[(String, String), MultiHarnessBinderFunction]
  def MultiHarnessBinderMapDefault: MultiHarnessBinderMap = (new ListMap[(String, String), MultiHarnessBinderFunction])
    .withDefaultValue((_: Any, _: Any, _: HasHarnessInstantiators, _: Seq[Data], _: Seq[Data]) => ())
}
