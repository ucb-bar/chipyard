package barstools.tapeout.transforms

import net.jcazevedo.moultingyaml._
import java.io.File

class YamlFileReader(resource: String) {
  def parse[A](file: String = "")(implicit reader: YamlReader[A]) : Seq[A] = {
    // If the user doesn't provide a Yaml file name, use defaults
    val yamlString = file match {
      case f if f.isEmpty => 
        // Use example config if no file is provided
        val stream = getClass.getResourceAsStream(resource)
        io.Source.fromInputStream(stream).mkString
      case f if new File(f).exists => 
        scala.io.Source.fromFile(f).getLines.mkString("\n")
      case _ => 
        throw new Exception("No valid Yaml file found!")
    }
    yamlString.parseYamls.map(x => reader.read(x))
  }
}