package example

import diplomacy.LazyModule
import rocketchip._
import testchipip._
import chisel3._
import config.Parameters
import _root_.util.{HasGeneratorUtilities, ParsedInputNames}
import java.io.File

class TestHarness(implicit val p: Parameters) extends Module {
  val io = IO(new Bundle {
    val success = Output(Bool())
  })

  def buildTop(p: Parameters): ExampleTop = LazyModule(new ExampleTop()(p))

  val dut = Module(buildTop(p).module)
  val ser = Module(new SimSerialWrapper(p(SerialInterfaceWidth)))

  val nMemChannels = p(coreplex.BankedL2Config).nMemoryChannels
  val mem = Module(LazyModule(new SimAXIMem(nMemChannels)).module)
  mem.io.axi4 <> dut.io.mem_axi4
  ser.io.serial <> dut.io.serial
  io.success := ser.io.exit
}

trait ExampleGeneratorApp extends App with HasGeneratorUtilities {
  lazy val names = ParsedInputNames(
    targetDir = args(0),
    topModuleProject = args(1),
    topModuleClass = args(2),
    configProject = args(3),
    configs = args(4))

  lazy val config = getConfig(names)
  lazy val world = config.toInstance
  lazy val params = Parameters.root(world)
  lazy val circuit = Driver.elaborate(() =>
      Class.forName(names.fullTopModuleClass)
        .getConstructor(classOf[Parameters])
        .newInstance(params)
        .asInstanceOf[Module])

  lazy val longName = names.topModuleProject + "." +
                 names.topModuleClass + "." +
                 names.configs

  def generateFirrtl =
    Driver.dumpFirrtl(circuit,
      Some(new File(names.targetDir, s"$longName.fir")))
}

object Generator extends ExampleGeneratorApp {
  generateFirrtl
}
