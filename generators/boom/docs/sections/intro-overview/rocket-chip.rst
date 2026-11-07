Rocket Chip SoC Generator
=========================

.. _boom-chip:
.. figure:: /figures/chip.png
    :alt: BOOM Chip

    A single-core "BOOM-chip", with no L2 last-level cache

As BOOM is just a core, an entire SoC infrastructure must be provided.
BOOM was developed to use the open-source `Rocket Chip SoC generator <https://github.com/chipsalliance/rocket-chip>`__.
The **Rocket Chip generator** can instantiate a wide range of SoC designs, including cache-coherent
multi-tile designs, cores with and without accelerators, and chips with or without a last-level shared cache.
It comes bundled with a 5-stage in-order core, called **Rocket**, by default.
BOOM uses the Rocket Chip infrastructure to instantiate it's core/tile complex (tile is a core, L1D/I$, and PTW) instead of a
Rocket tile.

To get more information, please visit the `Chipyard Rocket Chip documentation <https://chipyard.readthedocs.io/en/dev/Generators/Rocket-Chip.html>`__.

The Rocket Core - a Library of Processor Components!
----------------------------------------------------

From BOOM's point of view, the Rocket core can be thought of as a
"Library of Processor Components." There are a number of modules created
for Rocket that are also used by BOOM - the functional units, the
caches, the translation look-aside buffers (TLBs), the page table walker (PTW), and
more. Throughout this document you will find references to these
Rocket components and descriptions on how they fit into BOOM.

To get more information about the Rocket core, please visit the `Chipyard Rocket Core documentation <https://chipyard.readthedocs.io/en/dev/Generators/Rocket.html>`__.

.. Note:: Both Chipyard links point to the ``dev`` documentation of Chipyard to get the most recent documentation changes.
