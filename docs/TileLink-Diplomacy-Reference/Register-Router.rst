Register Router
===============

Memory-mapped devices generally follow a common pattern. They expose a set
of registers to the CPUs. By writing to a register, the CPU can change the 
device's settings or send a command. By reading from a register, the CPU can
query the device's state or retrieve results.

While designers can manually instantiate a manager node and write the logic 
for exposing registers themselves, it's much easier to use RocketChip's
``regmap`` interface, which can generate most of the glue logic.

For TileLink devices, you can use the ``regmap`` interface by extending 
the ``TLRegisterRouter`` class, as shown in :ref:`Adding An Accelerator/Device`,
or you can create a regular LazyModule and instantiate a ``TLRegisterNode``.
This section will focus on the second method.

Basic Usage
-----------

.. code-block:: scala

    import chisel3._
    import chisel3.util._
    import freechips.rocketchip.config.Parameters
    import freechips.rocketchip.diplomacy.{SimpleDevice, AddressSet}
    import freechips.rocketchip.tilelink.TLRegisterNode

    class MyDeviceController(implicit p: Parameters) extends LazyModule {
      val device = new SimpleDevice("my-device", Seq("tutorial,my-device0"))
      val node = TLRegisterNode(
        address = Seq(AddressSet(0x10019000, 0xfff)),
        device = device,
        beatBytes = 8,
        concurrency = 1)

      lazy val module = new LazyModuleImp(this) {
        val bigReg = RegInit(0.U(64.W))
        val mediumReg = RegInit(0.U(32.W))
        val smallReg = RegInit(0.U(16.W))

        val tinyReg0 = RegInit(0.U(4.W))
        val tinyReg1 = RegInit(0.U(4.W))

        node.regmap(
          0x00 -> Seq(RegField(64, bigReg)),
          0x08 -> Seq(RegField(32, mediumReg)),
          0x0C -> Seq(RegField(16, smallReg)),
          0x0E -> Seq(
            RegField(4, tinyReg0),
            RegField(4, tinyReg1)))
      }
    }

The code example above shows a simple lazy module that uses the ``TLRegisterNode``
to memory map hardware registers of different sizes. The constructor has
two required arguments: ``address``, which is the base address of the registers,
and ``device``, which is the device tree entry. There are also two optional
arguments. The ``beatBytes`` argument is the interface width in bytes.
The default value is 4 bytes. The ``concurrency`` argument is the size of the
internal queue for TileLink requests. By default, this value is 0, which means
there will be no queue. This value must be greater than 0 if you wish to
decoupled requests and responses for register accesses. This is discussed
in :ref:`Using Functions`.

The main way to interact with the node is to call the ``regmap`` method, which
takes a sequence of pairs. The first element of the pair is an offset from the
base address. The second is a sequence of ``RegField`` objects, each of
which maps a different register. The ``RegField`` constructor takes two
arguments. The first argument is the width of the register in bits.
The second is the register itself.

Since the argument is a sequence, you can associate multiple ``RegField``
objects with an offset. If you do, the registers are read or written in parallel
when the offset is accessed. The registers are in little endian order, so the
first register in the list corresponds to the least significant bits in the
value written. In this example, if the CPU wrote to offset 0x0E with the value
0xAB, ``smallReg0`` will get the value 0xB and ``smallReg1`` would get 0xA.

Decoupled Interfaces
--------------------

Sometimes you may want to do something other than read and write from a hardware
register. The ``RegField`` interface also provides support for reading
and writing ``DecoupledIO`` interfaces. For instance, you can implement a
hardware FIFO like so.

.. code-block:: scala

    // 4-entry 64-bit queue
    val queue = Module(new Queue(UInt(64.W), 4))

    node.regmap(
      0x00 -> Seq(RegField(64, queue.io.deq, queue.io.enq)))

This variant of the ``RegField`` constructor takes three arguments instead of
two. The first argument is still the bit width. The second is the decoupled
interface to read from. The third is the decoupled interface to write to.
In this example, writing to the "register" will push the data into the queue
and reading from it will pop data from the queue.

You need not specify both read and write for a register. You can also create
read-only or write-only registers. So for the previous example, if you wanted
enqueue and dequeue to use different addresses, you could write the following.

