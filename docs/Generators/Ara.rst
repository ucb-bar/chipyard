Ara
===

`Ara <https://github.com/pulp-platform/ara>`__ is a RISC-V vector unit developed by the PULP project.
The Ara vector unit supports integration with either the Rocket or Shuttle in-order cores, following a similar methodology as used in the original Ara+CVA6 system.
Example Ara configurations are listed in ``generators/chipyard/src/main/scala/config/AraConfigs.scala``.

.. Warning:: Ara only supports a partial subset of the full V-extension. Notably, we do not support virtual memory or precise traps with Ara.

To compile simulators using Ara, you must pass an additional ``USE_ARA`` flag to the makefile.

.. Note:: Ara only supports VCS for simulation

.. code-block:: shell

     make CONFIG=V4096Ara2LaneRocketConfig USE_ARA=1
