package example

import java.io.File

case class GenerateSimConfig(
  targetDir: String = ".",
  dotFName: String = "verilator_files.f",
)

trait HasGenerateSimConfig {
  val parser = new scopt.OptionParser[GenerateSimConfig]("GenerateSimFiles") {
    head("GenerateSimFiles", "0.1")

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
  def addOption(file: File): String = {
    val fname = file.getCanonicalPath
    // add -FI flag for header files
    if (fname.takeRight(2) == ".h") {
      s"-FI ${fname}"
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
  val resources = Seq(
    // TODO(rigge): make conditional on if we are using verilator
    "/project-template/csrc/emulator.cc",
    "/csrc/SimDTM.cc",
    "/csrc/SimJTAG.cc",
    "/csrc/remote_bitbang.h",
    "/csrc/remote_bitbang.cc",
    "/csrc/verilator.h",
    "/vsrc/EICG_wrapper.v",
  )

  def writeFiles(cfg: GenerateSimConfig): Unit = {
    firrtl.FileUtils.makeDirectory(cfg.targetDir)
    val files = resources.map { writeResource(_, cfg.targetDir) }
    writeDotF(files.map(addOption), cfg)
  }

  parser.parse(args, GenerateSimConfig()) match {
    case Some(cfg) => writeFiles(cfg)
    case _ => // error message already shown
  }
}
