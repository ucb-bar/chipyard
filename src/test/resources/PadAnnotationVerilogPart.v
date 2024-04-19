module ExampleTopModuleWithBB_PadFrame(
  output        clock_Int,
  output        reset_Int,
  output [14:0] io_a_Int,
  output [14:0] io_b_Int,
  output [13:0] io_c_Int,
  input  [15:0] io_x_Int,
  input  [15:0] io_y_Int,
  input  [15:0] io_z_Int,
  input  [4:0]  io_v_0_Int,
  input  [4:0]  io_v_1_Int,
  input  [4:0]  io_v_2_Int,
  input         clock_Ext,
  input         reset_Ext,
  input  [14:0] io_a_Ext,
  input  [14:0] io_b_Ext,
  input  [13:0] io_c_Ext,
  output [15:0] io_x_Ext,
  output [15:0] io_y_Ext,
  output [15:0] io_z_Ext,
  inout  [2:0]  io_analog1_Ext,
  inout  [2:0]  io_analog2_Ext,
  output [4:0]  io_v_0_Ext,
  output [4:0]  io_v_1_Ext,
  output [4:0]  io_v_2_Ext
);
  wire  pad_digital_from_tristate_foundry_vertical_input_array_reset_in;
  wire  pad_digital_from_tristate_foundry_vertical_input_array_reset_out;
  wire [14:0] pad_digital_from_tristate_foundry_horizontal_input_array_io_a_in;
  wire [14:0] pad_digital_from_tristate_foundry_horizontal_input_array_io_a_out;
  wire [14:0] pad_digital_from_tristate_foundry_horizontal_input_array_io_b_in;
  wire [14:0] pad_digital_from_tristate_foundry_horizontal_input_array_io_b_out;
  wire [13:0] pad_digital_from_tristate_foundry_horizontal_input_array_io_c_in;
  wire [13:0] pad_digital_from_tristate_foundry_horizontal_input_array_io_c_out;
  wire [15:0] pad_digital_from_tristate_foundry_horizontal_output_array_io_x_in;
  wire [15:0] pad_digital_from_tristate_foundry_horizontal_output_array_io_x_out;
  wire [15:0] pad_digital_from_tristate_foundry_vertical_output_array_io_z_in;
  wire [15:0] pad_digital_from_tristate_foundry_vertical_output_array_io_z_out;
  wire [4:0] pad_digital_from_tristate_foundry_horizontal_output_array_io_v_0_in;
  wire [4:0] pad_digital_from_tristate_foundry_horizontal_output_array_io_v_0_out;
  wire [4:0] pad_digital_from_tristate_foundry_horizontal_output_array_io_v_1_in;
  wire [4:0] pad_digital_from_tristate_foundry_horizontal_output_array_io_v_1_out;
  wire [4:0] pad_digital_from_tristate_foundry_horizontal_output_array_io_v_2_in;
  wire [4:0] pad_digital_from_tristate_foundry_horizontal_output_array_io_v_2_out;
  pad_digital_from_tristate_foundry_vertical_input_array #(.WIDTH(1)) pad_digital_from_tristate_foundry_vertical_input_array_reset (
    .in(pad_digital_from_tristate_foundry_vertical_input_array_reset_in),
    .out(pad_digital_from_tristate_foundry_vertical_input_array_reset_out)
  );
  pad_digital_from_tristate_foundry_horizontal_input_array #(.WIDTH(15)) pad_digital_from_tristate_foundry_horizontal_input_array_io_a (
    .in(pad_digital_from_tristate_foundry_horizontal_input_array_io_a_in),
    .out(pad_digital_from_tristate_foundry_horizontal_input_array_io_a_out)
  );
  pad_digital_from_tristate_foundry_horizontal_input_array #(.WIDTH(15)) pad_digital_from_tristate_foundry_horizontal_input_array_io_b (
    .in(pad_digital_from_tristate_foundry_horizontal_input_array_io_b_in),
    .out(pad_digital_from_tristate_foundry_horizontal_input_array_io_b_out)
  );
  pad_digital_from_tristate_foundry_horizontal_input_array #(.WIDTH(14)) pad_digital_from_tristate_foundry_horizontal_input_array_io_c (
    .in(pad_digital_from_tristate_foundry_horizontal_input_array_io_c_in),
    .out(pad_digital_from_tristate_foundry_horizontal_input_array_io_c_out)
  );
  pad_digital_from_tristate_foundry_horizontal_output_array #(.WIDTH(16)) pad_digital_from_tristate_foundry_horizontal_output_array_io_x (
    .in(pad_digital_from_tristate_foundry_horizontal_output_array_io_x_in),
    .out(pad_digital_from_tristate_foundry_horizontal_output_array_io_x_out)
  );
  pad_digital_from_tristate_foundry_vertical_output_array #(.WIDTH(16)) pad_digital_from_tristate_foundry_vertical_output_array_io_z (
    .in(pad_digital_from_tristate_foundry_vertical_output_array_io_z_in),
    .out(pad_digital_from_tristate_foundry_vertical_output_array_io_z_out)
  );
  pad_analog_fast_custom_horizontal_array #(.WIDTH(3)) pad_analog_fast_custom_horizontal_array_io_analog1 (
    .io(io_analog1_Ext)
  );
  pad_analog_slow_foundry_vertical_array #(.WIDTH(3)) pad_analog_slow_foundry_vertical_array_io_analog2 (
    .io(io_analog2_Ext)
  );
  pad_digital_from_tristate_foundry_horizontal_output_array #(.WIDTH(5)) pad_digital_from_tristate_foundry_horizontal_output_array_io_v_0 (
    .in(pad_digital_from_tristate_foundry_horizontal_output_array_io_v_0_in),
    .out(pad_digital_from_tristate_foundry_horizontal_output_array_io_v_0_out)
  );
  pad_digital_from_tristate_foundry_horizontal_output_array #(.WIDTH(5)) pad_digital_from_tristate_foundry_horizontal_output_array_io_v_1 (
    .in(pad_digital_from_tristate_foundry_horizontal_output_array_io_v_1_in),
    .out(pad_digital_from_tristate_foundry_horizontal_output_array_io_v_1_out)
  );
  pad_digital_from_tristate_foundry_horizontal_output_array #(.WIDTH(5)) pad_digital_from_tristate_foundry_horizontal_output_array_io_v_2 (
    .in(pad_digital_from_tristate_foundry_horizontal_output_array_io_v_2_in),
    .out(pad_digital_from_tristate_foundry_horizontal_output_array_io_v_2_out)
  );
  pad_supply_vdd_horizontal pad_supply_vdd_horizontal_left_0 (
  );
  pad_supply_vdd_horizontal pad_supply_vdd_horizontal_left_1 (
  );
  pad_supply_vdd_horizontal pad_supply_vdd_horizontal_left_2 (
  );
  pad_supply_vdd_vertical pad_supply_vdd_vertical_bottom_0 (
  );
  pad_supply_vdd_vertical pad_supply_vdd_vertical_bottom_1 (
  );
  pad_supply_vss_horizontal pad_supply_vss_horizontal_right_0 (
  );
  assign clock_Int = clock_Ext;
  assign reset_Int = pad_digital_from_tristate_foundry_vertical_input_array_reset_out;
  assign io_a_Int = pad_digital_from_tristate_foundry_horizontal_input_array_io_a_out;
  assign io_b_Int = pad_digital_from_tristate_foundry_horizontal_input_array_io_b_out;
  assign io_c_Int = $signed(pad_digital_from_tristate_foundry_horizontal_input_array_io_c_out);
  assign io_x_Ext = pad_digital_from_tristate_foundry_horizontal_output_array_io_x_out;
  assign io_y_Ext = io_y_Int;
  assign io_z_Ext = $signed(pad_digital_from_tristate_foundry_vertical_output_array_io_z_out);
  assign io_v_0_Ext = pad_digital_from_tristate_foundry_horizontal_output_array_io_v_0_out;
  assign io_v_1_Ext = pad_digital_from_tristate_foundry_horizontal_output_array_io_v_1_out;
  assign io_v_2_Ext = pad_digital_from_tristate_foundry_horizontal_output_array_io_v_2_out;
  assign pad_digital_from_tristate_foundry_vertical_input_array_reset_in = reset_Ext;
  assign pad_digital_from_tristate_foundry_horizontal_input_array_io_a_in = io_a_Ext;
  assign pad_digital_from_tristate_foundry_horizontal_input_array_io_b_in = io_b_Ext;
  assign pad_digital_from_tristate_foundry_horizontal_input_array_io_c_in = $unsigned(io_c_Ext);
  assign pad_digital_from_tristate_foundry_horizontal_output_array_io_x_in = io_x_Int;
  assign pad_digital_from_tristate_foundry_vertical_output_array_io_z_in = $unsigned(io_z_Int);
  assign pad_digital_from_tristate_foundry_horizontal_output_array_io_v_0_in = io_v_0_Int;
  assign pad_digital_from_tristate_foundry_horizontal_output_array_io_v_1_in = io_v_1_Int;
  assign pad_digital_from_tristate_foundry_horizontal_output_array_io_v_2_in = io_v_2_Int;
