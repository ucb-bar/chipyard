// Do not modify the ports of the TileLink interface
module ExampleVerilogTLDevice #(
                                parameter CTRL_ADDR_BITS,
                                parameter CTRL_DATA_BITS,
                                parameter CTRL_SOURCE_BITS,
                                parameter CTRL_SINK_BITS,
                                parameter CTRL_SIZE_BITS,
                                parameter CLIENT_ADDR_BITS,
                                parameter CLIENT_DATA_BITS,
                                parameter CLIENT_SOURCE_BITS,
                                parameter CLIENT_SINK_BITS,
                                parameter CLIENT_SIZE_BITS
                                )(
  // Client node TileLink interface for data transfer
  input                             tl_client_a_ready,
  output                            tl_client_a_valid,
  output [2:0]                      tl_client_a_bits_opcode,
  output [2:0]                      tl_client_a_bits_param,
  output [CLIENT_SIZE_BITS-1:0]     tl_client_a_bits_size,
  output [CLIENT_SOURCE_BITS-1:0]   tl_client_a_bits_source,
  output [CLIENT_ADDR_BITS-1:0]     tl_client_a_bits_address,
  output [(CLIENT_DATA_BITS/8)-1:0] tl_client_a_bits_mask,
  output [CLIENT_DATA_BITS-1:0]     tl_client_a_bits_data,
  output                            tl_client_a_bits_corrupt,
  output                            tl_client_d_ready,
  input                             tl_client_d_valid,
  input [2:0]                       tl_client_d_bits_opcode,
  input [1:0]                       tl_client_d_bits_param,
  input [CLIENT_SIZE_BITS-1:0]      tl_client_d_bits_size,
  input [CLIENT_SOURCE_BITS-1:0]    tl_client_d_bits_source,
  input [CLIENT_SINK_BITS-1:0]      tl_client_d_bits_sink,
  input                             tl_client_d_bits_denied,
  input [CLIENT_DATA_BITS-1:0]      tl_client_d_bits_data,
  input                             tl_client_d_bits_corrupt,
  // Control/MMIO/Register Node TileLink interface
  output                            tl_ctrl_a_ready,
  input                             tl_ctrl_a_valid,
  input [2:0]                       tl_ctrl_a_bits_opcode,
  input [2:0]                       tl_ctrl_a_bits_param,
  input [CTRL_SIZE_BITS-1:0]        tl_ctrl_a_bits_size,
  input [CTRL_SOURCE_BITS-1:0]      tl_ctrl_a_bits_source,
  input [CTRL_ADDR_BITS-1:0]        tl_ctrl_a_bits_address,
  input [(CTRL_DATA_BITS/8)-1:0]    tl_ctrl_a_bits_mask,
  input [CTRL_DATA_BITS-1:0]        tl_ctrl_a_bits_data,
  input                             tl_ctrl_a_bits_corrupt,
  input                             tl_ctrl_d_ready,
  output                            tl_ctrl_d_valid,
  output [2:0]                      tl_ctrl_d_bits_opcode,
  output [1:0]                      tl_ctrl_d_bits_param,
  output [CTRL_SIZE_BITS-1:0]       tl_ctrl_d_bits_size,
  output [CTRL_SOURCE_BITS-1:0]     tl_ctrl_d_bits_source,
  output [CTRL_SINK_BITS-1:0]       tl_ctrl_d_bits_sink,
  output                            tl_ctrl_d_bits_denied,
  output [CTRL_DATA_BITS-1:0]       tl_ctrl_d_bits_data,
  output                            tl_ctrl_d_bits_corrupt,
  input                             clock,
  input                             reset
);
  // State machine to control the high-level MMIO register mapping
  localparam S_IDLE = 2'b00, S_RUN = 2'b01, S_DONE = 2'b10;
  // DMA state machine
  localparam IDLE = 3'b000, READ_REQ = 3'b001, READ_RSP = 3'b010, WRITE_REQ = 3'b011, WRITE_RSP = 3'b100, DONE = 3'b101;

  wire input_ready;
  wire input1_valid;
  wire input2_valid;
  wire input3_valid;
  wire output_ready;
  wire in_bits_read; // Get or read
  wire [1:0] addr_index;
  wire out_oready;
  wire out_front_ready;
  wire out_valid;
  wire [2:0] nodeIn_d_bits_opcode;

  reg [1:0]                        state;     // State register for high level FSM
  reg [2:0]                        dma_state; // State register for the DMA FSM
  reg [CTRL_DATA_BITS-1:0]         src;       // MMIO register to store the source pointer
  reg [CTRL_DATA_BITS-1:0]         dest;      // MMIO register to store the destination pointer
  reg [CTRL_DATA_BITS-1:0]         size;      // MMIO register to store the size of transfer
  reg                              start;     // Trigger the DMA to start memcpy
  reg [CTRL_DATA_BITS-1:0]         counter;   // Counter to track the transfer
  reg                              done;      // Completed memcpy
  reg [CLIENT_DATA_BITS-1:0]       dma_data;  // Register to store the data from source

  assign input_ready = state == S_IDLE; // Check whether the module is ready to receive ctrl data
  assign output_ready = state == S_DONE; // Check whether the module is done and CPU can be informed

  assign in_bits_read = tl_ctrl_a_bits_opcode == 3'h4; // Check whether a read on the control node
  assign addr_index = tl_ctrl_a_bits_address[4:3]; // Check the address map of the CSRs
  // If reading CSR check if module is ready for output, if writing check if module is ready for input
  assign out_oready = in_bits_read ? ~(|addr_index) | output_ready : input_ready;
  // TODO: Check if out_oready and if the ctrl node D channel is ready to take response
  assign out_front_ready = 0;
  // TODO: Check if out_oready and module got a valid request on A channel
  assign out_valid = 0;
  // TODO: Assign opcode to 000 when writing/Put and 001 when reading/Get (Based on TL specs)
  assign nodeIn_d_bits_opcode = '0;

  // When Ctrl node A request is valid and its ready to take a response back
  // Check if its a read or writem, and the address bits are for input 1 -> src pointer
  assign input1_valid = tl_ctrl_a_valid & tl_ctrl_d_ready & ~in_bits_read & (~addr_index[1] & addr_index[0]);
  // TODO: Apply similar logic for checking if dest pointer and size input are valid (input2_valid and input3_valid)
  // Make sure to check the correct addr_index for the registers, check the memcpy.c for the #define statements

  // FSM to change the state of the high level module
  always @(posedge clock) begin
    if (reset) begin 
      state <= S_IDLE;
    end else if (state == S_IDLE && input3_valid) begin // Wait for input3 to be valid before changing state to RUN
      state <= S_RUN;
    end else if (state == S_RUN && done) begin
      state <= S_DONE;
    end else if (state == S_DONE && out_valid) begin
      state <= S_IDLE;
    end
  end

  // Logic for storing the src, dest and size
  always @(posedge clock) begin
    if (reset) begin
      start <= 0;
    end else if (state == S_IDLE) begin
      if (input1_valid) begin
        // TODO: store the src reg with the correct data on the ctrl interface
      end else if (input2_valid) begin
        // TODO: store the dest reg with the correct data on the ctrl interface
      end else if (input3_valid) begin
        // TODO: store the size reg with the correct data on the ctrl interface
      end
    end else if (state == S_RUN) begin
      start <= 1;
    end else if (state == S_DONE) begin
      start <= 0;
    end
  end

  // State machine for DMA, counting req/rsps, and logic to interface with the TL client node
  always @(posedge clock) begin
    if (reset) begin
      dma_state <= IDLE;
      counter <= '0;
      done <= 0;
      dma_data <= '0;
    end else if (dma_state == IDLE && start && !done) begin
      dma_state <= READ_REQ;
    end else if (dma_state == READ_REQ && tl_client_a_ready && !done) begin
      dma_state <= READ_RSP;
    end else if (dma_state == READ_RSP && tl_client_a_ready && !done) begin
      // TODO: Wait for a valid response on Client D channel before moving to next state
      // TODO: Store the data on D channel to dma_data register
    end else if (dma_state == WRITE_REQ && tl_client_a_ready && !done) begin
      dma_state <= WRITE_RSP;
    end else if (dma_state == WRITE_RSP && tl_client_a_ready && !done) begin
      // TODO: Wait for valid response on Client D channel, 
      // based on that increment counter and move back to READ_REQ state to get next data
      // TODO: Check counter reaches the desired size and change state to DONE
    end else if (dma_state == DONE) begin
      dma_state <= IDLE;
    end
  end

  // TileLink MMIO register interface signal assignment
  assign tl_ctrl_a_ready = out_front_ready; // You assigned this above
  assign tl_ctrl_d_valid = out_valid; // You assigned this above
  assign tl_ctrl_d_bits_opcode = nodeIn_d_bits_opcode; // You assigned this above
  assign tl_ctrl_d_bits_size = '0; // TODO: Should be same as the request 
  assign tl_ctrl_d_bits_source = '0; // TODO: Should be same as the request
  assign tl_ctrl_d_bits_data = (|(tl_ctrl_a_bits_address[11:5])) ? 64'h0 : {62'h0, input_ready, output_ready};

  // TileLink Client node interface signal assignment
  assign tl_client_d_ready = '0; // TODO: Module is ready to start DMA operation --> use start signal
  assign tl_client_a_valid = '0; // TODO: Valid request to the TL node, use the states to fill this
  assign tl_client_a_bits_address = '0; // TODO: Based on read or write, use the CSRs and counter to send the read/write address
  assign tl_client_a_bits_opcode = '0; // TODO: Read requests or Get opcode is 4 and Write request or Put is 0
  assign tl_client_a_bits_source = '0; // TODO: give your requests unique IDs, VERY IMP: every request needs to have a different ID
  assign tl_client_a_bits_data = '0; // TODO: Data for read is 0 and data for write should be the registered value from your read
  assign tl_client_a_bits_mask = (dma_state == READ_REQ || dma_state == WRITE_REQ) ? '1 : '0;
  assign tl_client_a_bits_param = 3'b0;
  assign tl_client_a_bits_size = 4'h3;
  assign tl_client_a_bits_denied = '0;
  assign tl_client_a_bits_corrupt = '0;

endmodule
