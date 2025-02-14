#include "catch.hpp"
#include "configuration.h"
#include "memory_system.h"

bool hmc_called = false;

void hmc_callback(uint64_t addr) {
    hmc_called = true;
    return;
}

TEST_CASE("HMC System Testing", "[dramsim3][hmc]") {
    // but THIS doesn't work?
    // dramsim3::Config config("configs/HMC_2GB_4Lx16.ini", ".");
    // dramsim3::HMCMemorySystem hmc(config, ".", hmc_callback, hmc_callback);
    dramsim3::MemorySystem hmc("configs/HMC_2GB_4Lx16.ini", ".", hmc_callback, hmc_callback);

    SECTION("TEST HMC interaction with controller") {
        REQUIRE(hmc.GetBurstLength() == 16);
        hmc.AddTransaction(1, false);
        int clk = 0;
        while (true) {
            hmc.ClockTick();
            clk++;
            if (hmc_called || clk == 1000) {
                break;
            }
        }

        // For HMC things are complicated, e.g. for a 64B read request and x2 bandwidth xbar
        // takes 1 cycle from CPU to Link
        // takes 1 cycle from Link to Quad
        // takes 1 cycle from Quad to DRAM
        // takes xx cycles for DRAM to finish
        // takes 1 cycle from DRAM to quad
        // takes multiple cycles from quad to CPU
        // (depending on packet size, and contention)
        int idle_lat = 52;
        REQUIRE(clk == idle_lat);
    }
}
