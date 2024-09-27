.. _incorporating-hls:

Incorporating HLS
============================

High Level Synthesis (HLS) is a method for iterating quickly on 
different hardware algorithms that automatically generates an RTL 
circuit to match a specification in a high level language like C.

Here, we will integrate an HLS-generated accelerator that computes 
the Great Common Denominator (GCD) of two integers. This tutorial 
builds on the sections :ref:`mmio-accelerators` and 
:ref:`incorporating-verilog-blocks`. 

Adding an HLS project
---------------------------------------

In this tutorial, we use Vitis HLS. The user guide for this tool 
can be found at https://docs.amd.com/r/en-US/ug1399-vitis-hls.

Our project consists of 3 HLS files:
* C program of the GCD algorithm: :gh-file-ref:`generators/chipyard/src/main/resources/hls/HLSAccel.cpp`
* TCL script to run Vitis HLS: :gh-file-ref:`generators/chipyard/src/main/resources/hls/run_hls.tcl`
* Makefile to run HLS and move verilog files: :gh-file-ref:`generators/chipyard/src/main/resources/hls/Makefile`

This example implements an iterative GCD algorithm, which is manually connected to 
a TileLink register node in the ``HLSGCDAccel`` class in 
:gh-file-ref:`generators/chipyard/src/main/scala/example/GCD.scala`.
HLS also supports adding AXI nodes to accelerators using compiler directives and 
the HLS stream library. See the Vitis HLS user guide for AXI implementation information.

The HLS code is synthesized for a particular FPGA target, in this case, 
an AMD Alveo U200. The target FPGA part is specified in ``run_hls.tcl`` using 
the ``set_part command``. The clock period, used for design optimization purposes, 
is also set in ``run_hls.tcl`` using the ``create_clock`` command.

To generate the verilog files, as well as synthesis reports, run:

.. code-block:: none

    vitis_hls run_hls.tcl

The files can be found in a generated folder named proj\_\<your\_project\_name>, 
in our case, ``proj_gcd_example``.

In our case, we include a ``Makefile`` to run HLS and to move files to 
their intended locations. To generate the verilog files using the Makefile, run:

.. code-block:: none

    make

To delete the generated files, run:

.. code-block:: none

    make clean

Creating the Verilog black box
---------------------------------------

.. Note:: This section discusses automatically running HLS within a Verilog black box. Please consult :ref:`incorporating-verilog-blocks` for background information on writing a Verilog black box. 

We use Scala to run ``make``, which runs HLS and copies the files into :gh-file-ref:`generators/chipyard/src/main/resources/vsrc`.
Then, we add the path to each file. This code will execute during Chisel elaboration, conveniently handling 
file generation for the user.

.. literalinclude:: ../../generators/chipyard/src/main/scala/example/GCD.scala
    :language: scala
    :start-after: DOC include start: HLS blackbox
    :end-before: DOC include end: HLS blackbox

Running the example
---------------------------------------

To test if the accelerator works, use the test program in :gh-file-ref:`tests/gcd.c`. 
Compile the program with ``make``. Then, run:

.. code-block:: none

    cd sims/vcs
    make run-binary CONFIG=HLSAcceleratorRocketConfig BINARY=../../tests/gcd.riscv