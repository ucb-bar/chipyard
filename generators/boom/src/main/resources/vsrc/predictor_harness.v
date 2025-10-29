
import "DPI-C" function void initialize_branch_predictor();



import "DPI-C" function void predict_branch(input longint unsigned ip,
                                            input longint unsigned hist,
                                            output bit pred
                                            );

import "DPI-C" function void update_branch(input longint unsigned ip,
                                           input longint unsigned hist,
                                           input bit taken
                                           );

module BranchPredictorHarness (input clock,
                               input        reset,

                               input        req_valid,
                               input [63:0] req_pc,
                               input [63:0] req_hist,
                               output       req_taken,

                               input        update_valid,
                               input [63:0] update_pc,
                               input [63:0] update_hist,
                               input        update_taken
                               );

   initial begin
      initialize_branch_predictor();
   end

   bit _req_taken;

   reg reg_req_taken;


   assign req_taken = reg_req_taken;

   always @(posedge clock) begin
      if (reset) begin
         _req_taken = 0;
         reg_req_taken <= 0;
      end else begin
         if (req_valid) begin
            predict_branch(req_pc, req_hist, _req_taken);
         end
         if (update_valid) begin
            update_branch(update_pc, update_hist, update_taken);
         end
         reg_req_taken <= _req_taken;
         
      end
   end

endmodule
