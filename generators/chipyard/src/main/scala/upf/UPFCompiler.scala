// See LICENSE for license details
package chipyard.upf

case class UPFOptions(
  topMod: String = "",
)

object UPFCompiler extends App {

  val opts = (new scopt.OptionParser[UPFOptions]("upfCompiler") {

    opt[String]('t', "top-module").
      required().
      valueName("<top module name>").
      action((x, c) => c.copy(topMod = x)).
      text("top module name")

  }).parse(args, UPFOptions()).getOrElse {
    throw new Exception("Error parsing options!")
  }

  //ChipTopUPF.default()

}

