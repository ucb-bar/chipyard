#define CATCH_CONFIG_MAIN
#include "catch.hpp"
#include "configuration.h"

TEST_CASE("Address Mapping", "[config]") {
    dramsim3::Config config("configs/HBM1_4Gb_x128.ini", ".");

    SECTION("TEST address mapping set up") {
        REQUIRE(config.address_mapping == "rorabgbachco");
        // COL width is not necessarily the same as col bits because BL
        REQUIRE(config.co_pos == 0);
        REQUIRE(config.ch_pos == 5);
        REQUIRE(config.ba_pos == 8);
        REQUIRE(config.bg_pos == 10);
        REQUIRE(config.ra_pos == 12);
        REQUIRE(config.ro_pos == 12);
    }

    SECTION("Test address mapping column") {
        uint64_t hex_addr = 0x0;
        auto addr = config.AddressMapping(hex_addr);
        REQUIRE(addr.column == 0);

        hex_addr = 0b11111000000;
        addr = config.AddressMapping(hex_addr);
        REQUIRE(addr.column == 31);
    }

    SECTION("Test address mapping channel") {
        uint64_t hex_addr = 0b11111111111;
        auto addr = config.AddressMapping(hex_addr);
        REQUIRE(addr.channel == 0);

        hex_addr = 0b111110111111111111;
        addr = config.AddressMapping(hex_addr);
        REQUIRE(addr.channel == 5);

        hex_addr = 0b000011111111111111;
        addr = config.AddressMapping(hex_addr);
        REQUIRE(addr.channel == 7);
    }

    SECTION("Test address mapping bank") {
        uint64_t hex_addr = 0b11111111111111;
        auto addr = config.AddressMapping(hex_addr);
        REQUIRE(addr.bank == 0);

        hex_addr = 0b1011111111111111;
        addr = config.AddressMapping(hex_addr);
        REQUIRE(addr.bank == 2);

        hex_addr = 0b1111011111111111111;
        addr = config.AddressMapping(hex_addr);
        REQUIRE(addr.bank == 2);
    }

    SECTION("Test address mapping bankgroup") {
        uint64_t hex_addr = 0b1111111111111111;
        auto addr = config.AddressMapping(hex_addr);
        REQUIRE(addr.bankgroup == 0);

        hex_addr = 0b101111111111111111;
        addr = config.AddressMapping(hex_addr);
        REQUIRE(addr.bankgroup == 2);

        hex_addr = 0b111101111111111111111;
        addr = config.AddressMapping(hex_addr);
        REQUIRE(addr.bankgroup == 2);
    }

    SECTION("Test address mapping rank") {
        uint64_t hex_addr = 0xFFFFFFFFFFFF;
        auto addr = config.AddressMapping(hex_addr);
        REQUIRE(addr.rank == 0);
    }

    SECTION("Test address mapping row") {
        uint64_t hex_addr = 0b111111111111111111;
        auto addr = config.AddressMapping(hex_addr);
        REQUIRE(addr.row == 0);

        hex_addr = 0b10001111111111111111111;
        addr = config.AddressMapping(hex_addr);
        REQUIRE(addr.row == 17);

        hex_addr = 0b10000000000000111111111111111111;
        addr = config.AddressMapping(hex_addr);
        REQUIRE(addr.row == 0b10000000000000);

        hex_addr = 0b11110000000000000111111111111111111;
        addr = config.AddressMapping(hex_addr);
        REQUIRE(addr.row == 0b10000000000000);
    }
}

