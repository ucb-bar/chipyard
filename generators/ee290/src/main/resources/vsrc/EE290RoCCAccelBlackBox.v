// EE290RoCCAccelBlackBox.v

module EE290RoCCAccelBlackBox(
    input         clock,
    input         reset,
    input         cmd_valid,
    output        cmd_ready,
    input  [63:0] rs1,
    input  [63:0] rs2,
    input  [4:0]  rd,
    output        resp_valid,
    input         resp_ready,
    output [4:0]  resp_rd,
    output [63:0] resp_data
);

// State machine declaration
reg [1:0] state;
// Result register to hold the computation result
reg [63:0] result_reg;
// Register to hold the destination register ID
reg [4:0] rd_reg;
// Registers to store value of operands
reg [63:0] operand1, operand2;

// State parameters
localparam sIdle = 2'b00,
           sProcess = 2'b01,
           sRespond = 2'b10;

// TODO: Assignment to output RoCC signals
assign cmd_ready = 00000;
assign resp_valid = 00000;
assign resp_rd = 00000;
assign resp_data = 00000;

// TODO: Implement the bit_matrix_mult function
// This function takes two 64-bit operands and performs a bit matrix multiplication
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
                // TODO: Extract a column from matrix B
                row_a = 00000;
                row_b = 00000;
                // TODO: Perform the bitwise AND and then reduce using XOR for each bit in the row and column
                result_bit = 00000;
                // Set the result bit in the result matrix
                bit_matrix_mult[i*8+j] = result_bit;
            end
        end
    end
endfunction

// Main always block for the FSM
always @(posedge clock) begin
    if (reset) begin
        // Reset state
        state <= sIdle;
    end else begin
        // FSM logic
        case (state)
            sIdle: begin
                    // TODO: Capture the operands and destination on command valid

                    // TODO: Transition to the next state

            end
            sProcess: begin
                // TODO: Perform the operation (e.g., bit matrix multiplication) on the operands

                // TODO Transition to the next state
            end
            sRespond: begin
                // Wait until command has been accepted and transition to the next state
            end
            default: begin
                // Handle undefined states
                state <= sIdle;
            end
        endcase
    end
end

endmodule
