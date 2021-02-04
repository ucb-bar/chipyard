package barstools.tapeout.transforms

object LowerName {
  def apply(s: String): String = s.replace(".", "_").replace("[", "_").replace("]", "")
}
