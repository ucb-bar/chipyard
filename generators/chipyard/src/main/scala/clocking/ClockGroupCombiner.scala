package chipyard.clocking

import chisel3._
import chisel3.util._
import chisel3.experimental.{Analog, IO}

import org.chipsalliance.cde.config._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.prci._
import freechips.rocketchip.util._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.subsystem._

object ClockGroupCombiner {
  def apply()(implicit p: Parameters, valName: ValName): ClockGroupAdapterNode = {
    LazyModule(new ClockGroupCombiner()).node
  }
}

case object ClockGroupCombinerKey extends Field[Seq[(String, ClockSinkParameters => Boolean)]](Nil)

// All clock groups with a name containing any substring in names will be combined into a single clock group
class WithClockGroupsCombinedByName(groups: (String, Seq[String], Seq[String])*) extends Config((site, here, up) => {
  case ClockGroupCombinerKey => groups.map { case (grouped_name, matched_names, unmatched_names) =>
    (grouped_name, (m: ClockSinkParameters) => matched_names.exists(n => m.name.get.contains(n)) && !unmatched_names.exists(n => m.name.get.contains(n)))
  }
})

/** This node combines sets of clock groups according to functions provided in the ClockGroupCombinerKey
  * The ClockGroupCombinersKey contains a list of tuples of:
  * - The name of the combined group
  * - A function on the ClockSinkParameters, returning True if the associated clock group should be grouped by this node
  * This node will fail if
  * - Multiple grouping functions match a single clock group
  * - A grouping function matches zero clock groups
  * - A grouping function matches clock groups with different requested frequncies
  */
class ClockGroupCombiner(implicit p: Parameters, v: ValName) extends LazyModule {
  val combiners = p(ClockGroupCombinerKey)
  val sourceFn: ClockGroupSourceParameters => ClockGroupSourceParameters = { m => m }
  val sinkFn: ClockGroupSinkParameters   => ClockGroupSinkParameters = { u =>
    var i = 0
    val (grouped, rest) = combiners.map(_._2).foldLeft((Seq[ClockSinkParameters](), u.members)) { case ((grouped, rest), c) =>
      val (g, r) = rest.partition(c(_))
      val name = combiners(i)._1
      i = i + 1
      require(g.size >= 1)
      val takes = g.map(_.take).flatten
      require(takes.distinct.size <= 1,
        s"Clock group $name has non-homogeneous requested ClockParameters $takes")
      require(takes.size > 0,
        s"Clock group $name has no inheritable frequencies")
      (grouped ++ Seq(ClockSinkParameters(take = takes.headOption, name = Some(name))), r)
    }

    ClockGroupSinkParameters(
      name = u.name,
      members = grouped ++ rest
    )
  }


  val node = ClockGroupAdapterNode(sourceFn, sinkFn)
  lazy val module = new LazyRawModuleImp(this) {
    (node.out zip node.in).map { case ((o, oe), (i, ie)) =>
      {
        val inMap = (i.member.data zip ie.sink.members).map { case (id, im) =>
          im.name.get -> id
        }.toMap
        (o.member.data zip oe.sink.members).map { case (od, om) =>
          val matches = combiners.filter(c => c._2(om))
          require(matches.size <= 1)
          if (matches.size == 0) {
            od := inMap(om.name.get)
          } else {
            od := inMap(matches(0)._1)
          }
        }
      }
    }
  }
}
