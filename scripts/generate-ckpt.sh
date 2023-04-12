#!/bin/bash

set -e

usage() {
    echo "Usage: $0 [OPTIONS] -- [SPIKEFLAGS]"
    echo ""
    echo "Options"
    echo "  --help -h  : Display this message"
    echo "  -n <n>     : Mumber of harts"
    echo "  -b <elf>   : Binary to run in spike"
    echo "  -p <pc>    : PC to take checkpoint at [default 0x80000000]"
    echo "  -c <cycles>: Cycles after PC to take checkpoint at [default 0]"
    exit "$1"
}

NHARTS=1
BINARY=""
PC="0x80000000"
CYCLES=0
while [ "$1" != "" ];
do
    case $1 in
	-h | --help )
	    usage 3 ;;
	-n )
	    shift
	    NHARTS=$1 ;;
	-b )
	    shift
	    BINARY=$1 ;;
	-p )
	    shift
	    PC=$1 ;;
	-c )
	    shift
	    CYCLES=$1 ;;
	* )
	    error "Invalid option $1"
	    usage 1 ;;
    esac
    shift
done
BASEMEM="$((0x80000000)):$((0x10000000))"
SPIKEFLAGS="-p$NHARTS --pmpregions=0 --isa=rv64gc -m$BASEMEM"

rm -rf cmds_tmp.txt
touch cmds_tmp.txt

echo "Generating state capture spike interactive commands in cmds_tmp.txt"
echo "until pc 0 $PC" >> cmds_tmp.txt
echo "rs $CYCLES" >> cmds_tmp.txt
echo "dump" >> cmds_tmp.txt
for (( h=0; h<$NHARTS; h++ ))
do
    echo "pc $h" >> cmds_tmp.txt
    echo "priv $h" >> cmds_tmp.txt
    echo "reg $h fcsr" >> cmds_tmp.txt

    echo "reg $h stvec" >> cmds_tmp.txt
    echo "reg $h sscratch" >> cmds_tmp.txt
    echo "reg $h sepc" >> cmds_tmp.txt
    echo "reg $h scause" >> cmds_tmp.txt
    echo "reg $h stval" >> cmds_tmp.txt
    echo "reg $h satp" >> cmds_tmp.txt

    echo "reg $h mstatus" >> cmds_tmp.txt
    echo "reg $h medeleg" >> cmds_tmp.txt
    echo "reg $h mideleg" >> cmds_tmp.txt
    echo "reg $h mie" >> cmds_tmp.txt
    echo "reg $h mtvec" >> cmds_tmp.txt
    echo "reg $h mscratch" >> cmds_tmp.txt
    echo "reg $h mepc" >> cmds_tmp.txt
    echo "reg $h mcause" >> cmds_tmp.txt
    echo "reg $h mtval" >> cmds_tmp.txt
    echo "reg $h mip" >> cmds_tmp.txt

    echo "reg $h mcycle" >> cmds_tmp.txt
    echo "reg $h minstret" >> cmds_tmp.txt

    echo "mtime" >> cmds_tmp.txt
    echo "mtimecmp $h" >> cmds_tmp.txt

    for (( fr=0; fr<32; fr++ ))
    do
	echo "freg $h $fr" >> cmds_tmp.txt
    done
    for (( xr=0; xr<32; xr++ ))
    do
	echo "reg $h $xr" >> cmds_tmp.txt
    done
done
echo "quit" >> cmds_tmp.txt

#cat cmds_tmp.txt
BASENAME=$(basename -- $BINARY)

echo "Capturing state at checkpoint to spikeout"
spike -d --debug-cmd=cmds_tmp.txt $SPIKEFLAGS $BINARY 2> $BASENAME.loadarch

echo "Finding tohost/fromhost in elf file"
TOHOST=$(riscv64-unknown-elf-nm $BINARY | grep tohost | head -c 16)
FROMHOST=$(riscv64-unknown-elf-nm $BINARY | grep fromhost | head -c 16)

echo "Compiling memory to elf"
riscv64-unknown-elf-objcopy -I binary -O elf64-littleriscv mem.0x80000000.bin $BASENAME.mem.elf
riscv64-unknown-elf-ld -Tdata=0x80000000 -nmagic --defsym tohost=0x$TOHOST --defsym fromhost=0x$FROMHOST -o $BASENAME.loadarch.elf $BASENAME.mem.elf
