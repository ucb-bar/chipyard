FFT Generator
====================================

The FFT generator is a parameterizable fft accelerator.

Configuration
--------------------------
The following configuration creates an 8-point FFT:

.. literalinclude:: ../../generators/chipyard/src/main/scala/config/RocketConfigs.scala
   :language: scala
   :start-after: DOC include start: FFTRocketConfig
   :end-before: DOC include end: FFTRocketConfig

:code:`baseAddress` specifies the starting address of the FFT's read and write lanes. The FFT write lane is always located at :code:`baseAddress`. There is 1 read lane per output point; since this config specifies an 8-point FFT, there will be 8 read lanes. Read lane :code:`i` (which can be loaded from to retrieve output point :code:`i`) will be located at :code:`baseAddr + 64bits (assuming 64bit system) + (i * 8)`. :code:`baseAddress` should be 64-bit aligned

:code:`width` is the size of input points in binary. A width of :code:`w` means that each point will have :code:`w` bits for the real component and :code:`w` bits for the imaginary component, yielding a total of `2w` bits per point. :code:`decPt` is the location of the decimal point in the fixed-precision representation of each point's real and imaginary value. In the Config above, each point is `32` bits wide, with `16` bits used to represent the real component and `16` bits used to represent the imaginary component. Within the `16` bits for each component, the `8` LSB are used to represent the decimal component of the value and the remaining (8) MSB are used to represent the integer component. Both the real and imaginary components use a fixed-precision representation.

To build a simulation of this example Chipyard config, run the following commands:

.. code-block:: shell

    cd sims/verilator # or "cd sims/vcs"
    make CONFIG=FFTRocketConfig

Usage and Testing
--------------------------

Points are passed into the FFT via the single write lane. In C pseudocode, this might look like:

.. code-block:: C

    for (int i = 0; i < num_points; i++) {
        // FFT_WRITE_LANE = baseAddress
        uint32_t write_val = points[i];
        volatile uint32_t* ptr = (volatile uint32_t*) FFT_WRITE_LANE;
        *ptr = write_val;
    }

Once the correct number of inputs are passed in (in the config above, 8 values would be passed in), the read lanes can be read from (again in C pseudocode):

.. code-block:: C

    for (int i = 0; i < num_points; i++) {
        // FFT_RD_LANE_BASE = baseAddress + 64bits (for write lane)
        volatile uint32_t* ptr_0 = (volatile uint32_t*) (FFT_RD_LANE_BASE + (i * 8));
        uint32_t read_val = *ptr_0;
    }

The :code:`fft.c` test file in the :code:`tests/` directory can be used to verify the fft's functionality on an SoC built with :code:`FFTRocketConfig`.

Acknowledgements
--------------------------
The code for the FFT Generator was adapted from the ADEPT Lab at UC Berkeley's `Hydra Spine <https://adept.eecs.berkeley.edu/projects/hydra-spine/>`_ project.

Authors for the original project (in no particular order):

* James Dunn, UC Berkeley (dunn [at] eecs [dot] berkeley [dot] edu)
   * :code:`Deserialize.scala`
   * :code:`Tail.scala`
   * :code:`Unscramble.scala`
* Stevo Bailey (stevo.bailey [at] berkeley [dot] edu)
   * :code:`FFT.scala`
