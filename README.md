# RISC-V Project Template

This is a starter template for your custom RISC-V project. It will allow you
to leverage the Chisel HDL and RocketChip SoC generator to produce a
RISC-V SoC with MMIO-mapped peripherals, DMA, and custom accelerators.

## Getting started

### Checking out the sources

After cloning this repo, you will need to initialize all of the submodules

    git clone https://github.com/ucb-bar/project-template.git
    cd project-template
    git submodule update --init --recursive

### Building the tools

The tools repo contains the cross-compiler toolchain, frontend server, and
proxy kernel, which you will need in order to compile code to RISC-V
instructions and run them on your design. There are detailed instructions at
https://github.com/riscv/riscv-tools. But to get a basic installation, just
the following steps are necessary.

    # You may want to add the following two lines to your shell profile
    export RISCV=/path/to/install/dir
    export PATH=$RISCV/bin:$PATH

    cd rocket-chip/riscv-tools
    ./build.sh

### Compiling and running the Verilator simulation

To compile the example design, run make in the "verisim" directory.
This will elaborate the DefaultExampleConfig in the example project.

An executable called simulator-example-DefaultExampleConfig will be produced.
You can then use this executable to run any compatible RV64 code. For instance,
to run one of the riscv-tools assembly tests.

    ./simulator-example-DefaultExampleConfig $RISCV/riscv64-unknown-elf/share/riscv-tests/isa/rv64ui-p-simple

If you later create your own project, you can use environment variables to
build an alternate configuration.

    make PROJECT=yourproject CONFIG=YourConfig
    ./simulator-yourproject-YourConfig ...

## Submodules and Subdirectories

The submodules and subdirectories for the project template are organized as
follows.

 * rocket-chip - contains code for the RocketChip generator and Chisel HDL
 * testchipip - contains the serial adapter, block device, and associated verilog and C++ code
 * verisim - directory in which Verilator simulations are compiled and run
 * vsim - directory in which Synopsys VCS simulations are compiled and run
 * bootrom - sources for the first-stage bootloader included in the Boot ROM
 * src/main/scala - scala source files for your project go here

## Using the block device

The default example project just provides the Rocket coreplex, memory, and
serial line. But testchipip also provides a simulated block device that can
be used for non-volatile storage. You can build a simulator including the
block device using the blkdev package.

    make CONFIG=SimBlockDeviceConfig
    ./simulator-example-SimBlockDeviceConfig +blkdev=block-device.img ...

By passing the +blkdev argument on the simulator command line, you can allow
the RTL simulation to read and write from a file. Take a look at tests/blkdev.c
for an example of how Rocket can program the block device controller.

## Adding an MMIO peripheral

You can RocketChip to create your own memory-mapped IO device and add it into
the SoC design. The easiest way to create a TileLink peripheral is to use the
TLRegisterRouter, which abstracts away the details of handling the TileLink
protocol and provides a convenient interface for specifying memory-mapped
registers. To create a RegisterRouter-based peripheral, you will need to
specify a parameter case class for the configuration settings, a bundle trait
with the extra top-level ports, and a module implementation containing the
actual RTL.

```scala
    case class PWMParams(address: BigInt, beatBytes: Int)

    trait PWMTLBundle extends Bundle {
      val pwmout = Output(Bool())
    }

    trait PWMTLModule {
      val io: PWMTLBundle
      implicit val p: Parameters
      def params: PWMParams

      val w = params.beatBytes * 8
      val period = Reg(UInt(w.W))
      val duty = Reg(UInt(w.W))
      val enable = RegInit(false.B)

      // ... Use the registers to drive io.pwmout ...

      regmap(
        0x00 -> Seq(
          RegField(w, period)),
        0x04 -> Seq(
          RegField(w, duty)),
        0x08 -> Seq(
          RegField(1, enable)))
    }
```

Once you have these classes, you can construct the final peripheral by
extending the TLRegisterRouter and passing the proper arguments. The first
set of arguments determines where the register router will be placed in the
global address map and what information will be put in its device tree entry.
The second set of arguments is the IO bundle constructor, which we create
by extending TLRegBundle with our bundle trait. The final set of arguments
is the module constructor, which we create by extends TLRegModule with our
module trait.

```scala
    class PWMTL(c: PWMParams)(implicit p: Parameters)
      extends TLRegisterRouter(
        c.address, "pwm", Seq("ucbbar,pwm"),
        beatBytes = c.beatBytes)(
          new TLRegBundle(c, _) with PWMTLBundle)(
          new TLRegModule(c, _, _) with PWMTLModule)
```

