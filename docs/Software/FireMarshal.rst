FireMarshal
=================

FireMarshal is a workload generation tool for RISC-V based systems. It
currently only supports the FireSim FPGA-accelerated simulation platform.

**Workloads** in FireMarshal consist of a series of **Jobs** that are assigned
to logical nodes in the target system. If no jobs are specified, then the
workload is considered ``uniform`` and only a single image will be produced for
all nodes in the system. Workloads are described by a ``json`` file and a
corresponding workload directory and can inherit their definitions from
existing workloads. Typically, workload configurations are kept in
``workloads/`` although you can use any directory you like. We provide a few
basic workloads to start with including buildroot or Fedora-based linux
distributions and bare-metal.

Once you define a workload, the ``marshal`` command will produce a
corresponding boot-binary and rootfs for each job in the workload. This binary
and rootfs can then be launched on qemu or spike (for functional simulation), or
installed to a platform for running on real RTL (currently only FireSim is
automated).

To get started, checkout the full `FireMarshal documentation <https://firemarshal.readthedocs.io/en/latest/index.html>`_.
