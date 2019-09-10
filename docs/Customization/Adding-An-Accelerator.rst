.. _adding-an-accelerator:

Adding An Accelerator/Device
===============================

Accelerators or custom IO devices can be added to your SoC in several ways:

* MMIO Peripheral (a.k.a TileLink-Attached Accelerator)
* Tightly-Coupled RoCC Accelerator

These approaches differ in the method of the communication between the processor and the custom block.

With the TileLink-Attached approach, the processor communicates with MMIO peripherals through memory-mapped registers.

In contrast, the processor communicates with a RoCC accelerators through a custom protocol and custom non-standard ISA instructions reserved in the RISC-V ISA encoding space.
Each core can have up to four accelerators that are controlled by custom instructions and share resources with the CPU.
RoCC coprocessor instructions have the following form.

.. code-block:: none

    customX rd, rs1, rs2, funct

The X will be a number 0-3, and determines the opcode of the instruction, which controls which accelerator an instruction will be routed to.
The ``rd``, ``rs1``, and ``rs2`` fields are the register numbers of the destination register and two source registers.
The ``funct`` field is a 7-bit integer that the accelerator can use to distinguish different instructions from each other.

Note that communication through a RoCC interface requires a custom software toolchain, whereas MMIO peripherals can use that standard toolchain with appropriate driver support.

Integrating into the Generator Build System
-------------------------------------------

While developing, you want to include Chisel code in a submodule so that it can be shared by different projects.
To add a submodule to the Chipyard framework, make sure that your project is organized as follows.

.. code-block:: none

    yourproject/
        build.sbt
        src/main/scala/
            YourFile.scala

Put this in a git repository and make it accessible.
Then add it as a submodule to under the following directory hierarchy: ``generators/yourproject``.

.. code-block:: shell

    cd generators/
    git submodule add https://git-repository.com/yourproject.git

Then add ``yourproject`` to the Chipyard top-level build.sbt file.

.. code-block:: scala

    lazy val yourproject = (project in file("generators/yourproject")).settings(commonSettings).dependsOn(rocketchip)

You can then import the classes defined in the submodule in a new project if
you add it as a dependency. For instance, if you want to use this code in
the ``example`` project, change the final line in build.sbt to the following.

.. code-block:: scala

    lazy val example = (project in file(".")).settings(commonSettings).dependsOn(testchipip, yourproject)

MMIO Peripheral
------------------

The easiest way to create a TileLink peripheral is to use the ``TLRegisterRouter``, which abstracts away the details of handling the TileLink protocol and provides a convenient interface for specifying memory-mapped registers.
To create a RegisterRouter-based peripheral, you will need to specify a parameter case class for the configuration settings, a bundle trait with the extra top-level ports, and a module implementation containing the actual RTL.
In this case we use a submodule ``PWMBase`` to actually perform the pulse-width modulation. The ``PWMModule`` class only creates the registers and hooks them
up using ``regmap``.

.. literalinclude:: ../../generators/example/src/main/scala/PWM.scala
    :language: scala
    :start-after: DOC include start: PWM generic traits
    :end-before: DOC include end: PWM generic traits

Once you have these classes, you can construct the final peripheral by extending the ``TLRegisterRouter`` and passing the proper arguments.
The first set of arguments determines where the register router will be placed in the global address map and what information will be put in its device tree entry.
The second set of arguments is the IO bundle constructor, which we create by extending ``TLRegBundle`` with our bundle trait.
The final set of arguments is the module constructor, which we create by extends ``TLRegModule`` with our module trait.

.. literalinclude:: ../../generators/example/src/main/scala/PWM.scala
    :language: scala
    :start-after: DOC include start: PWMTL
    :end-before: DOC include end: PWMTL

The full module code can be found in ``generators/example/src/main/scala/PWM.scala``.

