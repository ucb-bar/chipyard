package testchipip.serdes.old

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.{Parameters, Field}
import freechips.rocketchip.subsystem._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util._
import freechips.rocketchip.prci._
import testchipip.util.{ClockedIO}
import testchipip.soc.{OBUS}

// Parameters for a read-only-memory that appears over serial-TL
case class ManagerROMParams(
  address: BigInt = 0x20000,
  size: Int = 0x10000,
  contentFileName: Option[String] = None) // If unset, generates a JALR to DRAM_BASE

// Parameters for a read/write memory that appears over serial-TL
case class ManagerRAMParams(
  address: BigInt,
  size: BigInt)

// Parameters for a coherent cacheable read/write memory that appears over serial-TL
case class ManagerCOHParams(
  address: BigInt,
  size: BigInt)

// Parameters for a set of memory regions that appear over serial-TL
case class SerialTLManagerParams(
  memParams: Seq[ManagerRAMParams] = Nil,
  romParams: Seq[ManagerROMParams] = Nil,
  cohParams: Seq[ManagerCOHParams] = Nil,
  isMemoryDevice: Boolean = false,
  idBits: Int = 8,
  slaveWhere: TLBusWrapperLocation = OBUS
)

// Parameters for a TL client which may probe this system over serial-TL
case class SerialTLClientParams(
  idBits: Int = 8,
  masterWhere: TLBusWrapperLocation = FBUS,
  supportsProbe: Boolean = false
)

// The SerialTL can be configured to be bidirectional if serialTLManagerParams is set
case class SerialTLParams(
  client: Option[SerialTLClientParams] = None,
  manager: Option[SerialTLManagerParams] = None,
  phyParams: SerialParams = ExternalSyncSerialParams(),
  bundleParams: TLBundleParameters = TLSerdesser.STANDARD_TLBUNDLE_PARAMS)

case object SerialTLKey extends Field[Seq[SerialTLParams]](Nil)

