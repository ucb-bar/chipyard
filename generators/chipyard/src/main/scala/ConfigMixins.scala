package chipyard

import chisel3._
import chisel3.util.{log2Up}

import freechips.rocketchip.config.{Field, Parameters, Config}
import freechips.rocketchip.subsystem.{SystemBusKey, RocketTilesKey, WithRoccExample, WithNMemoryChannels, WithNBigCores, WithRV32, CacheBlockBytes}
import freechips.rocketchip.diplomacy.{LazyModule, ValName}
import freechips.rocketchip.devices.tilelink.BootROMParams
import freechips.rocketchip.devices.debug.{Debug}
import freechips.rocketchip.tile.{XLen, BuildRoCC, TileKey, LazyRoCC, RocketTileParams, MaxHartIdBits}
import freechips.rocketchip.rocket.{RocketCoreParams, MulDivParams, DCacheParams, ICacheParams}
import freechips.rocketchip.util.{AsyncResetReg}

import boom.common.{BoomTilesKey}

import testchipip._

import hwacha.{Hwacha}

import sifive.blocks.devices.gpio._
import sifive.blocks.devices.uart._

import icenet.{NICKey, NICConfig}

/**
 * TODO: Why do we need this?
 */
object ConfigValName {
  implicit val valName = ValName("TestHarness")
}
import ConfigValName._

// -----------------------
// Common Parameter Mixins
// -----------------------

/**
 * Mixin to add the Chipyard bootrom
 */
class WithBootROM extends Config((site, here, up) => {
  case BootROMParams => BootROMParams(
    contentFileName = s"./bootrom/bootrom.rv${site(XLen)}.img")
})

// DOC include start: gpio mixin
/**
 * Mixin to add GPIOs and tie them off outside the DUT
 */
class WithGPIO extends Config((site, here, up) => {
  case PeripheryGPIOKey => Seq(
    GPIOParams(address = 0x10012000, width = 4, includeIOF = false))
  case BuildTop => (clock: Clock, reset: Bool, p: Parameters, success: Bool) => {
    val top = up(BuildTop, site)(clock, reset, p, success)
    // TODO: Currently FIRRTL will error if the GPIO input
    // pins are unconnected, so tie them to 0.
    // In future IO cell blackboxes will replace this with
    // more correct functionality
    for (gpio <- top.gpio) {
      for (pin <- gpio.pins) {
        pin.i.ival := false.B
      }
    }
    top
  }
})
// DOC include end: gpio mixin

/**
 * Mixin to add in UART
 */
class WithUART extends Config((site, here, up) => {
  case PeripheryUARTKey => Seq(
    UARTParams(address = 0x54000000L, nTxEntries = 256, nRxEntries = 256))
})

/**
 * Mixin to remove any GPIOs
 */
class WithNoGPIO extends Config((site, here, up) => {
  case PeripheryGPIOKey => Seq()
})

// DOC include start: tsi mixin
/**
 * Mixin to add an offchip TSI link (used for backing memory)
 */
class WithTSI extends Config((site, here, up) => {
  case SerialKey => true
  case BuildTop => (clock: Clock, reset: Bool, p: Parameters, success: Bool) => {
    val top = up(BuildTop, site)(clock, reset, p, success)
    success := top.connectSimSerial()
    top
  }
})
// DOC include end: tsi mixin

/**
 * Mixin to add an DTM (used for dmi or jtag bringup)
 */
class WithDTM extends Config((site, here, up) => {
  case BuildTop => (clock: Clock, reset: Bool, p: Parameters, success: Bool) => {
    val top = up(BuildTop, site)(clock, reset, p, success)
    top.reset := reset.asBool | top.debug.map { debug => AsyncResetReg(debug.ndreset) }.getOrElse(false.B)
    Debug.connectDebug(top.debug, top.psd, clock, reset.asBool, success)(p)
    top
  }
})

// DOC include start: GCD mixin
/**
 * Mixin to add a GCD peripheral
 */
class WithGCD(useAXI4: Boolean, useBlackBox: Boolean) extends Config((site, here, up) => {
  case GCDKey => Some(GCDParams(useAXI4 = useAXI4, useBlackBox = useBlackBox))
})
// DOC include end: GCD mixin

/**
 * Mixin to add a RTL block device model
 */
