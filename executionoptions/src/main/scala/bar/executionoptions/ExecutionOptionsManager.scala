// See LICENSE for license details.

package bar.executionoptions

import scopt.OptionParser

/**
  * Use this trait to define an options class that can add its private command line options to a externally
  * declared parser
  */
trait ComposableOptions

/**
  * Most of the chisel toolchain components require a topName which defines a circuit or a device under test.
  * Much of the work that is done takes place in a directory.
  * It would be simplest to require topName to be defined but in practice it is preferred to defer this.
  * For example, in chisel, by deferring this it is possible for the execute there to first elaborate the
  * circuit and then set the topName from that if it has not already been set.
  */
case class CommonOptions(topName: String = "", targetDirName: String = "test_run_dir") extends ComposableOptions

abstract class HasParser(applicationName: String) {
  final val parser: OptionParser[Unit] = new OptionParser[Unit](applicationName) {}
}

trait HasCommonOptions {
  self: ExecutionOptionsManager =>
  var commonOptions = CommonOptions()

  parser.note("common options")

  parser.opt[String]("top-name")
    .abbr("tn")
    .valueName("<top-level-circuit-name>")
    .foreach { x =>
      commonOptions = commonOptions.copy(topName = x)
    }
    .text("This options defines the top level circuit, defaults to dut when possible")

  parser.opt[String]("target-dir")
    .abbr("td").valueName("<target-directory>")
    .foreach { x =>
      commonOptions = commonOptions.copy(targetDirName = x)
    }
    .text(s"This options defines a work directory for intermediate files, default is ${commonOptions.targetDirName}")

  parser.help("help").text("prints this usage text")
}

/**
  *
  * @param applicationName  The name shown in the usage
  */
class ExecutionOptionsManager(val applicationName: String) extends HasParser(applicationName) with HasCommonOptions {

  def parse(args: Array[String]): Boolean = {
    parser.parse(args)
  }

  def showUsageAsError(): Unit = parser.showUsageAsError()

  /**
    * make sure that all levels of targetDirName exist
    *
    * @return true if directory exists
    */
  def makeTargetDir(): Boolean = {
    (new java.io.File(commonOptions.targetDirName)).mkdirs()
  }

  def targetDirName: String = commonOptions.targetDirName

  /**
    * this function sets the topName in the commonOptions.
    * It would be nicer to not need this but many chisel tools cannot determine
    * the name of the device under test until other options have been parsed.
    * Havin this function allows the code to set the TopName after it has been
    * determined
    *
    * @param newTopName  the topName to be used
    */
  def setTopName(newTopName: String): Unit = {
    commonOptions = commonOptions.copy(topName = newTopName)
  }
  def setTopNameIfNotSet(newTopName: String): Unit = {
    if(commonOptions.topName.isEmpty) {
      setTopName(newTopName)
    }
  }
  def topName: String = commonOptions.topName
  def setTargetDirName(newTargetDirName: String): Unit = {
    commonOptions = commonOptions.copy(targetDirName = newTargetDirName)
  }

  /**
    * return a file based on targetDir, topName and suffix
    * Will not add the suffix if the topName already ends with that suffix
    *
    * @param suffix suffix to add, removes . if present
    * @param fileNameOverride this will override the topName if nonEmpty, when using this targetDir is ignored
    * @return
    */
  def getBuildFileName(suffix: String, fileNameOverride: String = ""): String = {
    makeTargetDir()

    val baseName = if(fileNameOverride.nonEmpty) fileNameOverride else topName
    val directoryName = {
      if(fileNameOverride.nonEmpty) {
        ""
      }
      else if(baseName.startsWith("./") || baseName.startsWith("/")) {
        ""
      }
      else {
        if(targetDirName.endsWith("/")) targetDirName else targetDirName + "/"
      }
    }
    val normalizedSuffix = {
      val dottedSuffix = if(suffix.startsWith(".")) suffix else s".$suffix"
      if(baseName.endsWith(dottedSuffix)) "" else dottedSuffix
    }

    s"$directoryName$baseName$normalizedSuffix"
  }
}

