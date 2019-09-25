.. _firrtl-transforms:

Adding a Firrtl Transform
=========================

After writing the Chisel RTL, you can make further modifications by adding transforms during the FIRRTL compilation phase.
As mentioned in Section <>, transforms are modifications that happen on the FIRRTL IR that can modify a circuit.
Transforms are a powerful tool to take in the FIRRTL IR that is emitted from Chisel and run analysis (https://www.youtube.com/watch?v=FktjrjRVBoY) or convert the circuit into a new form.

Where to add transforms
-----------------------

In Chipyard, the FIRRTL compiler is called multiple times to create a "Top" file that has the DUT and a "Harness" file that has all harness collateral.
This done by the ``tapeout`` SBT project (located in ``tools/barstools/tapeout``) which calls ``GenerateTopAndHarness`` (a function that wraps the multiple FIRRTL compiler calls and extra transforms).

.. literalinclude:: ../../common.mk
    :language: make
    :start-after: DOC include start: FirrtlCompiler
    :end-before: DOC include end: FirrtlCompiler

If you look inside of the `tools/barstools/tapeout/src/main/scala/transforms/Generate.scala <https://github.com/ucb-bar/barstools/blob/master/tapeout/src/main/scala/transforms/Generate.scala>`__ file,
you can see that FIRRTL invoked twice, once for the "Top" and once for the "Harness". If you want to add transforms to just modify the DUT, you can add them to ``topTransforms``.
Otherwise, if you want to add transforms to just modify the test harness, you can add them to ``harnessTransforms``.

Examples of transforms
----------------------

There are multiple examples of transforms that you can apply and are spread across the FIRRTL ecosystem.
Within FIRRTL there is a default set of supported transforms located in https://github.com/freechipsproject/firrtl/tree/master/src/main/scala/firrtl/transforms.
This includes transforms that can flatten modules (``Flatten``), group modules together (``GroupAndDedup``), and more.

Transforms can be standalone or can take annotations as input. Annotations are used to pass information between FIRRTL transforms. This includes information on
what modules to flatten, group, and more. Annotations can be added to the code by
adding them to your Chisel source or by creating the ``.json`` file and adding it to the FIRRTL compiler (note: adding to the Chisel source will add the ``json`` snippet into the build system for you).
**The recommended way to annotate something is to do it in the Chisel source**.

Here is an example of adding an annotation within the Chisel source. This example is taken from the
``firechip`` project and uses an annotation to mark BOOM's register file for optimization:

.. literalinclude:: ../../generators/firechip/src/main/scala/TargetMixins.scala
    :language: make
    :start-after: DOC include start: ChiselAnnotation
    :end-before: DOC include end: ChiselAnnotation

Here is an example of grouping a series of modules by specifying the ``.json`` file.

.. code-block:: json

   [
       {
           "class": "firrtl.transforms.GroupAnnotation",
           "components": [
               "BeagleChipTop.BeagleChipTop.instance1",
               "BeagleChipTop.BeagleChipTop.instance2",
           ],
           "newModule": "NewModule",
           "newInstance": "newModInst"
       }
   ]

In this case, the specific syntax depends on the type of annotation. The best way to figure out
what the ``json`` file should contain is to first try to annotate in the Chisel
source and then see what the ``json`` output gives and copy that format.

Once ``yourAnnoFile.json`` is created then you can add ``-faf yourAnnoFile.json`` to the FIRRTL compiler invocation in ``common.mk``.

.. literalinclude:: ../../common.mk
    :language: make
    :start-after: DOC include start: FirrtlCompiler
    :end-before: DOC include end: FirrtlCompiler

If you are interested in writing FIRRTL transforms please refer to the FIRRTL documentation located here:
https://github.com/freechipsproject/firrtl/wiki.
