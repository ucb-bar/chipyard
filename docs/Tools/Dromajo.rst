Dromajo
===============================

`Dromajo <https://github.com/chipsalliance/dromajo/>`__ is a RV64GC functional simulator designed for co-simulation.
To use it as a co-simulator, it requires you to pass the committed trace of instructions coming from the core into the tool.
Within Chipyard, this is done by connecting to the `TracePort`` signals that are piped to the top level of the DUT.
While the Rocket core does have a `TracePort`, it does not provide the committed write data that Dromajo requires.
Thus, Dromajo uses the `ExtendedTracePort` only probided by BOOM (BOOM is the only core that supports Dromajo co-simulation).
An example of a divergence and Dromajo's printout is shown below.

.. code-block:: shell

    [error] EMU PC ffffffe001055d84, DUT PC ffffffe001055d84
    [error] EMU INSN 14102973, DUT INSN 14102973
    [error] EMU WDATA 00000000000220d6, DUT WDATA 00000000000220d4
    [error] EMU MSTATUS a000000a0, DUT MSTATUS 00000000
    [error] DUT pending exception -1 pending interrupt -1

Dromajo shows the divergence compared to simulation (PC, inst, inst-bits, write data, etc) and also provides the register state on failure.
It is useful to catch bugs that affect architectural state before a simulation hangs or crashes.

To use Dromajo with BOOM, refer to :ref:`Advanced-Concepts/Debugging-RTL:Debugging RTL` section on Dromajo.
