package chipyard

import chisel3._
import chisel3.util._
import chisel3.experimental.{Analog, BaseModule, DataMirror, Direction}
import freechips.rocketchip.tile._
import org.chipsalliance.cde.config._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.rocket._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.util._
import freechips.rocketchip.prci._
import freechips.rocketchip.devices.debug._
import freechips.rocketchip.jtag.{JTAGIO}
import testchipip.{SerialTLKey, SerialAdapter, UARTAdapter, SimDRAM}
import chipyard.iobinders._
import chipyard.clocking._
import barstools.iocell.chisel._
import chipyard.{BuildTop}


case class DummyTileAttachParams(
  tileParams: DummyTileParams,
  crossingParams: RocketCrossingParams
) extends CanAttachTile {
  type TileType = DummyTile
// val lookup = PriorityMuxHartIdFromSeq(Seq(tileParams))
// val crossingParams = RocketCrossingParams()
}

case class DummyTileParams(
  core: RocketCoreParams = RocketCoreParams(),
  icache: Option[ICacheParams] = Some(ICacheParams()),
  dcache: Option[DCacheParams] = Some(DCacheParams()),
  btb: Option[BTBParams] = Some(BTBParams()),
  dataScratchpadBytes: Int = 0,
  name: Option[String] = Some("tile"),
  hartId: Int = 0,
  beuAddr: Option[BigInt] = None,
  blockerCtrlAddr: Option[BigInt] = None,
  clockSinkParams: ClockSinkParameters = ClockSinkParameters(),
  boundaryBuffers: Boolean = false // if synthesized with hierarchical PnR, cut feed-throughs?
) extends InstantiableTileParams[DummyTile] 
{
  def instantiate(crossing: TileCrossingParamsLike, lookup: LookupByHartIdImpl)(implicit p: Parameters): DummyTile = {
    new DummyTile(this, crossing, lookup)
  }
}

class TileNodeWrapperModule(dummyParams: DummyTileParams, xBytes: Int, masterPortBeatBytes: Int)(implicit p: Parameters) extends LazyModule {
  val placeholderMasterNode = TLClientNode(Seq(TLMasterPortParameters.v1(Seq(TLClientParameters(
    name = "placeholder-master-node",
    sourceId = IdRange(0, 31),
    supportsProbe = TransferSizes(64, 64)
  )))))


  override lazy val module = new TileNodeWrapperModuleImp(this)

  val masterPunchThroughIO = InModuleBody { placeholderMasterNode.makeIOs() }
}

class TileNodeWrapperModuleImp(outer: TileNodeWrapperModule) extends LazyModuleImp(outer) {
  dontTouch(outer.masterPunchThroughIO.head)
}



class DummyTile (val dummyParams: DummyTileParams,
                 crossing: ClockCrossingType,
                 lookup: LookupByHartIdImpl,
                 q: Parameters)
  extends BaseTile(dummyParams, crossing, lookup, q)
  with SinksExternalInterrupts
  with SourcesExternalNotifications
{

  def this(params: DummyTileParams, crossing: TileCrossingParamsLike, lookup: LookupByHartIdImpl)(implicit p: Parameters) =
    this(params, crossing.crossingType, lookup, p)

  val intOutwardNode = IntIdentityNode()
  val slaveNode = TLIdentityNode()
  val masterNode = visibilityNode

  val nodeWrapper = LazyModule(new TileNodeWrapperModule(dummyParams, xBytes, masterPortBeatBytes))

//  intOutwardNode := nodeWrapper.bus_error_unit_intNode

  tlOtherMastersNode := nodeWrapper.placeholderMasterNode
  masterNode :=* tlOtherMastersNode
  DisableMonitors { implicit p => tlSlaveXbar.node :*= slaveNode }

  // Required entry of CPU device in the device tree for interrupt purpose
  val cpuDevice: SimpleDevice = new SimpleDevice("cpu", Seq("ucb-bar,dummy", "riscv")) {
    override def parent = Some(ResourceAnchors.cpus)

    override def describe(resources: ResourceBindings): Description = {
      val Description(name, mapping) = super.describe(resources)
      Description(name, mapping ++
        cpuProperties ++
        nextLevelCacheProperty ++
        tileProperties)
    }
  }

  ResourceBinding {
    Resource(cpuDevice, "reg").bind(ResourceAddress(hartId))
  }

  override lazy val module = new DummyTileModuleImp(outer = this)
}

