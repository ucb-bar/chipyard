module EE290RoCCAccelWithCacheBlackBox(
    input         clock,
    input         reset,
    input         cmd_valid,
    output reg    cmd_ready,
    input  [63:0] rs1,
    input  [63:0] rs2,
    input  [4:0]  rd,
    input  [6:0]  funct,
    output reg    resp_valid,
    input         resp_ready,
    output reg [4:0]  resp_rd,
    output reg [63:0] resp_data,
    output reg        busy,

    // Cache request interface
    output reg        mem_req_valid,
    input             mem_req_ready,
    output reg [63:0] mem_req_addr,
    output reg [63:0] mem_req_tag,
    output reg        mem_req_wen,

    output reg [63:0] mem_wdata,
    input             s2_nack,

    // Cache response interface
    input             mem_resp_valid,
    input             mem_resp_replay,
    input             mem_resp_has_data,
    input  [63:0]     mem_resp_data_raw
);

// localparam for state machine
localparam  sIdle      = 4'b0000,
            sReqLoad1  = 4'b0001,
            sWaitLoad1 = 4'b0010,
            sRspLoad1  = 4'b0011,
            sWaitLoad2 = 4'b0100,
            sReqLoad2  = 4'b0101,
            sRspLoad2  = 4'b0110,
            sProcess   = 4'b0111,
            sRespond   = 4'b1000;

// localparam for rocc funct codes
localparam cmdConfig = 1'b1,
           cmdCompute = 1'b0;

// State register and variable for next state logic
reg [3:0] state;
reg [3:0] next_state;
reg [63:0] operand1, operand2; // Registers to hold the loaded operands
reg [4:0] rd_reg; // Register to hold the destination regiszter

// Buffer to accumulate result into
reg [63:0] result_reg;

reg [63:0] rs1_ptr, rs2_ptr; // Registers to hold the loaded operands' pointers

// Create a counter for memory sequences
reg [3:0] mem_seq;

// Create a counter to store number of elements to compute
reg [15:0] num_elements;
reg [15:0] element_counter;

// Update memory sequence
always @(posedge clock) begin
    if (reset) begin
        mem_seq <= 4'b1;
    end else begin
        if (cmd_valid && funct == cmdCompute) begin
            mem_seq <= 4'b1;
        end
        if(mem_req_valid) begin
            mem_seq <= mem_seq + 1;
        end
    end
end

// Update element max register
always @(posedge clock) begin
    if (reset) begin
        num_elements <= 16'b1;
        element_counter <= 16'b0;
    end else begin
        if(state == sProcess) begin
            element_counter <= element_counter + 1;
        end
        if (state == sIdle) begin
            element_counter <= 0;
        end
        if (cmd_valid && funct == cmdConfig) begin
            num_elements <= rs1;
        end
    end
end





// Function to perform bit matrix multiplication
function [63:0] bit_matrix_mult;
    input [63:0] a, b;
    integer i, j, k;
    reg [7:0] row_a, col_b;
    reg result_bit;
    begin
        // Initialize the result of the function
        bit_matrix_mult = 0;
        // Loop through each row and column to perform the matrix multiplication
        for (i = 0; i < 8; i = i + 1) begin
            for (j = 0; j < 8; j = j + 1) begin
                result_bit = 0;
                // TODO: Extract a row from matrix A
                for (k = 0; k < 8; k = k + 1) begin
                    row_a[k] = a[i*8+k];
                    col_b[k] = b[k*8+j];
                end
                // TODO: Extract a column from matrix B
                // TODO: Perform the bitwise AND and then reduce using XOR for each bit in the row and column
                result_bit = ^(row_a & col_b);
                // Set the result bit in the result matrix
                bit_matrix_mult[i*8+j] = result_bit;
            end
        end
    end
endfunction

// State Logic
always @(posedge clock) begin
    if (reset) begin
        state <= sIdle;
    end else begin
        state <= next_state;
    end
end

