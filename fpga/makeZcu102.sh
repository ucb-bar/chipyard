#!/bin/bash
#


make clean
nohup make SUB_PROJECT=zcu102 bitstream >zcu102Bit.txt 2>&1 &
