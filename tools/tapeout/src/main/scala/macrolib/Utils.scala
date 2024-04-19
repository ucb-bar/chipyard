package mdf.macrolib

import play.api.libs.json._

import java.io.FileNotFoundException
import scala.collection.mutable.ListBuffer
import scala.language.implicitConversions

object Utils {
  // Read a MDF file from a String.
  def readMDFFromString(str: String): Option[Seq[Macro]] = {
    Json.parse(str) match {
      // Make sure that the document is a list.
      case arr: JsArray => {
        val result: List[Option[Macro]] = arr.as[List[Map[String, JsValue]]].map { obj =>
          // Check the type of object.
          val objTypeStr: String = obj.get("type") match {
            case Some(x: JsString) => x.as[String]
            case _ => return None // error, no type found
          }
          objTypeStr match {
            case "filler cell" | "metal filler cell" => FillerMacroBase.parseJSON(obj)
            case "sram"                              => SRAMMacro.parseJSON(obj)
            case "sramcompiler"                      => SRAMCompiler.parseJSON(obj)
            case "io_properties"                     => IOProperties.parseJSON(obj)
            case "flipchip"                          => FlipChipMacro.parseJSON(obj)
            case _                                   => None // skip unknown macro types
          }
        }
        // Remove all the Nones and convert back to Seq[Macro]
        Some(result.filter { x => x != None }.map { x => x.get })
      }
      case _ => None
    }
  }

  // Read a MDF file from a path.
  def readMDFFromPath(path: Option[String]): Option[Seq[Macro]] = {
    path match {
      case None => None
      // Read file into string and parse
      case Some(p) =>
        try {
          Utils.readMDFFromString(scala.io.Source.fromFile(p).mkString)
        } catch {
          case f: FileNotFoundException =>
            println(s"FILE NOT FOUND $p in dir ${os.pwd}")
            throw f
        }
    }
  }

  // Write a MDF file to a String.
  def writeMDFToString(s: Seq[Macro]): String = {
    Json.prettyPrint(JsArray(s.map(_.toJSON)))
  }

  // Write a MDF file from a path.
  // Returns true upon success.
  def writeMDFToPath(path: Option[String], s: Seq[Macro]): Boolean = {
    path match {
      case None => false
      // Read file into string and parse
      case Some(p: String) => {
        import java.io._
        val pw = new PrintWriter(new File(p))
        pw.write(writeMDFToString(s))
        val error = pw.checkError
        pw.close()
        !error
      }
    }
  }

  // Write a macro file to a String.
  def writeMacroToString(s: Macro): String = {
    Json.prettyPrint(s.toJSON)
  }

  // Write a Macro file from a path.
  // Returns true upon success.
  def writeMacroToPath(path: Option[String], s: Macro): Boolean = {
    path match {
      case None => false
      // Read file into string and parse
      case Some(p: String) => {
        import java.io._
        val pw = new PrintWriter(new File(p))
        pw.write(writeMacroToString(s))
        val error = pw.checkError
        pw.close()
        !error
      }
    }
  }
}
