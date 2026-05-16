#!/bin/bash

# set -e

# usage() {
#     echo "Usage: $0 [OPTIONS]"
#     echo ""
#     echo "Options"
#     echo "  --help -h  : Display this message"
#     echo "  -n <n>     : Number of harts"
#     echo "  -b <elf>   : Binary to run in spike"
#     echo "  -p <pc>    : PC to take checkpoint at [default 0x80000000]"
#     echo "  -i <insns> : Instructions after PC to take checkpoint at [default 0]"
#     echo "  -m <isa>   : ISA to pass to spike for checkpoint generation [default rv64gc]"
#     echo "  -o <out>   : Output directory to store the checkpoint in. [default <elf>.<pc>.<insns>.loadarch]"
#     exit "$1"
# }

# NHARTS=1
BINARY="hello.riscv.0x80000000.1000.loadarch/mem.elf"
# PC="0x80000000"
# INSNS=0
# ISA="rv64gc"
# OUTPATH=""
# while [ "$1" != "" ];
# do
#     case $1 in
# 	-h | --help )
# 	    usage 3 ;;
# 	-n )
# 	    shift
# 	    NHARTS=$1 ;;
# 	-b )
# 	    shift
# 	    BINARY=$1 ;;
# 	-p )
# 	    shift
# 	    PC=$1 ;;
# 	-i )
# 	    shift
# 	    INSNS=$1 ;;
#         -m )
#             shift
#             ISA=$1 ;;
#         -o )
#             shift
#             OUTPATH=$1 ;;
# 	* )
# 	    error "Invalid option $1"
# 	    usage 1 ;;
#     esac
#     shift
# done

# BASEMEM="$((0x80000000)):$((0x10000000))"
# # SooHyuk: change thisBASEMEM="$((0x80000000)):$((0x20000000))"

# SPIKEFLAGS="-p$NHARTS --pmpregions=0 --isa=$ISA -m$BASEMEM"
# BASENAME=$(basename -- $BINARY)

# if [ -z "$OUTPATH" ] ; then
#     OUTPATH=$BASENAME.$PC.$INSNS.loadarch
# fi

# echo "Generating loadarch directory $OUTPATH"
# rm -rf $OUTPATH
# mkdir -p $OUTPATH

# LOADARCH_FILE=$OUTPATH/loadarch
# RAWMEM_ELF=$OUTPATH/raw.elf
# LOADMEM_ELF=$OUTPATH/mem.elf
# CMDS_FILE=$OUTPATH/cmds_tmp.txt
# SPIKECMD_FILE=$OUTPATH/spikecmd.sh

# echo "Generating state capture spike interactive commands in $CMDS_FILE"
# echo "until pc 0 $PC" >> $CMDS_FILE
# echo "rs $INSNS" >> $CMDS_FILE
# echo "dump" >> $CMDS_FILE
# for (( h=0; h<$NHARTS; h++ ))
# do
#     echo "pc $h" >> $CMDS_FILE
#     echo "priv $h" >> $CMDS_FILE
#     echo "reg $h fcsr" >> $CMDS_FILE

#     echo "reg $h vstart" >> $CMDS_FILE
#     echo "reg $h vxsat" >> $CMDS_FILE
#     echo "reg $h vxrm" >> $CMDS_FILE
#     echo "reg $h vcsr" >> $CMDS_FILE
#     echo "reg $h vtype" >> $CMDS_FILE

#     echo "reg $h stvec" >> $CMDS_FILE
#     echo "reg $h sscratch" >> $CMDS_FILE
#     echo "reg $h sepc" >> $CMDS_FILE
#     echo "reg $h scause" >> $CMDS_FILE
#     echo "reg $h stval" >> $CMDS_FILE
#     echo "reg $h satp" >> $CMDS_FILE

#     echo "reg $h mstatus" >> $CMDS_FILE
#     echo "reg $h medeleg" >> $CMDS_FILE
#     echo "reg $h mideleg" >> $CMDS_FILE
#     echo "reg $h mie" >> $CMDS_FILE
#     echo "reg $h mtvec" >> $CMDS_FILE
#     echo "reg $h mscratch" >> $CMDS_FILE
#     echo "reg $h mepc" >> $CMDS_FILE
#     echo "reg $h mcause" >> $CMDS_FILE
#     echo "reg $h mtval" >> $CMDS_FILE
#     echo "reg $h mip" >> $CMDS_FILE

#     echo "reg $h mcycle" >> $CMDS_FILE
#     echo "reg $h minstret" >> $CMDS_FILE

#     echo "mtime" >> $CMDS_FILE
#     echo "mtimecmp $h" >> $CMDS_FILE

#     for (( fr=0; fr<32; fr++ ))
#     do
# 	echo "freg $h $fr" >> $CMDS_FILE
#     done
#     for (( xr=0; xr<32; xr++ ))
#     do
# 	echo "reg $h $xr" >> $CMDS_FILE
#     done
#     echo "vreg $h" >> $CMDS_FILE
# done
# echo "quit" >> $CMDS_FILE

# echo "spike -d --debug-cmd=$CMDS_FILE $SPIKEFLAGS $BINARY" > $SPIKECMD_FILE

# echo "Capturing state at checkpoint to spikeout"
# spike -d --debug-cmd=$CMDS_FILE $SPIKEFLAGS $BINARY 2> $LOADARCH_FILE


echo "Finding tohost/fromhost in elf file"
TOHOST=$(riscv64-unknown-elf-nm $BINARY | grep tohost | head -c 16)
FROMHOST=$(riscv64-unknown-elf-nm $BINARY | grep fromhost | head -c 16)

# echo "Compiling memory to elf"
# riscv64-unknown-elf-objcopy -I binary -O elf64-littleriscv mem.0x80000000.bin $RAWMEM_ELF
# rm -rf mem.0x80000000.bin

echo "Link mem elf files with tohost/fromhost"
# riscv64-unknown-elf-ld -T linker_temp.ld --defsym tohost=0x$TOHOST --defsym fromhost=0x$FROMHOST -o mem_combined.elf mem.0x80000000.o mem.0xA0000000.o
# riscv64-unknown-elf-ld -T linker_temp.ld --defsym tohost=0x$TOHOST --defsym fromhost=0x$FROMHOST -o final_program_hosts_linked.elf final_program.elf
riscv64-unknown-elf-ld -T sparse_mem.ld --defsym tohost=0x$TOHOST --defsym fromhost=0x$FROMHOST -o final_program.elf data_mem0.o data_mem1.o data_mem2.o data_mem3.o data_mem4.o data_mem5.o data_mem6.o data_mem7.o data_mem8.o data_mem9.o data_mem10.o
# riscv64-unknown-elf-ld -Tdata=0x80000000 -nmagic --defsym tohost=0x$TOHOST --defsym fromhost=0x$FROMHOST -o $LOADMEM_ELF $RAWMEM_ELF
# rm -rf $RAWMEM_ELF

