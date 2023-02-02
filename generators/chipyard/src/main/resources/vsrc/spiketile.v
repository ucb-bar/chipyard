import "DPI-C" function void spike_tile_reset(input int hartid);

import "DPI-C" function void spike_tile(input int hartid,
                                        input string   isa,
                                        input int      pmpregions,
                                        input int      icache_sets,
                                        input int      icache_ways,
                                        input int      dcache_sets,
                                        input int      dcache_ways,
                                        input string   cacheable,
                                        input string   uncacheable,
                                        input string   readonly_uncacheable,
                                        input string   executable,
                                        input int      icache_sourceids,
                                        input int      dcache_sourceids,
                                        input longint  reset_vector,
                                        input longint  ipc,
                                        input longint  cycle,
                                        output longint insns_retired,

                                        input bit      debug,
                                        input bit      mtip,
                                        input bit      msip,
                                        input bit      meip,
                                        input bit      seip,

                                        input bit      icache_a_ready,
                                        output bit     icache_a_valid,
                                        output longint icache_a_address,
                                        output longint icache_a_sourceid,

                                        input bit      icache_d_valid,
                                        input longint  icache_d_sourceid,
                                        input longint  icache_d_data_0,
                                        input longint  icache_d_data_1,
                                        input longint  icache_d_data_2,
                                        input longint  icache_d_data_3,
                                        input longint  icache_d_data_4,
                                        input longint  icache_d_data_5,
                                        input longint  icache_d_data_6,
                                        input longint  icache_d_data_7,

                                        input bit      dcache_a_ready,
                                        output bit     dcache_a_valid,
                                        output longint dcache_a_address,
                                        output longint dcache_a_sourceid,
                                        output bit     dcache_a_state_old,
                                        output bit     dcache_a_state_new,

                                        input bit      dcache_b_valid,
                                        input longint  dcache_b_address,
                                        input longint  dcache_b_source,
                                        input int      dcache_b_param,

                                        input bit      dcache_c_ready,
                                        output bit     dcache_c_valid,
                                        output longint dcache_c_address,
                                        output longint dcache_c_sourceid,
                                        output int     dcache_c_param,
                                        output bit     dcache_c_voluntary,
                                        output bit     dcache_c_has_data,
                                        output longint dcache_c_data_0,
                                        output longint dcache_c_data_1,
                                        output longint dcache_c_data_2,
                                        output longint dcache_c_data_3,
                                        output longint dcache_c_data_4,
                                        output longint dcache_c_data_5,
                                        output longint dcache_c_data_6,
                                        output longint dcache_c_data_7,

                                        input bit      dcache_d_valid,
                                        input bit      dcache_d_has_data,
                                        input bit      dcache_d_grantack,
                                        input longint  dcache_d_sourceid,
                                        input longint  dcache_d_data_0,
                                        input longint  dcache_d_data_1,
                                        input longint  dcache_d_data_2,
                                        input longint  dcache_d_data_3,
                                        input longint  dcache_d_data_4,
                                        input longint  dcache_d_data_5,
                                        input longint  dcache_d_data_6,
                                        input longint  dcache_d_data_7,

                                        input bit      mmio_a_ready,
                                        output bit     mmio_a_valid,
                                        output longint mmio_a_address,
                                        output longint mmio_a_data,
                                        output bit     mmio_a_store,
                                        output int     mmio_a_size,

                                        input bit      mmio_d_valid,
                                        input longint  mmio_d_data
                                        );


