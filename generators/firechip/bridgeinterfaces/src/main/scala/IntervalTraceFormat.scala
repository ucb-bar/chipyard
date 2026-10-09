// See LICENSE for license details.
package firechip.bridgeinterfaces

/** Version 1 stream words are little endian; all padding is zero. */
object IntervalTraceFormat {
  val version = 1
  val beatBits = 512
  val bb = 1
  val pmu = 2
  val epochStart = 3
  val epochEnd = 4
  val terminateInterval = 1 << 0
  val partialStart = 1 << 1
  val partialEnd = 1 << 2
  val baseline = 1 << 3
  val finalSnapshot = 1 << 4
  val targetReset = 1 << 5
  val trap = 1 << 6
  val discontinuity = 1 << 7
  val privilegeChange = 1 << 8
  val privilegeShift = 13

  def pmuWords(counterCount: Int): Int = 5 + counterCount
  def pmuBeats(counterCount: Int): Int = (pmuWords(counterCount) + 7) / 8

  private def quote(value: String): String = "\"" + value.flatMap {
    case '"' => "\\\""
    case '\\' => "\\\\"
    case c if c < ' ' => f"\\u${c.toInt}%04x"
    case c => c.toString
  } + "\""

  // Length-prefixed fields give a stable identity independent of JSON formatting.
  def catalogId(key: IntervalTraceBridgeKey): BigInt = {
    val fields = Seq(key.source, key.traceWidths.toString, key.retirementLatency.toString) ++ key.events.flatMap(e =>
      Seq(e.id.toString, e.name, e.unit, e.description))
    val bytes = fields.map(s => s"${s.length}:$s").mkString.getBytes(java.nio.charset.StandardCharsets.UTF_8)
    BigInt(1, java.security.MessageDigest.getInstance("SHA-256").digest(bytes).take(8))
  }

  def manifestJson(key: IntervalTraceBridgeKey): String = {
    val events = key.events.map(e =>
      s"""{"id":${e.id},"name":${quote(e.name)},"unit":${quote(e.unit)},"description":${quote(e.description)}}""").mkString(",")
    s"""{"format_version":$version,"source":${quote(key.source)},"catalog_id":${quote(catalogId(key).toString)},"counter_bits":64,"retirement_latency":${key.retirementLatency},"retire_width":${key.traceWidths.retireWidth},"iaddr_width":${key.traceWidths.iaddrWidth},"pmu_beats":${pmuBeats(key.events.size)},"events":[$events]}"""
  }
}
