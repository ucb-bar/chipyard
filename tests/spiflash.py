#!/usr/bin/env python3
# Generates a binary file that the SPI test uses

import argparse

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Generate a binary file for SPI test")
    parser.add_argument("--outfile", type=str, default="spiflash.img", help="Output file")
    args = parser.parse_args()

    outfile = args.outfile

    with open(outfile, "wb") as f:
        for i in range(0,0x100000,4):
            check = 0xdeadbeef - i
            f.write(check.to_bytes(4, "little"))