module SpikeBlackBox #(
                      parameter HARTID,
                      parameter ISA,
                      parameter PMPREGIONS,
                      parameter ICACHE_SETS,
                      parameter ICACHE_WAYS,
                      parameter DCACHE_SETS,
                      parameter DCACHE_WAYS,
                      parameter CACHEABLE,
                      parameter UNCACHEABLE,
                      parameter READONLY_UNCACHEABLE,
                      parameter EXECUTABLE,
                      parameter ICACHE_SOURCEIDS,
                      parameter DCACHE_SOURCEIDS )(
                                             input         clock,
                                             input         reset,
                                             input [63:0]  reset_vector,
                                             input [63:0]  ipc,
                                             input [63:0]  cycle,
                                             output [63:0] insns_retired,

                                             input         debug,
                                             input         mtip,
                                             input         msip,
                                             input         meip,
                                             input         seip,

                                             input         icache_a_ready,
                                             output        icache_a_valid,
                                             output [63:0] icache_a_address,
                                             output [63:0] icache_a_sourceid,

                                             input         icache_d_valid,
                                             input [63:0]  icache_d_sourceid,
                                             input [63:0]  icache_d_data_0,
                                             input [63:0]  icache_d_data_1,
                                             input [63:0]  icache_d_data_2,
                                             input [63:0]  icache_d_data_3,
                                             input [63:0]  icache_d_data_4,
                                             input [63:0]  icache_d_data_5,
                                             input [63:0]  icache_d_data_6,
                                             input [63:0]  icache_d_data_7,

                                             input         dcache_a_ready,
                                             output        dcache_a_valid,
                                             output [63:0] dcache_a_address,
                                             output [63:0] dcache_a_sourceid,
                                             output        dcache_a_state_old,
                                             output        dcache_a_state_new,

                                             input         dcache_b_valid,
                                             input [63:0]  dcache_b_address,
                                             input [63:0]  dcache_b_source,
                                             input [31:0]  dcache_b_param,

                                             input         dcache_c_ready,
                                             output        dcache_c_valid,
                                             output [63:0] dcache_c_address,
                                             output [63:0] dcache_c_sourceid,
                                             output [31:0] dcache_c_param,
                                             output        dcache_c_voluntary,
                                             output        dcache_c_has_data,
                                             output [63:0] dcache_c_data_0,
                                             output [63:0] dcache_c_data_1,
                                             output [63:0] dcache_c_data_2,
                                             output [63:0] dcache_c_data_3,
                                             output [63:0] dcache_c_data_4,
                                             output [63:0] dcache_c_data_5,
                                             output [63:0] dcache_c_data_6,
                                             output [63:0] dcache_c_data_7,

                                             input         dcache_d_valid,
                                             input         dcache_d_has_data,
                                             input         dcache_d_grantack,
                                             input [63:0]  dcache_d_sourceid,
                                             input [63:0]  dcache_d_data_0,
                                             input [63:0]  dcache_d_data_1,
                                             input [63:0]  dcache_d_data_2,
                                             input [63:0]  dcache_d_data_3,
                                             input [63:0]  dcache_d_data_4,
                                             input [63:0]  dcache_d_data_5,
                                             input [63:0]  dcache_d_data_6,
                                             input [63:0]  dcache_d_data_7,

                                             input         mmio_a_ready,
                                             output        mmio_a_valid,
                                             output [63:0] mmio_a_address,
                                             output [63:0] mmio_a_data,
                                             output        mmio_a_store,
                                             output [31:0] mmio_a_size,

                                             input         mmio_d_valid,
                                             input [63:0]  mmio_d_data
 );

   longint                                                 __insns_retired;
   reg [63:0]                                              __insns_retired_reg;

   wire                                                    __icache_a_ready;
   bit                                                     __icache_a_valid;
   longint                                                 __icache_a_address;
   longint                                                 __icache_a_sourceid;

   reg                                                     __icache_a_valid_reg;
   reg [63:0]                                              __icache_a_address_reg;
   reg [63:0]                                              __icache_a_sourceid_reg;

   wire                                                    __mmio_a_ready;
   bit                                                     __mmio_a_valid;
   longint                                                 __mmio_a_address;
   longint                                                 __mmio_a_data;
   bit                                                     __mmio_a_store;
   int                                                     __mmio_a_size;

   reg                                                     __mmio_a_valid_reg;
   reg [63:0]                                              __mmio_a_address_reg;
   reg [31:0]                                              __mmio_a_size_reg;
   reg [63:0]                                              __mmio_a_data_reg;
   reg                                                     __mmio_a_store_reg;

   wire                                                    __dcache_a_ready;
   bit                                                     __dcache_a_valid;
   longint                                                 __dcache_a_address;
   longint                                                 __dcache_a_sourceid;
   bit                                                     __dcache_a_state_old;
   bit                                                     __dcache_a_state_new;

   reg                                                     __dcache_a_valid_reg;
   reg [63:0]                                              __dcache_a_address_reg;
   reg [63:0]                                              __dcache_a_sourceid_reg;
   reg                                                     __dcache_a_state_old_reg;
   reg                                                     __dcache_a_state_new_reg;

   wire                                                    __dcache_c_ready;
   bit                                                     __dcache_c_valid;
   longint                                                 __dcache_c_address;
   longint                                                 __dcache_c_sourceid;
   int                                                     __dcache_c_param;
   bit                                                     __dcache_c_voluntary;
   bit                                                     __dcache_c_has_data;
   longint                                                 __dcache_c_data_0;
   longint                                                 __dcache_c_data_1;
   longint                                                 __dcache_c_data_2;
   longint                                                 __dcache_c_data_3;
   longint                                                 __dcache_c_data_4;
   longint                                                 __dcache_c_data_5;
   longint                                                 __dcache_c_data_6;
   longint                                                 __dcache_c_data_7;

   reg                                                     __dcache_c_valid_reg;
   reg [63:0]                                              __dcache_c_address_reg;
   reg [63:0]                                              __dcache_c_sourceid_reg;
   reg [31:0]                                              __dcache_c_param_reg;
   reg                                                     __dcache_c_voluntary_reg;
   reg                                                     __dcache_c_has_data_reg;
   reg [63:0]                                              __dcache_c_data_0_reg;
   reg [63:0]                                              __dcache_c_data_1_reg;
   reg [63:0]                                              __dcache_c_data_2_reg;
   reg [63:0]                                              __dcache_c_data_3_reg;
   reg [63:0]                                              __dcache_c_data_4_reg;
   reg [63:0]                                              __dcache_c_data_5_reg;
   reg [63:0]                                              __dcache_c_data_6_reg;
   reg [63:0]                                              __dcache_c_data_7_reg;




   always @(posedge clock) begin
      if (reset) begin
         __insns_retired = 64'h0;
         __insns_retired_reg <= 64'h0;

         __icache_a_valid = 1'b0;
         __icache_a_valid_reg <= 1'b0;
         __icache_a_address = 64'h0;
         __icache_a_address_reg <= 64'h0;
         __icache_a_sourceid = 64'h0;
         __icache_a_sourceid_reg <= 64'h0;

         __mmio_a_valid = 1'b0;
         __mmio_a_valid_reg <= 1'b0;
         __mmio_a_address = 64'h0;
         __mmio_a_address_reg <= 64'h0;
         __mmio_a_data = 64'h0;
         __mmio_a_data_reg <= 64'h0;
         __mmio_a_store = 1'b0;
         __mmio_a_store_reg <= 1'b0;
         __mmio_a_size = 32'h0;
         __mmio_a_size_reg <= 32'h0;

         __dcache_a_valid = 1'b0;
         __dcache_a_valid_reg <= 1'b0;
         __dcache_a_address = 64'h0;
         __dcache_a_address_reg <= 64'h0;
         __dcache_a_sourceid = 64'h0;
         __dcache_a_sourceid_reg <= 64'h0;
         __dcache_a_state_old = 1'h0;
         __dcache_a_state_old_reg <= 1'h0;
         __dcache_a_state_new = 1'h0;
         __dcache_a_state_new_reg <= 1'h0;

         __dcache_c_valid = 1'b0;
         __dcache_c_valid_reg <= 1'b0;
         __dcache_c_address = 64'h0;
         __dcache_c_address_reg <= 64'h0;
         __dcache_c_sourceid = 64'h0;
         __dcache_c_sourceid_reg <= 64'h0;
         __dcache_c_param = 32'h0;
         __dcache_c_param_reg <= 32'h0;
         __dcache_c_voluntary = 1'h0;
         __dcache_c_voluntary_reg <= 1'h0;
         __dcache_c_has_data = 1'h0;
         __dcache_c_has_data_reg <= 1'h0;
         __dcache_c_data_0 = 64'h0;
         __dcache_c_data_0_reg <= 64'h0;
         __dcache_c_data_1 = 64'h0;
         __dcache_c_data_1_reg <= 64'h0;
         __dcache_c_data_2 = 64'h0;
         __dcache_c_data_2_reg <= 64'h0;
         __dcache_c_data_3 = 64'h0;
         __dcache_c_data_3_reg <= 64'h0;
         __dcache_c_data_4 = 64'h0;
         __dcache_c_data_4_reg <= 64'h0;
         __dcache_c_data_5 = 64'h0;
         __dcache_c_data_5_reg <= 64'h0;
         __dcache_c_data_6 = 64'h0;
         __dcache_c_data_6_reg <= 64'h0;
         __dcache_c_data_7 = 64'h0;
         __dcache_c_data_7_reg <= 64'h0;
         spike_tile_reset(HARTID);
      end else begin
         spike_tile(HARTID, ISA, PMPREGIONS,
                    ICACHE_SETS, ICACHE_WAYS, DCACHE_SETS, DCACHE_WAYS,
                    CACHEABLE, UNCACHEABLE, READONLY_UNCACHEABLE, EXECUTABLE,
                    ICACHE_SOURCEIDS, DCACHE_SOURCEIDS,
                    reset_vector, ipc, cycle, __insns_retired,
                    debug, mtip, msip, meip, seip,

                    __icache_a_ready, __icache_a_valid, __icache_a_address, __icache_a_sourceid,

                    icache_d_valid, icache_d_sourceid,
                    icache_d_data_0, icache_d_data_1, icache_d_data_2, icache_d_data_3,
                    icache_d_data_4, icache_d_data_5, icache_d_data_6, icache_d_data_7,

                    __dcache_a_ready, __dcache_a_valid, __dcache_a_address, __dcache_a_sourceid, __dcache_a_state_old, __dcache_a_state_new,

                    dcache_b_valid, dcache_b_address, dcache_b_source, dcache_b_param,

                    __dcache_c_ready, __dcache_c_valid, __dcache_c_address, __dcache_c_sourceid, __dcache_c_param, __dcache_c_voluntary, __dcache_c_has_data,
                    __dcache_c_data_0, __dcache_c_data_1, __dcache_c_data_2, __dcache_c_data_3,
                    __dcache_c_data_4, __dcache_c_data_5, __dcache_c_data_6, __dcache_c_data_7,

                    dcache_d_valid, dcache_d_has_data, dcache_d_grantack, dcache_d_sourceid,
                    dcache_d_data_0, dcache_d_data_1, dcache_d_data_2, dcache_d_data_3,
                    dcache_d_data_4, dcache_d_data_5, dcache_d_data_6, dcache_d_data_7,

                    __mmio_a_ready, __mmio_a_valid, __mmio_a_address, __mmio_a_data, __mmio_a_store, __mmio_a_size,
                    mmio_d_valid, mmio_d_data
                    );
         __insns_retired_reg <= __insns_retired;


         __icache_a_valid_reg <= __icache_a_valid;
         __icache_a_address_reg <= __icache_a_address;
         __icache_a_sourceid_reg <= __icache_a_sourceid;

         __dcache_a_valid_reg <= __dcache_a_valid;
         __dcache_a_address_reg <= __dcache_a_address;
         __dcache_a_sourceid_reg <= __dcache_a_sourceid;
         __dcache_a_state_old_reg <= __dcache_a_state_old;
         __dcache_a_state_new_reg <= __dcache_a_state_new;

         __dcache_c_valid_reg <= __dcache_c_valid;
         __dcache_c_address_reg <= __dcache_c_address;
         __dcache_c_sourceid_reg <= __dcache_c_sourceid;
         __dcache_c_param_reg <= __dcache_c_param;
         __dcache_c_voluntary_reg <= __dcache_c_voluntary;
         __dcache_c_has_data_reg <= __dcache_c_has_data;
         __dcache_c_data_0_reg <= __dcache_c_data_0;
         __dcache_c_data_1_reg <= __dcache_c_data_1;
         __dcache_c_data_2_reg <= __dcache_c_data_2;
         __dcache_c_data_3_reg <= __dcache_c_data_3;
         __dcache_c_data_4_reg <= __dcache_c_data_4;
         __dcache_c_data_5_reg <= __dcache_c_data_5;
         __dcache_c_data_6_reg <= __dcache_c_data_6;
         __dcache_c_data_7_reg <= __dcache_c_data_7;

         __mmio_a_valid_reg <= __mmio_a_valid;
         __mmio_a_address_reg <= __mmio_a_address;
         __mmio_a_data_reg <= __mmio_a_data;
         __mmio_a_store_reg <= __mmio_a_store;
         __mmio_a_size_reg <= __mmio_a_size;
      end
   end // always @ (posedge clock)
   assign insns_retired = __insns_retired_reg;

   assign icache_a_valid = __icache_a_valid_reg;
   assign icache_a_address = __icache_a_address_reg;
   assign icache_a_sourceid = __icache_a_sourceid_reg;
   assign __icache_a_ready = icache_a_ready;

   assign dcache_a_valid = __dcache_a_valid_reg;
   assign dcache_a_address = __dcache_a_address_reg;
   assign dcache_a_sourceid = __dcache_a_sourceid_reg;
   assign dcache_a_state_old = __dcache_a_state_old_reg;
   assign dcache_a_state_new = __dcache_a_state_new_reg;
   assign __dcache_a_ready = dcache_a_ready;

   assign dcache_c_valid = __dcache_c_valid_reg;
   assign dcache_c_address = __dcache_c_address_reg;
   assign dcache_c_sourceid = __dcache_c_sourceid_reg;
   assign dcache_c_param = __dcache_c_param_reg;
   assign dcache_c_voluntary = __dcache_c_voluntary_reg;
   assign dcache_c_has_data = __dcache_c_has_data_reg;
   assign dcache_c_data_0 = __dcache_c_data_0_reg;
   assign dcache_c_data_1 = __dcache_c_data_1_reg;
   assign dcache_c_data_2 = __dcache_c_data_2_reg;
   assign dcache_c_data_3 = __dcache_c_data_3_reg;
   assign dcache_c_data_4 = __dcache_c_data_4_reg;
   assign dcache_c_data_5 = __dcache_c_data_5_reg;
   assign dcache_c_data_6 = __dcache_c_data_6_reg;
   assign dcache_c_data_7 = __dcache_c_data_7_reg;
   assign __dcache_c_ready = dcache_c_ready;

   assign mmio_a_valid = __mmio_a_valid_reg;
   assign mmio_a_address = __mmio_a_address_reg;
   assign mmio_a_store = __mmio_a_store_reg;
   assign mmio_a_data = __mmio_a_data_reg;
   assign mmio_a_size = __mmio_a_size_reg;
   assign __mmio_a_ready = mmio_a_ready;



endmodule;
