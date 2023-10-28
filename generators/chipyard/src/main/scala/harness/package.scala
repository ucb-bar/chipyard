package chipyard

import chisel3._
import scala.collection.immutable.ListMap

package object harness
{
  import chipyard.iobinders.Port
  type HarnessBinderFunction = PartialFunction[(HasHarnessInstantiators, Port[_]), Unit]
  type MultiHarnessBinderFunction = (Seq[Port[_]], Seq[Port[_]]) => Unit
}