.. code-block:: scala

    node.regmap(
      0x00 -> Seq(RegField.r(64, queue.io.deq)),
      0x08 -> Seq(RegField.w(64, queue.io.enq)))

The read-only register function can also be used to read signals
that aren't registers.

.. code-block:: scala

    val constant = 0xf00d.U

    node.regmap(
      0x00 -> Seq(RegField.r(8, constant)))

Using Functions
---------------

You can also create registers using functions. Say, for instance, that you
want to create a counter that gets incremented on a write and decremented on
a read.

.. code-block:: scala

    val counter = RegInit(0.U(64.W))

    def readCounter(ready: Bool): (Bool, UInt) = {
      when (ready) { counter := counter - 1.U }
      (true.B, counter)
    }

    def writeCounter(valid: Bool, bits: UInt): Bool = {
      when (valid) { counter := counter + 1.U }
      // Ignore bits
      true.B
    }

    node.regmap(
      0x00 -> Seq(RegField.r(64, readCounter(_))),
      0x08 -> Seq(RegField.w(64, writeCounter(_, _))))

The functions here are essentially the same as a decoupled interface.
The read function gets passed the ``ready`` signal and returns the
``valid`` and ``bits`` signals. The write function gets passed ``valid` and
``bits`` and returns ``ready``.

You can also pass functions that decouple the read/write request and response.
The request will appear as a decoupled input and the response as a decoupled
output. So for instance, if we wanted to do this for the previous example.

.. code-block:: scala

    val counter = RegInit(0.U(64.W))

    def readCounter(ivalid: Bool, oready: Bool): (Bool, Bool, UInt) = {
      val responding = RegInit(false.B)

      when (ivalid && !responding) { responding := true.B }

      when (responding && oready) { 
        counter := counter - 1.U
        responding := false.B
      }

      (!responding, responding, counter)
    }

    def writeCounter(ivalid: Bool, bits: UInt, oready: Bool): (Bool, Bool) = {
      val responding = RegInit(false.B)

      when (ivalid && !responding) { responding := true.B }

      when (responding && oready) { 
        counter := counter + 1.U
        responding := false.B
      }

      (!responding, responding)
    }

    node.regmap(
      0x00 -> Seq(RegField.r(64, readCounter(_, _))),
      0x08 -> Seq(RegField.w(64, writeCounter(_, _, _))))

In each function, we set up a state variable ``responding``. The function
is ready to take requests when this is false and is sending a response when
this is true.

In this variant, both read and write take an input valid and return an
output ready. The only different is that bits is an input for read and an
output for write.

In order to use this variant, you need to set ``concurrency`` to a value
larger than 0.

Register Routers for Other Protocols
------------------------------------

One useful feature of the register router interface is that you can easily
change the protocol being used. For instance, in the first example in
:ref:`Basic Usage`, you could simply change the ``TLRegisterNode`` to
and ``AXI4RegisterNode``.

.. code-block:: scala

    import chisel3._
    import chisel3.util._
    import freechips.rocketchip.config.Parameters
    import freechips.rocketchip.diplomacy.{SimpleDevice, AddressSet}
    import freechips.rocketchip.amba.axi4.AXI4RegisterNode

    class MyAXI4DeviceController(implicit p: Parameters) extends LazyModule {
      val node = AXI4RegisterNode(
        address = Seq(AddressSet(0x10019000, 0xfff)),
        beatBytes = 8,
        concurrency = 1)

      lazy val module = new LazyModuleImp(this) {
        val bigReg = RegInit(0.U(64.W))
        val mediumReg = RegInit(0.U(32.W))
        val smallReg = RegInit(0.U(16.W))

        val tinyReg0 = RegInit(0.U(4.W))
        val tinyReg1 = RegInit(0.U(4.W))

        node.regmap(
          0x00 -> Seq(RegField(64, bigReg)),
          0x08 -> Seq(RegField(32, mediumReg)),
          0x0C -> Seq(RegField(16, smallReg)),
          0x0E -> Seq(
            RegField(4, tinyReg0),
            RegField(4, tinyReg1)))
      }
    }

Other than the fact that AXI4 nodes don't take a ``device`` argument,
everything else is unchanged.
