Development Ecosystem
===============================

Chipyard Approach
-------------------------------------------

The trend towards agile hardware design and evaluation provides an ecosystem of debugging and implementation tools, that make it easier for computer architecture researchers to develop novel concepts.
Chipyard hopes to build on this prior work in order to create a singular location to which multiple projects within the `Berkeley Architecture Research <https://bar.eecs.berkeley.edu/index.html>`__ can coexist and be used together.
Chipyard aims to be the "one-stop shop" for creating and testing your own unique System on a Chip (SoC).

Chisel/FIRRTL
-------------------------------------------

One of the tools to help create new RTL designs quickly is the `Chisel Hardware Construction Language <https://chisel-lang.org/>`__ together with the `FIRRTL <https://github.com/chipsalliance/firrtl-spec>`__ intermediate representation and its compiler, `CIRCT <https://github.com/llvm/circt>`__'s ``firtool``.
Chisel is an embedded language within Scala that provides a set of libraries to help hardware designers create highly parameterizable RTL.
FIRRTL, on the other hand, is an intermediate representation for hardware, and FIRRTL compilers such as ``firtool`` run passes over it that can do dead code elimination, circuit analysis, connectivity checks, and much more!
These two tools in combination allow quick design space exploration and development of new RTL.

RTL Generators
-------------------------------------------

Within this repository, all of the Chisel RTL is written as generators.
Generators are parametrized programs designed to generate RTL code based on configuration specifications.
Generators can be used to generate Systems-on-Chip (SoCs) using a collection of system components organized in unique generator projects.
Generators allow you to create a family of SoC designs instead of a single instance of a design!
