module SimSPIFlashModel #(
    parameter string PLUSARG,
    parameter bit READONLY,
    parameter longint CAPACITY_BYTES
) (
    input sck,
    input cs_0,
    input reset,
    inout dq_0,
    inout dq_1,
    inout dq_2,
    inout dq_3
);

    wire [3:0] dq_in;
    wire [3:0] dq_out;
    wire [3:0] dq_drive;

    wire mem_req_valid, mem_req_r_wb;
    wire [7:0] mem_req_data;
    wire [7:0] mem_resp_data;
    wire [$clog2(CAPACITY_BYTES)-1:0] mem_req_addr;

    SPIFlashMemCtrl #(
        .ADDR_BITS($clog2(CAPACITY_BYTES))
    ) ctrl (
        .sck(sck),
        .cs(cs_0),
        .reset(reset),
        .dq_in(dq_in),
        .dq_drive(dq_drive),
        .dq_out(dq_out),
        .mem_req_valid(mem_req_valid),
        .mem_req_addr(mem_req_addr),
        .mem_req_data(mem_req_data),
        .mem_req_r_wb(mem_req_r_wb),
        .mem_resp_data(mem_resp_data)
    );

    plusarg_file_mem #(
        .PLUSARG(PLUSARG),
        .ADDR_BITS($clog2(CAPACITY_BYTES)),
        .READONLY(READONLY),
        .DATA_BYTES(1)
    ) memory (
        .clock(sck),
        .reset(reset),
        .mem_req_valid(mem_req_valid),
        .mem_req_addr(mem_req_addr),
        .mem_req_data(mem_req_data),
        .mem_req_r_wb(mem_req_r_wb),
        .mem_resp_data(mem_resp_data)
    );

    assign dq_3 = (dq_drive[3] ? dq_out[3] : 1'bz);
    assign dq_2 = (dq_drive[2] ? dq_out[2] : 1'bz);
    assign dq_1 = (dq_drive[1] ? dq_out[1] : 1'bz);
    assign dq_0 = (dq_drive[0] ? dq_out[0] : 1'bz);
    assign dq_in = {dq_3, dq_2, dq_1, dq_0};

endmodule