class DummyTileModuleImp(outer: DummyTile) extends BaseTileModuleImp(outer)
{
  // TODO : instantiate bridges here

  val int_bundle = Wire(new TileInterrupts)
  outer.decodeCoreInterrupts(int_bundle)
  dontTouch(outer.intSinkNode.in(0)._1)
  println(outer.intSinkNode.in(0))
  // TODO : bridge_emulator_blackbox.io.lin := int_bundle.???

  val bridge_emulator_blackbox = Module(new BridgeEmulatorBlackBox)
  bridge_emulator_blackbox.io.clock := clock
  bridge_emulator_blackbox.io.reset := reset.asBool

  val master_a = outer.nodeWrapper.masterPunchThroughIO.head.a
  master_a.valid := bridge_emulator_blackbox.io.masterPunchThroughIO_0_a_valid
  master_a.bits.opcode := bridge_emulator_blackbox.io.masterPunchThroughIO_0_a_bits_opcode
  master_a.bits.param := bridge_emulator_blackbox.io.masterPunchThroughIO_0_a_bits_param
  master_a.bits.size := bridge_emulator_blackbox.io.masterPunchThroughIO_0_a_bits_size
  master_a.bits.source := bridge_emulator_blackbox.io.masterPunchThroughIO_0_a_bits_source
  master_a.bits.address := bridge_emulator_blackbox.io.masterPunchThroughIO_0_a_bits_address
  master_a.bits.mask := bridge_emulator_blackbox.io.masterPunchThroughIO_0_a_bits_mask
  master_a.bits.data := bridge_emulator_blackbox.io.masterPunchThroughIO_0_a_bits_data
  master_a.bits.corrupt := bridge_emulator_blackbox.io.masterPunchThroughIO_0_a_bits_corrupt

  val master_d = outer.nodeWrapper.masterPunchThroughIO.head.d
  master_d.ready := bridge_emulator_blackbox.io.masterPunchThroughIO_0_d_ready

  bridge_emulator_blackbox.io.masterPunchThroughIO_0_a_ready := master_a.ready
  bridge_emulator_blackbox.io.masterPunchThroughIO_0_d_valid := master_d.valid
  bridge_emulator_blackbox.io.masterPunchThroughIO_0_d_bits_opcode := master_d.bits.opcode
  bridge_emulator_blackbox.io.masterPunchThroughIO_0_d_bits_param := master_d.bits.param
  bridge_emulator_blackbox.io.masterPunchThroughIO_0_d_bits_size := master_d.bits.size
  bridge_emulator_blackbox.io.masterPunchThroughIO_0_d_bits_source := master_d.bits.source
  bridge_emulator_blackbox.io.masterPunchThroughIO_0_d_bits_sink := master_d.bits.sink
  bridge_emulator_blackbox.io.masterPunchThroughIO_0_d_bits_denied := master_d.bits.denied
  bridge_emulator_blackbox.io.masterPunchThroughIO_0_d_bits_data := master_d.bits.data
  bridge_emulator_blackbox.io.masterPunchThroughIO_0_d_bits_corrupt := master_d.bits.corrupt


  bridge_emulator_blackbox.io.hartid := outer.hartIdSinkNode.bundle

  val (wfi, _) = outer.wfiNode.out(0)
  wfi(0) := RegNext(bridge_emulator_blackbox.io.wfi)

  int_bundle.debug := bridge_emulator_blackbox.io.debug
  int_bundle.mtip  := bridge_emulator_blackbox.io.mtip
  int_bundle.msip  := bridge_emulator_blackbox.io.msip
  int_bundle.meip  := bridge_emulator_blackbox.io.meip
  int_bundle.seip.get  := bridge_emulator_blackbox.io.seip // HACK : Assume that seip is defined in the default config
  dontTouch(int_bundle)
}


