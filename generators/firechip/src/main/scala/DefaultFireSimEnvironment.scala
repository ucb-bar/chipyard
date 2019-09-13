package firesim.firesim

import chisel3._
import chisel3.experimental.RawModule

import freechips.rocketchip.config.{Field, Parameters}
import freechips.rocketchip.diplomacy.{LazyModule}
import freechips.rocketchip.devices.debug.HasPeripheryDebugModuleImp
import freechips.rocketchip.subsystem.{CanHaveMasterAXI4MemPortModuleImp}
import sifive.blocks.devices.uart.HasPeripheryUARTModuleImp

import testchipip.{HasPeripherySerialModuleImp, HasPeripheryBlockDeviceModuleImp}
import icenet.HasPeripheryIceNICModuleImpValidOnly

import junctions.{NastiKey, NastiParameters}
import midas.widgets.{IsEndpoint, PeekPokeEndpoint}
import midas.models.{FASEDEndpoint, FasedAXI4Edge}
import firesim.endpoints._
import firesim.configs.MemModelKey

// Creates a wrapper module that instantiates endpoints based on the scala type
// of the Target (_not_ its IO). This avoids needing to duplicate environments
// (essentially test harnesses) for each target type,
//
// You could just as well create a custom environment (essentially, test
// harness) module that instantiates endpoints explicitly, or add methods to
// your target traits that instantiate the endpoint there (i.e., akin to
// SimAXI4Mem). Since cake traits live in Rocket Chip it was easiest to match
// on the types rather than change trait code.

case object NumNodes extends Field[Int](1)

class DefaultFireSimEnvironment[T <: LazyModule](dutGen: () => T)(implicit val p: Parameters) extends RawModule {
  val clock = IO(Input(Clock()))
  val reset = WireInit(false.B)
  withClockAndReset(clock, reset) {
    // Instantiate multiple instances of the DUT to implement supernode
    val targets = Seq.fill(p(NumNodes))(Module(LazyModule(dutGen()).module))
    val peekPokeEndpoint = PeekPokeEndpoint(reset)
    // A Seq of partial functions that will instantiate the right endpoint only
    // if that Mixin trait is present in the target's class instance
    //
    // TODO: If we like this PF approach, register them in the config instead of centralizing them here
    val endpointBinders = Seq[PartialFunction[Any, Seq[IsEndpoint]]](
      { case t: HasPeripheryDebugModuleImp =>
        t.debug.clockeddmi.foreach({ cdmi =>
          cdmi.dmi.req.valid := false.B
          cdmi.dmi.req.bits := DontCare
          cdmi.dmi.resp.ready := false.B
          cdmi.dmiClock := false.B.asClock
          cdmi.dmiReset := false.B
        })
        Seq()
      },
      { case t: HasPeripherySerialModuleImp => Seq(SerialEndpoint(t.serial)) },
      { case t: HasPeripheryIceNICModuleImpValidOnly => Seq(NICEndpoint(t.net)) },
      { case t: HasPeripheryUARTModuleImp => t.uart.map(u => UARTEndpoint(u)) },
      { case t: HasPeripheryBlockDeviceModuleImp => Seq(BlockDevEndpoint(t.bdev, reset)) },
      { case t: CanHaveMasterAXI4MemPortModuleImp =>
        (t.mem_axi4 zip t.outer.memAXI4Node).flatMap({ case (io, node) =>
          (io zip node.in).map({ case (axi4Bundle, (_, edge)) =>
            val nastiKey = NastiParameters(axi4Bundle.r.bits.data.getWidth,
                                           axi4Bundle.ar.bits.addr.getWidth,
                                           axi4Bundle.ar.bits.id.getWidth)
            val fasedP = p.alterPartial({
              case NastiKey => nastiKey
              case FasedAXI4Edge => Some(edge)
            })
            FASEDEndpoint(axi4Bundle, reset, p(MemModelKey)(fasedP))(fasedP)
          })
        }).toSeq
      },
      { case t: HasTraceIOImp => TracerVEndpoint(t.traceIO) }
    )
    // Apply each partial function to each DUT instance
    for ((target) <- targets) {
      endpointBinders.map(_.lift).flatMap(elaborator => elaborator(target))
    }
  }
}