class WithBlockDeviceModel extends Config((site, here, up) => {
  case BuildTop => (clock: Clock, reset: Bool, p: Parameters, success: Bool) => {
    val top = up(BuildTop, site)(clock, reset, p, success)
    top.connectBlockDeviceModel()
    top
  }
})

/**
 * Mixin to add a simulated block device model
 */
class WithSimBlockDevice extends Config((site, here, up) => {
  case BuildTop => (clock: Clock, reset: Bool, p: Parameters, success: Bool) => {
    val top = up(BuildTop, site)(clock, reset, p, success)
    top.connectSimBlockDevice(clock, reset)
    top
  }
})

// DOC include start: WithInitZero
/**
 * Mixin to add a peripheral that clears memory
 */
class WithInitZero(base: BigInt, size: BigInt) extends Config((site, here, up) => {
  case InitZeroKey => Some(InitZeroConfig(base, size))
})
// DOC include end: WithInitZero

// ------------------
// Multi-RoCC Support
// ------------------

/**
 * Map from a hartId to a particular RoCC accelerator
 */
case object MultiRoCCKey extends Field[Map[Int, Seq[Parameters => LazyRoCC]]](Map.empty[Int, Seq[Parameters => LazyRoCC]])

/**
 * Mixin to enable different RoCCs based on the hartId
 */
class WithMultiRoCC extends Config((site, here, up) => {
  case BuildRoCC => site(MultiRoCCKey).getOrElse(site(TileKey).hartId, Nil)
})

/**
 * Mixin to add Hwachas to cores based on hart
 *
 * For ex:
 *   Core 0, 1, 2, 3 have been defined earlier
 *     with hartIds of 0, 1, 2, 3 respectively
 *   And you call WithMultiRoCCHwacha(0,1)
 *   Then Core 0 and 1 will get a Hwacha
 *
 * @param harts harts to specify which will get a Hwacha
 */
class WithMultiRoCCHwacha(harts: Int*) extends Config((site, here, up) => {
  case MultiRoCCKey => {
    require(harts.max <= ((up(RocketTilesKey, site).length + up(BoomTilesKey, site).length) - 1))
    up(MultiRoCCKey, site) ++ harts.distinct.map{ i =>
      (i -> Seq((p: Parameters) => {
        LazyModule(new Hwacha()(p)).suggestName("hwacha")
      }))
    }
  }
})


/**
 * Mixin to add a small Rocket core to the system as a "control" core.
 * Used as an example of a PMU core.
 */
class WithControlCore extends Config((site, here, up) => {
  case RocketTilesKey => up(RocketTilesKey, site) :+
    RocketTileParams(
      core = RocketCoreParams(
        useVM = false,
        fpu = None,
        mulDiv = Some(MulDivParams(mulUnroll = 8))),
      btb = None,
      dcache = Some(DCacheParams(
        rowBits = site(SystemBusKey).beatBits,
        nSets = 64,
        nWays = 1,
        nTLBEntries = 4,
        nMSHRs = 0,
        blockBytes = site(CacheBlockBytes))),
      icache = Some(ICacheParams(
        rowBits = site(SystemBusKey).beatBits,
        nSets = 64,
        nWays = 1,
        nTLBEntries = 4,
        blockBytes = site(CacheBlockBytes))),
      hartId = up(RocketTilesKey, site).size + up(BoomTilesKey, site).size
    )
  case MaxHartIdBits => log2Up(up(RocketTilesKey, site).size + up(BoomTilesKey, site).size + 1)
})

/**
 * Mixin to add an IceNIC
 */
class WithIceNIC(inBufFlits: Int = 1800, usePauser: Boolean = false)
    extends Config((site, here, up) => {
  case NICKey => Some(NICConfig(
    inBufFlits = inBufFlits,
    usePauser = usePauser,
    checksumOffload = true))
})

/**
 * Mixin to loopback the IceNIC
 */
class WithLoopbackNIC extends Config((site, here, up) => {
  case BuildTop => (clock: Clock, reset: Bool, p: Parameters, success: Bool) => {
    val top = up(BuildTop, site)(clock, reset, p, success)
    top.connectNicLoopback()
    top
  }
})

/**
 * Mixin to add a backing scratchpad (default size 4MB)
 */
class WithBackingScratchpad(base: BigInt = 0x80000000L, mask: BigInt = ((4 << 20) - 1)) extends Config((site, here, up) => {
  case BackingScratchpadKey => Some(BackingScratchpadParams(base, mask))
})
