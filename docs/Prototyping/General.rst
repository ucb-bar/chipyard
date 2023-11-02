General Setup and Usage
==============================

Sources
-------

All FPGA prototyping-related collateral and sources are located in the ``fpga`` top-level Chipyard directory.
This includes the ``fpga-shells`` submodule and the ``src`` directory that hold both Scala, TCL and other collateral.

Generating a Bitstream
----------------------

Generating a bitstream for any FPGA target using Vivado is similar to building RTL for a software RTL simulation.
Similar to a software RTL simulation (:ref:`Simulation/Software-RTL-Simulation:Simulating A Custom Project`), you can run the following command in the ``fpga`` directory to build a bitstream using Vivado:

.. code-block:: shell

    make SBT_PROJECT=... MODEL=... VLOG_MODEL=... MODEL_PACKAGE=... CONFIG=... CONFIG_PACKAGE=... GENERATOR_PACKAGE=... TB=... TOP=... BOARD=... FPGA_BRAND=... bitstream

    # or

    make SUB_PROJECT=<sub_project> bitstream

The ``SUB_PROJECT`` make variable is a way to meta make variable that sets all of the other make variables to a specific default.
For example:

.. code-block:: shell

    make SUB_PROJECT=vcu118 bitstream

    # converts to

    make SBT_PROJECT=fpga_platforms MODEL=VCU118FPGATestHarness VLOG_MODEL=VCU118FPGATestHarness MODEL_PACKAGE=chipyard.fpga.vcu118 CONFIG=RocketVCU118Config CONFIG_PACKAGE=chipyard.fpga.vcu118 GENERATOR_PACKAGE=chipyard TB=none TOP=ChipTop BOARD=vcu118 FPGA_BRAND=... bitstream

Some ``SUB_PROJECT`` defaults are already defined for use, including ``vcu118`` and ``arty``.
These default ``SUB_PROJECT``'s setup the necessary test harnesses, packages, and more for the Chipyard make system.
Like a software RTL simulation make invocation, all of the make variables can be overridden with user specific values (ex. include the ``SUB_PROJECT`` with a ``CONFIG`` and ``CONFIG_PACKAGE`` override).
In most cases, you will just need to run a command with a ``SUB_PROJECT`` and an overridden ``CONFIG`` to point to.
For example, building the BOOM configuration on the VCU118:

.. code-block:: shell

    make SUB_PROJECT=vcu118 CONFIG=BoomVCU118Config bitstream

That command will build the RTL and generate a bitstream using Vivado.
The generated bitstream will be located in your designs specific build folder (``generated-src/<LONG_NAME>/obj``).
However, like a software RTL simulation, you can also run the intermediate make steps to just generate Verilog or FIRRTL.

Debugging with ILAs on Supported FPGAs
--------------------------------------

ILA (integrated logic analyzers) can be added to certain designs for debugging relevant signals.
First, open up the post synthesis checkpoint located in the build directory for your design in Vivado (it should be labeled ``post_synth.dcp``).
Then using Vivado, add ILAs (and other debugging tools) for your design (search online for more information on how to add an ILA).
This can be done by modifying the post synthesis checkpoint, saving it, and running ``make ... debug-bitstream``.
This will create a new bitstream called ``top.bit`` in a folder named ``generated-src/<LONG_NAME>/debug_obj/``.
For example, running the bitstream build for an added ILA for a BOOM config.:

.. code-block:: shell

    make SUB_PROJECT=vcu118 CONFIG=BoomVCU118Config debug-bitstream

.. IMPORTANT:: For more extensive debugging tools for FPGA simulations including printf synthesis, assert synthesis, instruction traces, ILAs, out-of-band profiling, co-simulation, and more, please refer to the :ref:`Simulation/FPGA-Accelerated-Simulation:FireSim` platform.
