package tapeout.transforms.utils

object LowerName {
  def apply(s: String): String = s.replace(".", "_").replace("[", "_").replace("]", "")
}
