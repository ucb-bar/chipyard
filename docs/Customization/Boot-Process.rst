Chipyard Boot Process
=======================

This section will describe in detail the process by which a Chipyard-based
SoC boots a Linux kernel and the changes you can make to customize this process.

BootROM and RISC-V Frontend Server
----------------------------------

The BootROM contains both the first instructions to run when the SoC is powered on as well as the
Device Tree Binary (dtb) which details the components of the system.
The assembly for the BootROM code is located in
`generators/testchipip/src/main/resources/testchipip/bootrom/bootrom.S <https://github.com/ucb-bar/testchipip/blob/master/src/main/resources/testchipip/bootrom/bootrom.S>`_.
The BootROM address space starts at ``0x10000`` (determined by the ``BootROMParams`` key in the configuration) and execution starts at address
``0x10040`` (given by the linker script and reset vector in the ``BootROMParams``), which is marked by the ``_hang`` label in the BootROM assembly.

The Chisel generator encodes the assembled instructions into the BootROM
hardware at elaboration time, so if you want to change the BootROM code, you
will need to run ``make`` in the bootrom directory and then regenerate the
Verilog. If you don't want to overwrite the existing ``bootrom.S``, you can
also point the generator to a different bootrom image by overriding the
``BootROMParams`` key in the configuration.

.. code-block:: scala

    class WithMyBootROM extends Config((site, here, up) => {
      case BootROMParams =>
        BootROMParams(contentFileName = "/path/to/your/bootrom.img")
    })

The default bootloader simply loops on a wait-for-interrupt (WFI) instruction
as the RISC-V frontend-server (FESVR) loads the actual program.
FESVR is a program that runs on the host CPU and can read/write arbitrary
parts of the target system memory using the Tethered Serial Interface (TSI).

FESVR uses TSI to load a baremetal executable or second-stage bootloader into
the SoC memory. In :ref:`Simulation/Software-RTL-Simulation:Software RTL Simulation`, this will be the binary you
pass to the simulator. Once it is finished loading the program, FESVR will
write to the software interrupt register for CPU 0, which will bring CPU 0
out of its WFI loop. Once it receives the interrupt, CPU 0 will write to
the software interrupt registers for the other CPUs in the system and then
jump to the beginning of DRAM to execute the first instruction of the loaded
executable. The other CPUs will be woken up by the first CPU and also jump
to the beginning of DRAM.

The executable loaded by FESVR should have memory locations designated
as *tohost* and *fromhost*. FESVR uses these memory locations to communicate
with the executable once it is running. The executable uses *tohost* to send
commands to FESVR for things like printing to the console,
proxying system calls, and shutting down the SoC. The *fromhost* register is
used to send back responses for *tohost* commands and for sending console
input.

The Berkeley Boot Loader and RISC-V Linux
-----------------------------------------

For baremetal programs, the story ends here. The loaded executable will run in
machine mode until it sends a command through the *tohost* register telling the
FESVR to power off the SoC.

However, for booting the Linux Kernel, you will need to use a second-stage
bootloader called the Berkeley Boot Loader, or BBL. This program reads the
device tree encoded in the boot ROM and transforms it into a format compatible
with the Linux kernel. It then sets up virtual memory and the interrupt
controller, loads the kernel, which is embedded in the bootloader binary as a
payload, and starts executing the kernel in supervisor mode. The bootloader is
also responsible for servicing machine-mode traps from the kernel and
proxying them over FESVR.

Once BBL jumps into supervisor mode, the Linux kernel takes over and begins
its process. It eventually loads the ``init`` program and runs it in user
mode, thus starting userspace execution.

The easiest way to build a BBL image that boots Linux is to use the FireMarshal
tool that lives in the `firesim-software <https://github.com/firesim/firesim-software>`_
repository. Directions on how to use FireMarshal can be found in the
:fsim_doc:`FireSim documentation <Advanced-Usage/FireMarshal/index.html>`.
Using FireMarshal, you can add custom kernel configurations and userspace software
to your workload.
