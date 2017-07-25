//~ package barstools.tapeout.transforms.macros

//~ import java.io.File

//~ class Synflops2048x16_mrw extends MacroCompilerSpec {
  //~ val mem = new File(macroDir, "mem-2048x16-mrw.json")
  //~ val v = new File(testDir, "syn_flops_2048x16_mrw.v")
  //~ val output =
//~ """
//~ circuit name_of_sram_module :
  //~ module name_of_sram_module :
    //~ input clock : Clock
    //~ input RW0A : UInt<11>
    //~ input RW0I : UInt<16>
    //~ output RW0O : UInt<16>
    //~ input RW0E : UInt<1>
    //~ input RW0W : UInt<1>
    //~ input RW0M : UInt<2>

    //~ mem ram :
      //~ data-type => UInt<8>[2]
      //~ depth => 2048
      //~ read-latency => 0
      //~ write-latency => 1
      //~ reader => R_0
      //~ writer => W_0
      //~ read-under-write => undefined
    //~ reg R_0_addr_reg : UInt<11>, clock with :
      //~ reset => (UInt<1>("h0"), R_0_addr_reg)
    //~ ram.R_0.clk <= clock
    //~ ram.R_0.addr <= R_0_addr_reg
    //~ ram.R_0.en <= RW0E
    //~ RW0O <= cat(ram.R_0.data[1], ram.R_0.data[0])
    //~ R_0_addr_reg <= mux(RW0E, RW0A, R_0_addr_reg)
    //~ ram.W_0.clk <= clock
    //~ ram.W_0.addr <= RW0A
    //~ ram.W_0.en <= and(RW0E, RW0W)
    //~ ram.W_0.data[0] <= bits(RW0I, 7, 0)
    //~ ram.W_0.data[1] <= bits(RW0I, 15, 8)
    //~ ram.W_0.mask[0] <= bits(RW0M, 0, 0)
    //~ ram.W_0.mask[1] <= bits(RW0M, 1, 1)
//~ """
  //~ compile(mem, None, v, true)
  //~ execute(Some(mem), None, true, output)
//~ }

//~ class Synflops2048x8_r_mw extends MacroCompilerSpec {
  //~ val mem = new File(macroDir, "mem-2048x8-r-mw.json")
  //~ val v = new File(testDir, "syn_flops_2048x8_r_mw.v")
  //~ val output =
//~ """
//~ circuit name_of_sram_module :
  //~ module name_of_sram_module :
    //~ input clock : Clock
    //~ input W0A : UInt<11>
    //~ input W0I : UInt<8>
    //~ input W0E : UInt<1>
    //~ input W0M : UInt<1>
    //~ input clock : Clock
    //~ input R0A : UInt<11>
    //~ output R0O : UInt<8>

    //~ mem ram :
      //~ data-type => UInt<8>[1]
      //~ depth => 2048
      //~ read-latency => 0
      //~ write-latency => 1
      //~ reader => R_0
      //~ writer => W_0
      //~ read-under-write => undefined
    //~ reg R_0_addr_reg : UInt<11>, clock with :
      //~ reset => (UInt<1>("h0"), R_0_addr_reg)
    //~ ram.R_0.clk <= clock
    //~ ram.R_0.addr <= R_0_addr_reg
    //~ ram.R_0.en <= UInt<1>("h1")
    //~ R0O <= ram.R_0.data[0]
    //~ R_0_addr_reg <= mux(UInt<1>("h1"), R0A, R_0_addr_reg)
    //~ ram.W_0.clk <= clock
    //~ ram.W_0.addr <= W0A
    //~ ram.W_0.en <= W0E
    //~ ram.W_0.data[0] <= bits(W0I, 7, 0)
    //~ ram.W_0.mask[0] <= bits(W0M, 0, 0)
//~ """
  //~ compile(mem, None, v, true)
  //~ execute(Some(mem), None, true, output)
//~ }

//~ class Synflops2048x10_rw extends MacroCompilerSpec {
  //~ val mem = new File(macroDir, "lib-2048x10-rw.json")
  //~ val v = new File(testDir, "syn_flops_2048x10_rw.v")
  //~ val output =
