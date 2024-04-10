.. _harness-clocks:

Creating Clocks in the Test Harness
===================================

Chipyard currently allows the SoC design (everything under ``ChipTop``) to
have independent clock domains through diplomacy.
``ChipTop`` clock ports are driven by ``harnessClockInstantiator.requestClock(freq)``.
``ChipTop`` reset ports are driven by the ``referenceReset()`` function, which is intended to provide an asynchronous reset.

The ``HarnessBinder`` s in ``ChipTop`` are clocked by the ``HarnessBinderClockFrequencyKey`` value. The reset is provided as a synchronous reset, sync'd to the clock.


Requests for a harness clock is done by the ``HarnessClockInstantiator`` class in ``generators/chipyard/src/main/scala/harness/HarnessClocks.scala``.
Then you can request a clock and syncronized reset at a particular frequency by invoking the ``requestClock`` function.
Take the following example:

.. literalinclude:: ../../generators/chipyard/src/main/scala/harness/HarnessBinders.scala
    :language: scala
    :start-after: DOC include start: HarnessClockInstantiatorEx
    :end-before: DOC include end: HarnessClockInstantiatorEx

Here you can see the ``th.harnessClockInstantiator`` is used to request a clock and reset at ``memFreq`` frequency.
