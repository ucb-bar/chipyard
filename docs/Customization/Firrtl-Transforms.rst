.. _firrtl-transforms:

Adding a Firrtl Transform
=========================

Similar to how LLVM IR passes can perform transformations and optimizations on software, FIRRTL transforms can
modify Chisel-elaborated RTL.
As mentioned in Section :ref:`firrtl`, transforms are modifications that happen on the FIRRTL IR that can modify a circuit.
Transforms are a powerful tool to take in the FIRRTL IR that is emitted from Chisel and run analysis or convert the circuit into a new form.

Where to add transforms
-----------------------

In Chipyard, the FIRRTL compiler is called multiple times to create a "Top" file that contains the DUT and a "Harness" file containing the test harness, which instantiates the DUT.
The "Harness" file does not contain the DUT's module definition or any of its submodules.
This is done by the ``tapeout`` SBT project (located in ``tools/barstools/tapeout``) which calls ``GenerateTopAndHarness`` (a function that wraps the multiple FIRRTL compiler calls and extra transforms).

.. literalinclude:: ../../common.mk
    :language: make
    :start-after: DOC include start: FirrtlCompiler
    :end-before: DOC include end: FirrtlCompiler

If you look inside of the `tools/barstools/tapeout/src/main/scala/transforms/Generate.scala <https://github.com/ucb-bar/barstools/blob/master/tapeout/src/main/scala/transforms/Generate.scala>`__ file,
you can see that FIRRTL is invoked twice, once for the "Top" and once for the "Harness". If you want to add transforms to just modify the DUT, you can add them to ``topTransforms``.
Otherwise, if you want to add transforms to just modify the test harness, you can add them to ``harnessTransforms``.

For more information on Barstools, please visit the :ref:`Barstools` section.

Examples of transforms
----------------------

There are multiple examples of transforms that you can apply and are spread across the FIRRTL ecosystem.
Within FIRRTL there is a default set of supported transforms located in https://github.com/freechipsproject/firrtl/tree/master/src/main/scala/firrtl/transforms.
This includes transforms that can flatten modules (``Flatten``), group modules together (``GroupAndDedup``), and more.

Transforms can be standalone or can take annotations as input. Annotations are used to pass information between FIRRTL transforms. This includes information on
what modules to flatten, group, and more. Annotations can be added to the code by
adding them to your Chisel source or by creating a serialized annotation ``json`` file and adding it to the FIRRTL compiler
(note: annotating the Chisel source will automatically serialize the annotation as a ``json`` snippet into the build system for you).
**The recommended way to annotate something is to do it in the Chisel source, but not all annotation types have Chisel APIs**.

The example below shows two ways to annotate the signal using the ``DontTouchAnnotation``
(makes sure that a particular signal is not removed by the "Dead Code Elimination" pass in FIRRTL):

* use the Chisel API/wrapper function called ``dontTouch`` that does this automatically for you (more `dontTouch <https://www.chisel-lang.org/api/SNAPSHOT/chisel3/dontTouch$.html>`__ information):
* directly annotate the signal with the ``annotate`` function and the ``DontTouchAnnotation`` class if there is no Chisel API for it (note: most FIRRTL annotations have Chisel APIs for them)

.. code-block:: scala

    class TopModule extends Module {
        ...
        val submod = Module(new Submodule)
        ...
    }

    class Submodule extends Module {
        ...
        val some_signal := ...

        // MAIN WAY TO USE `dontTouch`
        // how to annotate if there is a Chisel API/wrapper
        chisel3.dontTouch(some_signal)

        // how to annotate WITHOUT a Chisel API/wrapper
        annotate(new ChiselAnnotation {
            def toFirrtl = DontTouchAnnotation(some_signal.toNamed)
        })

        ...
    }

Here is an example of the ``DontTouchAnnotation`` when it is serialized:

.. code-block:: json

   [
       {
           "class": "firrtl.transforms.DontTouchAnnotation",
           "target": "~TopModule|Submodule>some_signal"
       }
   ]

In this case, the specific syntax depends on the type of annotation and its fields.
One of the easier ways to figure out the serialized syntax is to first try and find a Chisel
annotation to add to the code. Then you can look at the collateral that is generated from the
build system, find the ``*.anno.json``, and find the proper syntax for the annotation.

Once ``yourAnnoFile.json`` is created then you can add ``-faf yourAnnoFile.json`` to the FIRRTL
compiler invocation in ``common.mk``.

.. literalinclude:: ../../common.mk
    :language: make
    :start-after: DOC include start: FirrtlCompiler
    :end-before: DOC include end: FirrtlCompiler

If you are interested in writing FIRRTL transforms please refer to the FIRRTL documentation located here:
https://github.com/freechipsproject/firrtl/wiki.
