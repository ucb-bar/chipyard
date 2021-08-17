package barstools.macros

import firrtl.ir.Circuit
import firrtl_interpreter.InterpretiveTester

// Functional tests on memory compiler outputs.

// Synchronous write and read back.
class SynchronousReadAndWrite extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator {
  override lazy val width = 12
  override lazy val memDepth = BigInt(2048)
  override lazy val libDepth = BigInt(1024)

  compile(mem, lib, v, synflops = true)
  val result: Circuit = execute(mem, lib, synflops = true)

  it should "run with InterpretedTester" in {
    pending // Enable this when https://github.com/freechipsproject/firrtl-interpreter/pull/88 is snapshot-published

    val addr1 = 0
    val addr1val = 0xff
    val addr2 = 42
    val addr2val = 0xf0
    val addr3 = 1 << 10
    val addr3val = 1 << 10

    val tester = new InterpretiveTester(result.serialize)
    //~ tester.setVerbose()

    tester.poke("outer_write_en", 0)
    tester.step()

    // Write addresses and read them.
    tester.poke("outer_addr", addr1)
    tester.poke("outer_din", addr1val)
    tester.poke("outer_write_en", 1)
    tester.step()
    tester.poke("outer_write_en", 0)
    tester.step()
    tester.poke("outer_addr", addr2)
    tester.poke("outer_din", addr2val)
    tester.poke("outer_write_en", 1)
    tester.step()
    tester.poke("outer_write_en", 0)
    tester.step()
    tester.poke("outer_addr", addr3)
    tester.poke("outer_din", addr3val)
    tester.poke("outer_write_en", 1)
    tester.step()
    tester.poke("outer_write_en", 0)
    tester.step()

    tester.poke("outer_addr", addr1)
    tester.step()
    tester.expect("outer_dout", addr1val)

    tester.poke("outer_addr", addr2)
    tester.step()
    tester.expect("outer_dout", addr2val)

    tester.poke("outer_addr", addr3)
    tester.step()
    tester.expect("outer_dout", addr3val)
  }
}

// Test to verify that the circuit doesn't read combinationally based on addr
// between two submemories.
class DontReadCombinationally extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator {
  override lazy val width = 8
  override lazy val memDepth = BigInt(2048)
  override lazy val libDepth = BigInt(1024)

  compile(mem, lib, v, synflops = true)
  val result: Circuit = execute(mem, lib, synflops = true)

  it should "run with InterpretedTester" in {
    pending // Enable this when https://github.com/freechipsproject/firrtl-interpreter/pull/88 is snapshot-published

    val addr1 = 0
    val addr1a = 1
    val addr2 = 1 << 10

    val tester = new InterpretiveTester(result.serialize)
    //~ tester.setVerbose()

    tester.poke("outer_write_en", 0)
    tester.step()

    // Write two addresses, one in the lower submemory and the other in the
    // higher submemory.
    tester.poke("outer_addr", addr1)
    tester.poke("outer_din", 0x11)
    tester.poke("outer_write_en", 1)
    tester.step()
    tester.poke("outer_addr", addr1a)
    tester.poke("outer_din", 0x1a)
    tester.poke("outer_write_en", 1)
    tester.step()
    tester.poke("outer_addr", addr2)
    tester.poke("outer_din", 0xaa)
    tester.poke("outer_write_en", 1)
    tester.step()
    tester.poke("outer_write_en", 0)
    tester.poke("outer_addr", addr1)
    tester.step()

    // Test that there is no combinational read.
    tester.poke("outer_addr", addr1)
    tester.expect("outer_dout", 0x11)
    tester.poke("outer_addr", addr1a)
    tester.expect("outer_dout", 0x11)
    tester.poke("outer_addr", addr2)
    tester.expect("outer_dout", 0x11)

    // And upon step it should work again.
    tester.step()
    tester.expect("outer_addr", 0xaa)
  }
}
