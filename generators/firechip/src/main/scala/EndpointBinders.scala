//See LICENSE for license details.

package firesim.firesim

import chisel3._
import chisel3.experimental.RawModule

import freechips.rocketchip.config.{Field, Parameters, Config}
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


// A sequence of partial functions that match on the type the DUT (_not_ it's
// IO) to generate an appropriate endpoint. You can add your own endpoint by prepending 
// a custom PartialFunction to this Seq
case object EndpointBinders extends Field[Seq[PartialFunction[Any, Seq[IsEndpoint]]]](Seq())

// Config sugar that accepts a partial function and prepends it to EndpointBinders
class RegisterEndpointBinder(pf: =>PartialFunction[Any, Seq[IsEndpoint]]) extends Config((site, here, up) => {
  case EndpointBinders => pf +: up(EndpointBinders, site)
})

// Default FireSim Endpoint binders follow
class WithTiedOffDebug extends RegisterEndpointBinder({ case target: HasPeripheryDebugModuleImp =>
  target.debug.clockeddmi.foreach({ cdmi =>
    cdmi.dmi.req.valid := false.B
    cdmi.dmi.req.bits := DontCare
    cdmi.dmi.resp.ready := false.B
    cdmi.dmiClock := false.B.asClock
    cdmi.dmiReset := false.B
  })
  Seq()
})

class WithSerialEndpoint extends RegisterEndpointBinder({
  case target: HasPeripherySerialModuleImp => Seq(SerialEndpoint(target.serial)(target.p)) 
})

class WithNICEndpoint extends RegisterEndpointBinder({
  case target: HasPeripheryIceNICModuleImpValidOnly => Seq(NICEndpoint(target.net)(target.p)) 
})

class WithUARTEndpoint extends RegisterEndpointBinder({
  case target: HasPeripheryUARTModuleImp => target.uart.map(u => UARTEndpoint(u)(target.p)) 
})

class WithBlockDeviceEndpoint extends RegisterEndpointBinder({
  case target: HasPeripheryBlockDeviceModuleImp => Seq(BlockDevEndpoint(target.bdev, target.reset.toBool)(target.p)) 
})

class WithFASEDEndpoint extends RegisterEndpointBinder({
  case t: CanHaveMasterAXI4MemPortModuleImp =>
    implicit val p = t.p
    (t.mem_axi4 zip t.outer.memAXI4Node).flatMap({ case (io, node) =>
      (io zip node.in).map({ case (axi4Bundle, (_, edge)) =>
        val nastiKey = NastiParameters(axi4Bundle.r.bits.data.getWidth,
                                       axi4Bundle.ar.bits.addr.getWidth,
                                       axi4Bundle.ar.bits.id.getWidth)
        val fasedP = p.alterPartial({
          case NastiKey => nastiKey
          case FasedAXI4Edge => Some(edge)
        })
        FASEDEndpoint(axi4Bundle, t.reset.toBool, p(MemModelKey)(fasedP))(fasedP)
      })
    }).toSeq
})

class WithTracerVEndpoint extends RegisterEndpointBinder({
  case target: HasTraceIOImp => TracerVEndpoint(target.traceIO)(target.p)
})

// Shorthand to register all of the provided endpoints above
class WithDefaultFireSimEndpoints extends Config(
  new WithTiedOffDebug ++
  new WithSerialEndpoint ++
  new WithNICEndpoint ++
  new WithUARTEndpoint ++
  new WithBlockDeviceEndpoint ++
  new WithFASEDEndpoint ++
  new WithTracerVEndpoint
)
