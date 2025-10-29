`define SECTOR_BITS 32
`define DATA_BITS 64

import "DPI-C" function void block_device_tick
(
    input  bit req_valid,
    output bit req_ready,
    input  bit req_bits_write,
    input  int req_bits_offset,
    input  int req_bits_len,
    input  int req_bits_tag,

    input  bit     data_valid,
    output bit     data_ready,
    input  longint data_bits_data,
    input  int     data_bits_tag,

    output bit     resp_valid,
    input  bit     resp_ready,
    output longint resp_bits_data,
    output int     resp_bits_tag
);

import "DPI-C" function void block_device_init(
    input  string  filename,
    input  int     ntags,
    output int     nsectors,
    output int     max_req_len
);

module SimBlockDevice #(TAG_BITS=1) (
    input                      clock,
    input                      reset,

    input                      bdev_req_valid,
    output                     bdev_req_ready,
    input                      bdev_req_bits_write,
    input  [`SECTOR_BITS-1:0]  bdev_req_bits_offset,
    input  [`SECTOR_BITS-1:0]  bdev_req_bits_len,
    input  [TAG_BITS-1:0]      bdev_req_bits_tag,

    input                      bdev_data_valid,
    output                     bdev_data_ready,
    input  [`DATA_BITS-1:0]    bdev_data_bits_data,
    input  [TAG_BITS-1:0]      bdev_data_bits_tag,

    output                     bdev_resp_valid,
    input                      bdev_resp_ready,
    output [`DATA_BITS-1:0]    bdev_resp_bits_data,
    output [TAG_BITS-1:0]      bdev_resp_bits_tag,

    output [`SECTOR_BITS-1:0]  bdev_info_nsectors,
    output [`SECTOR_BITS-1:0]  bdev_info_max_req_len
);

    bit __req_ready;
    bit __data_ready;
    bit __resp_valid;
    longint __resp_bits_data;
    int __resp_bits_tag;
    int __nsectors;
    int __max_req_len;

    reg __req_ready_reg;
    reg __data_ready_reg;
    reg __resp_valid_reg;
    reg [`DATA_BITS-1:0] __resp_bits_data_reg;
    reg [TAG_BITS-1:0]   __resp_bits_tag_reg;
    reg [`SECTOR_BITS-1:0] __nsectors_reg;
    reg [`SECTOR_BITS-1:0] __max_req_len_reg;

    string filename;
    int ntags;

    assign bdev_req_ready = __req_ready_reg;
    assign bdev_data_ready = __data_ready_reg;
    assign bdev_resp_valid = __resp_valid_reg;
    assign bdev_resp_bits_data = __resp_bits_data_reg;
    assign bdev_resp_bits_tag = __resp_bits_tag_reg;
    assign bdev_info_nsectors = __nsectors_reg;
    assign bdev_info_max_req_len = __max_req_len_reg;

    /* verilator lint_off WIDTH */

    initial begin
        ntags = 1 << TAG_BITS;
        if ($value$plusargs("blkdev=%s", filename)) begin
            block_device_init(filename, ntags, __nsectors, __max_req_len);
            __nsectors_reg = __nsectors;
            __max_req_len_reg = __max_req_len;
        end else begin
            __nsectors_reg = 0;
        end
    end

    always @(posedge clock) begin
        if (reset) begin
            __req_ready = 0;
            __data_ready = 0;
            __resp_valid = 0;
            __resp_bits_data = 0;
            __resp_bits_tag = 0;

            __req_ready_reg <= 0;
            __data_ready_reg <= 0;
            __resp_valid_reg <= 0;
            __resp_bits_data_reg <= 0;
            __resp_bits_tag_reg <= 0;
        end else begin
            block_device_tick(
                bdev_req_valid,
                __req_ready,
                bdev_req_bits_write,
                bdev_req_bits_offset,
                bdev_req_bits_len,
                bdev_req_bits_tag,

                bdev_data_valid,
                __data_ready,
                bdev_data_bits_data,
                bdev_data_bits_tag,

                __resp_valid,
                bdev_resp_ready,
                __resp_bits_data,
                __resp_bits_tag);

            __req_ready_reg <= __req_ready;
            __data_ready_reg <= __data_ready;
            __resp_valid_reg <= __resp_valid;
            __resp_bits_data_reg <= __resp_bits_data;
            __resp_bits_tag_reg <= __resp_bits_tag;
        end
    end

endmodule