//~ """
//~ circuit vendor_sram :
  //~ module vendor_sram :
    //~ input clock : Clock
    //~ input RW0A : UInt<11>
    //~ input RW0I : UInt<10>
    //~ output RW0O : UInt<10>
    //~ input RW0E : UInt<1>
    //~ input RW0W : UInt<1>

    //~ mem ram :
      //~ data-type => UInt<10>
      //~ depth => 2048
      //~ read-latency => 0
      //~ write-latency => 1
      //~ reader => R_0
      //~ writer => W_0
      //~ read-under-write => undefined
    //~ reg R_0_addr_reg : UInt<11>, clock with :
      //~ reset => (UInt<1>("h0"), R_0_addr_reg)
    //~ ram.R_0.clk <= clock
    //~ ram.R_0.addr <= R_0_addr_reg
    //~ ram.R_0.en <= RW0E
    //~ RW0O <= ram.R_0.data
    //~ R_0_addr_reg <= mux(RW0E, RW0A, R_0_addr_reg)
    //~ ram.W_0.clk <= clock
    //~ ram.W_0.addr <= RW0A
    //~ ram.W_0.en <= and(RW0E, RW0W)
    //~ ram.W_0.data <= RW0I
    //~ ram.W_0.mask <= UInt<1>("h1")
//~ """
  //~ compile(mem, None, v, true)
  //~ execute(Some(mem), None, true, output)
//~ }

//~ class Synflops2048x8_mrw_re extends MacroCompilerSpec {
  //~ val mem = new File(macroDir, "lib-2048x8-mrw-re.json")
  //~ val v = new File(testDir, "syn_flops_2048x8_mrw_re.v")
  //~ val output =
//~ """
//~ circuit vendor_sram :
  //~ module vendor_sram :
    //~ input clock : Clock
    //~ input RW0A : UInt<11>
    //~ input RW0I : UInt<8>
    //~ output RW0O : UInt<8>
    //~ input RW0E : UInt<1>
    //~ input RW0R : UInt<1>
    //~ input RW0W : UInt<1>
    //~ input RW0M : UInt<1>

    //~ mem ram :
      //~ data-type => UInt<8>[1]
      //~ depth => 2048
      //~ read-latency => 0
      //~ write-latency => 1
      //~ reader => R_0
      //~ writer => W_0
      //~ read-under-write => undefined
    //~ reg R_0_addr_reg : UInt<11>, clock with :
      //~ reset => (UInt<1>("h0"), R_0_addr_reg)
    //~ ram.R_0.clk <= clock
    //~ ram.R_0.addr <= R_0_addr_reg
    //~ ram.R_0.en <= and(RW0E, not(RW0R))
    //~ RW0O <= ram.R_0.data[0]
    //~ R_0_addr_reg <= mux(and(RW0E, not(RW0R)), RW0A, R_0_addr_reg)
    //~ ram.W_0.clk <= clock
    //~ ram.W_0.addr <= RW0A
    //~ ram.W_0.en <= and(RW0E, RW0W)
    //~ ram.W_0.data[0] <= bits(RW0I, 7, 0)
    //~ ram.W_0.mask[0] <= bits(RW0M, 0, 0)
//~ """
  //~ compile(mem, None, v, true)
  //~ execute(Some(mem), None, true, output)
//~ }

//~ class Synflops2048x16_n28 extends MacroCompilerSpec {
  //~ val mem = new File(macroDir, "lib-2048x16-n28.json")
  //~ val v = new File(testDir, "syn_flops_2048x16_n28.v")
  //~ val output =
