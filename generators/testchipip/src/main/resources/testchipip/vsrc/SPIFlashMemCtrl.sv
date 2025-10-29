
// This macro is used to avoid asynchronous resets and negative edge clocks
// It requires that you provide sck at 2x the normal frequency and always have
// sck frerunning (so that you can synchronously reset)
`ifdef SPI_FLASH_POSEDGE_ONLY
`define SPI_FLASH_POSEDGE_CLOCK posedge sck
`define SPI_FLASH_NEGEDGE_CLOCK posedge sck
`define SPI_FLASH_RESET_COND cs || reset
`else
`define SPI_FLASH_POSEDGE_CLOCK posedge sck or posedge cs or posedge reset
`define SPI_FLASH_NEGEDGE_CLOCK negedge sck or posedge cs or posedge reset
`define SPI_FLASH_RESET_COND cs || reset
`endif

module SPIFlashMemCtrl #(
    parameter int ADDR_BITS
) (
    input sck,
    input cs,
    input reset,
    input [3:0] dq_in,
    output [3:0] dq_drive,
    output [3:0] dq_out,
    output mem_req_valid,
    output [ADDR_BITS-1:0] mem_req_addr,
    output [7:0] mem_req_data,
    output mem_req_r_wb,
    input [7:0] mem_resp_data
);

    // CAUTION! This model only supports a small subset of standard QSPI flash
    // features. It is useful for modeling a pre-loaded flash memory intended
    // to be used as a ROM. You should replace this with a verilog model of
    // your specific flash device for more rigorous verification.

    // Supported SPI instructions
    // 3-byte address reads
    localparam CMD_READ          = 8'h03; // No dummy
    localparam CMD_FAST_READ     = 8'h0B;
    localparam CMD_QUAD_D_READ   = 8'h6B;
    localparam CMD_QUAD_AD_READ  = 8'hEB;
    // 4-byte address reads
    localparam CMD_READ4         = 8'h13; // No dummy
    localparam CMD_FAST_READ4    = 8'h0C;
    localparam CMD_QUAD_D_READ4  = 8'h6C;
    localparam CMD_QUAD_AD_READ4 = 8'hEC;
    // 3-byte address writes (all have no dummy)
    localparam CMD_WRITE          = 8'h02;
    localparam CMD_QUAD_D_WRITE   = 8'h32;
    localparam CMD_QUAD_AD_WRITE  = 8'h38;
    // 4-byte address writes (all have no dummy)
    localparam CMD_WRITE4         = 8'h12;
    localparam CMD_QUAD_D_WRITE4  = 8'h34;
    localparam CMD_QUAD_AD_WRITE4 = 8'h3E;
    // No register reads are supported by this model yet

    // SPI flash behavior settings
    // Often the number of dummy cycles depends on the instruction, but here
    // we'll make it all uniform. DUMMY_CYCLES is the number of cycles between
    // the last address bit and the start of read data transfer.
    localparam DUMMY_CYCLES = 8;

    // State
    reg [3:0] state;
    logic [3:0] state_next;
    logic [3:0] put_get_state_next;
    // States
    localparam STANDBY   = 4'h0;
    localparam GET_CMD   = 4'h1;
    localparam GET_ADDRS = 4'h2;
    localparam GET_ADDRQ = 4'h3;
    localparam DUMMY     = 4'h4;
    localparam PUT_DATAS = 4'h5;
    localparam PUT_DATAQ = 4'h6;
    localparam GET_DATAS = 4'h7;
    localparam GET_DATAQ = 4'h8;
    localparam ERROR     = 4'hf;

    // Incoming data
    reg [31:0] data_buf;
    logic [31:0] data_buf_next;
    // Incoming data bit count
    reg [5:0] data_count;
    logic [5:0] data_count_next;
    // Dummy cycle counter
    reg [7:0] dummy;
    logic [7:0] dummy_next;
    // Command
    reg [7:0] cmd;
    logic [7:0] cmd_next;
    // Address
    reg [31:0] addr;
    logic [31:0] addr_next;

    // Quad data stuff
    logic quad_io, quad_addr_next, quad_data_next;

    assign quad_addr_next = cmd_next inside {CMD_QUAD_AD_READ, CMD_QUAD_AD_READ4, CMD_QUAD_AD_WRITE, CMD_QUAD_AD_WRITE4};
    assign quad_data_next = quad_addr_next || (cmd_next inside {CMD_QUAD_D_READ, CMD_QUAD_D_READ4, CMD_QUAD_D_WRITE, CMD_QUAD_D_WRITE4});
    assign quad_io = state inside {GET_ADDRQ, GET_DATAQ, PUT_DATAQ};

    // State machine stuff
    logic addr_4byte, cmd_done, addr_done, dummy_done, cmd_has_dummy, cmd_valid_next, data_count_aligned, incr_addr;
    logic cmd_read, cmd_write;

    assign addr_4byte = cmd inside
        {CMD_READ4, CMD_FAST_READ4, CMD_QUAD_D_READ4,  CMD_QUAD_AD_READ4,
         CMD_WRITE4,                CMD_QUAD_D_WRITE4, CMD_QUAD_AD_WRITE4};

    assign cmd_valid_next = cmd_next inside
        {CMD_READ,  CMD_FAST_READ,  CMD_QUAD_D_READ,   CMD_QUAD_AD_READ,
         CMD_READ4, CMD_FAST_READ4, CMD_QUAD_D_READ4,  CMD_QUAD_AD_READ4,
         CMD_WRITE,                 CMD_QUAD_D_WRITE,  CMD_QUAD_AD_WRITE,
         CMD_WRITE4,                CMD_QUAD_D_WRITE4, CMD_QUAD_AD_WRITE4};

    assign cmd_has_dummy = cmd inside
        {CMD_FAST_READ,  CMD_QUAD_D_READ,  CMD_QUAD_AD_READ,
         CMD_FAST_READ4, CMD_QUAD_D_READ4, CMD_QUAD_AD_READ4};

    assign cmd_read = cmd inside
        {CMD_READ,  CMD_FAST_READ,  CMD_QUAD_D_READ,  CMD_QUAD_AD_READ,
         CMD_READ4, CMD_FAST_READ4, CMD_QUAD_D_READ4, CMD_QUAD_AD_READ4};

    assign cmd_write = cmd inside
        {CMD_WRITE,  CMD_QUAD_D_WRITE,  CMD_QUAD_AD_WRITE,
         CMD_WRITE4, CMD_QUAD_D_WRITE4, CMD_QUAD_AD_WRITE4};

    assign cmd_done = (state == GET_CMD) && (data_count_next == 6'd8);
    assign addr_done = (state inside {GET_ADDRS, GET_ADDRQ}) && (data_count_next == (addr_4byte ? 6'd40 : 6'd32));
    assign dummy_done = dummy == (DUMMY_CYCLES - 1);

    assign data_count_aligned = (data_count_next[2:0] == 3'd0);

    // We let the counter free run...this just increments every byte
    assign incr_addr = (state inside {PUT_DATAS, PUT_DATAQ, GET_DATAS, GET_DATAQ}) && data_count_aligned;

    assign data_buf_next = quad_io ? {data_buf[27:0], dq_in} : {data_buf[30:0], dq_in[0]};
    assign data_count_next = (state == DUMMY) ? data_count : data_count + (quad_io ? 6'd4 : 6'd1) ;
    assign dummy_next = (state == DUMMY) ? dummy + 8'd1 : 8'd0 ;

    always_comb begin
        if (state == DUMMY) addr_next = addr;
        else if (addr_done) addr_next = addr_4byte ? data_buf_next : (data_buf_next & 32'h00ff_ffff);
        else if (incr_addr) addr_next = addr + 32'd1;
        else addr_next = addr;
    end

    assign cmd_next = cmd_done ? data_buf_next[7:0] : cmd ;

    // Output driving
    reg [7:0] data_out;
    logic [7:0] data_out_next;
    reg dq_drive_reg;

    assign dq_out[3] = data_out[7];
    assign dq_out[2] = data_out[6];
    assign dq_out[1] = (quad_io ? data_out[5] : data_out[7]);
    assign dq_out[0] = data_out[4];

    assign dq_drive[3] = (dq_drive_reg && quad_io);
    assign dq_drive[2] = (dq_drive_reg && quad_io);
    assign dq_drive[1] =  dq_drive_reg;
    assign dq_drive[0] = (dq_drive_reg && quad_io);

    assign mem_req_addr = (cmd_read ? addr_next[ADDR_BITS-1:0] : addr[ADDR_BITS-1:0]);
    assign mem_req_data = data_buf_next[7:0];
    assign mem_req_r_wb = (state_next inside {PUT_DATAS, PUT_DATAQ});
`ifdef SPI_FLASH_POSEDGE_ONLY
    reg sckedge;
    assign mem_req_valid = ((state_next inside {PUT_DATAS, PUT_DATAQ}) || (state inside {GET_DATAS, GET_DATAQ})) && data_count_aligned && sckedge;
