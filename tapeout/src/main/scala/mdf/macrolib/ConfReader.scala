package mdf.macrolib

object ConfReader {
  import scala.util.matching.Regex._

  type ConfPort = (String, Boolean) // prefix (e.g. "RW0") and true if masked

  /** Rename ports like "read" to R0, "write" to W0, and "rw" to RW0, and
    * return a count of read, write, and readwrite ports.
    */
  def renamePorts(ports: Seq[String]): (Seq[ConfPort], Int, Int, Int) = {
    var readCount = 0
    var writeCount = 0
    var readWriteCount = 0
    (
      ports.map {
        _ match {
          case "read"   => readCount += 1; (s"R${readCount - 1}", false)
          case "write"  => writeCount += 1; (s"W${writeCount - 1}", false)
          case "mwrite" => writeCount += 1; (s"W${writeCount - 1}", true)
          case "rw"     => readWriteCount += 1; (s"RW${readWriteCount - 1}", false)
          case "mrw"    => readWriteCount += 1; (s"RW${readWriteCount - 1}", true)
        }
      },
      readCount,
      writeCount,
      readWriteCount
    )
  }

  def generateFirrtlPort(port: ConfPort, width: Int, depth: Int, maskGran: Option[Int]): MacroPort = {
    val (prefix, masked) = port
    val isReadWriter = prefix.startsWith("RW")
    val isReader = prefix.startsWith("R") && !isReadWriter
    val isWriter = prefix.startsWith("W")
    val r = if (isReadWriter) "r" else ""
    val w = if (isReadWriter) "w" else ""
    MacroPort(
      address = PolarizedPort(s"${prefix}_addr", ActiveHigh),
      clock = Some(PolarizedPort(s"${prefix}_clk", PositiveEdge)),
      writeEnable = if (isReadWriter) Some(PolarizedPort(s"${prefix}_${w}mode", ActiveHigh)) else None,
      output = if (isReader || isReadWriter) Some(PolarizedPort(s"${prefix}_${w}data", ActiveHigh)) else None,
      input = if (isWriter || isReadWriter) Some(PolarizedPort(s"${prefix}_${r}data", ActiveHigh)) else None,
      maskPort = if (masked) Some(PolarizedPort(s"${prefix}_${w}mask", ActiveHigh)) else None,
      maskGran = if (masked) maskGran else None,
      width = Some(width),
      depth = Some(depth)
    )
  }

  /** Read a conf line into a SRAMMacro, but returns an error string in Left
    * instead of throwing errors if the line is malformed.
    */
  def readSingleLineSafe(line: String): Either[String, SRAMMacro] = {
    val pattern = """name ([^\s]+) depth (\d+) width (\d+) ports ([a-z,]+)\s?(?:mask_gran (\d+))?""".r
    pattern.findFirstMatchIn(line) match {
      case Some(m: Match) => {
        val name:  String = m.group(1)
        val depth: Int = (m.group(2)).toInt
        val width: Int = (m.group(3)).toInt
        val ports: Seq[String] = (m.group(4)).split(",")
        val (firrtlPorts, readPortCount, writePortCount, readWritePortCount) = renamePorts(ports)
        val familyStr =
          (if (readPortCount > 0) s"${readPortCount}r" else "") +
            (if (writePortCount > 0) s"${writePortCount}w" else "") +
            (if (readWritePortCount > 0) s"${readWritePortCount}rw" else "")
        val maskGran: Option[Int] = Option(m.group(5)).map(_.toInt)
        Right(
          SRAMMacro(
            name = name,
            width = width,
            depth = depth,
            family = familyStr,
            vt = "",
            mux = 1,
            ports = firrtlPorts.map(generateFirrtlPort(_, width, depth, maskGran)),
            extraPorts = List()
          )
        )
      }
      case _ => Left("Input line did not match conf regex")
    }
  }

  /** Read a conf line into a SRAMMacro. */
  def readSingleLine(line: String): SRAMMacro = {
    readSingleLineSafe(line).right.get
  }

  /** Read the contents of the conf file into a seq of SRAMMacro. */
  def readFromString(contents: String): Seq[SRAMMacro] = {
    // Trim, remove empty lines, then pass to readSingleLine
    contents.split("\n").map(_.trim).filter(_ != "").map(readSingleLine(_))
  }
}
