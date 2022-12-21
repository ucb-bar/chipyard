#!/usr/bin/env python3

# Generates a binary file that the SPI test uses

outfile = "spiflash.img"

with open(outfile, 'wb') as f:
    for i in range(0,0x100000,4):
        check = 0xdeadbeef - i
        f.write(check.to_bytes(4,'little'))
