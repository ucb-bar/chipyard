Hwacha
====================================

The Hwacha project is developing a new vector architecture for future computer systems that are constrained in their power and energy consumption.
The Hwacha project is inspired by traditional vector machines from the 70s and 80s, and lessons learned from our previous vector-thread architectures such as Scale and Maven
The Hwacha project includes the Hwacha microarchitecture generator, as well as the ``XHwacha`` non-standard RISC-V extension. Hwacha does not implement the RISC-V standard vector extension proposal.

For more information on the Hwacha project, please visit the `Hwacha website <http://hwacha.org/>`__.

To add the Hwacha vector unit to an SoC, you should add the ``hwacha.DefaultHwachaConfig`` config mixin to the SoC configurations. The Hwacha vector unit uses the RoCC port of a Rocket or BOOM `tile`, and by default connects to the memory system through the `System Bus` (i.e., directly to the L2 cache). 

To change the configuration of the Hwacha vector unit, you can write a custom configuration to replace the ``DefaultHwachaConfig``. You can view the ``DefaultHwachaConfig`` under `generators/hwacha/src/main/scala/configs.scala <https://github.com/ucb-bar/hwacha/blob/master/src/main/scala/configs.scala>`__ to see the possible configuration parameters.
 
Since Hwacha implements a non-standard RISC-V extension, it requires a unique software toolchain to be able to compile and asseble its vector instructions.
To install the Hwacha toolchain, run the ``./scripts/build-toolchains.sh esp-tools`` command within the root Chipyard directory. This may take a while, and it will install the ``esp-tools-install`` directory within your Chipyard root directory. ``esp-tools`` is a fork of ``riscv-tools`` (formerly a collection of relevant software RISC-V tools) that was enhanced with additional non-standard vector instructions. However, due to the upstreaming of the equivalent RISC-V toolchains, ``esp-tools`` may not be up-to-date with the latest mainline version of the tools included in it.
