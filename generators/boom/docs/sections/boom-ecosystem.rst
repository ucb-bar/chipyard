The BOOM Development Ecosystem
==============================

The BOOM Repository
------------------------------------

The BOOM repository holds the source code to the BOOM core; it is not a full processor and thus is **NOT A SELF-RUNNING** repository.
To instantiate a BOOM core, you must use a top-level project to integrate the core into an SoC. For this purpose you can use
the `Chipyard Template <https://github.com/ucb-bar/chipyard>`__.

The BOOM core source code can be found in ``src/main/scala``.

The core code structure is shown below:

* ``src/main/scala/``

  * ``bpu/`` - branch predictor unit
  * ``common/`` - configs fragments, constants, bundles, tile definitions
  * ``exu/`` - execute/core unit
  * ``ifu/`` - instruction fetch unit
  * ``lsu/`` - load/store/memory unit
  * ``util/`` - utilities

Scala, Chisel, Generators, Configs, Oh My!
------------------------------------------

Working with BOOM has a large learning curve for those people new to *Chisel* and the BOOM ecosystem.
To be productive, it takes time to learn about the micro-architecture, *Rocket chip* components, *Chisel* (maybe *Firrtl*), *Scala*, and the build system.
Luckily, the micro-architecture is detailed in this documentation and some of the other topics (*Chisel*, *Firrtl*, *Scala*) are discussed in their respective websites.
Instead of focusing solely on those topics, this section hopes to show how they all fit together by giving a high level of the entire build process.
Put in more specific terms: How do you get from *Scala*/*Chisel* to Verilog? [1]_

Recap on Coding in Scala/Chisel
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

When making changes to BOOM, you are working in *Scala*/*Chisel* code.
*Chisel* is the language embedded inside of *Scala* to create RTL.
One way to view *Scala*/*Chisel* is that *Chisel* is a set of libraries that are used in *Scala* that help hardware designers create highly parameterizable RTL.
For example, if you want to make a hardware queue, you would use something like *Chisel*'s ``chisel3.util.Queue`` to make a queue.
However, if you want to change the amount of entries of the queue based on some variable, that would be *Scala* code.
Another way to think of the distinction between the two languages is that *Chisel* code will make a circuit in hardware while *Scala* code will change the parameters of the circuit that *Chisel* will create.
A simple example is shown below in :numref:`scala-chisel-example`.

.. _scala-chisel-example:
.. code-block:: scala
    :caption: Scala and Chisel Code

    var Q_DEPTH = 1 // Scala variable
    if (WANT_HUGE_QUEUE == true) {
        Q_DEPTH = 123456789 // Big number!
    }
    else {
        Q_DEPTH = 1 // Small number.
    }

    // Create a queue through Chisel with the parameter specified by a Scala variable
    val queue = Module(new chisel3.util.Queue(HardwareDataType, Q_DEPTH))

Generating a BOOM System
~~~~~~~~~~~~~~~~~~~~~~~~

The word "generator" used in many *Chisel* projects refers to a program that takes in a *Chisel Module* and a *Configuration* and returns a circuit based on those parameters.
The generator for BOOM and Rocket SoC's can be found in Chipyard under the ``Generator.scala`` file.
The *Chisel Module* used in the generator is normally the top-level *Chisel Module* class that you (the developer) want to make a circuit of.
The *Configuration* is just a set of *Scala* variables used to configure the parameters of the passed in *Chisel Module*.
In BOOM's case, the top-level *Module* would be something like the BoomRocketSystem found in ``src/main/scala/system/BoomRocketSystem.scala`` and a *Configuration* like MediumBoomConfig found in ``src/main/scala/common/configs.scala``. [2]_
In this case, the parameters specified in MediumBoomConfig would set the necessary *Scala* variables needed throughout the ExampleBoomSystem *Module*.
Once the *Module* and *Configuration* is passed into the generator, they will be combined to form a piece of RTL representing the circuit given by the *Module* parameterized by the *Configuration*.

Compilation and Elaboration
~~~~~~~~~~~~~~~~~~~~~~~~~~~

Since the generator is just a *Scala* program, all *Scala*/*Chisel* sources must be built.
This is the *compilation* step.
If *Chisel* is thought as a library within *Scala*, then these classes being built are just *Scala* classes which call *Chisel* functions.
Thus, any errors that you get in compiling the *Scala*/*Chisel* files are errors that you have violated the typing system, messed up syntax, or more.
After the *compilation* is complete, *elaboration* begins.
The generator starts *elaboration* using the *Module* and *Configuration* passed to it.
This is where the *Chisel* "library functions" are called with the parameters given and *Chisel* tries to construct a circuit based on the *Chisel* code.
If a runtime error happens here, *Chisel* is stating that it cannot "build" your circuit due to "violations" between your code and the Chisel "library".
However, if that passes, the output of the generator gives you an RTL file!

Quickly on Firrtl
~~~~~~~~~~~~~~~~~

Up until this point, I have been saying that your generator gives you a RTL file.
However... this is not true.
Instead the generator emits `Firrtl <https://github.com/freechipsproject/firrtl>`__, an intermediate representation of your circuit.
Without going into too much detail, this *Firrtl* is consumed by a *Firrtl* compiler (another *Scala* program) which passes the circuit through a series of circuit-level transformations.
An example of a *Firrtl* pass (transformation) is one that optimizes out unused signals.
Once the transformations are done, a Verilog file is emitted and the build process is done!

Big Picture
~~~~~~~~~~~

Now that the flow of ecosystem has been briefly explained here is a quick recap.

1. You write code in *Scala* + *Chisel* (where *Chisel* can be seen as a library that *Scala* uses)
2. You compile the *Scala* + *Chisel* into classes to be used by the generator
3. Deal with compile errors (related to syntax, type system violations, or more)
4. You run the generator with the *Module* and *Configuration* for your circuit to get the *Firrtl* output file
5. Deal with runtime errors (*Chisel* elaboration errors, which may occur from violating Chisel's expectations)
6. You run the *Firrtl* compiler on the output *Firrtl* file to get a Verilog output file
7. Deal with runtime errors (*Firrtl* compile errors, which occur from compiler passes that perform checks e.g. for uninitialized wires)
8. Done. A Verilog file was created!!!

More Resources
--------------

If you would like more detail on top-level integration, how accelerators work in the Rocket Chip system, and much more please visit the
`Chipyard Documentation <https://chipyard.readthedocs.io/en/latest/>`__.


.. [1] This section describes the current build process that is used in `Chipyard <https://github.com/ucb-bar/chipyard>`__.

.. [2] This is not exactly true since to be able to run BOOM in simulations we wrap the BoomRocketSystem in a TestHarness found in Chipyard.