// Address register logic
always @(posedge clock) begin
    if (reset) begin
        rs1_ptr <= 64'b0;
        rs2_ptr <= 64'b0;
        rd_reg <= 5'b0;
    end else begin
        // TODO: Store the rs1 and rs2 values into rs1_ptr and rs2_ptr when a cmdCompute is received
        // Store the rd value into rd_reg when a cmdCompute is received
        if(cmd_valid && funct == cmdCompute) begin
            rs1_ptr <= rs1;
            rs2_ptr <= rs2;
            rd_reg <= rd;
        end
    end
end

// Operand register logic
always @(posedge clock) begin
    if (reset) begin
        operand1 <= 64'b0;
        operand2 <= 64'b0;
    end else begin
        // TODO: Load the memory response data into operand1 and operand2 when a memory response is received
        // This must be performed for states sRspLoad1 and sRspLoad2, respectively
        if (state == sRspLoad1 && mem_resp_valid) begin
            operand1 <= mem_resp_data_raw;
        end
        if (state == sRspLoad2 && mem_resp_valid) begin
            operand2 <= mem_resp_data_raw;
        end
    end
end

// Result register logic
always @(posedge clock) begin
    if (reset) begin
        result_reg <= 64'b0;
    end else begin
        // TODO: Clear the result register when a cmdCompute is received
        if (cmd_valid && funct == cmdCompute) begin
            result_reg <= 0;
        end
        // TODO: Perform the bit matrix multiplication and accumulate the result 
        // with an xor into result_reg when in state sProcess
        if (state == sProcess) begin
            result_reg <= result_reg ^ bit_matrix_mult(operand1, operand2);
        end
    end
end


// Next state logic
always @(*) begin
    next_state = state;
    case (state) 
        sIdle: begin
            // TODO: Transition to sReqLoad1 when a cmdCompute is received
            if (cmd_valid && funct == cmdCompute) begin
                next_state = sReqLoad1;
            end
        end
        sReqLoad1: begin
            // TODO: Transition to sRspLoad1 when a memory request is recieved 
            if (mem_req_ready) begin
                next_state = sRspLoad1;
            end
        end
        sRspLoad1: begin
            // TODO: Transition to sReqLoad2 when a memory response is received
            if (mem_resp_valid) begin
                next_state = sReqLoad2;
            end
        end
        sReqLoad2: begin
            // TODO: Transition to sRspLoad2 when a memory request is received
	        if (mem_req_ready) begin
                next_state = sRspLoad2;
            end
        end
        sRspLoad2: begin
            // TODO: Transition to sProcess when a memory response is received
	        if (mem_resp_valid) begin
                next_state = sProcess;
            end
        end
        sProcess: begin
            // TODO: Transition to sRespond when the last element has been processed
            // Ohterwise, continue processing the BMM
            if (element_counter == num_elements - 1) begin 
                next_state = sRespond;
            end
	    else begin
            next_state = sReqLoad1;
        end
        end
        sRespond: begin
            // TODO: Transition to sIdle after putting the resulting data on the response queue
            next_state = sIdle;

        end
    endcase 
end

// Output logic
assign resp_rd = rd_reg;
assign resp_data = result_reg;

always @(*) begin
    // Set defaults
    busy = 1'b0;
    cmd_ready = 1'b0;
    resp_valid = 1'b0;
    mem_req_valid = 1'b0;
    mem_req_wen = 1'b0;
    mem_wdata = 64'b0;
    mem_req_addr = 64'b0;
    mem_req_tag = mem_seq;

    // TODO: Assert busy while processing any command
    if (state != sIdle) busy = 1;
    // TODO: Only recieve commands when in sIDLE
    if (state == sIdle) cmd_ready = 1;
    // TODO: Assert mem_req_valid when requesting memory loads,
    // and set the appropriate memory pointer
    if(state == sReqLoad1 && mem_req_ready) begin 
	    mem_req_valid = 1;
	    mem_req_addr = rs1_ptr + element_counter * 8;
    end
    if(state == sReqLoad2 && mem_req_ready) begin 
	    mem_req_valid = 1;
	    mem_req_addr = rs2_ptr + element_counter * 8;
    end
    // TODO: Assert resp_valid when responding to a command
    if(state == sRespond) begin
        resp_valid = 1;
    end
end


endmodule