trait CanHavePeripheryTLSerial { this: BaseSubsystem =>
  private val portName = "serial-tl"
  val (old_serdessers, old_serial_tls, old_serial_tl_debugs) = p(SerialTLKey).zipWithIndex.map { case (params, sid) =>

    val name = s"serial_tl_$sid"
    lazy val manager_bus = params.manager.map(m => locateTLBusWrapper(m.slaveWhere))
    lazy val client_bus = params.client.map(c => locateTLBusWrapper(c.masterWhere))
    val clientPortParams = params.client.map { c => TLMasterPortParameters.v1(
      clients = Seq(TLMasterParameters.v1(
        name = name,
        sourceId = IdRange(0, 1 << c.idBits),
        supportsProbe = if (c.supportsProbe) TransferSizes(client_bus.get.blockBytes, client_bus.get.blockBytes) else TransferSizes.none
      ))
    ) }

    val managerPortParams = params.manager.map { m =>
      val memParams = m.memParams
      val romParams = m.romParams
      val cohParams = m.cohParams
      val memDevice = if (m.isMemoryDevice) new MemoryDevice else new SimpleDevice("lbwif-readwrite", Nil)
      val romDevice = new SimpleDevice("lbwif-readonly", Nil)
      val blockBytes = manager_bus.get.blockBytes
      TLSlavePortParameters.v1(
        managers = memParams.map { memParams => TLSlaveParameters.v1(
          address            = AddressSet.misaligned(memParams.address, memParams.size),
          resources          = memDevice.reg,
          regionType         = RegionType.UNCACHED, // cacheable
          executable         = true,
          supportsGet        = TransferSizes(1, blockBytes),
          supportsPutFull    = TransferSizes(1, blockBytes),
          supportsPutPartial = TransferSizes(1, blockBytes)
        )} ++ romParams.map { romParams => TLSlaveParameters.v1(
          address            = List(AddressSet(romParams.address, romParams.size-1)),
          resources          = romDevice.reg,
          regionType         = RegionType.UNCACHED, // cacheable
          executable         = true,
          supportsGet        = TransferSizes(1, blockBytes),
          fifoId             = Some(0)
        )} ++ cohParams.map { cohParams => TLSlaveParameters.v1(
          address            = AddressSet.misaligned(cohParams.address, cohParams.size),
          regionType         = RegionType.UNCACHED, // cacheable
          executable         = true,
          supportsAcquireT   = TransferSizes(1, blockBytes),
          supportsAcquireB   = TransferSizes(1, blockBytes),
          supportsGet        = TransferSizes(1, blockBytes),
          supportsPutFull    = TransferSizes(1, blockBytes),
          supportsPutPartial = TransferSizes(1, blockBytes)
        )},
        beatBytes = manager_bus.get.beatBytes,
        endSinkId = if (cohParams.isEmpty) 0 else (1 << m.idBits),
        minLatency = 1
      )
    }

    val serial_tl_domain = LazyModule(new ClockSinkDomain(name=Some(name)))
    serial_tl_domain.clockNode := manager_bus.getOrElse(client_bus.get).fixedClockNode

    if (manager_bus.isDefined) require(manager_bus.get.dtsFrequency.isDefined,
      s"Manager bus ${manager_bus.get.busName} must provide a frequency")
    if (client_bus.isDefined) require(client_bus.get.dtsFrequency.isDefined,
      s"Client bus ${client_bus.get.busName} must provide a frequency")
    if (manager_bus.isDefined && client_bus.isDefined) {
      val managerFreq = manager_bus.get.dtsFrequency.get
      val clientFreq = client_bus.get.dtsFrequency.get
      require(managerFreq == clientFreq, s"Mismatching manager freq $managerFreq != client freq $clientFreq")
    }

    val serdesser = serial_tl_domain { LazyModule(new TLSerdesser(
      w = params.phyParams.width,
      clientPortParams = clientPortParams,
      managerPortParams = managerPortParams,
      bundleParams = params.bundleParams
    )) }
    serdesser.managerNode.foreach { managerNode =>
      manager_bus.get.coupleTo(s"port_named_${name}_out") {
        managerNode := TLSourceShrinker(1 << params.manager.get.idBits) := TLWidthWidget(manager_bus.get.beatBytes) := _
      }
    }
    serdesser.clientNode.foreach { clientNode =>
      client_bus.get.coupleFrom(s"port_named_${name}_in") { _ := TLBuffer() := clientNode }
    }


    // If we provide a clock, generate a clock domain for the outgoing clock
    val serial_tl_clock_freqMHz = params.phyParams match {
      case params: InternalSyncSerialParams => Some(params.freqMHz)
      case params: ExternalSyncSerialParams => None
      case params: SourceSyncSerialParams => Some(params.freqMHz)
    }
    val serial_tl_clock_node = serial_tl_clock_freqMHz.map { f =>
      serial_tl_domain { ClockSinkNode(Seq(ClockSinkParameters(take=Some(ClockParameters(f))))) }
    }
    serial_tl_clock_node.foreach(_ := ClockGroup()(p, ValName(s"${name}_clock")) := allClockGroupsNode)

    val inner_io = serial_tl_domain { InModuleBody {
      val inner_io = IO(params.phyParams.genIO).suggestName(name)

      inner_io match {
        case io: InternalSyncSerialIO => {
          // Outer clock comes from the clock node. Synchronize the serdesser's reset to that
          // clock to get the outer reset
          val outer_clock = serial_tl_clock_node.get.in.head._1.clock
          val outer_reset = ResetCatchAndSync(outer_clock, serdesser.module.reset.asBool)
          io.clock_out := outer_clock
          val out_async = Module(new AsyncQueue(UInt(params.phyParams.width.W), AsyncQueueParams(depth=params.phyParams.asyncQueueSz)))
          out_async.io.enq_clock := serdesser.module.clock
          out_async.io.enq_reset := serdesser.module.reset
          out_async.io.deq_clock := outer_clock
          out_async.io.deq_reset := outer_reset
          out_async.io.enq <> serdesser.module.io.ser.out
          io.out <> out_async.io.deq

          val in_async = Module(new AsyncQueue(UInt(params.phyParams.width.W), AsyncQueueParams(depth=params.phyParams.asyncQueueSz)))
          in_async.io.enq_clock := outer_clock
          in_async.io.enq_reset := outer_reset
          in_async.io.deq_clock := serdesser.module.clock
          in_async.io.deq_reset := serdesser.module.reset
          serdesser.module.io.ser.in <> in_async.io.deq
          in_async.io.enq            <> io.in
        }
        case io: ExternalSyncSerialIO => {
          // Outer clock comes from the IO. Synchronize the serdesser's reset to that
          // clock to get the outer reset
          val outer_clock = io.clock_in
          val outer_reset = ResetCatchAndSync(outer_clock, serdesser.module.reset.asBool)
          val out_async = Module(new AsyncQueue(UInt(params.phyParams.width.W), AsyncQueueParams(depth=params.phyParams.asyncQueueSz)))
          out_async.io.enq_clock := serdesser.module.clock
          out_async.io.enq_reset := serdesser.module.reset
          out_async.io.deq_clock := outer_clock
          out_async.io.deq_reset := outer_reset
          out_async.io.enq <> serdesser.module.io.ser.out
          io.out <> out_async.io.deq

          val in_async = Module(new AsyncQueue(UInt(params.phyParams.width.W), AsyncQueueParams(depth=params.phyParams.asyncQueueSz)))
          in_async.io.enq_clock := outer_clock
          in_async.io.enq_reset := outer_reset
          in_async.io.deq_clock := serdesser.module.clock
          in_async.io.deq_reset := serdesser.module.reset
          serdesser.module.io.ser.in <> in_async.io.deq
          in_async.io.enq            <> io.in
        }
        case io: SourceSyncSerialIO => {
          // 3 clock domains -
          // - serdesser's "Inner clock": synchronizes signals going to the digital logic
          // - outgoing clock: synchronizes signals going out
          // - incoming clock: synchronizes signals coming in
          val outgoing_clock = serial_tl_clock_node.get.in.head._1.clock
          val outgoing_reset = ResetCatchAndSync(outgoing_clock, serdesser.module.reset.asBool)
          val incoming_clock = io.clock_in
          val incoming_reset = ResetCatchAndSync(incoming_clock, io.reset_in.asBool)
          io.clock_out := outgoing_clock
          io.reset_out := outgoing_reset.asAsyncReset

          val out_async = Module(new AsyncQueue(UInt(params.phyParams.width.W)))
          out_async.io.enq_clock := serdesser.module.clock
          out_async.io.enq_reset := serdesser.module.reset
          out_async.io.deq_clock := outgoing_clock
          out_async.io.deq_reset := outgoing_reset
          out_async.io.enq <> serdesser.module.io.ser.out

          val out_credits = Module(new AsyncQueue(Bool(), AsyncQueueParams(depth=params.phyParams.asyncQueueSz)))
          out_credits.io.enq_clock := outgoing_clock
          out_credits.io.enq_reset := outgoing_reset
          out_credits.io.deq_clock := incoming_clock
          out_credits.io.deq_reset := incoming_reset

          // Sending data out
          out_credits.io.enq.valid := out_async.io.deq.valid
          out_credits.io.enq.bits := DontCare // Should cause most of the AsyncQueue to DCE away
          out_async.io.deq.ready := out_credits.io.enq.ready
          io.out.valid := out_async.io.deq.fire
          io.out.bits  := out_async.io.deq.bits

          // Handling credit in
          out_credits.io.deq.ready := io.credit_in

          val in_async = Module(new AsyncQueue(UInt(params.phyParams.width.W), AsyncQueueParams(depth=params.phyParams.asyncQueueSz)))
          in_async.io.enq_clock := incoming_clock
          in_async.io.enq_reset := incoming_reset
          in_async.io.deq_clock := serdesser.module.clock
          in_async.io.deq_reset := serdesser.module.reset
          serdesser.module.io.ser.in <> in_async.io.deq

          val in_credits = Module(new AsyncQueue(Bool(), AsyncQueueParams(depth=params.phyParams.asyncQueueSz)))
          in_credits.io.enq_clock := serdesser.module.clock
          in_credits.io.enq_reset := serdesser.module.reset
          in_credits.io.deq_clock := outgoing_clock
          in_credits.io.deq_reset := outgoing_reset

          // Handling data in
          in_async.io.enq.valid := io.in.valid
          in_async.io.enq.bits := io.in.bits
          when (io.in.valid) { assert(in_async.io.enq.ready, "Credited flow control broke") }

          // Sending credit out
          in_credits.io.enq.valid := in_async.io.deq.fire
          in_credits.io.enq.bits := DontCare
          when (in_async.io.deq.fire) { assert(in_credits.io.enq.ready, "Credited flow control broke") }
          in_credits.io.deq.ready := true.B
          io.credit_out := in_credits.io.deq.valid
        }
      }
      inner_io
    }}
    val outer_io = InModuleBody {
      val outer_io = IO(params.phyParams.genIO).suggestName(name)
      outer_io <> inner_io
      outer_io
    }

    val inner_debug_io = serial_tl_domain { InModuleBody {
      val inner_debug_io = IO(new SerdesDebugIO).suggestName(s"${name}_debug")
      inner_debug_io := serdesser.module.io.debug
      inner_debug_io
    }}
    val outer_debug_io = InModuleBody {
      val outer_debug_io = IO(new SerdesDebugIO).suggestName(s"${name}_debug")
      outer_debug_io := inner_debug_io
      outer_debug_io
    }
    (serdesser, outer_io, outer_debug_io)
  }.unzip3
}
