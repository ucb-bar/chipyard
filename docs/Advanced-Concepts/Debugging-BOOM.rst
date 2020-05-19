Debugging BOOM
======================

In addition to the default debugging techniques specified in :ref:`Debugging RTL`,
single-core BOOM designs can utilize the Dromajo co-simulator (see :ref:`Dromajo`)
to verify functionality.

.. warning:: Dromajo currently only works in single-core BOOM systems without accelerators.

.. warning:: Dromajo currently only works in VCS simulation and FireSim.

Setting up Dromajo Co-simulation
--------------------------------------

Dromajo co-simulation is setup to work when two config fragments are added to a BOOM config.
First, a ``chipyard.config.WithTraceIO`` config fragment must be added so that BOOM's traceport is enabled.
Second, a ``chipyard.iobinders.WithSimDromajoBridge`` config fragment must be added to
connect the Dromajo co-simulator to the traceport.
Once both config fragments are added Dromajo should be enabled.

To build/run Dromajo with a BOOM design, run your configuration the following make commands:

.. code-block:: shell

    # build the default Dromajo BOOM config without waveform dumps
    # replace "DromajoBoomConfig" with your particular config
    make CONFIG=DromajoBoomConfig ENABLE_DROMAJO=1

    # run a simulation with Dromajo
    make CONFIG=DromajoBoomConfig ENABLE_DROMAJO=1 BINARY=<YOUR-BIN> run-binary
