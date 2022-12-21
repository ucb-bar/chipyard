.. _keys-traits-configs:

Keys, Traits, and Configs
=========================

You have probably seen snippets of Chisel referencing keys, traits, and configs by this point.
This section aims to elucidate the interactions between these Chisel/Scala components, and provide
best practices for how these should be used to create a parameterized design and configure it.

We will continue to use the GCD example.

Keys
----

Keys specify some parameter which controls some custom widget. Keys should typically be implemented as **Option types**, with a default value of ``None`` that means no change in the system. In other words, the default behavior when the user does not explicitly set the key should be a no-op.

Keys should be defined and documented in sub-projects, since they generally deal with some specific block, and not system-level integration. (We make an exception for the example GCD widget).

.. literalinclude:: ../../generators/chipyard/src/main/scala/example/GCD.scala
    :language: scala
    :start-after: DOC include start: GCD key
    :end-before: DOC include end: GCD key

The object within a key is typically a ``case class XXXParams``, which defines a set of parameters which some block accepts. For example, the GCD widget's ``GCDParams`` parameterizes its address, operand widths, whether the widget should be connected by Tilelink or AXI4, and whether the widget should use the blackbox-Verilog implementation, or the Chisel implementation.


.. literalinclude:: ../../generators/chipyard/src/main/scala/example/GCD.scala
    :language: scala
    :start-after: DOC include start: GCD params
    :end-before: DOC include end: GCD params

Accessing the value stored in the key is easy in Chisel, as long as the ``implicit p: Parameters`` object is being passed through to the relevant module. For example, ``p(GCDKey).get.address`` returns the address field of ``GCDParams``. Note this only works if ``GCDKey`` was not set to ``None``, so your Chisel should check for that case!

Traits
------

Typically, most custom blocks will need to modify the behavior of some pre-existing block. For example, the GCD widget needs the ``DigitalTop`` module to instantiate and connect the widget via Tilelink, generate a top-level ``gcd_busy`` port, and connect that to the module as well. Traits let us do this without modifying the existing code for the ``DigitalTop``, and enables compartmentalization of code for different custom blocks.

Top-level traits specify that the ``DigitalTop`` has been parameterized to read some custom key and optionally instantiate and connect a widget defined by that key. Traits **should not** mandate the instantiation of custom logic. In other words, traits should be written with ``CanHave`` semantics, where the default behavior when the key is unset is a no-op.

Top-level traits should be defined and documented in subprojects, alongside their corresponding keys. The traits should then be added to the ``DigitalTop`` being used by Chipyard.

Below we see the traits for the GCD example. The Lazy trait connects the GCD module to the Diplomacy graph, while the Implementation trait causes the ``DigitalTop`` to instantiate an additional port and concretely connect it to the GCD module.

.. literalinclude:: ../../generators/chipyard/src/main/scala/example/GCD.scala
    :language: scala
    :start-after: DOC include start: GCD lazy trait
    :end-before: DOC include end: GCD imp trait

These traits are added to the default ``DigitalTop`` in Chipyard.

.. literalinclude:: ../../generators/chipyard/src/main/scala/DigitalTop.scala
    :language: scala
    :start-after: DOC include start: DigitalTop
    :end-before: DOC include end: DigitalTop

Config Fragments
----------------

Config fragments set the keys to a non-default value. Together, the collection of config fragments which define a configuration generate the values for all the keys used by the generator.

For example, the ``WithGCD`` config fragment is parameterized by the type of GCD widget you want to instantiate. When this config fragment is added to a config, the ``GCDKey`` is set to a instance of ``GCDParams``, informing the previously mentioned traits to instantiate and connect the GCD widget appropriately.

.. literalinclude:: ../../generators/chipyard/src/main/scala/example/GCD.scala
    :language: scala
    :start-after: DOC include start: GCD config fragment
    :end-before: DOC include end: GCD config fragment

We can use this config fragment when composing our configs.

.. literalinclude:: ../../generators/chipyard/src/main/scala/config/RocketConfigs.scala
    :language: scala
    :start-after: DOC include start: GCDTLRocketConfig
    :end-before: DOC include end: GCDTLRocketConfig

.. note::
   Readers who want more information on the configuration system may be interested in reading :ref:`cdes`.
