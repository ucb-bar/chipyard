Dromajo
===============================

`Dromajo <https://github.com/chipsalliance/dromajo/>`__ is a RV64GC functional simulator designed for co-simulation.
To use it as a co-simulator, it requires you to pass the committed trace of instructions to the tool.
Within Chipyard, this is done by connecting to the `TracePort`` signals that are piped to the top level of the DUT.
While the Rocket core does have a `TracePort`, it does not provide the committed write data that Dromajo requires.
Thus, BOOM is the only core that supports Dromajo co-simulation.

To use Dromajo with BOOM, refer to :ref:`Debugging RTL` section on Dromajo.