After creating the module, we need to hook it up to our SoC.
Rocket Chip accomplishes this using the cake pattern.
This basically involves placing code inside traits.
In the Rocket Chip cake, there are two kinds of traits: a ``LazyModule`` trait and a module implementation trait.

The ``LazyModule`` trait runs setup code that must execute before all the hardware gets elaborated.
For a simple memory-mapped peripheral, this just involves connecting the peripheral's TileLink node to the MMIO crossbar.

.. literalinclude:: ../../generators/example/src/main/scala/PWM.scala
    :language: scala
    :start-after: DOC include start: HasPeripheryPWMTL
    :end-before: DOC include end: HasPeripheryPWMTL

Note that the ``PWMTL`` class we created from the register router is itself a ``LazyModule``.
Register routers have a TileLink node simply named "node", which we can hook up to the Rocket Chip bus.
This will automatically add address map and device tree entries for the peripheral.

The module implementation trait is where we instantiate our PWM module and connect it to the rest of the SoC.
Since this module has an extra `pwmout` output, we declare that in this trait, using Chisel's multi-IO functionality.
We then connect the ``PWMTL``'s pwmout to the pwmout we declared.

.. literalinclude:: ../../generators/example/src/main/scala/PWM.scala
    :language: scala
    :start-after: DOC include start: HasPeripheryPWMTLModuleImp
    :end-before: DOC include end: HasPeripheryPWMTLModuleImp

Now we want to mix our traits into the system as a whole.
This code is from ``generators/example/src/main/scala/Top.scala``.

.. literalinclude:: ../../generators/example/src/main/scala/Top.scala
    :language: scala
    :start-after: DOC include start: TopWithPWMTL
    :end-before: DOC include end: TopWithPWMTL

Just as we need separate traits for ``LazyModule`` and module implementation, we need two classes to build the system.
The ``Top`` classes already have the basic peripherals included for us, so we will just extend those.

The ``Top`` class includes the pre-elaboration code and also a ``lazy val`` to produce the module implementation (hence ``LazyModule``).
The ``TopModule`` class is the actual RTL that gets synthesized.

Next, we need to add a configuration mixin in ``generators/example/src/main/scala/ConfigMixins.scala`` that tells the ``TestHarness`` to instantiate ``TopWithPWMTL`` instead of the default ``Top``.

.. literalinclude:: ../../generators/example/src/main/scala/ConfigMixins.scala
    :language: scala
    :start-after: DOC include start: WithPWMTop
    :end-before: DOC include end: WithPWMTop

And finally, we create a configuration class in ``generators/example/src/main/scala/Configs.scala`` that uses this mixin.

.. literalinclude:: ../../generators/example/src/main/scala/RocketConfigs.scala
    :language: scala
    :start-after: DOC include start: PWMRocketConfig
    :end-before: DOC include end: PWMRocketConfig

Now we can test that the PWM is working. The test program is in ``tests/pwm.c``.

.. literalinclude:: ../../tests/pwm.c
    :language: c

This just writes out to the registers we defined earlier.
The base of the module's MMIO region is at 0x2000.
This will be printed out in the address map portion when you generated the verilog code.

Compiling this program with make produces a ``pwm.riscv`` executable.

Now with all of that done, we can go ahead and run our simulation.

.. code-block:: shell

    cd verilator
    make CONFIG=PWMConfig
    ./simulator-example-PWMConfig ../tests/pwm.riscv

Adding a RoCC Accelerator
----------------------------

RoCC accelerators are lazy modules that extend the ``LazyRoCC`` class.
Their implementation should extends the ``LazyRoCCModule`` class.

.. code-block:: scala

    class CustomAccelerator(opcodes: OpcodeSet)
        (implicit p: Parameters) extends LazyRoCC(opcodes) {
      override lazy val module = new CustomAcceleratorModule(this)
    }

    class CustomAcceleratorModule(outer: CustomAccelerator)
        extends LazyRoCCModuleImp(outer) {
      val cmd = Queue(io.cmd)
      // The parts of the command are as follows
      // inst - the parts of the instruction itself
      //   opcode
      //   rd - destination register number
      //   rs1 - first source register number
      //   rs2 - second source register number
      //   funct
      //   xd - is the destination register being used?
      //   xs1 - is the first source register being used?
      //   xs2 - is the second source register being used?
      // rs1 - the value of source register 1
      // rs2 - the value of source register 2
      ...
    }


