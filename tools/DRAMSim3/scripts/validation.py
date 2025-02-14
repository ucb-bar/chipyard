#!/usr/bin/env python3
import os
import sys
import parse_config

class Command(object):
    """
    providing a data structure to
    conveniently convert the format from simulator output to the verilog format
    """
    def __init__(self, line):
        elements = line.split()
        assert len(elements) == 8, "each line has to be in the format of "\
                                   "clk cmd chan rank bankgroup bank row col"
        self.clk = int(elements[0])
        self.cmd = elements[1]
        self.chan = int(elements[2])
        self.rank = int(elements[3])
        self.bankgroup = int(elements[4])
        self.bank = int(elements[5])
        self.row = int(elements[6], 16)
        self.col = int(elements[7], 16)

    def get_ddr4_str(self):
        """
        get a command line for verilog model workbench
        """
        if self.cmd == "activate":
            return "activate(.bg(%d), .ba(%d), .row(%d));\n" % (self.bankgroup, self.bank, self.row)
        elif self.cmd == "read":  # ap=0 no auto precharge, bc=1 NO burst chop, weird...
            return "read(.bg(%d), .ba(%d), .col(%d), .ap(0), .bc(1));\n" % \
                   (self.bankgroup, self.bank, self.col)
        elif self.cmd == "read_p":
            return "read(.bg(%d), .ba(%d), .col(%d), .ap(1), .bc(1));\n" % \
                   (self.bankgroup, self.bank, self.col)
        elif self.cmd == "write":
            return "write(.bg(%d), .ba(%d), .col(%d), .ap(0), .bc(1));\n" % \
                   (self.bankgroup, self.bank, self.col)
        elif self.cmd == "write_p":
            return "write(.bg(%d), .ba(%d), .col(%d), .ap(1), .bc(1));\n" % \
                   (self.bankgroup, self.bank, self.col)
        elif self.cmd == "precharge":
            return "precharge(.bg(%d), .ba(%d), .ap(0));\n" % \
                   (self.bankgroup, self.bank)
        elif self.cmd == "refresh" or self.cmd == "refresh_bank":
            return "refresh();\n"  # currently the verilog model doesnt do bank refresh
        return

    def get_ddr3_str(self):
        """
        get a command line for verilog model workbench
        """
        if self.cmd == "activate":
            return "activate(%d, %d);\n" % (self.bank, self.row)
        elif self.cmd == "read":
            return "read(%d, %d, %d, %d);\n" % (self.bank, self.col, 0, 1)
        elif self.cmd == "read_p":
            return "read(%d, %d, %d, %d);\n" % (self.bank, self.col, 1, 1)
        elif self.cmd == "write":
            return "write(%d, %d, %d, %d, %d, %d);\n" % (self.bank, self.col, 0, 1, 0, 0xdeadbeaf)
        elif self.cmd == "write_p":
            return "write(%d, %d, %d, %d, %d, %d);\n" % (self.bank, self.col, 1, 1, 0, 0xdeadbeaf)
        elif self.cmd == "refresh":
            return "refresh;\n"
        elif self.cmd == "precharge":
            return "precharge(%d, %d);\n" % (self.bank, 0)

    def get_drampower_str(self, config):
        """
        translate to generate command trace that DRAMPower can take as input
        to validate power calculation, but
        we need to know the config in order to calculate bank number
        """
        str_map = {
            "activate": "ACT",
            "read": "RD",
            "read_p": "RDA",
            "write": "WR",
            "write_p": "WRA",
            "precharge": "PRE",
            "refresh": "REF"
        }
        cmd_str = str_map[self.cmd]
        bank_num = self.bankgroup * config["dram_structure"]["banks_per_group"] + self.bank
        return "%d,%s,%d\n" % (self.clk, cmd_str, bank_num)

def calculate_megs_per_device(config):
    """
    given config dict, calculate device density in Mbits
    """
    rows = config["dram_structure"]["rows"]
    cols = config["dram_structure"]["columns"]
    width = config["dram_structure"]["device_width"]
    bgs = config["dram_structure"]["bankgroups"]
    banks_per_group = config["dram_structure"]["banks_per_group"]
    banks = bgs * banks_per_group
    bytes_per_bank = rows * cols * width
    mega_bytes_per_device = bytes_per_bank * banks / 1024 /1024
    return mega_bytes_per_device


