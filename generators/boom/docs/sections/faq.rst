Frequently Asked Questions
==========================

For questions regarding the BOOM core, please refer to our GitHub page issues section
located at https://github.com/riscv-boom/riscv-boom/issues.

Help! BOOM isn't working
------------------------

First verify the software is not an issue. Run spike first:

.. _verify-spike-first:
.. code-block:: bash
    # Verify it works on spike.
    spike my_program

    # Then we can run on BOOM.
    ./simulator-...-LargeBoomConfig my_program

Also verify the riscv-tools you built is the one pointed to by Chipyard.
Otherwise a version mismatch can easily occur!

Master branch is broken! How do I get a working BOOM?
-------------------------------------------------------

The `Chipyard <https://github.com/ucb-bar/chipyard>`__ SoC super-repo should
always be pointing to a working BOOM/rocket-chip/riscv-tools combination. The
`master` branch of riscv-boom may run ahead though. Ideally, `master` should never be
broken, but it may be somewhat unstable as development continues. For more
stability, please use one of the tagged `releases <https://github.com/riscv-boom/riscv-boom/releases>`__.
