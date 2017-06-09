# RISC-V Project Template

This is a starter template for your custom RISC-V project. It will allow you
to leverage the Chisel HDL and RocketChip SoC generator to produce a
RISC-V SoC with MMIO-mapped peripherals, DMA, and custom accelerators.

# Need experience:

Gnu/Linux
Bash
Makefile
Gcc
Debuging
C++(С)
Java
Scala
Chisel3

## Getting started

### Checking out the sources

After cloning this repo, you will need to initialize all of the submodules

    git clone https://github.com/flamederwolf/acceptable-riscv.git
    cd acceptable-riscv
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

    cd riscv-tools
    ./build.sh

### Compiling and running the Verilator simulation

To compile the example design, run make in the "verisim" directory.
This will elaborate the DefaultExampleConfig in the example project.

Note that due to mismanaged Chisel/Firrtl dependencies in the upstream
code, you may see an error like this:

    [error] (coreMacros/*:update) sbt.ResolveException: unresolved dependency: edu.berkeley.cs#firrtl_2.11;1.1-SNAPSHOT: not found

You can work around this error by first building Firrtl and publishing
the jar locally:

    cd rocket-chip/firrtl; sbt publish-local

Once these issues are resolved, an executable called
simulator-example-DefaultExampleConfig will be produced.
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
 * testchipip - contains the serial adapter and associated verilog and C++ code
 * verisim - directory in which Verilator simulations are compiled and run
 * vsim - directory in which Synopsys VCS simulations are compiled and run
 * bootrom - sources for the first-stage bootloader included in the Boot ROM
 * src/main/scala - scala source files for your project go here

## Creating your own project

To create your own project, you should create your own scala package.
Let's use the PWM package as an example. First you create a directory
under src/main/scala that has the same name as your package.

    mkdir src/main/scala/pwm

Now let's add a peripheral device to the new SoC. First, add a Chisel module
for your device.

    package pwm

    import chisel3._
    import cde.{Parameters, Field}
    import uncore.tilelink._

    class PWMTL(implicit p: Parameters) extends Module {
      val io = new Bundle {
        val tl = new ClientUncachedTileLinkIO().flip
        val pwmout = Bool(OUTPUT)
      }

      // ...
    }

The `io` bundle holds the ports that this module exposes externally. It has
two parts, a uncached TileLink port (tl), and a single-bit output (pwmout).
The TL port is how the core communicates to the peripheral over MMIO.
The pwmout signal is what we will drive as our PWM output.

Note that we have made our package `pwm` to match the subdirectory name.
Also note that we have imported the chisel3 package, which contains all the HDL
directives; cde.Parameters, an object which allows us to pass configurations
to different parts of the code (mainly used by TileLink in this case); and
the TileLink definitions from the uncore.tilelink package.

The full module code, with comments can be found in src/main/scala/pwm/PWM.scala.

After creating the module, we need to hook it up to our SoC. Rocketchip
accomplishes this using the [cake pattern](http://www.cakesolutions.net/teamblogs/2011/12/19/cake-pattern-in-depth).
This basically involves placing code inside traits. In RocketChip, there are
three kinds of traits: a LazyModule trait, and IO bundle trait, and a module
implementation trait.

The LazyModule trait runs setup code that must execute before all the hardware
gets elaborated. For a simple memory-mapped peripheral, this just involves
adding an entry to the address map. The SoC generator provides each leaf node
in the address map with a port from the MMIO interconnect.

    import junctions._
    import diplomacy._
    import rocketchip._

    trait PeripheryPWM extends LazyModule {
      val pDevices: ResourceManager[AddrMapEntry]

      pDevices.add(AddrMapEntry("pwm", MemSize(4096, MemAttr(AddrMapProt.RW))))
    }

This adds an entry called "pwm" and makes it 4096 bytes in size. This is more
than we really need, but to play nicely with the core's virtual memory system,
address map regions must be page-aligned. We also give this regions
read/write permissions. This device can be loaded from or stored to,
but CPU instructions cannot be fetched from this location.

The IO bundle trait contains the extra IO ports that will be exported off-chip.
For the PWM peripheral, this will just be the `pwmout` pin.

    trait PeripheryPWMBundle {
      val pwmout = Bool(OUTPUT)
    }

The module implementation trait is where we instantiate our PWM module and
connect it to the rest of the SoC. 

    case object BuildPWM extends Field[(ClientUncachedTileLinkIO, Parameters) => Bool]

    trait PeripheryPWMModule extends HasPeripheryParameters {
      val pBus: TileLinkRecursiveInterconnect
      val io: PeripheryPWMBundle

      io.pwmout := p(BuildPWM)(pBus.port("pwm"), outerMMIOParams)
    }

We just need to connect the MMIO TileLink port to the PWM module's TileLink port
and connect the PWM module's `pwmout` pin to the `pwmout` pin going off-chip.
We would like to do this in a configurable way so that we can swap out the
PWM module if need be. To do this, we create a new Field for the Parameters
object that produces a function taking in the Tilelink port and returning
the pwmout as a Bool. We will define this function later in the configuration
file.

Note that we extend the HasPeripheryParameters trait. This provides us the
`outerMMIOParams` parameter object, which gets passed in as the `p` parameters
object to the PWM module. We have several parameters objects because different
TileLink interfaces need different configurations. For all MMIO ports
(i.e. those coming from pBus), you will want to use `outerMMIOParams`.
Peripheral devices can also connect TileLink client ports going into the
coreplex, which use the `innerParams` object.

Now we want to mix our traits into the system as a whole. This code is from
src/main/scala/pwm/Top.scala.

    package pwm

    import chisel3._
    import example._
    import cde.Parameters

    class ExampleTopWithPWM(q: Parameters) extends ExampleTop(q)
        with PeripheryPWM {
      override lazy val module = Module(
        new ExampleTopWithPWMModule(p, this, new ExampleTopWithPWMBundle(p)))
    }

    class ExampleTopWithPWMBundle(p: Parameters) extends ExampleTopBundle(p)
      with PeripheryPWMBundle

    class ExampleTopWithPWMModule(p: Parameters, l: ExampleTopWithPWM, b: => ExampleTopWithPWMBundle)
      extends ExampleTopModule(p, l, b) with PeripheryPWMModule

Just as we have three traits, we have three classes to build the system.
The ExampleTop classes from the example package already have the basic
peripherals included for us, so we will just extend those.

The ExampleTop class includes the pre-elaboration code and also a lazy val
to produce the module implementation (hence LazyModule). The ExampleTopBundle
class becomes the top-level IO of the module implementation. And finally the
ExampleTopModule class is the actual RTL that gets synthesized.

Now we have the RTL for the chip, but we need a test harness to simulate it.

    package pwm

    import cde.Parameters
    import diplomacy.LazyModule

    class TestHarness(q: Parameters) extends example.TestHarness()(q) {
      override def buildTop(p: Parameters) =
        LazyModule(new ExampleTopWithPWM(p))
    }

We just extend the TestHarness from the example package, which already has
the extra RTL to simulate the memory system and tethered serial port.
It provides us the hook function `buildTop` which we can override to build
our ExampleTopWithPWM instead of the regular ExampleTop.

We also need to create a Generator object, which gets called as the entry
point for elaboration.

    object Generator extends GeneratorApp {
      val longName = names.topModuleProject + "." +
                     names.topModuleClass + "." +
                     names.configs
      generateFirrtl
    }

Finally, we need to add a configuration class in src/main/scala/pwm/Configs.scala.
This defines all the settings in the Parameters object.

    package pwm

    import cde.{Parameters, Config, CDEMatchError}

    class WithPWMTL extends Config(
      (pname, site, here) => pname match {
        case BuildPWM => (port: ClientUncachedTileLinkIO, p: Parameters) => {
          val pwm = Module(new PWMTL()(p))
          pwm.io.tl <> port
          pwm.io.pwmout
        }
      })

    class PWMTLConfig extends Config(new WithPWMTL ++ new example.DefaultExampleConfig)

The only thing we need to add to the DefaultExampleConfig is the definition
of the BuildPWM field. We just instantiate our PWMTL module, connect the
TileLink port and pass out the `pwmout` signal.

Now we can test that the PWM is working. The test program is in tests/pwm.c

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

This just writes out to the registers we defined earlier. The base of the
module's MMIO region is at 0x2000. This will be printed out in the address
map portion when you generated the verilog code.

Compiling this program with make produces a `pwm.riscv` executable.

Now with all of that done, we can go ahead and run our simulation.

    cd verisim
    make PROJECT=pwm CONFIG=PWMTLConfig
    ./simulator-pwm-PWMTLConfig ../tests/pwm.riscv

## Adding a DMA port

In the example above, we gave allowed the processor to communicate with the
peripheral through MMIO. However, for IO devices (like a disk or network
driver), we may want to have the device write directly to the coherent
memory system instead. To add a device like that, you would do the following.

    package dmadevice
    
    import chisel3._
    import cde.Parameters

    class ExtBundle extends Bundle {
        ...
    }

    class DMADevice(implicit p: Parameters) extends Module {
      val io = new Bundle {
        val tl = new ClientUncachedTileLinkIO
        val ext = new ExtBundle
      }

      ...
    }

    trait PeripheryDMA extends LazyModule {
      val pBusMasters: RangeManager

      pBusMasters.add("dma", 1)
    }

    trait PeripheryDMABundle extends HasPeripheryParameters {
      val ext = new ExtBundle
    }

    trait PeripheryDMAModule {
      val outer: PeripheryDMA
      val io: PeripheryDMABundle
      val coreplexIO: BaseCoreplexBundle

      val (r_start, r_end) = outer.pBusMasters.range("dma")

      val device = Module(new DMADevice()(innerParams))
      io.ext <> device.io.ext
      coreplexIO.slave(r_start) <> device.io.tl
    }

The `ExtBundle` contains the signals we connect off-chip that we get data from.
The DMADevice also has a Tilelink port to communicate with the coherent memory
system (note that there's no .flip() call). Another thing to note is that
when we instantiate DMADevice in PeripheryDMAModule, the Parameters object
we give to it is `innerParams` instead of `outerMMIOParams`.

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

RoCC accelerators should extends the RoCC class.

    package accel

    import chisel3._
    import rocket._

    class CustomAccelerator(implicit p: Parameters) extends RoCC()(p) {
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

The other interfaces available to the accelerator are `mem`, which provides
access to the L1 cache, `ptw` which provides access to the page-table walker,
`autl` which provides shared access to the L2 alongside the ICache refill,
and `utl` which provides dedicated access to the L2.

Look at the examples in rocket-chip/src/main/scala/rocket/rocc.scala for
detailed information on the different IOs

### Adding RoCC accelerator to Config

RoCC accelerators can be added to a core by overriding the BuildRoCC parameter
in the configuration. This takes a sequence of RoccParameters objects, one
for each accelerator you wish to add. The two required fields for this
object are `opcodes` which determines which custom opcodes get routed to the
accelerator, and `generator` which specifies how to build the accelerator itself.
For instance, if we wanted to add the previously defined accelerator and
route custom0 and custom1 instructions to it, we could do the following.

    class WithCustomAccelerator extends Config(
      (pname, site, here) => pname match {
        case BuildRoCC => Seq(
          opcodes = OpcodeSet.custom0 | OpcodeSet.custom1,
          generator = (p: Parameters) => Module(new CustomAccelerator()(p)))
      })

    class CustomAcceleratorConfig extends Config(
      new WithCustomAccelerator ++ new BaseConfig)

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