class DRAMValidation(object):
    """
    Base class for validation
    """
    def __init__(self, config_file_name, trace_file_name, script_name="", verilog_output=""):
        """
        Need to specify config ini file and trace file upon creation
        """
        if (not os.path.exists(config_file_name)) or \
           (not os.path.exists(trace_file_name)):
            print("config file or path file does not exist!")
            exit(1)

        if verilog_output:
            self.verilog_out = verilog_output
        else:
            self.verilog_out = trace_file_name + ".vh"

        if script_name:
            self.script_out = script_name
        else:
            self.script_out = ""

        self.drampower_out = self.verilog_out[:-3] + ".power.trc"

        self.configs = parse_config.get_dict(config_file_name)

        trace_in = open(trace_file_name, "r")

        self.commands = trace_in.readlines()

    def get_prefix_str(self):
        pass

    def get_postfix_str(self):
        postfix_str = """
            test_done;
        end
        """
        return postfix_str

    def generate_modelsim_script(self, script_name=""):
        pass

    def generate_verilog_bench(self, bench_name=""):
        pass

    def generate_drampower_trace(self):
        with open(self.drampower_out, "w") as fp:
            for cmd_str in self.commands:
                cmd = Command(cmd_str)
                fp.write(cmd.get_drampower_str(self.configs))
        return


    def validation(self):
        self.generate_modelsim_script(self.script_out)
        self.generate_verilog_bench(self.verilog_out)
        self.generate_drampower_trace()