//~ """
//~ circuit vendor_sram_4 :
  //~ module vendor_sram_16 :
    //~ input clock : Clock
    //~ input RW0A : UInt<11>
    //~ input RW0I : UInt<16>
    //~ output RW0O : UInt<16>
    //~ input RW0E : UInt<1>
    //~ input RW0W : UInt<1>
    //~ input RW0M : UInt<16>

    //~ mem ram :
      //~ data-type => UInt<1>[16]
      //~ depth => 2048
      //~ read-latency => 0
      //~ write-latency => 1
      //~ reader => R_0
      //~ writer => W_0
      //~ read-under-write => undefined
    //~ reg R_0_addr_reg : UInt<11>, clock with :
      //~ reset => (UInt<1>("h0"), R_0_addr_reg)
    //~ ram.R_0.clk <= clock
    //~ ram.R_0.addr <= R_0_addr_reg
    //~ ram.R_0.en <= RW0E
    //~ RW0O <= cat(ram.R_0.data[15], cat(ram.R_0.data[14], cat(ram.R_0.data[13], cat(ram.R_0.data[12], cat(ram.R_0.data[11], cat(ram.R_0.data[10], cat(ram.R_0.data[9], cat(ram.R_0.data[8], cat(ram.R_0.data[7], cat(ram.R_0.data[6], cat(ram.R_0.data[5], cat(ram.R_0.data[4], cat(ram.R_0.data[3], cat(ram.R_0.data[2], cat(ram.R_0.data[1], ram.R_0.data[0])))))))))))))))
    //~ R_0_addr_reg <= mux(RW0E, RW0A, R_0_addr_reg)
    //~ ram.W_0.clk <= clock
    //~ ram.W_0.addr <= RW0A
    //~ ram.W_0.en <= and(RW0E, RW0W)
    //~ ram.W_0.data[0] <= bits(RW0I, 0, 0)
    //~ ram.W_0.data[1] <= bits(RW0I, 1, 1)
    //~ ram.W_0.data[2] <= bits(RW0I, 2, 2)
    //~ ram.W_0.data[3] <= bits(RW0I, 3, 3)
    //~ ram.W_0.data[4] <= bits(RW0I, 4, 4)
    //~ ram.W_0.data[5] <= bits(RW0I, 5, 5)
    //~ ram.W_0.data[6] <= bits(RW0I, 6, 6)
    //~ ram.W_0.data[7] <= bits(RW0I, 7, 7)
    //~ ram.W_0.data[8] <= bits(RW0I, 8, 8)
    //~ ram.W_0.data[9] <= bits(RW0I, 9, 9)
    //~ ram.W_0.data[10] <= bits(RW0I, 10, 10)
    //~ ram.W_0.data[11] <= bits(RW0I, 11, 11)
    //~ ram.W_0.data[12] <= bits(RW0I, 12, 12)
    //~ ram.W_0.data[13] <= bits(RW0I, 13, 13)
    //~ ram.W_0.data[14] <= bits(RW0I, 14, 14)
    //~ ram.W_0.data[15] <= bits(RW0I, 15, 15)
    //~ ram.W_0.mask[0] <= bits(RW0M, 0, 0)
    //~ ram.W_0.mask[1] <= bits(RW0M, 1, 1)
    //~ ram.W_0.mask[2] <= bits(RW0M, 2, 2)
    //~ ram.W_0.mask[3] <= bits(RW0M, 3, 3)
    //~ ram.W_0.mask[4] <= bits(RW0M, 4, 4)
    //~ ram.W_0.mask[5] <= bits(RW0M, 5, 5)
    //~ ram.W_0.mask[6] <= bits(RW0M, 6, 6)
    //~ ram.W_0.mask[7] <= bits(RW0M, 7, 7)
    //~ ram.W_0.mask[8] <= bits(RW0M, 8, 8)
    //~ ram.W_0.mask[9] <= bits(RW0M, 9, 9)
    //~ ram.W_0.mask[10] <= bits(RW0M, 10, 10)
    //~ ram.W_0.mask[11] <= bits(RW0M, 11, 11)
    //~ ram.W_0.mask[12] <= bits(RW0M, 12, 12)
    //~ ram.W_0.mask[13] <= bits(RW0M, 13, 13)
    //~ ram.W_0.mask[14] <= bits(RW0M, 14, 14)
    //~ ram.W_0.mask[15] <= bits(RW0M, 15, 15)

  //~ module vendor_sram_4 :
    //~ input clock : Clock
    //~ input RW0A : UInt<11>
    //~ input RW0I : UInt<4>
    //~ output RW0O : UInt<4>
    //~ input RW0E : UInt<1>
    //~ input RW0W : UInt<1>
    //~ input RW0M : UInt<4>

    //~ mem ram :
      //~ data-type => UInt<1>[4]
      //~ depth => 2048
      //~ read-latency => 0
      //~ write-latency => 1
      //~ reader => R_0
      //~ writer => W_0
      //~ read-under-write => undefined
    //~ reg R_0_addr_reg : UInt<11>, clock with :
      //~ reset => (UInt<1>("h0"), R_0_addr_reg)
    //~ ram.R_0.clk <= clock
    //~ ram.R_0.addr <= R_0_addr_reg
    //~ ram.R_0.en <= RW0E
    //~ RW0O <= cat(ram.R_0.data[3], cat(ram.R_0.data[2], cat(ram.R_0.data[1], ram.R_0.data[0])))
    //~ R_0_addr_reg <= mux(RW0E, RW0A, R_0_addr_reg)
    //~ ram.W_0.clk <= clock
    //~ ram.W_0.addr <= RW0A
    //~ ram.W_0.en <= and(RW0E, RW0W)
    //~ ram.W_0.data[0] <= bits(RW0I, 0, 0)
    //~ ram.W_0.data[1] <= bits(RW0I, 1, 1)
    //~ ram.W_0.data[2] <= bits(RW0I, 2, 2)
    //~ ram.W_0.data[3] <= bits(RW0I, 3, 3)
    //~ ram.W_0.mask[0] <= bits(RW0M, 0, 0)
    //~ ram.W_0.mask[1] <= bits(RW0M, 1, 1)
    //~ ram.W_0.mask[2] <= bits(RW0M, 2, 2)
    //~ ram.W_0.mask[3] <= bits(RW0M, 3, 3)
