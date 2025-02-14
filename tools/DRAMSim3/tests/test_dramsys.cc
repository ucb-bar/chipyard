#include "catch.hpp"
#include "configuration.h"
#include "dram_system.h"

bool call_back_called = false;
void dummy_call_back(uint64_t addr) {
    call_back_called = true;
    return;
}

TEST_CASE("Jedec DRAMSystem Testing", "[dramsim3]") {
    dramsim3::Config config("configs/HBM1_4Gb_x128.ini", ".");

    dramsim3::JedecDRAMSystem dramsys(config, ".", dummy_call_back,
                                      dummy_call_back);

    SECTION("TEST interaction with controller") {
        dramsys.AddTransaction(1, false);
        int clk = 0;
        while (true) {
            dramsys.ClockTick();
            clk++;
            if (call_back_called) {
                call_back_called = false;
                break;
            }
        }

        int tRC = config.tRCDRD + config.CL + config.BL;
        REQUIRE(clk == tRC);
    }
}
