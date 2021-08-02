.. _harness-clocks:

Creating Clocks in the Test Harness
===================================

Chipyard currently allows the SoC design (everything under ``ChipTop``) to
have independent clock domains through diplomacy.
This implies that some reference clock enters the ``ChipTop`` and then is divided down into
separate clock domains.
From the perspective of the ``TestHarness`` module, the ``ChipTop`` clock and reset is
provided from a clock and reset called ``buildtopClock`` and ``buildtopReset``.
In the default case, this ``buildtopClock`` and ``buildtopReset`` is directly wired to the
clock and reset IO's of the ``TestHarness`` module.
However, the ``TestHarness`` has the ability to generate a standalone clock and reset signal
that is separate from the reference clock/reset of ``ChipTop``.
This allows harness components (including harness binders) the ability to "request" a clock
for a new clock domain.
This is useful for simulating systems in which modules in the harness have independent clock domains
from the DUT.

Requests for a harness clock is done by the ``HarnessClockInstantiator`` class in ``generators/chipyard/src/main/scala/TestHarness.scala``.
This class is accessed in harness components by referencing the Rocket Chip parameters key ``p(HarnessClockInstantiatorKey)``.
Then you can request a clock and syncronized reset at a particular frequency by invoking the ``requestClockBundle`` function.
Take the following example:

.. literalinclude:: ../../generators/chipyard/src/main/scala/HarnessBinders.scala
    :language: scala
    :start-after: DOC include start: HarnessClockInstantiatorEx
    :end-before: DOC include end: HarnessClockInstantiatorEx

Here you can see the ``p(HarnessClockInstantiatorKey)`` is used to request a clock and reset at ``memFreq`` frequency.

.. note::
    In the case that the reference clock entering ``ChipTop`` is not the overall reference clock of the simulation
    (i.e. the clock/reset coming into the ``TestHarness`` module), the ``buildtopClock`` and ``buildtopReset`` can
    differ from the implicit ``TestHarness`` clock and reset. For example, if the ``ChipTop`` reference is 500MHz but an
    extra harness clock is requested at 1GHz, the ``TestHarness`` implicit clock/reset will be at 1GHz while the ``buildtopClock``
    and ``buildtopReset`` will be at 500MHz.
