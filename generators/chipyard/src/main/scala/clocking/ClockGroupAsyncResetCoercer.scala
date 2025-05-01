package chipyard.clocking

import freechips.rocketchip.prci.ClockGroupAdapterNode
import org.chipsalliance.cde.config.{Config, Field, Parameters}
import org.chipsalliance.diplomacy.ValName
import org.chipsalliance.diplomacy.lazymodule.{LazyModule, LazyRawModuleImp}


/** A list of clock group names which will use an asynchronous reset. */
case object ClockGroupAsyncResetKey extends Field[Seq[String]](Nil)

/** Use this to configure `ClockGroupAsyncResetKey`. */
class WithAsyncClockGroups(groups: String*) extends Config((_, _, _) => {
  case ClockGroupAsyncResetKey => groups
})

/** Coerces the reset of selected clock groups to be asynchronous.
  *
  * Clock groups to coerce to use an async reset are customized with `ClockGroupAsyncResetKey`. All clock groups which
  * aren't present in that list are left as synchronous. Use the clock group names which
  * `chipyard.clocking.WithClockGroupsCombinedByName` produces.
  */
class ClockGroupAsyncResetCoercer()(implicit p: Parameters, valName: ValName) extends LazyModule {
  val node = ClockGroupAdapterNode()
  lazy val module = new Impl
  class Impl extends LazyRawModuleImp(this) {
    val asyncResetGroups = p(ClockGroupAsyncResetKey).toSet
    (node.out zip node.in).map { case ((outBundles, outEdges), (inBundles, _)) =>
      outBundles.member.data.zip(inBundles.member.data).zip(outEdges.sink.members).foreach {
        case ((outBundle, inBundle), outMember) =>
          val groupName = outMember.name.getOrElse("unnamed")
          val makeAsync = asyncResetGroups.contains(groupName)
          if (makeAsync) {
            println(s"Coerceing clock group $groupName to use asynchronous reset")
          } else {
            println(s"Leaving clock group $groupName with synchronous reset")
          }
          outBundle.clock :<= inBundle.clock
          outBundle.reset :<= (if (makeAsync) inBundle.reset.asAsyncReset else inBundle.reset)
      }
    }
  }
}

object ClockGroupAsyncResetCoercer {
  def apply()(implicit p: Parameters, valName: ValName): ClockGroupAdapterNode = {
    LazyModule(new ClockGroupAsyncResetCoercer()).node
  }
}
