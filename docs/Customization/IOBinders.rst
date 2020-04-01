IOBinders
=========

In Chipyard we use a special ``Parameters`` key, ``IOBinders`` to instantiate IO cells in the ``ChipTop`` layer and determine what modules to bind to the IOs of a ``ChipTop`` in the ``TestHarness``.

.. literalinclude:: ../../generators/chipyard/src/main/scala/IOBinders.scala
   :language: scala
   :start-after: DOC include start: IOBinders
   :end-before: DOC include end: IOBinders


This special key solves the problem of duplicating test-harnesses for each different ``System`` type.

You could just as well create a custom harness module that attaches IOs explicitly. Instead, the ``IOBinders`` key provides a map from Scala traits to attachment behaviors.

For example, the ``WithSimAXIMemTiedOff`` IOBinder specifies that any ``System`` which matches ``CanHaveMasterAXI4MemPortModuleImp`` will have a ``SimAXIMem`` connected.

.. literalinclude:: ../../generators/chipyard/src/main/scala/IOBinders.scala
   :language: scala
   :start-after: DOC include start: WithSimAXIMem
   :end-before: DOC include end: WithSimAXIMem

These classes are all ``Config`` objects, which can be mixed into the configs to specify IO connection behaviors.

There are two macros for generating these ``Config``s. ``OverrideIOBinder`` overrides any existing behaviors set for a particular IO in the ``Config`` object. This macro is frequently used because typically top-level IOs drive or are driven by only one source, so a composition of ``IOBinders`` does not make sense. The ``ComposeIOBinder`` macro provides the functionality of not overriding existing behaviors.