//~ """
  //~ compile(mem, None, v, true)
  //~ execute(Some(mem), None, true, output)
//~ }

//~ class Synflops32x32_2rw extends MacroCompilerSpec {
  //~ val mem = new File(macroDir, "lib-32x32-2rw.json")
  //~ val v = new File(testDir, "syn_flops_32x32_2rw.v")
  //~ val output =
//~ """
//~ circuit SRAM2RW32x32 :
  //~ module SRAM2RW32x32 :
    //~ input CE1 : Clock
    //~ input A1 : UInt<5>
    //~ input I1 : UInt<32>
    //~ output O1 : UInt<32>
    //~ input CSB1 : UInt<1>
    //~ input OEB1 : UInt<1>
    //~ input WEB1 : UInt<1>
    //~ input CE2 : Clock
    //~ input A2 : UInt<5>
    //~ input I2 : UInt<32>
    //~ output O2 : UInt<32>
    //~ input CSB2 : UInt<1>
    //~ input OEB2 : UInt<1>
    //~ input WEB2 : UInt<1>

    //~ mem ram :
      //~ data-type => UInt<32>
      //~ depth => 32
      //~ read-latency => 0
      //~ write-latency => 1
      //~ reader => R_0
      //~ reader => R_1
      //~ writer => W_0
      //~ writer => W_1
      //~ read-under-write => undefined
    //~ reg R_0_addr_reg : UInt<5>, CE1 with :
      //~ reset => (UInt<1>("h0"), R_0_addr_reg)
    //~ ram.R_0.clk <= CE1
    //~ ram.R_0.addr <= R_0_addr_reg
    //~ ram.R_0.en <= and(not(CSB1), not(OEB1))
    //~ O1 <= ram.R_0.data
    //~ R_0_addr_reg <= mux(and(not(CSB1), not(OEB1)), A1, R_0_addr_reg)
    //~ reg R_1_addr_reg : UInt<5>, CE2 with :
      //~ reset => (UInt<1>("h0"), R_1_addr_reg)
    //~ ram.R_1.clk <= CE2
    //~ ram.R_1.addr <= R_1_addr_reg
    //~ ram.R_1.en <= and(not(CSB2), not(OEB2))
    //~ O2 <= ram.R_1.data
    //~ R_1_addr_reg <= mux(and(not(CSB2), not(OEB2)), A2, R_1_addr_reg)
    //~ ram.W_0.clk <= CE1
    //~ ram.W_0.addr <= A1
    //~ ram.W_0.en <= and(not(CSB1), not(WEB1))
    //~ ram.W_0.data <= I1
    //~ ram.W_0.mask <= UInt<1>("h1")
    //~ ram.W_1.clk <= CE2
    //~ ram.W_1.addr <= A2
    //~ ram.W_1.en <= and(not(CSB2), not(WEB2))
    //~ ram.W_1.data <= I2
    //~ ram.W_1.mask <= UInt<1>("h1")
//~ """
  //~ compile(mem, None, v, true)
  //~ execute(Some(mem), None, true, output)
//~ }
