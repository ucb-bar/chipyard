FIRRTL
================================

`FIRRTL <https://github.com/freechipsproject/firrtl>`__ is an intermediate representation of your circuit.
It is emitted by the Chisel compiler and is used to translate Chisel source files into another representation such as Verilog.
Without going into too much detail, FIRRTL is consumed by FIRRTL compilers which passes the circuit through a series of circuit-level transformations.
An example of a FIRRTL pass (transformation) is one that optimizes out unused signals.
Once the transformations are done, a Verilog file is emitted and the build process is done.

To see how FIRRTL is transformed to Verilog in Chipyard, please visit the :ref:`firrtl-transforms` section.

For more information on FIRRTL, please visit their `website <https://chisel-lang.org/firrtl/>`__.
