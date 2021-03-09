.. _harness-clocks:

Creating Clocks in the Test Harness
===================================

By default, all modules in the Test Harness, including those made by harness binders
use the implicit clock and reset of the Test Harness.
However, the test harness and harness binders, have the ability to generate a standalone
clock and reset signal.
This is done by the ``HarnessClockInstantiator`` which allows you to request a clock at a
particular frequency.
Take the following harness binder example:

.. literalinclude:: ../../generators/chipyard/src/main/scala/config/HarnessBinders.scala
    :language: scala
    :start-after: DOC include start: HarnessClockInstantiatorEx
    :end-before: DOC include end: HarnessClockInstantiatorEx

While the purpose of the binder isn't necessary here, you can see that the ``p(HarnessClockInstantiatorKey).getClockBundle``
allows the binder to request a clock/reset bundle at a particular frequency.
