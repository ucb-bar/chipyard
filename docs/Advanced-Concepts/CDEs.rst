.. _cdes:

Context-Dependent-Environments
========================================

Readers may notice that the parameterization system frequently uses ``(site, here, up)``.
This construct is an artifact of the "context-dependent-environment" system which Chipyard and Rocket Chip both leverage for powerful composable hardware configuration.

The CDE parameterization system provides different "Views" of a single global parameterization. The syntax for accessing a ``Field`` within a ``View`` is ``my_view(MyKey, site_view)``, where ``site_view`` is a "global" view that will be passed recursively into various functions and key-lookups in the call-stack of ``my_view(MyKey, site_view)``.

.. note::
   Rocket Chip based designs will frequently use ``val p: Parameters`` and ``p(SomeKey)`` to lookup the value of a key. ``Parameters`` is just a subclass of the ``View`` abstract class, and ``p(SomeKey)`` really expands into ``p(SomeKey, p)``. This is because we consider the call ``p(SomeKey)`` to be the "site", or "source" of the original key query, so we need to pass in the view of the configuration provided by ``p`` recursively to future calls through the ``site`` argument.

Consider the following example using CDEs.

.. code:: scala

    case object SomeKeyX extends Field[Boolean](false) // default is false
    case object SomeKeyY extends Field[Boolean](false) // default is false
    case object SomeKeyZ extends Field[Boolean](false) // default is false

    class WithX(b: Boolean) extends Config((site, here, up) => {
      case SomeKeyX => b
    }

    class WithY(b: Boolean) extends Config((site, here, up) => {
      case SomeKeyY => b
    }


When forming a query based on a ``Parameters`` object, like ``p(SomeKeyX)``, the configuration system traverses the "chain" of config fragments until it finds a partial function which is defined at the key, and then returns that value.

.. code:: scala

    val params = Config(new WithX(true) ++ new WithY(true)) // "chain" together config fragments
    params(SomeKeyX) // evaluates to true
    params(SomeKeyY) // evaluates to true
    params(SomeKeyZ) // evaluates to false

In this example, the evaluation of ``params(SomeKeyX)`` will terminate in the partial function defined in ``WithX(true)``, while the evaluation of ``params(SomeKeyY)`` will terminate in the partial function defined in ``WithY(true)``. Note that when no partial functions match, the evaluation will return the default value for that parameter.

The real power of CDEs arises from the ``(site, here, up)`` parameters to the partial functions, which provide useful "views" into the global parameterization that the partial functions may access to determine a parameterization.

.. note::
   Additional information on the motivations for CDEs can be found in Chapter 2 of `Henry Cook's Thesis <https://www2.eecs.berkeley.edu/Pubs/TechRpts/2016/EECS-2016-89.pdf>`_ .


Site
~~~~

``site`` provides a ``View`` of the "source" of the original parameter query.

.. code:: scala

    class WithXEqualsYSite extends Config((site, here, up) => {
      case SomeKeyX => site(SomeKeyY) // expands to site(SomeKeyY, site)
    }

    val params_1 = Config(new WithXEqualsYSite ++ new WithY(true))
    val params_2 = Config(new WithY(true) ++ new WithXEqualsYSite)
    params_1(SomeKeyX) // evaluates to true
    params_2(SomeKeyX) // evaluates to true


In this example, the partial function in ``WithXEqualsYSite`` will look up the value of ``SomeKeyY`` in the original ``params_N`` object, which becomes ``site`` in each call in the recursive traversal.


Here
~~~~

``here`` provides a ``View`` of the locally defined config, which typically just contains some partial function.

.. code:: scala

    class WithXEqualsYHere extends Config((site, here, up) => {
      case SomeKeyY => false
      case SomeKeyX => here(SomeKeyY, site)
    }

    val params_1 = Config(new WithXEqualsYHere ++ new WithY(true))
    val params_2 = Config(new WithY(true) ++ new WithXEqualsYHere)

    params_1(SomeKeyX) // evaluates to false
    params_2(SomeKeyX) // evaluates to false

In this example, note that although our final parameterization in ``params_2`` has ``SomeKeyY`` set to ``true``, the call to ``here(SomeKeyY, site)`` only looks in the local partial function defined in ``WithXEqualsYHere``. Note that we pass ``site`` to ``here`` since ``site`` may be used in the recursive call.


Up
~~~~

``up`` provides a ``View`` of the previously defined set of partial functions in the "chain" of partial functions. This is useful when we want to lookup a previously set value for some key, but not the final value for that key.

.. code:: scala

    class WithXEqualsYUp extends Config((site, here, up) => {
      case SomeKeyX => up(SomeKeyY, site)
    }

    val params_1 = Config(new WithXEqualsYUp ++ new WithY(true))
    val params_2 = Config(new WithY(true) ++ new WithXEqualsYUp)

    params_1(SomeKeyX) // evaluates to true
    params_2(SomeKeyX) // evaluates to false

In this example, note how ``up(SomeKeyY, site)`` in ``WithXEqualsYUp`` will refer to *either* the the partial function defining ``SomeKeyY`` in ``WithY(true)`` *or* the default value for ``SomeKeyY`` provided in the original ``case object SomeKeyY`` definition, *depending on the order in which the config fragments were used*. Since the order of config fragments affects the the order of the ``View`` traversal, ``up`` provides a different ``View`` of the parameterization in ``params_1`` and ``params_2``.


Also note that again, ``site`` must be recursively passed through the call to ``up``.
