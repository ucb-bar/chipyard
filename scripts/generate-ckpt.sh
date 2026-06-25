#!/bin/bash

set -e

# assumes that only memory region is at 0x80000000. this might break with more regions (and/or at different locations)
DEFAULT_MEM_START_ADDR=0x80000000

NHARTS=1
BINARY=""
PC="$DEFAULT_MEM_START_ADDR"
INSN=
INSNS=0
ISA="rv64gc"
OUTPATH=""
MEMOVERRIDE=""
VERBOSE=0
DTS=
TYPE="defaultspikedts"

usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Misc Options"
    echo "  --help -h  : Display this message"
    echo "  -o <out>   : Output directory to store the checkpoint in. [default <elf>.<pc>.<insn>.<insns>.<dtstype>.loadarch]"
    echo "  -v         : Verbose"
    echo ""
    echo "Required Options"
    echo "  -b <elf>   : Binary to run in spike"
    echo ""
    echo "Other Required Options (with defaults if excluded)"
    echo "  -r <mem>   : Memory regions to pass to spike. Passed to spike's '-m' flag. [default starting at $DEFAULT_MEM_START_ADDR with 256MiB]"
    echo ""
    echo "Options Group (one is required)"
    echo "  -p <pc>    : PC to take checkpoint at [default $PC]"
    echo "  -t <insn>  : Instruction (in hex) to take checkpoint at [default is to use ${INSN:-none}]"
    echo "  -i <insns> : Instructions after PC to take checkpoint at [default $INSNS]"
    echo ""
    echo "Mutually Exclusive Option Groups (each group is mutually exclusive with one another)"
    echo "  Group: Use Spike default DTS with modifications (can choose multiple)"
    echo "    -n <n>     : Number of harts [default $NHARTS]"
    echo "    -m <isa>   : ISA to pass to spike for checkpoint generation [default $ISA]"
    echo "  Group: Use custom DTS"
    echo "    Important Note: a dts only affects the devices used, pmps, mmu."
    echo "                    the isa string and num. of harts from the dts is inferred from this script to pass to the spike --isa and -p flags, respectively."
    echo "    -s <dts>   : DTS file to use. Converted to a DTB then passed to spike's '--dtb' flag. [default is to use ${DTS:-none}]"
    exit "$1"
}

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
	-i )
	    shift
	    INSNS=$1 ;;
	-t )
	    shift
	    INSN=$1 ;;
        -m )
            shift
            ISA=$1 ;;
        -o )
            shift
            OUTPATH=$1 ;;
        -r )
            shift
            MEMOVERRIDE=$1 ;;
        -v )
            VERBOSE=1 ;;
	-s )
	    shift
	    TYPE="customdts"
	    DTS=$1 ;;
	* )
	    error "Invalid option $1"
	    usage 1 ;;
    esac
    shift
done

if [[ $VERBOSE -eq 1 ]] ; then
    set -x
fi

BASENAME=$(basename -- $BINARY)

if [ -z "$OUTPATH" ] ; then
    OUTPATH=$BASENAME.$PC.${INSN:-unused}.$INSNS.$TYPE.loadarch
fi

echo "Generating loadarch directory $OUTPATH"
rm -rf $OUTPATH
mkdir -p $OUTPATH

SPIKEFLAGS=""

if [ -z "$MEMOVERRIDE" ] ; then
    BASEMEM="$(($DEFAULT_MEM_START_ADDR)):$((0x10000000))"
else
    BASEMEM=$MEMOVERRIDE
fi
SPIKEFLAGS+=" -m$BASEMEM"

DTB=
if [ ! -z "$DTS" ] ; then
    dtc -I dts -O dtb -o $OUTPATH/tmp.dtb $(readlink -f $DTS)
    DTB=$OUTPATH/tmp.dtb
fi

# TODO: unsure if mem region can be set from dtb (for now users must provide it separately)
if [ ! -z "$DTB" ]; then
    SPIKEFLAGS+=" --dtb=$DTB"

    # HACK: set the global spike isa string with the dtb value (ensure isa's are the same across harts)
    all_isas=$(dtc -I dtb -O dts $(readlink -f $DTB) | grep "riscv,isa")
    unique_isas=$(echo "$all_isas" | sort -u)
    if [[ $(echo "$unique_isas" | wc -l) == "1" ]]; then
	ISA=$(echo "$unique_isas" | sed 's/.*"\(.*\)".*/\1/')
    else
	echo "Unable to set ISA from DT{B,S}. Ensure all hart ISAs are equivalent."
	exit 1
    fi

    # HACK: set the global spike number of harts with the dtb hart count
    NHARTS=$(echo "$all_isas" | wc -l)