class DDR3Validation(DRAMValidation):

    def get_prefix_str(self):
        """
        setting up the workbench initialization
        """
        al = self.configs["timing"]["al"]
        cl = self.configs["timing"]["cl"]

        if al == 0:
            mod_1_str = "b0000000110"
        elif al == (cl - 1):
            mod_1_str = "b0000001110"
        elif al == (cl - 2):
            mod_1_str = "b0000010110"
        else:
            mod_1_str = "b0000000110"
            print("Invalid AL/CL values!")
            exit(1)

        prefix_str = """
        initial begin : test
            parameter [31:0] REP = DQ_BITS/8.0;
            reg         [BA_BITS-1:0] r_bank;
            reg        [ROW_BITS-1:0] r_row;
            reg        [COL_BITS-1:0] r_col;
            reg  [BL_MAX*DQ_BITS-1:0] r_data;
            integer                   r_i, r_j;
            real original_tck;
            reg [8*DQ_BITS-1:0] d0, d1, d2, d3;
            d0 = {
            {REP{8'h07}}, {REP{8'h06}}, {REP{8'h05}}, {REP{8'h04}},
            {REP{8'h03}}, {REP{8'h02}}, {REP{8'h01}}, {REP{8'h00}}
            };
            d1 = {
            {REP{8'h17}}, {REP{8'h16}}, {REP{8'h15}}, {REP{8'h14}},
            {REP{8'h13}}, {REP{8'h12}}, {REP{8'h11}}, {REP{8'h10}}
            };
            d2 = {
            {REP{8'h27}}, {REP{8'h26}}, {REP{8'h25}}, {REP{8'h24}},
            {REP{8'h23}}, {REP{8'h22}}, {REP{8'h21}}, {REP{8'h20}}
            };
            d3 = {
            {REP{8'h37}}, {REP{8'h36}}, {REP{8'h35}}, {REP{8'h34}},
            {REP{8'h33}}, {REP{8'h32}}, {REP{8'h31}}, {REP{8'h30}}
            };
            rst_n   <=  1'b0;
            cke     <=  1'b0;
            cs_n    <=  1'b1;
            ras_n   <=  1'b1;
            cas_n   <=  1'b1;
            we_n    <=  1'b1;
            ba      <=  {BA_BITS{1'bz}};
            a       <=  {ADDR_BITS{1'bz}};
            odt_out <=  1'b0;
            dq_en   <=  1'b0;
            dqs_en  <=  1'b0;
            // POWERUP SECTION
            power_up;
            // INITIALIZE SECTION
            zq_calibration  (1);                            // perform Long ZQ Calibration
            load_mode       (3, 14'b00000000000000);        // Extended Mode Register (3)
            nop             (tmrd-1);
            load_mode       (2, {14'b00001000_000_000} | mr_cwl<<3); // Extended Mode Register 2 with DCC Disable
            nop             (tmrd-1);
            load_mode       (1, 14'%s);            // Extended Mode Register with DLL Enable
            nop             (tmrd-1);
            load_mode       (0, {14'b0_1_000_1_0_000_1_0_00} | mr_wr<<9 | mr_cl<<2); // Mode Register with DLL Reset
            nop             (683);  // make sure tDLLK and tZQINIT satisify
            odt_out         <= 0;                           // turn off odt, making life much easier...
            nop (7);
            """ % (mod_1_str)
        return prefix_str

    def generate_modelsim_script(self, script_name=""):
        if not self.script_out:
            self.script_out = "run_modelsim_ddr3.sh"

        megs = calculate_megs_per_device(self.configs)
        if megs == 1024:
            density = "den1024Mb"
        elif megs == 2048:
            density = "den2048Mb"
        elif megs == 4096:
            density = "den4096Mb"
        elif megs == 8192:
            density = "den8192Mb"
        else:
            print("unknown device density: %d! MBs" % (megs))
            exit(1)
        width = self.configs["dram_structure"]["device_width"]
        tck = self.configs["timing"]["tck"]
        speed_table = {  # based on 1Gb device
            0.938: "sg093",  # 2133
            1.07 : "sg107",  # 1866
            1.25 : "sg125", # 1600
            1.50 : "sg15",  # 1333J, there is also sg15E
            1.875: "sg187", # 1066G, there is also sg187E
            2.5:   "sg25",  # 800, there is also sg25E
        }
        if tck not in speed_table.keys():
            print("Invalid tCK value in ini file, use the followings for DDR3:" +\
                str([k for k in speed_table]))
        speed = speed_table[tck]

        # generate script to run modelsim simulation in command line mode
        # note this will generate a run_modelsim.sh script file
        # also a v_out.log file after running the script
        cmd_str = \
        """
        vlib work
        vlog -quiet -suppress 2597 +define+%s +define+x%d +define+%s tb.v ddr3.v
        vsim -quiet -nostdout -c -l v_out.log -novopt tb -do "run -all"

        """ % (density, width, speed)

        with open(self.script_out, "w") as fp:
            fp.write(cmd_str)
        return

    def generate_verilog_bench(self, bench_name=""):
        with open(self.verilog_out, "w") as fp:
            # write prefix that initializes device
            fp.write(self.get_prefix_str())

            # convert trace file
            last_clk = 0
            for cmd_str in self.commands:
                cmd = Command(cmd_str)
                this_clk = cmd.clk
                nop_cycles = this_clk - last_clk - 1
                if nop_cycles > 0:
                    nop_str = "nop(%d);\n" % nop_cycles
                    fp.write(nop_str)
                last_clk = this_clk
                fp.write(cmd.get_ddr3_str())

            # finishing up
            fp.write(self.get_postfix_str())
        return