`else
    assign mem_req_valid = ((state_next inside {PUT_DATAS, PUT_DATAQ}) || (state inside {GET_DATAS, GET_DATAQ})) && data_count_aligned;
`endif

    always_comb begin
        if (data_count[2:0] == 3'd0)
            data_out_next = mem_resp_data;
        else if (quad_io)
            data_out_next = {data_out[3:0], 4'd0};
        else
            data_out_next = {data_out[6:0], 1'd0};
    end

    // state machine
    // helper wire for which put/get state to go to
    always_comb begin
        if (quad_data_next) begin
            put_get_state_next = GET_DATAQ;
            if (cmd_read)
                put_get_state_next = PUT_DATAQ;
        end else begin
            put_get_state_next = GET_DATAS;
            if (cmd_read)
                put_get_state_next = PUT_DATAS;
        end
    end

    // next state calculation
    always_comb begin
        state_next = state;
        case (state)
            STANDBY:
                if (!cs) state_next = GET_CMD;
            GET_CMD:
                if (cmd_done) begin
                    if (cmd_valid_next)
                        state_next = (quad_addr_next ? GET_ADDRQ : GET_ADDRS);
                    else
                        state_next = ERROR;
                end
            GET_ADDRS:
                if (addr_done) begin
                    if (cmd_has_dummy)
                        state_next = DUMMY;
                    else
                        state_next = put_get_state_next;
                end
            GET_ADDRQ:
                if (addr_done) begin
                    if (cmd_has_dummy)
                        state_next = DUMMY;
                    else
                        state_next = put_get_state_next;
                end
            DUMMY:
                if (dummy_done) begin
                    state_next = put_get_state_next;
                end
            PUT_DATAS:
                if (cs) state_next = STANDBY;
            PUT_DATAQ:
                if (cs) state_next = STANDBY;
            GET_DATAS:
                if (cs) state_next = STANDBY;
            GET_DATAQ:
                if (cs) state_next = STANDBY;
            ERROR:
                if (cs) state_next = STANDBY;
            default:
                state_next = state;
        endcase
    end

`ifdef SPI_FLASH_POSEDGE_ONLY
    always_ff @(`SPI_FLASH_POSEDGE_CLOCK) begin
        if (`SPI_FLASH_RESET_COND) begin
            sckedge <= 1'b0;
        end else begin
            sckedge <= ~sckedge;
        end
    end
`endif

    // Positive edge state changes
    always_ff @(`SPI_FLASH_POSEDGE_CLOCK) begin
        if (`SPI_FLASH_RESET_COND) begin
            data_count <= 6'd0;
            state <= STANDBY;
            dummy <= 8'd0;
            cmd <= 8'd0;
`ifdef SPI_FLASH_POSEDGE_ONLY
        end else if (sckedge) begin
`else
        end else begin
`endif
           // Capture edge
           data_buf    <= data_buf_next;
           data_count  <= data_count_next;
           state       <= state_next;
           dummy       <= dummy_next;
           addr        <= addr_next;
           cmd         <= cmd_next;

        end
    end

    // Negative edge state changes
    always_ff @(`SPI_FLASH_NEGEDGE_CLOCK) begin
        if (`SPI_FLASH_RESET_COND) begin
            dq_drive_reg <= 1'b0;
`ifdef SPI_FLASH_POSEDGE_ONLY
        end else if (!sckedge) begin
`else
        end else begin
`endif
            // Launch edge
            dq_drive_reg <= ((state == PUT_DATAS) || (state == PUT_DATAQ));
            data_out <= data_out_next;
        end
    end

endmodule