fi

# pmpregions is overridden by the dt{b,s} so fine to include on CLI here
SPIKEFLAGS+=" --pmpregions=0 --isa=$ISA -p$NHARTS"

LOADARCH_FILE=$OUTPATH/loadarch
RAWMEM_ELF=$OUTPATH/raw.elf
LOADMEM_ELF=$OUTPATH/mem.elf
CMDS_FILE=$OUTPATH/cmds_tmp.txt
SPIKECMD_FILE=$OUTPATH/spikecmd.sh

echo "Generating state capture spike interactive commands in $CMDS_FILE"
if [ ! -z "$INSN" ]; then
    echo "until insn 0 $INSN" >> $CMDS_FILE
else
    echo "until pc 0 $PC" >> $CMDS_FILE
fi
echo "rs $INSNS" >> $CMDS_FILE
echo "dump" >> $CMDS_FILE
for (( h=0; h<$NHARTS; h++ ))
do
    echo "pc $h" >> $CMDS_FILE
    echo "priv $h" >> $CMDS_FILE
    echo "reg $h fcsr" >> $CMDS_FILE

    echo "reg $h vstart" >> $CMDS_FILE
    echo "reg $h vxsat" >> $CMDS_FILE
    echo "reg $h vxrm" >> $CMDS_FILE
    echo "reg $h vcsr" >> $CMDS_FILE
    echo "reg $h vtype" >> $CMDS_FILE

    echo "reg $h stvec" >> $CMDS_FILE
    echo "reg $h sscratch" >> $CMDS_FILE
    echo "reg $h sepc" >> $CMDS_FILE
    echo "reg $h scause" >> $CMDS_FILE
    echo "reg $h stval" >> $CMDS_FILE
    echo "reg $h satp" >> $CMDS_FILE

    echo "reg $h mstatus" >> $CMDS_FILE
    echo "reg $h medeleg" >> $CMDS_FILE
    echo "reg $h mideleg" >> $CMDS_FILE
    echo "reg $h mie" >> $CMDS_FILE
    echo "reg $h mtvec" >> $CMDS_FILE
    echo "reg $h mscratch" >> $CMDS_FILE
    echo "reg $h mepc" >> $CMDS_FILE
    echo "reg $h mcause" >> $CMDS_FILE
    echo "reg $h mtval" >> $CMDS_FILE
    echo "reg $h mip" >> $CMDS_FILE

    echo "reg $h mcycle" >> $CMDS_FILE
    echo "reg $h minstret" >> $CMDS_FILE

    echo "mtime" >> $CMDS_FILE
    echo "mtimecmp $h" >> $CMDS_FILE

    for (( fr=0; fr<32; fr++ ))
    do
	echo "freg $h $fr" >> $CMDS_FILE
    done
    for (( xr=0; xr<32; xr++ ))
    do
	echo "reg $h $xr" >> $CMDS_FILE
    done
    echo "vreg $h" >> $CMDS_FILE
done
echo "quit" >> $CMDS_FILE

echo "spike -d --debug-cmd=$CMDS_FILE $SPIKEFLAGS $BINARY" > $SPIKECMD_FILE

echo "Capturing state at checkpoint to spikeout"
echo $NHARTS > $LOADARCH_FILE
spike -d --debug-cmd=$CMDS_FILE $SPIKEFLAGS $BINARY 2>> $LOADARCH_FILE
sed -i '/stdout/d' $LOADARCH_FILE


echo "Finding tohost/fromhost in elf file to inject in new elf"
function get_symbol_value() {
    echo $(riscv64-unknown-elf-nm $2 | grep " ${1}$" | head -c 16)
}
# ensure these symbols are not 'htif_*' versions
TOHOST=$(get_symbol_value tohost $BINARY)
FROMHOST=$(get_symbol_value fromhost $BINARY)

MEM_DUMP=mem.${DEFAULT_MEM_START_ADDR}.bin
echo "Compiling memory to elf"
du -sh $MEM_DUMP
riscv64-unknown-elf-objcopy -I binary -O elf64-littleriscv $MEM_DUMP $RAWMEM_ELF
rm -rf $MEM_DUMP

riscv64-unknown-elf-ld -Tdata=$DEFAULT_MEM_START_ADDR -nmagic --defsym tohost=0x$TOHOST --defsym fromhost=0x$FROMHOST -o $LOADMEM_ELF $RAWMEM_ELF
rm -rf $RAWMEM_ELF

if [[ -z "$DTB" && -z "$DTS" ]] ; then
    echo "Ensure that (at minimum) you have memory regions corresponding to $BASEMEM in downstream RTL tooling"
fi

echo "Loadarch directory $OUTPATH created"