The full module code with comments can be found in src/main/scala/example/PWM.scala.

After creating the module, we need to hook it up to our SoC. Rocketchip
accomplishes this using the [cake pattern](http://www.cakesolutions.net/teamblogs/2011/12/19/cake-pattern-in-depth).
This basically involves placing code inside traits. In the RocketChip cake,
there are two kinds of traits: a LazyModule trait and a module implementation
trait.

The LazyModule trait runs setup code that must execute before all the hardware
gets elaborated. For a simple memory-mapped peripheral, this just involves
connecting the peripheral's TileLink node to the MMIO crossbar.

```scala
    trait HasPeripheryPWM extends HasSystemNetworks {
      implicit val p: Parameters

      private val address = 0x2000

      val pwm = LazyModule(new PWMTL(
        PWMParams(address, peripheryBusConfig.beatBytes))(p))

      pwm.node := TLFragmenter(
        peripheryBusConfig.beatBytes, cacheBlockBytes)(peripheryBus.node)
    }
```

Note that the PWMTL class we created from the register router is itself a
LazyModule. Register routers have a TileLike node simply named "node", which
we can hook up to the RocketChip peripheryBus. This will automatically add
address map and device tree entries for the peripheral.

The module implementation trait is where we instantiate our PWM module and
connect it to the rest of the SoC. Since this module has an extra `pwmout`
output, we declare that in this trait, using Chisel's multi-IO
functionality. We then connect the PWMTL's pwmout to the pwmout we declared.

```scala
    trait HasPeripheryPWMModuleImp extends LazyMultiIOModuleImp {
      implicit val p: Parameters
      val outer: HasPeripheryPWM

      val pwmout = IO(Output(Bool()))

      pwmout := outer.pwm.module.io.pwmout
    }
```

Now we want to mix our traits into the system as a whole. This code is from
src/main/scala/example/Top.scala.

```scala
    class ExampleTopWithPWM(q: Parameters) extends ExampleTop(q)
        with PeripheryPWM {
      override lazy val module = Module(
        new ExampleTopWithPWMModule(p, this))
    }

    class ExampleTopWithPWMModule(l: ExampleTopWithPWM)
      extends ExampleTopModule(l) with HasPeripheryPWMModuleImp
```

Just as we need separate traits for LazyModule and module implementation, we
need two classes to build the system. The ExampleTop classes already have the
basic peripherals included for us, so we will just extend those.

The ExampleTop class includes the pre-elaboration code and also a lazy val to
produce the module implementation (hence LazyModule). The ExampleTopModule
class is the actual RTL that gets synthesized.

Finally, we need to add a configuration class in
src/main/scala/example/Configs.scala that tells the TestHarness to instantiate
ExampleTopWithPWM instead of the default ExampleTop.

```scala
    class WithPWM extends Config((site, here, up) => {
      case BuildTop => (p: Parameters) =>
        Module(LazyModule(new ExampleTopWithPWM()(p)).module)
    })

    class PWMConfig extends Config(new WithPWM ++ new BaseExampleConfig)
```

Now we can test that the PWM is working. The test program is in tests/pwm.c

```c
    #define PWM_PERIOD 0x2000
    #define PWM_DUTY 0x2008
    #define PWM_ENABLE 0x2010

    static inline void write_reg(unsigned long addr, unsigned long data)
    {
            volatile unsigned long *ptr = (volatile unsigned long *) addr;
            *ptr = data;
    }

    static inline unsigned long read_reg(unsigned long addr)
    {
            volatile unsigned long *ptr = (volatile unsigned long *) addr;
            return *ptr;
    }

    int main(void)
    {
            write_reg(PWM_PERIOD, 20);
            write_reg(PWM_DUTY, 5);
            write_reg(PWM_ENABLE, 1);
    }
```

This just writes out to the registers we defined earlier. The base of the
module's MMIO region is at 0x2000. This will be printed out in the address
map portion when you generated the verilog code.

Compiling this program with make produces a `pwm.riscv` executable.

Now with all of that done, we can go ahead and run our simulation.

    cd verisim
    make CONFIG=PWMConfig
    ./simulator-example-PWMConfig ../tests/pwm.riscv

## Adding a DMA port

In the example above, we gave allowed the processor to communicate with the
peripheral through MMIO. However, for IO devices (like a disk or network
driver), we may want to have the device write directly to the coherent
memory system instead. To add a device like that, you would do the following.

```scala
    class DMADevice(implicit p: Parameters) extends LazyModule {
      val node = TLClientNode(TLClientParameters(
        name = "dma-device", sourceId = IdRange(0, 1)))

      lazy val module = new DMADeviceModule(this)
    }

    class DMADeviceModule(outer: DMADevice) extends LazyModuleImp(outer) {
      val io = IO(new Bundle {
        val mem = outer.node.bundleOut
        val ext = new ExtBundle
      })

      // ... rest of the code ...
    }

    trait HasPeripheryDMA extends HasSystemNetworks {
      implicit val p: Parameters

      val dma = LazyModule(new DMADevice)

      fsb.node := dma.node
    }

    trait HasPeripheryDMAModuleImp extends LazyMultiIOModuleImp {
      val ext = IO(new ExtBundle)
      ext <> outer.dma.module.io.ext
    }
```

The `ExtBundle` contains the signals we connect off-chip that we get data from.
The DMADevice also has a Tilelink client port that we connect into the L1-L2
crossbar through the front-side buffer (fsb). The sourceId variable given in
the TLClientNode instantiation determines the range of ids that can be used
in acquire messages from this device. Since we specified [0, 1) as our range,
only the ID 0 can be used.

## Adding a RoCC accelerator

Besides peripheral devices, a RocketChip-based SoC can also be customized with
coprocessor accelerators. Each core can have up to four accelerators that
are controlled by custom instructions and share resources with the CPU.

### A RoCC instruction

Coprocessor instructions have the following form.

    customX rd, rs1, rs2, funct

The X will be a number 0-3, and determines the opcode of the instruction,
which controls which accelerator an instruction will be routed to.
The `rd`, `rs1`, and `rs2` fields are the register numbers of the destination
register and two source registers. The `funct` field is a 7-bit integer that
the accelerator can use to distinguish different instructions from each other.

### Creating an accelerator

RoCC accelerators are lazy modules that extend the LazyRoCC class.
Their implementation should extends the LazyRoCCModule class.

```scala
    class CustomAccelerator(implicit p: Parameters) extends LazyRoCC {
      override lazy val module = new CustomAcceleratorModule(this)
    }

    class CustomAcceleratorModule(outer: CustomAccelerator) extends LazyRoCCModule(outer) {
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
```

The LazyRoCC class contains two TLOutputNode instances, `atlNode` and `tlNode`.
The former connects into a tile-local arbiter along with the backside of the
L1 instruction cache. The latter connects directly to the L1-L2 crossbar.
The corresponding Tilelink ports in the module implementation's IO bundle
are `atl` and `tl`, respectively.

The other interfaces available to the accelerator are `mem`, which provides
access to the L1 cache; `ptw` which provides access to the page-table walker;
the `busy` signal, which indicates when the accelerator is still handling an
instruction; and the `interrupt` signal, which can be used to interrupt the CPU.

Look at the examples in rocket-chip/src/main/scala/tile/LazyRocc.scala for
detailed information on the different IOs.

### Adding RoCC accelerator to Config

RoCC accelerators can be added to a core by overriding the BuildRoCC parameter
in the configuration. This takes a sequence of RoccParameters objects, one
for each accelerator you wish to add. The two required fields for this
object are `opcodes` which determines which custom opcodes get routed to the
accelerator, and `generator` which specifies how to build the accelerator itself.
For instance, if we wanted to add the previously defined accelerator and
route custom0 and custom1 instructions to it, we could do the following.

```scala
    class WithCustomAccelerator extends Config((site, here, up) => {
      case RocketTilesKey => up(RocketTilesKey, site).map { r =>
        r.copy(rocc = Seq(
          RoCCParams(
            opcodes = OpcodeSet.custom0 | OpcodeSet.custom1,
            generator = (p: Parameters) => LazyModule(new CustomAccelerator()(p)))))
      }
    })

    class CustomAcceleratorConfig extends Config(
      new WithCustomAccelerator ++ new BaseConfig)
```

## Adding a submodule

While developing, you want to include Chisel code in a submodule so that it
can be shared by different projects. To add a submodule to the project
template, make sure that your project is organized as follows.

    yourproject/
        build.sbt
        src/main/scala/
            YourFile.scala

Put this in a git repository and make it accessible. Then add it as a submodule
to the project template.

    git submodule add https://git-repository.com/yourproject.git

Then add `yourproject` to the `EXTRA_PACKAGES` variable in the Makefrag.
Now your project will be bundled into a jar file alongside the rocket-chip
and testchipip libraries. You can then import the classes defined in the
submodule in a new project.