class BridgeEmulatorBlackBox extends BlackBox with HasBlackBoxResource {
  val io = IO(new Bundle {
    val clock = Input(Clock())
    val reset = Input(Bool())
    val masterPunchThroughIO_0_a_valid = Output(Bool())
    val masterPunchThroughIO_0_a_bits_opcode = Output(UInt(3.W))
    val masterPunchThroughIO_0_a_bits_param = Output(UInt(3.W))
    val masterPunchThroughIO_0_a_bits_size = Output(UInt(4.W))
    val masterPunchThroughIO_0_a_bits_source = Output(UInt(2.W))
    val masterPunchThroughIO_0_a_bits_address = Output(UInt(32.W))
    val masterPunchThroughIO_0_a_bits_mask = Output(UInt(8.W))
    val masterPunchThroughIO_0_a_bits_data = Output(UInt(64.W))
    val masterPunchThroughIO_0_a_bits_corrupt = Output(Bool())
    val masterPunchThroughIO_0_d_ready = Output(Bool())
    val beuIntSlavePunchThroughIO_0_0 = Output(Bool())
    val masterPunchThroughIO_0_a_ready = Input(Bool())
    val masterPunchThroughIO_0_d_valid = Input(Bool())
    val masterPunchThroughIO_0_d_bits_opcode = Input(UInt(3.W))
    val masterPunchThroughIO_0_d_bits_param = Input(UInt(2.W))
    val masterPunchThroughIO_0_d_bits_size = Input(UInt(4.W))
    val masterPunchThroughIO_0_d_bits_source = Input(UInt(2.W))
    val masterPunchThroughIO_0_d_bits_sink = Input(UInt(3.W))
    val masterPunchThroughIO_0_d_bits_denied = Input(Bool())
    val masterPunchThroughIO_0_d_bits_data = Input(UInt(64.W))
    val masterPunchThroughIO_0_d_bits_corrupt = Input(Bool())
    val hartid = Input(UInt(2.W))
    val wfi = Output(Bool())
    val debug = Output(Bool())
    val mtip = Output(Bool())
    val msip = Output(Bool())
    val meip = Output(Bool())
    val seip = Output(Bool())
  })

  addResource("/vsrc/BridgeEmulatorBlackBox.v")
}




///////////////////////////////////////////////////////////////////////////////



class DummyChipTop(implicit p: Parameters) extends LazyModule {
  override lazy val desiredName = "DummyChipTop"
  val system = LazyModule(p(BuildSystem)(p)).suggestName("system").asInstanceOf[DigitalTop]

  //========================
  // Diplomatic clock stuff
  //========================
  val implicitClockSinkNode = ClockSinkNode(Seq(ClockSinkParameters(name = Some("implicit_clock"))))
  system.connectImplicitClockSinkNode(implicitClockSinkNode)

  val tlbus = system.locateTLBusWrapper(system.prciParams.slaveWhere)
  val baseAddress = system.prciParams.baseAddress
  val clockDivider  = system.prci_ctrl_domain { LazyModule(new TLClockDivider (baseAddress + 0x20000, tlbus.beatBytes)) }
  val clockSelector = system.prci_ctrl_domain { LazyModule(new TLClockSelector(baseAddress + 0x30000, tlbus.beatBytes)) }
  val pllCtrl       = system.prci_ctrl_domain { LazyModule(new FakePLLCtrl    (baseAddress + 0x40000, tlbus.beatBytes)) }

  tlbus.toVariableWidthSlave(Some("clock-div-ctrl")) { clockDivider.tlNode := TLBuffer() }
  tlbus.toVariableWidthSlave(Some("clock-sel-ctrl")) { clockSelector.tlNode := TLBuffer() }
  tlbus.toVariableWidthSlave(Some("pll-ctrl")) { pllCtrl.tlNode := TLBuffer() }

  system.allClockGroupsNode := clockDivider.clockNode := clockSelector.clockNode

  // Connect all other requested clocks
  val slowClockSource = ClockSourceNode(Seq(ClockSourceParameters()))
  val pllClockSource = ClockSourceNode(Seq(ClockSourceParameters()))

  // The order of the connections to clockSelector.clockNode configures the inputs
  // of the clockSelector's clockMux. Default to using the slowClockSource,
  // software should enable the PLL, then switch to the pllClockSource
  clockSelector.clockNode := slowClockSource
  clockSelector.clockNode := pllClockSource

