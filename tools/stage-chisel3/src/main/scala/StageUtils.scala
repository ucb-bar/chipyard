// See LICENSE

package chipyard.stage

import java.io.{File, FileWriter}

import org.chipsalliance.cde.config.{Config, Parameters}
import chisel3.internal.firrtl.Circuit
import freechips.rocketchip.util.{BlackBoxedROM, ROMGenerator}

trait HasChipyardStageUtils {

  def getConfig(fullConfigClassNames: Seq[String]): Config = {
    new Config(fullConfigClassNames.foldRight(Parameters.empty) { case (currentName, config) =>
      val currentConfig = try {
        Class.forName(currentName).newInstance.asInstanceOf[Config]
      } catch {
        case e: java.lang.ClassNotFoundException =>
          throw new Exception(s"""Unable to find part "$currentName" from "$fullConfigClassNames", did you misspell it or specify the wrong package path?""", e)
      }
      currentConfig ++ config
    })
  }

  def enumerateROMs(circuit: Circuit): String = {
    val res = new StringBuilder
    val configs =
      circuit.components flatMap { m =>
        m.id match {
          case rom: BlackBoxedROM => Some((rom.name, ROMGenerator.lookup(rom)))
          case _ => None
        }
      }
    configs foreach { case (name, c) =>
      res append s"name ${name} depth ${c.depth} width ${c.width}\n"
    }
    res.toString
  }

  def writeOutputFile(targetDir: String, fname: String, contents: String): File = {
    val f = new File(targetDir, fname)
    val fw = new FileWriter(f)
    fw.write(contents)
    fw.close
    f
  }

}
