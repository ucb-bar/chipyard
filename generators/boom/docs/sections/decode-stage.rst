The Decode Stage
================

The **Decode** stage takes instructions from the :term:`Fetch Buffer`, decodes them,
and allocates the necessary resources as required by each instruction.
The **Decode** stage will stall as needed if not all resources are available.

RVC Changes
-----------

RVC decode is performed by expanding RVC instructions using Rocket's
``RVCExpander``. This does not change normal functionality of the **Decode** stage.
