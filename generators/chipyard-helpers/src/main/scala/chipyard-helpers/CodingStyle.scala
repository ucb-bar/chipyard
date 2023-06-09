class ExampleModule extends Module {
  // val chisel stuff should be in snake case
  val temp_wire = WireDefault(...)
  val my_cool_reg = RegInit(...)

  // scala global defaults should be all caps
  val DEFAULT_VALUE = 20

  // other scala vars should be in camelcase
  val myOtherVar = 20

  // scala functions that are associated with the module should be put in an obj
  // notice how the function is also in camel case since it gives a scala value out
  def myFoo(intBits: Int, chisel_val: UInt): Int = {

  }

  // notice how the function is also in snake case since it gives a chisel value out
  def my_bar(intBits: Int, intBits2: Int): UInt = {

  }
}

class ModuleTemplate {
  // override this function to place all ios in a known location
  // must be scaladoc commented
  def ios: ??? = ???

  // override this function to place all chisel code in a known location
  // must be scaladoc commented
  def implementation: ??? = ???
}

class ChiselModuleStart extends ModuleTemplate {
  def multiplyScalaNumbers(inputNumber0: Int, inputNumber1: Int): Int = {
    inputNumber0 * inputNumber1
  }

  def add_two_chisel_uints(in_0: UInt, in_1: UInt): UInt = {
    in_0 + in_1
  }

  override def ios: ??? = {
    val io = IO(new Bundle {
      val my_io_here = Input(UInt())
      val my_io_again_here = Output(UInt())
    })
  }

  override def implementation: ??? = {
    io.my_io_again_here := add_two_chisel_uints(io.my_io_here, multiplyScalaNumbers(10, 2).U)
  }
}
