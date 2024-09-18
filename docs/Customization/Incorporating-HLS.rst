.. _incorporating-hls:

Incorporating HLS
============================

High Level Synthesis (HLS) is a method for iterating quickly on 
different hardware algorithms that automatically generates an RTL 
circuit to match a specification in a high level language like C.

Here, we will integrate an HLS-generated accelerator that computes 
the Great Common Denominator (GCD) of two integers. This tutorial 
builds on the sections :ref:`mmio-accelerators` and 
:ref:`incorporating-verilog-blocks`. The code for this example can 
be found in ``/generators/hls-example``

Adding an HLS project
---------------------------------------

In this tutorial, we use Vitis HLS, version 2023.2. 

Our project consists of 3 HLS files:
* C program of the GCD algorithm: ``accel/HLSAccel.cpp``
* Header file: ``accel/HLSAccel.hpp``
* TCL script to run Vitis HLS: ``run_hls.tcl``

To generate the verilog files, as well as synthesis reports, run:

.. code-block:: none
    vitis_hls run_hls.tcl

The files can be found in a generated folder named proj\_\<your\_project\_name>, 
in our case, ``proj_gcd_example``.

In our case, we include a ``Makefile`` to script running HLS. To generate the 
verilog files using the Makefile, run:

.. code-block:: none
    make

To delete the generated files, run:

.. code-block:: none
    make clean

Creating the Verilog black box
---------------------------------------

.. Note:: This section discusses automatically running HLS within a Verilog black box.
Please consult :ref:`incorporating-verilog-blocks` for background information 
on writing a Verilog black box. 

We use Scala to run ``make``, which runs HLS and copies the files into ``hls-example/src/main/resources/vsrc``.
Then, we add the path to each file. This code will execute during Chisel elaboration, conveniently handling 
file generation for the user.

.. literalinclude:: ../../generators/hls-example/src/main/scala/example/HLSExample.scala
    :language: scala
    :start-after: DOC include start: HLS blackbox
    :end-before: DOC include end: HLS blackbox

Running the example
---------------------------------------

To test if the accelerator works, use the test program in ``tests/gcd.c``. 
Compile the program with ``make``. Then, run:

.. code-block:: none
    cd sims/vcs
    make run-binary CONFIG=HLSAcceleratorRocketConfig BINARY=../../tests/gcd.riscv