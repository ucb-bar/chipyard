import "DPI-C" function void cospike_set_sysinfo_wrapper(
                                                 input string  isa,
                                                 input string  priv,
                                                 input int     pmpregions,
                                                 input int     maxpglevels,
                                                 input longint mem0_base,
                                                 input longint mem0_size,
                                                 input longint mem1_base,
                                                 input longint mem1_size,
                                                 input longint mem2_base,
                                                 input longint mem2_size,
                                                 input int     nharts,
                                                 input string  bootrom
                                                 );

import "DPI-C" function void cospike_cosim_wrapper(input longint cycle,
                                           input longint hartid,
                                           input bit     has_wdata,
                                           input bit     valid,
                                           input longint iaddr,
                                           input int     insn,
                                           input bit     raise_exception,
                                           input bit     raise_interrupt,
                                           input longint cause,
                                           input longint wdata,
                                           input int     priv
                                           );

import "DPI-C" function void cospike_register_memory_wrapper(input longint base,
                                                             input longint size
                                                             );


module SpikeCosim  #(
                     parameter ISA,
                     parameter PRIV,
                     parameter PMPREGIONS,
                     parameter MAXPGLEVELS,
                     parameter MEM0_BASE,
                     parameter MEM0_SIZE,
                     parameter MEM1_BASE,
                     parameter MEM1_SIZE,
                     parameter MEM2_BASE,
                     parameter MEM2_SIZE,
                     parameter NHARTS,
                     parameter BOOTROM) (
                                         input        clock,
                                         input        reset,

                                         input [63:0] cycle,

                                         input [63:0] hartid,

                                         input        trace_0_valid,
                                         input [63:0] trace_0_iaddr,
                                         input [31:0] trace_0_insn,
                                         input        trace_0_exception,
                                         input        trace_0_interrupt,
                                         input [63:0] trace_0_cause,
                                         input        trace_0_has_wdata,
                                         input [63:0] trace_0_wdata,
                                         input [2:0]  trace_0_priv,

                                         input        trace_1_valid,
                                         input [63:0] trace_1_iaddr,
                                         input [31:0] trace_1_insn,
                                         input        trace_1_exception,
                                         input        trace_1_interrupt,
                                         input [63:0] trace_1_cause,
                                         input        trace_1_has_wdata,
                                         input [63:0] trace_1_wdata,
                                         input [2:0]  trace_1_priv,

                                         input        trace_2_valid,
                                         input [63:0] trace_2_iaddr,
                                         input [31:0] trace_2_insn,
                                         input        trace_2_exception,
                                         input        trace_2_interrupt,
                                         input [63:0] trace_2_cause,
                                         input        trace_2_has_wdata,
                                         input [63:0] trace_2_wdata,
                                         input [2:0]  trace_2_priv,

                                         input        trace_3_valid,
                                         input [63:0] trace_3_iaddr,
                                         input [31:0] trace_3_insn,
                                         input        trace_3_exception,
                                         input        trace_3_interrupt,
                                         input [63:0] trace_3_cause,
                                         input        trace_3_has_wdata,
                                         input [63:0] trace_3_wdata,
                                         input [2:0]  trace_3_priv
                                         );

   initial begin
      cospike_set_sysinfo_wrapper(ISA, PRIV, PMPREGIONS, MAXPGLEVELS,
                                  MEM0_BASE, MEM0_SIZE,
                                  MEM1_BASE, MEM1_SIZE,
                                  MEM2_BASE, MEM2_SIZE,
                                  NHARTS, BOOTROM);
   end;

   always @(posedge clock) begin
      if (!reset) begin
         if (trace_0_valid || trace_0_exception || trace_0_cause) begin
            cospike_cosim_wrapper(cycle, hartid, trace_0_has_wdata, trace_0_valid, trace_0_iaddr,
                                  trace_0_insn, trace_0_exception, trace_0_interrupt, trace_0_cause,
                                  trace_0_wdata, trace_0_priv);
         end
         if (trace_1_valid || trace_1_exception || trace_1_cause) begin
            cospike_cosim_wrapper(cycle, hartid, trace_1_has_wdata, trace_1_valid, trace_1_iaddr,
                                  trace_1_insn, trace_1_exception, trace_1_interrupt, trace_1_cause,
                                  trace_1_wdata, trace_1_priv);
         end
         if (trace_2_valid || trace_2_exception || trace_2_cause) begin
            cospike_cosim_wrapper(cycle, hartid, trace_2_has_wdata, trace_2_valid, trace_2_iaddr,
                                  trace_2_insn, trace_2_exception, trace_2_interrupt, trace_2_cause,
                                  trace_2_wdata, trace_2_priv);
         end
         if (trace_3_valid || trace_3_exception || trace_3_cause) begin
            cospike_cosim_wrapper(cycle, hartid, trace_3_has_wdata, trace_3_valid, trace_3_iaddr,
                                  trace_3_insn, trace_3_exception, trace_3_interrupt, trace_3_cause,
                                  trace_3_wdata, trace_3_priv);
         end
      end
   end
endmodule; // CospikeCosim

module SpikeCosimRegisterMemory #(
                                  parameter BASE,
                                  parameter SIZE) ();
   initial begin
      cospike_register_memory_wrapper(BASE, SIZE);
   end;
endmodule; // SpikeCosimRegisterMemory
