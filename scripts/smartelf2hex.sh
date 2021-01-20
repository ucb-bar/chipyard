#!/usr/bin/env bash

# This script find the appropriate arguments to pass to elf2hex by inspecting the given RISC-V elf binary
# First and only argument is the binary to be converted.
# The output of this script should be redirected to a file (as with normal elf2hex).

binary=$1
segments=`readelf --segments --wide $binary`
entry_hex=`echo -e "$segments" | grep "Entry point" | cut -f3 -d' ' | sed 's/0x//' | tr [:lower:] [:upper:]`
entry_dec=`bc <<< "ibase=16;$entry_hex"`
length_hex=`echo "$segments" | grep "LOAD\|TLS" | tail -n 1 | tr -s [:space:] | cut -f4,7 -d' '`
length_dec=`echo $length_hex | tr -d x | tr [:lower:] [:upper:] | tr ' ' + | sed 's/^/ibase=16;/' | sed "s/$/-$entry_hex/" | bc`
power_2_length=`echo "x=l($length_dec)/l(2); scale=0; 2^((x+1)/1)" | bc -l`
width=64
depth=$((power_2_length / width))
elf2hex $width $depth $binary $entry_dec
