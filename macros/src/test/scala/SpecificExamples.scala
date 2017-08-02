package barstools.macros

import mdf.macrolib._

// Specific one-off tests to run, not created by a generator.

class RocketChipTest extends MacroCompilerSpec with HasSRAMGenerator {
  val mem = s"mem-RocketChipTest.json"
  val lib = s"lib-RocketChipTest.json"
  val v = s"RocketChipTest.v"

  val libSRAMs = Seq(
    SRAMMacro(
      name="SRAM1RW1024x8",
      depth=1024,
      width=8,
      family="1rw",
      ports=Seq(
        generateReadWritePort("", 8, 1024)
      )
    ),
    SRAMMacro(
      name="SRAM1RW512x32",
      depth=512,
      width=32,
      family="1rw",
      ports=Seq(
        generateReadWritePort("", 32, 512)
      )
    ),
    SRAMMacro(
      name="SRAM1RW64x128",
      depth=64,
      width=128,
      family="1rw",
      ports=Seq(
        generateReadWritePort("", 128, 64)
      )
    ),
    SRAMMacro(
      name="SRAM1RW64x32",
      depth=64,
      width=32,
      family="1rw",
      ports=Seq(
        generateReadWritePort("", 32, 64)
      )
    ),
    SRAMMacro(
      name="SRAM1RW64x8",
      depth=64,
      width=8,
      family="1rw",
      ports=Seq(
        generateReadWritePort("", 8, 64)
      )
    ),
    SRAMMacro(
      name="SRAM1RW512x8",
      depth=512,
      width=8,
      family="1rw",
      ports=Seq(
        generateReadWritePort("", 8, 512)
      )
    ),
    SRAMMacro(
      name="SRAM2RW64x32",
      depth=64,
      width=32,
      family="1r1w",
      ports=Seq(
        generateReadPort("portA", 32, 64),
        generateWritePort("portB", 32, 64)
      )
    )
  )

  val memSRAMs = mdf.macrolib.Utils.readMDFFromString(
"""
[
  {
    "type": "sram",
    "name": "tag_array_ext",
    "depth": 64,
    "width": 80,
    "ports": [
      {
        "clock port name": "RW0_clk",
        "mask granularity": 20,
        "output port name": "RW0_rdata",
        "input port name": "RW0_wdata",
        "address port name": "RW0_addr",
        "mask port name": "RW0_wmask",
        "chip enable port name": "RW0_en",
        "write enable port name": "RW0_wmode"
      }
    ]
  },
  {
    "type": "sram",
    "name": "T_1090_ext",
    "depth": 512,
    "width": 64,
    "ports": [
      {
        "clock port name": "RW0_clk",
        "output port name": "RW0_rdata",
        "input port name": "RW0_wdata",
        "address port name": "RW0_addr",
        "chip enable port name": "RW0_en",
        "write enable port name": "RW0_wmode"
      }
    ]
  },
  {
    "type": "sram",
    "name": "T_406_ext",
    "depth": 512,
    "width": 64,
    "ports": [
      {
        "clock port name": "RW0_clk",
        "mask granularity": 8,
        "output port name": "RW0_rdata",
        "input port name": "RW0_wdata",
        "address port name": "RW0_addr",
        "mask port name": "RW0_wmask",
        "chip enable port name": "RW0_en",
        "write enable port name": "RW0_wmode"
      }
    ]
  },
  {
    "type": "sram",
    "name": "T_2172_ext",
    "depth": 64,
    "width": 88,
    "ports": [
      {
        "clock port name": "W0_clk",
        "mask granularity": 22,
        "input port name": "W0_data",
        "address port name": "W0_addr",
        "chip enable port name": "W0_en",
        "mask port name": "W0_mask"
      },
      {
        "clock port name": "R0_clk",
        "output port name": "R0_data",
        "address port name": "R0_addr",
        "chip enable port name": "R0_en"
      }
    ]
  }
]
""").getOrElse(List())

  writeToLib(lib, libSRAMs)
  writeToMem(mem, memSRAMs)

