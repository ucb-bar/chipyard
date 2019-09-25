.. _firrtl-transforms:

Adding a Firrtl Transform
=========================

After generating a ``.fir`` file from the Chisel generator, this ``.fir`` file is passed to
FIRRTL. FIRRTL then goes ahead and modifies the ``.fir`` IR with a series of tranforms that can modify
the circuit and then converts it to Verilog.

Where to add transforms
-----------------------

The main location to add tranforms is within the ``tools/barstools`` project.
Inside the ``tranforms`` package, the FIRRTL compiler is called twice,
first on the top-level circuit, then on the test harness. This is because we want to separate out
the harness and all the modules associated with it from the top-level design under test.

If you want to add transforms to just modify the DUT, you can add them to ``topTransforms``.

If you want to add transforms to just modify the test harness, you can add them to ``harnessTransforms``.

Examples of transforms
----------------------

There are multiple examples of transforms that you can apply and are spread across the FIRRTL ecosystem.
Within FIRRTL there is a default set of supported transforms located in https://github.com/freechipsproject/firrtl/tree/master/src/main/scala/firrtl/transforms.
This includes transforms that can flatten modules (``Flatten``), group modules together (``GroupAndDedup``), and more.

Transforms can be standalone or can take annotations as input. Annotations are FIRRTL specific ``json`` files that
are used to pass information into FIRRTL transforms (e.g. what modules to flatten, group, etc). Annotations can be added to the code by
adding them to your Chisel source (which will generate the ``json`` code for you) or by creating the ``.json`` file and adding it to the FIRRTL compiler.

Here is an example of grouping a series of modules by specifying the ``.json`` file:

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


Once created then you can add this to the FIRRTL compiler by modifying ``common.mk`` and adding the particu

.. code-block:: none

	-faf yourAnnoFile.json



If you are interested in writing FIRRTL transforms please refer to the FIRRTL documentation located here:
https://github.com/freechipsproject/firrtl/wiki.

The main Chipyard tranforms that are applied to the top and are located in the ``tools/barstools``.

- Talk about .json annotations