  val pllCtrlSink = BundleBridgeSink[FakePLLCtrlBundle]()
  pllCtrlSink := pllCtrl.ctrlNode

  val debugClockSinkNode = ClockSinkNode(Seq(ClockSinkParameters()))
  debugClockSinkNode := system.locateTLBusWrapper(p(ExportDebug).slaveWhere).fixedClockNode
  def debugClockBundle = debugClockSinkNode.in.head._1

  override lazy val module = new DummyChipTopImpl(this)
}

class DummyChipTopImpl(outer: DummyChipTop) extends LazyRawModuleImp(outer) {
  //=========================
  // Clock/reset
  //=========================
  val implicit_clock = outer.implicitClockSinkNode.in.head._1.clock
  val implicit_reset = outer.implicitClockSinkNode.in.head._1.reset
  outer.system.module match { case l: LazyModuleImp => {
    l.clock := implicit_clock
    l.reset := implicit_reset
  }}

  val clock_wire = Wire(Input(new ClockWithFreq(80)))
  val reset_wire = Wire(Input(AsyncReset()))
  val (clock_pad, clockIOCell) = IOCell.generateIOFromSignal(clock_wire, "clock", p(IOCellKey))
  val (reset_pad, resetIOCell) = IOCell.generateIOFromSignal(reset_wire, "reset", p(IOCellKey))

  outer.slowClockSource.out.unzip._1.map { o =>
    o.clock := clock_wire.clock
    o.reset := reset_wire
  }

  // For a real chip you should replace this ClockSourceAtFreqFromPlusArg
  // with a blackbox of whatever PLL is being integrated
  val fake_pll = Module(new ClockSourceAtFreqFromPlusArg("pll_freq_mhz"))
  fake_pll.io.power := outer.pllCtrlSink.in(0)._1.power
  fake_pll.io.gate := outer.pllCtrlSink.in(0)._1.gate

  outer.pllClockSource.out.unzip._1.map { o =>
    o.clock := fake_pll.io.clk
    o.reset := reset_wire
  }

  //=========================
  // Custom Boot
  //=========================
  val (custom_boot_pad, customBootIOCell) = IOCell.generateIOFromSignal(outer.system.custom_boot_pin.get.getWrappedValue, "custom_boot", p(IOCellKey))

  //=========================
  // Serialized TileLink
  //=========================
  val (serial_tl_pad, serialTLIOCells) = IOCell.generateIOFromSignal(outer.system.serial_tl.get.getWrappedValue, "serial_tl", p(IOCellKey))

  //=========================
  // JTAG/Debug
  //=========================
  val debug = outer.system.debug.get
  // We never use the PSDIO, so tie it off on-chip
  outer.system.psd.psd.foreach { _ <> 0.U.asTypeOf(new PSDTestMode) }
  outer.system.resetctrl.map { rcio => rcio.hartIsInReset.map { _ := false.B } }

  // Tie off extTrigger
  debug.extTrigger.foreach { t =>
    t.in.req := false.B
    t.out.ack := t.out.req
  }
  // Tie off disableDebug
  debug.disableDebug.foreach { d => d := false.B }
  // Drive JTAG on-chip IOs
  debug.systemjtag.map { j =>
    j.reset := ResetCatchAndSync(j.jtag.TCK, outer.debugClockBundle.reset.asBool)
    j.mfr_id := p(JtagDTMKey).idcodeManufId.U(11.W)
    j.part_number := p(JtagDTMKey).idcodePartNum.U(16.W)
    j.version := p(JtagDTMKey).idcodeVersion.U(4.W)
  }

  Debug.connectDebugClockAndReset(Some(debug), outer.debugClockBundle.clock)

  // Add IOCells for the DMI/JTAG/APB ports
  require(!debug.clockeddmi.isDefined)
  require(!debug.apb.isDefined)
  val (jtag_pad, jtagIOCells) = debug.systemjtag.map { j =>
    val jtag_wire = Wire(new JTAGChipIO)
    j.jtag.TCK := jtag_wire.TCK
    j.jtag.TMS := jtag_wire.TMS
    j.jtag.TDI := jtag_wire.TDI
    jtag_wire.TDO := j.jtag.TDO.data
    IOCell.generateIOFromSignal(jtag_wire, "jtag", p(IOCellKey), abstractResetAsAsync = true)
  }.get