class DDR4Validation(DRAMValidation):

    def get_prefix_str(self):
        """
        this is necessary for setting up a verilog workbench
        depending on the config, some of the values in this string will change correspondingly
        """
        ts_table = {  # tCK -> [min_ts, nominal_ts, max_ts]
            1.875: ["TS_1875", "TS_1875", "TS_1875"],  # 1066MHz
            1.500: ["TS_1500", "TS_1500", "TS_1875"],  # 1333MHz
            1.250: ["TS_1250", "TS_1250", "TS_1500"],  # 1600MHz
            1.072: ["TS_1072", "TS_1072", "TS_1250"],  # 1866MHz
            0.938: ["TS_938", "TS_938", "TS_1072"],    # 2133MHz
            0.833: ["TS_833", "TS_833", "TS_938"],     # 2400MHz
            0.750: ["TS_750", "TS_750", "TS_833"],     # 2667MHz
            0.682: ["TS_682", "TS_682", "TS_750"],     # 2934MHz
            0.625: ["TS_625", "TS_625", "TS_682"]      # 3200MHz
        }
        ts = self.configs["timing"]["tck"]
        if ts not in ts_table.keys():
            print("Invalid tCK value in ini file, use the followings for DDR4:" +\
                str([k for k in ts_table]))
        ddr4_prefix_str = """
        initial begin : test
                UTYPE_TS min_ts, nominal_ts, max_ts;
                reg [MAX_BURST_LEN*MAX_DQ_BITS-1:0] b0to7, b8tof, b7to0, bfto8;
                reg [MODEREG_BITS-1:0] mode_regs[MAX_MODEREGS];
                UTYPE_DutModeConfig dut_mode_config;
                bit failure;
        min_ts = %s;
        nominal_ts = %s;
        max_ts = %s;
        b0to7 = { {MAX_DQ_BITS/4{4'h7}}, {MAX_DQ_BITS/4{4'h6}}, {MAX_DQ_BITS/4{4'h5}}, {MAX_DQ_BITS/4{4'h4}},
                    {MAX_DQ_BITS/4{4'h3}}, {MAX_DQ_BITS/4{4'h2}}, {MAX_DQ_BITS/4{4'h1}}, {MAX_DQ_BITS/4{4'h0}} };
        b8tof = { {MAX_DQ_BITS/4{4'hf}}, {MAX_DQ_BITS/4{4'he}}, {MAX_DQ_BITS/4{4'hd}}, {MAX_DQ_BITS/4{4'hc}},
                    {MAX_DQ_BITS/4{4'hb}}, {MAX_DQ_BITS/4{4'ha}}, {MAX_DQ_BITS/4{4'h9}}, {MAX_DQ_BITS/4{4'h8}} };
        b7to0 = { {MAX_DQ_BITS/4{4'h0}}, {MAX_DQ_BITS/4{4'h1}}, {MAX_DQ_BITS/4{4'h2}}, {MAX_DQ_BITS/4{4'h3}},
                    {MAX_DQ_BITS/4{4'h4}}, {MAX_DQ_BITS/4{4'h5}}, {MAX_DQ_BITS/4{4'h6}}, {MAX_DQ_BITS/4{4'h7}} };
        bfto8 = { {MAX_DQ_BITS/4{4'h8}}, {MAX_DQ_BITS/4{4'h9}}, {MAX_DQ_BITS/4{4'ha}}, {MAX_DQ_BITS/4{4'hb}},
                    {MAX_DQ_BITS/4{4'hc}}, {MAX_DQ_BITS/4{4'hd}}, {MAX_DQ_BITS/4{4'he}}, {MAX_DQ_BITS/4{4'hf}} };
        iDDR4.RESET_n <= 1'b1;
        iDDR4.CKE <= 1'b0;
        iDDR4.CS_n  <= 1'b1;
        iDDR4.ACT_n <= 1'b1;
        iDDR4.RAS_n_A16 <= 1'b1;
        iDDR4.CAS_n_A15 <= 1'b1;
        iDDR4.WE_n_A14 <= 1'b1;
        iDDR4.BG <= '1;
        iDDR4.BA <= '1;
        iDDR4.ADDR <= '1;
        iDDR4.ADDR_17 <= '0;
        iDDR4.ODT <= 1'b0;
        iDDR4.PARITY <= 0;
        iDDR4.ALERT_n <= 1;
        iDDR4.PWR <= 0;
        iDDR4.TEN <= 0;
        iDDR4.VREF_CA <= 0;
        iDDR4.VREF_DQ <= 0;
        iDDR4.ZQ <= 0;
        dq_en <= 1'b0;
        dqs_en <= 1'b0;
        default_period(nominal_ts);
        // POWERUP SECTION
        power_up();
        // Reset DLL
        dut_mode_config = _state.DefaultDutModeConfig(.cl(%d),
                                                        .write_recovery(%d),
                                                        .qoff(0),
                                                        .cwl(%d),
                                                        .rd_preamble_clocks(%d),
                                                        .wr_preamble_clocks(%d),
                                                        .read_dbi(0),
                                                        .write_dbi(0),
                                                        .bl_reg(rBL8),
                                                        .dll_enable(1),
                                                        .dll_reset(1),
                                                        .dm_enable(0));
        dut_mode_config.AL = 0;
        dut_mode_config.BL = 8;
        _state.ModeToAddrDecode(dut_mode_config, mode_regs);
        load_mode(.bg(0), .ba(1), .addr(mode_regs[1]));
        deselect(timing.tDLLKc);
        dut_mode_config.DLL_reset = 0;
        _state.ModeToAddrDecode(dut_mode_config, mode_regs);
        load_mode(.bg(0), .ba(3), .addr(mode_regs[3]));
        deselect(timing.tMOD/timing.tCK);
        load_mode(.bg(1), .ba(2), .addr(mode_regs[6]));
        deselect(timing.tMOD/timing.tCK);
        load_mode(.bg(1), .ba(1), .addr(mode_regs[5]));
        deselect(timing.tMOD/timing.tCK);
        load_mode(.bg(1), .ba(0), .addr(mode_regs[4]));
        deselect(timing.tMOD/timing.tCK);
        load_mode(.bg(0), .ba(2), .addr(mode_regs[2]));
        deselect(timing.tMOD/timing.tCK);
        load_mode(.bg(0), .ba(1), .addr(mode_regs[1]));
        deselect(timing.tMOD/timing.tCK);
        load_mode(.bg(0), .ba(0), .addr(mode_regs[0]));
        deselect(timing.tMOD/timing.tCK);
        zq_cl();
        deselect(timing.tZQinitc);
        odt_out <= 0;                           // turn off odt

        golden_model.SetDutModeConfig(dut_mode_config);
        golden_model.set_timing_parameter_lock(.locked(1), .recalculate_params(1), .non_spec_tck(%s)); // Prevent tCK changes from changing the loaded timeset.
        """ % (ts_table[ts][0],  # min_ts, not used
               ts_table[ts][1],
               ts_table[ts][2],  # max_ts, not used
               self.configs["timing"]["cl"],
               self.configs["timing"]["twr"] + 1,  # have to + 1 to make verilog model happy..
               self.configs["timing"]["cwl"],
               self.configs["timing"]["trpre"],
               self.configs["timing"]["twpre"],
               ts_table[ts][1][3:],  # non_spec_tck, throw away TS_ and only leaves clock
              )
        return ddr4_prefix_str

    def generate_modelsim_script(self, script_name=""):
        """
        This function should:
        - generate a verilog file for the validation workbench
        - generate the command based on the config file
        """
        if not self.script_out:
            self.script_out = "run_modelsim_ddr4.sh"

        dev_str = "DDR4_"
        megs = calculate_megs_per_device(self.configs)
        if megs == 4096:
            density = "4G"
        elif megs == 8192:
            density = "8G"
        elif megs == 16384:
            density = "16G"
        else:
            print("unknown device density: %d! MBs" % (megs))
            exit(1)

        width = self.configs["dram_structure"]["device_width"]

        dev_str = dev_str + density + "_X" + str(width)  # should be something like DDR4_8G_X8

        vlib_str = "vlib work\n"
        cmd_str = "vlog -quiet -suppress 2597 -work work +acc -l vcs.log -novopt -sv "\
                  "+define+%s arch_package.sv proj_package.sv " \
                  "interface.sv StateTable.svp MemoryArray.svp ddr4_model.svp tb.sv \n" % dev_str
        vsim_str = "vsim -quiet -nostdout -c -l v_out.log -novopt tb -do \"run -all\"\n"

        with open(self.script_out, "w") as fp:
            fp.write(vlib_str)
            fp.write(cmd_str)
            fp.write(vsim_str)
        return

    def generate_verilog_bench(self, bench_name=""):
        with open(self.verilog_out, "w") as fp:
            # write prefix that initializes device
            fp.write(self.get_prefix_str())

            # convert trace file
            last_clk = 0
            for cmd_str in self.commands:
                cmd = Command(cmd_str)
                this_clk = cmd.clk
                nop_cycles = this_clk - last_clk - 1
                if nop_cycles > 0:
                    nop_str = "deselect(%d);\n" % nop_cycles
                    fp.write(nop_str)
                last_clk = this_clk
                fp.write(cmd.get_ddr4_str())

            # finishing up
            fp.write(self.get_postfix_str())
        return