endmodule
module ExampleTopModuleWithBB(
  input         clock,
  input         reset,
  input  [14:0] io_a,
  input  [14:0] io_b,
  input  [13:0] io_c,
  output [15:0] io_x,
  output [15:0] io_y,
  output [15:0] io_z,
  inout  [2:0]  io_analog1,
  inout  [2:0]  io_analog2,
  output [4:0]  io_v_0,
  output [4:0]  io_v_1,
  output [4:0]  io_v_2
);
  wire  ExampleTopModuleWithBB_PadFrame_clock_Int;
  wire  ExampleTopModuleWithBB_PadFrame_reset_Int;
  wire [14:0] ExampleTopModuleWithBB_PadFrame_io_a_Int;
  wire [14:0] ExampleTopModuleWithBB_PadFrame_io_b_Int;
  wire [13:0] ExampleTopModuleWithBB_PadFrame_io_c_Int;
  wire [15:0] ExampleTopModuleWithBB_PadFrame_io_x_Int;
  wire [15:0] ExampleTopModuleWithBB_PadFrame_io_y_Int;
  wire [15:0] ExampleTopModuleWithBB_PadFrame_io_z_Int;
  wire [4:0] ExampleTopModuleWithBB_PadFrame_io_v_0_Int;
  wire [4:0] ExampleTopModuleWithBB_PadFrame_io_v_1_Int;
  wire [4:0] ExampleTopModuleWithBB_PadFrame_io_v_2_Int;
  wire  ExampleTopModuleWithBB_PadFrame_clock_Ext;
  wire  ExampleTopModuleWithBB_PadFrame_reset_Ext;
  wire [14:0] ExampleTopModuleWithBB_PadFrame_io_a_Ext;
  wire [14:0] ExampleTopModuleWithBB_PadFrame_io_b_Ext;
  wire [13:0] ExampleTopModuleWithBB_PadFrame_io_c_Ext;
  wire [15:0] ExampleTopModuleWithBB_PadFrame_io_x_Ext;
  wire [15:0] ExampleTopModuleWithBB_PadFrame_io_y_Ext;
  wire [15:0] ExampleTopModuleWithBB_PadFrame_io_z_Ext;
  wire [4:0] ExampleTopModuleWithBB_PadFrame_io_v_0_Ext;
  wire [4:0] ExampleTopModuleWithBB_PadFrame_io_v_1_Ext;
  wire [4:0] ExampleTopModuleWithBB_PadFrame_io_v_2_Ext;
  wire  ExampleTopModuleWithBB_Internal_clock;
  wire  ExampleTopModuleWithBB_Internal_reset;
  wire [14:0] ExampleTopModuleWithBB_Internal_io_a;
  wire [14:0] ExampleTopModuleWithBB_Internal_io_b;
  wire [13:0] ExampleTopModuleWithBB_Internal_io_c;
  wire [15:0] ExampleTopModuleWithBB_Internal_io_x;
  wire [15:0] ExampleTopModuleWithBB_Internal_io_y;
  wire [15:0] ExampleTopModuleWithBB_Internal_io_z;
  wire [4:0] ExampleTopModuleWithBB_Internal_io_v_0;
  wire [4:0] ExampleTopModuleWithBB_Internal_io_v_1;
  wire [4:0] ExampleTopModuleWithBB_Internal_io_v_2;
  ExampleTopModuleWithBB_PadFrame ExampleTopModuleWithBB_PadFrame (
    .clock_Int(ExampleTopModuleWithBB_PadFrame_clock_Int),
    .reset_Int(ExampleTopModuleWithBB_PadFrame_reset_Int),
    .io_a_Int(ExampleTopModuleWithBB_PadFrame_io_a_Int),
    .io_b_Int(ExampleTopModuleWithBB_PadFrame_io_b_Int),
    .io_c_Int(ExampleTopModuleWithBB_PadFrame_io_c_Int),
    .io_x_Int(ExampleTopModuleWithBB_PadFrame_io_x_Int),
    .io_y_Int(ExampleTopModuleWithBB_PadFrame_io_y_Int),
    .io_z_Int(ExampleTopModuleWithBB_PadFrame_io_z_Int),
    .io_v_0_Int(ExampleTopModuleWithBB_PadFrame_io_v_0_Int),
    .io_v_1_Int(ExampleTopModuleWithBB_PadFrame_io_v_1_Int),
    .io_v_2_Int(ExampleTopModuleWithBB_PadFrame_io_v_2_Int),
    .clock_Ext(ExampleTopModuleWithBB_PadFrame_clock_Ext),
    .reset_Ext(ExampleTopModuleWithBB_PadFrame_reset_Ext),
    .io_a_Ext(ExampleTopModuleWithBB_PadFrame_io_a_Ext),
    .io_b_Ext(ExampleTopModuleWithBB_PadFrame_io_b_Ext),
    .io_c_Ext(ExampleTopModuleWithBB_PadFrame_io_c_Ext),
    .io_x_Ext(ExampleTopModuleWithBB_PadFrame_io_x_Ext),
    .io_y_Ext(ExampleTopModuleWithBB_PadFrame_io_y_Ext),
    .io_z_Ext(ExampleTopModuleWithBB_PadFrame_io_z_Ext),
    .io_analog1_Ext(io_analog1),
    .io_analog2_Ext(io_analog2),
    .io_v_0_Ext(ExampleTopModuleWithBB_PadFrame_io_v_0_Ext),
    .io_v_1_Ext(ExampleTopModuleWithBB_PadFrame_io_v_1_Ext),
    .io_v_2_Ext(ExampleTopModuleWithBB_PadFrame_io_v_2_Ext)
  );
  ExampleTopModuleWithBB_Internal ExampleTopModuleWithBB_Internal (
    .clock(ExampleTopModuleWithBB_Internal_clock),
    .reset(ExampleTopModuleWithBB_Internal_reset),
    .io_a(ExampleTopModuleWithBB_Internal_io_a),
    .io_b(ExampleTopModuleWithBB_Internal_io_b),
    .io_c(ExampleTopModuleWithBB_Internal_io_c),
    .io_x(ExampleTopModuleWithBB_Internal_io_x),
    .io_y(ExampleTopModuleWithBB_Internal_io_y),
    .io_z(ExampleTopModuleWithBB_Internal_io_z),
    .io_analog1(io_analog1),
    .io_analog2(io_analog2),
    .io_v_0(ExampleTopModuleWithBB_Internal_io_v_0),
    .io_v_1(ExampleTopModuleWithBB_Internal_io_v_1),
    .io_v_2(ExampleTopModuleWithBB_Internal_io_v_2)
  );
  assign io_x = ExampleTopModuleWithBB_PadFrame_io_x_Ext;
  assign io_y = ExampleTopModuleWithBB_PadFrame_io_y_Ext;
  assign io_z = ExampleTopModuleWithBB_PadFrame_io_z_Ext;
  assign io_v_0 = ExampleTopModuleWithBB_PadFrame_io_v_0_Ext;
  assign io_v_1 = ExampleTopModuleWithBB_PadFrame_io_v_1_Ext;
  assign io_v_2 = ExampleTopModuleWithBB_PadFrame_io_v_2_Ext;
  assign ExampleTopModuleWithBB_PadFrame_io_x_Int = ExampleTopModuleWithBB_Internal_io_x;
  assign ExampleTopModuleWithBB_PadFrame_io_y_Int = ExampleTopModuleWithBB_Internal_io_y;
  assign ExampleTopModuleWithBB_PadFrame_io_z_Int = ExampleTopModuleWithBB_Internal_io_z;
  assign ExampleTopModuleWithBB_PadFrame_io_v_0_Int = ExampleTopModuleWithBB_Internal_io_v_0;
  assign ExampleTopModuleWithBB_PadFrame_io_v_1_Int = ExampleTopModuleWithBB_Internal_io_v_1;
  assign ExampleTopModuleWithBB_PadFrame_io_v_2_Int = ExampleTopModuleWithBB_Internal_io_v_2;
  assign ExampleTopModuleWithBB_PadFrame_clock_Ext = clock;
  assign ExampleTopModuleWithBB_PadFrame_reset_Ext = reset;
  assign ExampleTopModuleWithBB_PadFrame_io_a_Ext = io_a;
  assign ExampleTopModuleWithBB_PadFrame_io_b_Ext = io_b;
  assign ExampleTopModuleWithBB_PadFrame_io_c_Ext = io_c;
  assign ExampleTopModuleWithBB_Internal_clock = ExampleTopModuleWithBB_PadFrame_clock_Int;
  assign ExampleTopModuleWithBB_Internal_reset = ExampleTopModuleWithBB_PadFrame_reset_Int;
  assign ExampleTopModuleWithBB_Internal_io_a = ExampleTopModuleWithBB_PadFrame_io_a_Int;
  assign ExampleTopModuleWithBB_Internal_io_b = ExampleTopModuleWithBB_PadFrame_io_b_Int;
  assign ExampleTopModuleWithBB_Internal_io_c = ExampleTopModuleWithBB_PadFrame_io_c_Int;
endmodule