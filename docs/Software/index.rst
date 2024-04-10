Target Software
==================================

Chipyard includes tools for developing target software workloads. The primary
tool is FireMarshal, which manages workload descriptions and generates binaries
and disk images to run on your target designs. Workloads can be bare-metal, or
be based on standard Linux distributions. Users can customize every part of the
build process, including providing custom kernels (if needed by the hardware).

FireMarshal can also run your workloads on high-performance functional
simulators like Spike and Qemu. Spike is easily customized and serves as the
official RISC-V ISA reference implementation. Qemu is a high-performance
functional simulator that can run nearly as fast as native code, but can be
challenging to modify.

To initialize additional software repositories, such as wrappers for Coremark,
SPEC2017, and workloads for the NVDLA, run the following script. The
submodules are located in the ``software`` directory.

.. code-block:: shell

    ./scripts/init-software.sh


.. toctree::
   :maxdepth: 2
   :caption: Contents:

   FireMarshal
   Spike
   Baremetal
