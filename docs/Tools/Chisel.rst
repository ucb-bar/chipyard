Chisel
===========================

`Chisel <https://chisel-lang.org/>`__ is an open-source hardware description language embedded in Scala.
It supports advanced hardware design using highly parameterized generators and supports things such as Rocket Chip and BOOM.

After writing Chisel, there are multiple steps before the Chisel source code "turns into" Verilog.
First is the compilation step.
If Chisel is thought as a library within Scala, then these classes being built are just Scala classes which call Chisel functions.
Thus, any errors that you get in compiling the Scala/Chisel files are errors that you have violated the typing system, messed up syntax, or more.
After the compilation is complete, elaboration begins.
The Chisel generator starts elaboration using the module and configuration classes passed to it.
This is where the Chisel "library functions" are called with the parameters given and Chisel tries to construct a circuit based on the Chisel code.
If a runtime error happens here, Chisel is stating that it cannot "build" your circuit due to "violations" between your code and the Chisel "library".
However, if that passes, the output of the generator gives you an FIRRTL file and other misc collateral!
See :ref:`Tools/FIRRTL:FIRRTL` for more information on how to get a FIRRTL file to Verilog.

For an interactive tutorial on how to use Chisel and get started please visit the `Chisel Bootcamp <https://github.com/freechipsproject/chisel-bootcamp>`__.
Otherwise, for all things Chisel related including API documentation, news, etc, visit their `website <https://chisel-lang.org/>`__.