class LPDDRValidtion(DRAMValidation):

    def get_prefix_str(self):
        cl = self.configs["timing"]["cl"]
        bl = self.configs["dram_structure"]["bl"]

        if bl == 2:
            bl_bits = "001"
        elif bl == 4:
            bl_bits = "010"
        elif bl == 8:
            bl_bits = "011"
        else:  # BL=16
            bl_bits = "100"

        if cl == 2:
            cl_bits = "10"
        else:
            cl_bits = "11"

        prefix = """
        initial begin:test
        //ck     <= 1'b0;
        cke    <= 1'b0;
        cs_n   <= 1'bz;
        ras_n  <= 1'bz;
        cas_n  <= 1'bz;
        we_n   <= 1'bz;
        a      <= {ADDR_BITS{1'bz}};
        ba     <= {BA_BITS{1'bz}};
        dq_en  <= 1'b0;
        dqs_en <= 1'b0;
        power_up;
        nop (10); // wait 10 clocks intead of 200 us for simulation purposes
        precharge('h00000000, 1);
        nop(trp);
        refresh;
        nop(trfc);
        refresh;
        nop(trfc);
        load_mode('h0, 'b0%s_0_%s); // setting CL and BL correctly
        nop(tmrd);
        load_mode('h2, 'b0);
        nop(tmrd);
        """ % (cl_bits, bl_bits)
        return prefix

    def generate_modelsim_script(self, script_name=""):
        if not self.script_out:
            self.script_out = "run_modelsim_lpddr.sh"
        megs = calculate_megs_per_device(self.configs)
        density = "den%dMb" % megs
        tck = self.configs["timing"]["tck"]
        speed_table = {  # based on 1Gb device
            4.8: "sg5",
            5.4 : "sg54",
            6.0 : "sg6",
            7.5 : "sg75",
        }
        if tck not in speed_table.keys():
            print("Invalid tCK value in ini file, use the followings for DDR3:" +\
                str([k for k in speed_table]))
        speed = speed_table[tck]
        width = self.configs["dram_structure"]["device_width"]
        cmd_str = \
        """
        vlib work
        vlog -quiet -suppress 2597 +define+%s +define+%s +define+x%d tb.v mobile_ddr.v
        vsim -quiet -nostdout -c -l v_out.log -novopt tb -do "run -all"
        """ % (density, speed, width)

        with open(self.script_out, "w") as fp:
            fp.write(cmd_str)
        return

    def generate_verilog_bench(self, bench_name=""):
        with open(self.verilog_out, "w") as fp:
            # write prefix that initializes device
            fp.write(self.get_prefix_str())

            # convert trace file
            last_clk = 0
            for cmd_str in self.commands:
                cmd = Command(cmd_str)
                this_clk = cmd.clk
                nop_cycles = this_clk - last_clk - 1
                if nop_cycles > 0:
                    nop_str = "nop(%d);\n" % nop_cycles
                    fp.write(nop_str)
                last_clk = this_clk
                fp.write(cmd.get_ddr3_str())

            # finishing up
            fp.write(self.get_postfix_str())
        return


if __name__ == "__main__":
    assert len(sys.argv) == 3, "Need 2 arguments, 1. config file name  2. command trace file name"

    if not (os.path.exists(sys.argv[1]) and os.path.exists(sys.argv[2])):
        print("cannot locate input files, please check your input file name and path")
        exit(1)

    ini_file = sys.argv[1]
    cmd_trace_file = sys.argv[2]

    configs = parse_config.get_dict(ini_file)

    validation = None
    if configs["dram_structure"]["protocol"] == "DDR4":
        validation = DDR4Validation(ini_file, cmd_trace_file)
    elif configs["dram_structure"]["protocol"] == "DDR3":
        validation = DDR3Validation(ini_file, cmd_trace_file)
    elif configs["dram_structure"]["protocol"] == "LPDDR":
        validation = LPDDRValidtion(ini_file, cmd_trace_file)
    else:
        pass

    validation.validation()





