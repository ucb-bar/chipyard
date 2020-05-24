package utilities

import java.io.File

case class GenerateSimConfig(
  targetDir: String = ".",
  dotFName: String = "sim_files.f",
  simulator: Simulator = VerilatorSimulator,
)

sealed trait Simulator
object VerilatorSimulator extends Simulator
object VCSSimulator extends Simulator

trait HasGenerateSimConfig {
  val parser = new scopt.OptionParser[GenerateSimConfig]("GenerateSimFiles") {
    head("GenerateSimFiles", "0.1")

    opt[String]("simulator")
      .abbr("sim")
      .valueName("<simulator-name>")
      .action((x, c) => x match {
        case "verilator" => c.copy(simulator = VerilatorSimulator)
        case "vcs" => c.copy(simulator = VCSSimulator)
        case _ => throw new Exception(s"Unrecognized simulator $x")
      })
      .text("Name of simulator to generate files for (verilator, vcs)")

    opt[String]("target-dir")
      .abbr("td")
      .valueName("<target-directory>")
      .action((x, c) => c.copy(targetDir = x))
      .text("Target director to put files")

    opt[String]("dotFName")
      .abbr("df")
      .valueName("<dot-f filename>")
      .action((x, c) => c.copy(dotFName = x))
      .text("Name of generated dot-f file")
  }
}

object GenerateSimFiles extends App with HasGenerateSimConfig {
  def addOption(file: File, cfg: GenerateSimConfig): String = {
    val fname = file.getCanonicalPath
    // deal with header files
    if (fname.takeRight(2) == ".h") {
      cfg.simulator match {
        // verilator needs to explicitly include verilator.h, so use the -FI option
        case VerilatorSimulator => s"-FI ${fname}"
        // vcs pulls headers in with +incdir, doesn't have anything like verilator.h
        case VCSSimulator => ""
      }
    } else { // do nothing otherwise
      fname
    }
  }
  def writeDotF(lines: Seq[String], cfg: GenerateSimConfig): Unit = {
    writeTextToFile(lines.mkString("\n"), new File(cfg.targetDir, cfg.dotFName))
  }
  // From FIRRTL
  def safeFile[A](fileName: String)(code: => A) = try { code } catch {
    case e@ (_: java.io.FileNotFoundException | _: NullPointerException) => throw new Exception(fileName, e)
    case t: Throwable                                            => throw t
  }
  // From FIRRTL
  def writeResource(name: String, targetDir: String): File = {
    val in = getClass.getResourceAsStream(name)
    val p = java.nio.file.Paths.get(name)
    val fname = p.getFileName().toString();

    val f = new File(targetDir, fname)
    val out = new java.io.FileOutputStream(f)
    safeFile(name)(Iterator.continually(in.read).takeWhile(-1 != _).foreach(out.write))
    out.close()
    f
  }
  // From FIRRTL
  def writeTextToFile(text: String, file: File) {
    val out = new java.io.PrintWriter(file)
    out.write(text)
    out.close()
  }
  def resources(sim: Simulator): Seq[String] = Seq(
    "/testchipip/csrc/SimSerial.cc",
    "/testchipip/csrc/SimDRAM.cc",
    "/testchipip/csrc/mm.h",
    "/testchipip/csrc/mm.cc",
    "/testchipip/csrc/mm_dramsim2.h",
    "/testchipip/csrc/mm_dramsim2.cc",
    "/csrc/SimDTM.cc",
    "/csrc/SimJTAG.cc",
    "/csrc/remote_bitbang.h",
    "/csrc/remote_bitbang.cc",
    "/vsrc/EICG_wrapper.v",
  ) ++ (sim match { // simulator specific files to include
    case VerilatorSimulator => Seq(
      "/csrc/emulator.cc",
      "/csrc/verilator.h",
    )
    case VCSSimulator => Seq(
      "/vsrc/TestDriver.v",
    )
  })

  def writeBootrom(): Unit = {
    firrtl.FileUtils.makeDirectory("./bootrom/")
    writeResource("/testchipip/bootrom/bootrom.rv64.img", "./bootrom/")
    writeResource("/testchipip/bootrom/bootrom.rv32.img", "./bootrom/")
    writeResource("/bootrom/bootrom.img", "./bootrom/")
  }

  def writeFiles(cfg: GenerateSimConfig): Unit = {
    writeBootrom()
    firrtl.FileUtils.makeDirectory(cfg.targetDir)
    val files = resources(cfg.simulator).map { writeResource(_, cfg.targetDir) }
    writeDotF(files.map(addOption(_, cfg)), cfg)
  }

  parser.parse(args, GenerateSimConfig()) match {
    case Some(cfg) => writeFiles(cfg)
    case _ => // error message already shown
  }
}
