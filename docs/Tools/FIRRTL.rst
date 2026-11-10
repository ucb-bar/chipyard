FIRRTL
================================

`FIRRTL <https://github.com/chipsalliance/firrtl-spec>`__ (Flexible Intermediate Representation for RTL) is an intermediate representation of your circuit.
It is emitted by the Chisel compiler and is used to translate Chisel source files into another representation such as Verilog.
Without going into too much detail, FIRRTL is consumed by a FIRRTL compiler which passes the circuit through a series of circuit-level transformations.
An example of a FIRRTL pass (transformation) is one that optimizes out unused signals.
Once the transformations are done, Verilog files are emitted and the build process is done.

The original Scala-based FIRRTL compiler (SFC) is deprecated and is no longer used by Chipyard.
Instead, Chipyard compiles FIRRTL to Verilog with ``firtool``, the MLIR-based FIRRTL compiler from the `CIRCT <https://github.com/llvm/circt>`__ project.
``firtool`` is installed by ``build-setup.sh`` (either as a precompiled binary or built from source with ``--build-circt``, see :ref:`Chipyard-Basics/Initial-Repo-Setup:Setting up the Chipyard Repo`), and a different binary can be used by setting the ``FIRTOOL_BIN`` make variable.
The ``make firrtl`` target runs Chisel elaboration to produce the ``.fir`` file (and its annotations), while the ``make run-firtool`` target (or any target that depends on the generated Verilog, such as ``make verilog``) invokes ``firtool`` on it.

For more information, see the `FIRRTL specification <https://github.com/chipsalliance/firrtl-spec>`__ and the `CIRCT documentation <https://circt.llvm.org/>`__.