  val output = // TODO: check correctness...
"""
circuit T_2172_ext :
  module tag_array_ext :
    input RW0_clk : Clock
    input RW0_addr : UInt<6>
    input RW0_wdata : UInt<80>
    output RW0_rdata : UInt<80>
    input RW0_en : UInt<1>
    input RW0_wmode : UInt<1>
    input RW0_wmask : UInt<4>

    inst mem_0_0 of SRAM1RW64x8
    inst mem_0_1 of SRAM1RW64x8
    inst mem_0_2 of SRAM1RW64x8
    inst mem_0_3 of SRAM1RW64x8
    inst mem_0_4 of SRAM1RW64x8
    inst mem_0_5 of SRAM1RW64x8
    inst mem_0_6 of SRAM1RW64x8
    inst mem_0_7 of SRAM1RW64x8
    inst mem_0_8 of SRAM1RW64x8
    inst mem_0_9 of SRAM1RW64x8
    inst mem_0_10 of SRAM1RW64x8
    inst mem_0_11 of SRAM1RW64x8
    mem_0_0.clk <= RW0_clk
    mem_0_0.addr <= RW0_addr
    node RW0_rdata_0_0 = bits(mem_0_0.dout, 7, 0)
    mem_0_0.din <= bits(RW0_wdata, 7, 0)
    mem_0_0.write_en <= and(and(RW0_wmode, bits(RW0_wmask, 0, 0)), UInt<1>("h1"))
    mem_0_1.clk <= RW0_clk
    mem_0_1.addr <= RW0_addr
    node RW0_rdata_0_1 = bits(mem_0_1.dout, 7, 0)
    mem_0_1.din <= bits(RW0_wdata, 15, 8)
    mem_0_1.write_en <= and(and(RW0_wmode, bits(RW0_wmask, 0, 0)), UInt<1>("h1"))
    mem_0_2.clk <= RW0_clk
    mem_0_2.addr <= RW0_addr
    node RW0_rdata_0_2 = bits(mem_0_2.dout, 3, 0)
    mem_0_2.din <= bits(RW0_wdata, 19, 16)
    mem_0_2.write_en <= and(and(RW0_wmode, bits(RW0_wmask, 0, 0)), UInt<1>("h1"))
    mem_0_3.clk <= RW0_clk
    mem_0_3.addr <= RW0_addr
    node RW0_rdata_0_3 = bits(mem_0_3.dout, 7, 0)
    mem_0_3.din <= bits(RW0_wdata, 27, 20)
    mem_0_3.write_en <= and(and(RW0_wmode, bits(RW0_wmask, 1, 1)), UInt<1>("h1"))
    mem_0_4.clk <= RW0_clk
    mem_0_4.addr <= RW0_addr
    node RW0_rdata_0_4 = bits(mem_0_4.dout, 7, 0)
    mem_0_4.din <= bits(RW0_wdata, 35, 28)
    mem_0_4.write_en <= and(and(RW0_wmode, bits(RW0_wmask, 1, 1)), UInt<1>("h1"))
    mem_0_5.clk <= RW0_clk
    mem_0_5.addr <= RW0_addr
    node RW0_rdata_0_5 = bits(mem_0_5.dout, 3, 0)
    mem_0_5.din <= bits(RW0_wdata, 39, 36)
    mem_0_5.write_en <= and(and(RW0_wmode, bits(RW0_wmask, 1, 1)), UInt<1>("h1"))
    mem_0_6.clk <= RW0_clk
    mem_0_6.addr <= RW0_addr
    node RW0_rdata_0_6 = bits(mem_0_6.dout, 7, 0)
    mem_0_6.din <= bits(RW0_wdata, 47, 40)
    mem_0_6.write_en <= and(and(RW0_wmode, bits(RW0_wmask, 2, 2)), UInt<1>("h1"))
    mem_0_7.clk <= RW0_clk
    mem_0_7.addr <= RW0_addr
    node RW0_rdata_0_7 = bits(mem_0_7.dout, 7, 0)
    mem_0_7.din <= bits(RW0_wdata, 55, 48)
    mem_0_7.write_en <= and(and(RW0_wmode, bits(RW0_wmask, 2, 2)), UInt<1>("h1"))
    mem_0_8.clk <= RW0_clk
    mem_0_8.addr <= RW0_addr
    node RW0_rdata_0_8 = bits(mem_0_8.dout, 3, 0)
    mem_0_8.din <= bits(RW0_wdata, 59, 56)
    mem_0_8.write_en <= and(and(RW0_wmode, bits(RW0_wmask, 2, 2)), UInt<1>("h1"))
    mem_0_9.clk <= RW0_clk
    mem_0_9.addr <= RW0_addr
    node RW0_rdata_0_9 = bits(mem_0_9.dout, 7, 0)
    mem_0_9.din <= bits(RW0_wdata, 67, 60)
    mem_0_9.write_en <= and(and(RW0_wmode, bits(RW0_wmask, 3, 3)), UInt<1>("h1"))
    mem_0_10.clk <= RW0_clk
    mem_0_10.addr <= RW0_addr
    node RW0_rdata_0_10 = bits(mem_0_10.dout, 7, 0)
    mem_0_10.din <= bits(RW0_wdata, 75, 68)
    mem_0_10.write_en <= and(and(RW0_wmode, bits(RW0_wmask, 3, 3)), UInt<1>("h1"))
    mem_0_11.clk <= RW0_clk
    mem_0_11.addr <= RW0_addr
    node RW0_rdata_0_11 = bits(mem_0_11.dout, 3, 0)
    mem_0_11.din <= bits(RW0_wdata, 79, 76)
    mem_0_11.write_en <= and(and(RW0_wmode, bits(RW0_wmask, 3, 3)), UInt<1>("h1"))
    node RW0_rdata_0 = cat(RW0_rdata_0_11, cat(RW0_rdata_0_10, cat(RW0_rdata_0_9, cat(RW0_rdata_0_8, cat(RW0_rdata_0_7, cat(RW0_rdata_0_6, cat(RW0_rdata_0_5, cat(RW0_rdata_0_4, cat(RW0_rdata_0_3, cat(RW0_rdata_0_2, cat(RW0_rdata_0_1, RW0_rdata_0_0)))))))))))
    RW0_rdata <= mux(UInt<1>("h1"), RW0_rdata_0, UInt<1>("h0"))

  extmodule SRAM1RW64x8 :
    input clk : Clock
    input addr : UInt<6>
    input din : UInt<8>
    output dout : UInt<8>
    input write_en : UInt<1>

    defname = SRAM1RW64x8


  module T_1090_ext :
    input RW0_clk : Clock
    input RW0_addr : UInt<9>
    input RW0_wdata : UInt<64>
    output RW0_rdata : UInt<64>
    input RW0_en : UInt<1>
    input RW0_wmode : UInt<1>

    inst mem_0_0 of SRAM1RW512x32
    inst mem_0_1 of SRAM1RW512x32
    mem_0_0.clk <= RW0_clk
    mem_0_0.addr <= RW0_addr
    node RW0_rdata_0_0 = bits(mem_0_0.dout, 31, 0)
    mem_0_0.din <= bits(RW0_wdata, 31, 0)
    mem_0_0.write_en <= and(and(RW0_wmode, UInt<1>("h1")), UInt<1>("h1"))
    mem_0_1.clk <= RW0_clk
    mem_0_1.addr <= RW0_addr
    node RW0_rdata_0_1 = bits(mem_0_1.dout, 31, 0)
    mem_0_1.din <= bits(RW0_wdata, 63, 32)
    mem_0_1.write_en <= and(and(RW0_wmode, UInt<1>("h1")), UInt<1>("h1"))
    node RW0_rdata_0 = cat(RW0_rdata_0_1, RW0_rdata_0_0)
    RW0_rdata <= mux(UInt<1>("h1"), RW0_rdata_0, UInt<1>("h0"))

  extmodule SRAM1RW512x32 :
    input clk : Clock
    input addr : UInt<9>
    input din : UInt<32>
    output dout : UInt<32>
    input write_en : UInt<1>

    defname = SRAM1RW512x32


  module T_406_ext :
    input RW0_clk : Clock
    input RW0_addr : UInt<9>
    input RW0_wdata : UInt<64>
    output RW0_rdata : UInt<64>
    input RW0_en : UInt<1>
    input RW0_wmode : UInt<1>
    input RW0_wmask : UInt<8>

    inst mem_0_0 of SRAM1RW512x8
    inst mem_0_1 of SRAM1RW512x8
    inst mem_0_2 of SRAM1RW512x8
    inst mem_0_3 of SRAM1RW512x8
    inst mem_0_4 of SRAM1RW512x8
    inst mem_0_5 of SRAM1RW512x8
    inst mem_0_6 of SRAM1RW512x8
    inst mem_0_7 of SRAM1RW512x8
    mem_0_0.clk <= RW0_clk
    mem_0_0.addr <= RW0_addr
    node RW0_rdata_0_0 = bits(mem_0_0.dout, 7, 0)
    mem_0_0.din <= bits(RW0_wdata, 7, 0)
    mem_0_0.write_en <= and(and(RW0_wmode, bits(RW0_wmask, 0, 0)), UInt<1>("h1"))
    mem_0_1.clk <= RW0_clk
    mem_0_1.addr <= RW0_addr
    node RW0_rdata_0_1 = bits(mem_0_1.dout, 7, 0)
    mem_0_1.din <= bits(RW0_wdata, 15, 8)
    mem_0_1.write_en <= and(and(RW0_wmode, bits(RW0_wmask, 1, 1)), UInt<1>("h1"))
    mem_0_2.clk <= RW0_clk
    mem_0_2.addr <= RW0_addr
    node RW0_rdata_0_2 = bits(mem_0_2.dout, 7, 0)
    mem_0_2.din <= bits(RW0_wdata, 23, 16)
    mem_0_2.write_en <= and(and(RW0_wmode, bits(RW0_wmask, 2, 2)), UInt<1>("h1"))
    mem_0_3.clk <= RW0_clk
    mem_0_3.addr <= RW0_addr
    node RW0_rdata_0_3 = bits(mem_0_3.dout, 7, 0)
    mem_0_3.din <= bits(RW0_wdata, 31, 24)
    mem_0_3.write_en <= and(and(RW0_wmode, bits(RW0_wmask, 3, 3)), UInt<1>("h1"))
    mem_0_4.clk <= RW0_clk
    mem_0_4.addr <= RW0_addr
    node RW0_rdata_0_4 = bits(mem_0_4.dout, 7, 0)
    mem_0_4.din <= bits(RW0_wdata, 39, 32)
    mem_0_4.write_en <= and(and(RW0_wmode, bits(RW0_wmask, 4, 4)), UInt<1>("h1"))
    mem_0_5.clk <= RW0_clk
    mem_0_5.addr <= RW0_addr
    node RW0_rdata_0_5 = bits(mem_0_5.dout, 7, 0)
    mem_0_5.din <= bits(RW0_wdata, 47, 40)
    mem_0_5.write_en <= and(and(RW0_wmode, bits(RW0_wmask, 5, 5)), UInt<1>("h1"))
    mem_0_6.clk <= RW0_clk
    mem_0_6.addr <= RW0_addr
    node RW0_rdata_0_6 = bits(mem_0_6.dout, 7, 0)
    mem_0_6.din <= bits(RW0_wdata, 55, 48)
    mem_0_6.write_en <= and(and(RW0_wmode, bits(RW0_wmask, 6, 6)), UInt<1>("h1"))
    mem_0_7.clk <= RW0_clk
    mem_0_7.addr <= RW0_addr
    node RW0_rdata_0_7 = bits(mem_0_7.dout, 7, 0)
    mem_0_7.din <= bits(RW0_wdata, 63, 56)
    mem_0_7.write_en <= and(and(RW0_wmode, bits(RW0_wmask, 7, 7)), UInt<1>("h1"))
    node RW0_rdata_0 = cat(RW0_rdata_0_7, cat(RW0_rdata_0_6, cat(RW0_rdata_0_5, cat(RW0_rdata_0_4, cat(RW0_rdata_0_3, cat(RW0_rdata_0_2, cat(RW0_rdata_0_1, RW0_rdata_0_0)))))))
    RW0_rdata <= mux(UInt<1>("h1"), RW0_rdata_0, UInt<1>("h0"))

  extmodule SRAM1RW512x8 :
    input clk : Clock
    input addr : UInt<9>
    input din : UInt<8>
    output dout : UInt<8>
    input write_en : UInt<1>

    defname = SRAM1RW512x8


  module T_2172_ext :
    input W0_clk : Clock
    input W0_addr : UInt<6>
    input W0_data : UInt<88>
    input W0_en : UInt<1>
    input W0_mask : UInt<4>
    input R0_clk : Clock
    input R0_addr : UInt<6>
    output R0_data : UInt<88>
    input R0_en : UInt<1>

    inst mem_0_0 of SRAM2RW64x32
    inst mem_0_1 of SRAM2RW64x32
    inst mem_0_2 of SRAM2RW64x32
    inst mem_0_3 of SRAM2RW64x32
    mem_0_0.portB_clk <= W0_clk
    mem_0_0.portB_addr <= W0_addr
    mem_0_0.portB_din <= bits(W0_data, 21, 0)
    mem_0_0.portB_write_en <= and(and(UInt<1>("h1"), bits(W0_mask, 0, 0)), UInt<1>("h1"))
    mem_0_1.portB_clk <= W0_clk
    mem_0_1.portB_addr <= W0_addr
    mem_0_1.portB_din <= bits(W0_data, 43, 22)
    mem_0_1.portB_write_en <= and(and(UInt<1>("h1"), bits(W0_mask, 1, 1)), UInt<1>("h1"))
    mem_0_2.portB_clk <= W0_clk
    mem_0_2.portB_addr <= W0_addr
    mem_0_2.portB_din <= bits(W0_data, 65, 44)
    mem_0_2.portB_write_en <= and(and(UInt<1>("h1"), bits(W0_mask, 2, 2)), UInt<1>("h1"))
    mem_0_3.portB_clk <= W0_clk
    mem_0_3.portB_addr <= W0_addr
    mem_0_3.portB_din <= bits(W0_data, 87, 66)
    mem_0_3.portB_write_en <= and(and(UInt<1>("h1"), bits(W0_mask, 3, 3)), UInt<1>("h1"))
    mem_0_0.portA_clk <= R0_clk
    mem_0_0.portA_addr <= R0_addr
    node R0_data_0_0 = bits(mem_0_0.portA_dout, 21, 0)
    mem_0_1.portA_clk <= R0_clk
    mem_0_1.portA_addr <= R0_addr
    node R0_data_0_1 = bits(mem_0_1.portA_dout, 21, 0)
    mem_0_2.portA_clk <= R0_clk
    mem_0_2.portA_addr <= R0_addr
    node R0_data_0_2 = bits(mem_0_2.portA_dout, 21, 0)
    mem_0_3.portA_clk <= R0_clk
    mem_0_3.portA_addr <= R0_addr
    node R0_data_0_3 = bits(mem_0_3.portA_dout, 21, 0)
    node R0_data_0 = cat(R0_data_0_3, cat(R0_data_0_2, cat(R0_data_0_1, R0_data_0_0)))
    R0_data <= mux(UInt<1>("h1"), R0_data_0, UInt<1>("h0"))

  extmodule SRAM2RW64x32 :
    input portA_clk : Clock
    input portA_addr : UInt<6>
    output portA_dout : UInt<32>
    input portB_clk : Clock
    input portB_addr : UInt<6>
    input portB_din : UInt<32>
    input portB_write_en : UInt<1>

    defname = SRAM2RW64x32
"""

  compileExecuteAndTest(mem, lib, v, output)
}
