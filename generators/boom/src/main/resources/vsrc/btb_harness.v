
import "DPI-C" function void initialize_btb();



import "DPI-C" function void predict_target(input longint unsigned ip,
                                            input longint unsigned hist,
                                            output bit              valid,
                                            output longint unsigned target,
                                            output bit              is_br,
                                            output bit              is_jal
                                            );

import "DPI-C" function void update_btb(input longint unsigned ip,
                                        input longint unsigned hist,
                                        input longint unsigned target,
                                        input bit              is_br,
                                        input bit              is_jal
                                        );

module BTBHarness (input clock,
                   input         reset,

                   input         req_valid,
                   input [63:0]  req_pc,
                   input [63:0]  req_hist,
                   output        req_target_valid,
                   output [63:0] req_target_pc,
                   output        req_is_br,
                   output        req_is_jal,

                   input         update_valid,
                   input [63:0]  update_pc,
                   input [63:0]  update_hist,
                   input [63:0]  update_target,
                   input         update_is_br,
                   input         update_is_jal
                   );

   initial begin
      initialize_btb();
   end

   bit     _req_target_valid;
   longint _req_target_pc;
   bit     _req_is_br;
   bit     _req_is_jal;

   reg        reg_req_target_valid;
   reg [63:0] reg_req_target_pc;
   reg        reg_req_is_br;
   reg        reg_req_is_jal;

   assign req_target_valid = reg_req_target_valid;
   assign req_target_pc = reg_req_target_pc;
   assign req_is_br = reg_req_is_br;
   assign req_is_jal = reg_req_is_jal;


   always @(posedge clock) begin
      if (reset) begin
         _req_target_valid = 0;
         _req_target_pc = 0;
         _req_is_br = 0;
         _req_is_jal = 0;

         reg_req_target_valid <= 0;
         reg_req_target_pc <= 0;
         reg_req_is_br <= 0;
         reg_req_is_jal <= 0;
      end else begin
         if (req_valid) begin
            predict_target(req_pc, req_hist, _req_target_valid, _req_target_pc, _req_is_br, _req_is_jal);
         end
         if (update_valid) begin
            update_btb(update_pc, update_hist, update_target, update_is_br, update_is_jal);
         end
         reg_req_target_valid <= _req_target_valid;
         reg_req_target_pc <= _req_target_pc;
         reg_req_is_br <= _req_is_br;
         reg_req_is_jal <= _req_is_jal;

      end
   end

endmodule