  //==========================
  // UART
  //==========================
  require(outer.system.uarts.size == 1)
  val (uart_pad, uartIOCells) = IOCell.generateIOFromSignal(outer.system.module.uart.head, "uart_0", p(IOCellKey))
}

class DummyTileTestHarness(implicit p: Parameters) extends Module {
  val io = IO(new Bundle{
    val success = Output(Bool())
  })
  println("Elaborating DummyTileTestHarness")

  io.success := false.B

  val lazyDut = LazyModule(new DummyChipTop).suggestName("chiptop")
  val dut =  Module(lazyDut.module)

  // Clock
  val clock_source = Module(new ClockSourceAtFreqFromPlusArg("slow_clk_freq_mhz"))
  clock_source.io.power := true.B
  clock_source.io.gate := false.B
  dut.clock_pad.clock := clock_source.io.clk

  // Reset
  dut.reset_pad := reset.asAsyncReset

  // Custom boot
  dut.custom_boot_pad := PlusArg("custom_boot_pin", width=1)

  // Serialized TL
  val sVal = p(SerialTLKey).get
  require(sVal.axiMemOverSerialTLParams.isDefined)
  require(sVal.isMemoryDevice)
  val axiDomainParams = sVal.axiMemOverSerialTLParams.get
  val memFreq = axiDomainParams.getMemFrequency(lazyDut.system)

  withClockAndReset(clock, reset) {
    val memOverSerialTLClockBundle = Wire(new ClockBundle(ClockBundleParameters()))
    memOverSerialTLClockBundle.clock := clock
    memOverSerialTLClockBundle.reset := reset
    val serial_bits = SerialAdapter.asyncQueue(dut.serial_tl_pad, clock, reset)
    val harnessMultiClockAXIRAM = SerialAdapter.connectHarnessMultiClockAXIRAM(
      lazyDut.system.serdesser.get,
      serial_bits,
      memOverSerialTLClockBundle,
      reset)
    io.success := SerialAdapter.connectSimSerial(harnessMultiClockAXIRAM.module.io.tsi_ser, clock, reset)

    // connect SimDRAM from the AXI port coming from the harness multi clock axi ram
    (harnessMultiClockAXIRAM.mem_axi4 zip harnessMultiClockAXIRAM.memNode.edges.in).map { case (axi_port, edge) =>
      val memSize = sVal.memParams.size
      val lineSize = p(CacheBlockBytes)
      val mem = Module(new SimDRAM(memSize, lineSize, BigInt(memFreq.toLong), edge.bundle)).suggestName("simdram")
      mem.io.axi <> axi_port.bits
      mem.io.clock := axi_port.clock
      mem.io.reset := axi_port.reset
    }
  }

  // JTAG
  val jtag_wire = Wire(new JTAGIO)
  jtag_wire.TDO.data := dut.jtag_pad.TDO
  jtag_wire.TDO.driven := true.B
  dut.jtag_pad.TCK := jtag_wire.TCK
  dut.jtag_pad.TMS := jtag_wire.TMS
  dut.jtag_pad.TDI := jtag_wire.TDI
  val dtm_success = WireInit(false.B)
  val jtag = Module(new SimJTAG(tickDelay=3)).connect(jtag_wire, clock, reset.asBool, ~(reset.asBool), dtm_success)

  // UART
  UARTAdapter.connect(Seq(dut.uart_pad))
}


class WithDummyTile(n: Int = 1, tileParams: DummyTileParams = DummyTileParams(),
  overrideIdOffset: Option[Int] = None) extends Config((site, here, up) => {
  case TilesLocated(InSubsystem) => {
    val prev = up(TilesLocated(InSubsystem), site)
    val idOffset = overrideIdOffset.getOrElse(prev.size)
    (0 until n).map { i =>
      DummyTileAttachParams(
        tileParams = tileParams.copy(
          hartId = i + idOffset
        ),
        crossingParams = RocketCrossingParams()
      )
    } ++ prev
  }
})

class DummyTileConfig extends Config(
  new chipyard.WithDummyTile ++
  new chipyard.config.WithSerialTLBackingMemory ++
  new chipyard.config.AbstractConfig
)
