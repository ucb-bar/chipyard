/*
 * Force variables to be placed at specific cache offsets to exacerbate
 * false sharing:
 *
 *   verify_data: 8
 *   input1_data: 8
 *   input2_data: 8
 *   output_data: 16
 */
SECTIONS {
    . = ALIGN(0x40);
    . += 8;
}
INSERT BEFORE .data
