IOBinders
=========

In Chipyard we use a special ``Parameters`` key, ``IOBinders`` to instantiate IO cells in the ``ChipTop`` layer and determine what modules to bind to the IOs of a ``ChipTop`` in the ``TestHarness``.

.. literalinclude:: ../../generators/chipyard/src/main/scala/IOBinders.scala
   :language: scala
   :start-after: DOC include start: IOBinders
   :end-before: DOC include end: IOBinders


This special key solves the problem of duplicating test-harnesses for each different ``System`` type.
You could just as well create a custom harness module that attaches IOs explicitly.
Instead, the ``IOBinders`` key provides a map from Scala traits to attachment behaviors.
Each ``IOBinder`` returns a tuple of three values: the list of ``ChipTop`` ports created by the ``IOBinder``, the list of all IO cell modules instantiated by the ``IOBinder``, and an optional function to be called inside the test harness.
This function is responsible for instantiating logic inside the ``TestHarness`` to appropriately drive the ``ChipTop`` IO ports created by the ``IOBinder``.
Conveniently, because the ``IOBinder`` is generating the port, it may also use the port inside this function, which prevents the ``BaseChipTop`` code from ever needing to access the port ``val``.
This scheme prevents the need to have two separate binder functions for each ``System`` trait.
When creating custom ``IOBinders`` it is important to use ``suggestName`` to name ports; otherwise Chisel will raise an exception trying to name the IOs.
The example ``IOBinders`` demonstrate this.

As an example, the ``WithGPIOTiedOff`` IOBinder creates IO cells for the GPIO module(s) instantiated in the ``System``, then punches out new ``Analog`` ports for each one.
The test harness simply ties these off, but additional logic could be inserted to perform some kind of test in the ``TestHarness``.

.. literalinclude:: ../../generators/chipyard/src/main/scala/IOBinders.scala
   :language: scala
   :start-after: DOC include start: WithGPIOTiedOff
   :end-before: DOC include end: WithGPIOTiedOff


``IOBinders`` also do not need to create ports. Some ``IOBinders`` can simply insert circuitry inside the ``ChipTop`` layer.
For example, the ``WithSimAXIMemTiedOff`` IOBinder specifies that any ``System`` which matches ``CanHaveMasterAXI4MemPortModuleImp`` will have a ``SimAXIMem`` connected inside ``ChipTop``.

.. literalinclude:: ../../generators/chipyard/src/main/scala/IOBinders.scala
   :language: scala
   :start-after: DOC include start: WithSimAXIMem
   :end-before: DOC include end: WithSimAXIMem

These classes are all ``Config`` objects, which can be mixed into the configs to specify IO connection behaviors.

There are two macros for generating these ``Config``s. ``OverrideIOBinder`` overrides any existing behaviors set for a particular IO in the ``Config`` object. This macro is frequently used because typically top-level IOs drive or are driven by only one source, so a composition of ``IOBinders`` does not make sense. The ``ComposeIOBinder`` macro provides the functionality of not overriding existing behaviors.