The ``opcodes`` parameter for ``LazyRoCC`` is the set of custom opcodes that will map to this accelerator.
More on this in the next subsection.

The ``LazyRoCC`` class contains two TLOutputNode instances, ``atlNode`` and ``tlNode``.
The former connects into a tile-local arbiter along with the backside of the L1 instruction cache.
The latter connects directly to the L1-L2 crossbar.
The corresponding Tilelink ports in the module implementation's IO bundle are ``atl`` and ``tl``, respectively.

The other interfaces available to the accelerator are ``mem``, which provides access to the L1 cache;
``ptw`` which provides access to the page-table walker;
the ``busy`` signal, which indicates when the accelerator is still handling an instruction;
and the ``interrupt`` signal, which can be used to interrupt the CPU.

Look at the examples in ``generators/rocket-chip/src/main/scala/tile/LazyRocc.scala`` for detailed information on the different IOs.

Adding RoCC accelerator to Config
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

RoCC accelerators can be added to a core by overriding the ``BuildRoCC`` parameter in the configuration.
This takes a sequence of functions producing ``LazyRoCC`` objects, one for each accelerator you wish to add.

For instance, if we wanted to add the previously defined accelerator and route custom0 and custom1 instructions to it, we could do the following.

.. code-block:: scala

    class WithCustomAccelerator extends Config((site, here, up) => {
      case BuildRoCC => Seq((p: Parameters) => LazyModule(
        new CustomAccelerator(OpcodeSet.custom0 | OpcodeSet.custom1)(p)))
    })

    class CustomAcceleratorConfig extends Config(
      new WithCustomAccelerator ++ new RocketConfig)

To add RoCC instructions in your program, use the RoCC C macros provided in ``tests/rocc.h``. You can find examples in the files ``tests/accum.c`` and ``charcount.c``.

Adding a DMA port
-------------------

IO devices or accelerators (like a disk or network driver), we may want to have the device write directly to the coherent memory system instead.
To add a device like that, you would do the following.

.. code-block:: scala

    class DMADevice(implicit p: Parameters) extends LazyModule {
      val node = TLHelper.makeClientNode(
        name = "dma-device", sourceId = IdRange(0, 1))

      lazy val module = new DMADeviceModule(this)
    }

    class DMADeviceModule(outer: DMADevice) extends LazyModuleImp(outer) {
      val io = IO(new Bundle {
        val ext = new ExtBundle
      })

      val (mem, edge) = outer.node.out(0)

      // ... rest of the code ...
    }

    trait HasPeripheryDMA { this: BaseSubsystem =>
      implicit val p: Parameters

      val dma = LazyModule(new DMADevice)

      fbus.fromPort(Some(portName))() := dma.node
    }

    trait HasPeripheryDMAModuleImp extends LazyModuleImp {
      val ext = IO(new ExtBundle)
      ext <> outer.dma.module.io.ext
    }

    class TopWithDMA(implicit p: Parameters) extends Top
        with HasPeripheryDMA {
      override lazy val module = new TopWithDMAModule
    }

    class TopWithDMAModule(l: TopWithDMA) extends TopModule(l)
        with HasPeripheryDMAModuleImp


The ``ExtBundle`` contains the signals we connect off-chip that we get data from.
The DMADevice also has a Tilelink client port that we connect into the L1-L2 crossbar through the frontend bus (fbus).
The sourceId variable given in the ``TLClientNode`` instantiation determines the range of ids that can be used in acquire messages from this device.
Since we specified [0, 1) as our range, only the ID 0 can be used.
