#!/bin/bash

# Script to run the csmith random test generator multiple times

TEST_NAME=test
SIM=$1
RUN_AMT=-1
P_INST=1
SEED=-1
WDEBUG_EXT=false
NDEBUG=false
PK=false

# Make output directory
OUTPUT_DIR=output
SRC_DIR=sources
mkdir -p output

usage(){
    echo "run-csmith.sh --sim SIM_BINARY [--run RUN_AMT] [--parallel PARALLEL_INSTANCES] [--seed SEED] [--withdebugext] [--nodebug]"
    echo "    --sim -s SIM_BINARY                   is the simulator to test spike against"
    echo "    --run -r RUN_AMT                      is the amount of times to run the csmith tests"
    echo "                                          defaults to infinity"
    echo "    --parallel -p PARALLEL_INSTANCES      is the amount of instances to spawn in parallel"
    echo "                                          defaults to one instance"
    echo "    --seed -e SEED                        runs a single test with the seed specified"
    echo "                                          ignores all other parameters"
    echo "    --withdebugext -d                     run debug version of simulator (used for when"
    echo "                                          main sim is not debug version)"
    echo "                                          (just appends -debug to sim name)"
    echo "    --nodebug -n                          just error when there is a sim mismatch (no vpd)"
    echo "    -k                                    run tests with the proxy kernel (slower, but"
    echo "                                          uses virtual memory)"
}

# Exit everything on one ctrl+c
trap kill_group SIGINT
kill_group(){
    echo ""
    echo "[ALL] Killing instances."
    kill 0
}

# Run the csmith test once
#
# Args:
#   $1 instance that this test is running on
#   $2 seed to run the test with
run_once () {
    BASE_NAME=$OUTPUT_DIR/$TEST_NAME-$1-$2

    echo "[$1] Running csmith test with seed=$2"
    csmith --seed $2 > $BASE_NAME.c

    # Build both a RISCV binary and normal binary

    # Test x86-64 first
    gcc -I$RISCV/include -w $BASE_NAME.c -o $BASE_NAME.bin
    timeout 1s ./$BASE_NAME.bin | awk '{print tolower($0)}' > $BASE_NAME.host.out
    RV=$?
    if [ $RV -ne 0 ]; then
        echo "[$1] x86-64 binary timed out. Discard and start over."
        rm $BASE_NAME.*
        return 0
    fi

    # Test RISCV spike version
    if [ $PK == false ]; then
        riscv64-unknown-elf-gcc -w -I./$SRC_DIR -DPREALLOCATE=1 -mcmodel=medany -static -std=gnu99 -O2 -ffast-math -fno-common -o $BASE_NAME.riscv $BASE_NAME.c $SRC_DIR/syscalls.c $SRC_DIR/crt.S -static -nostdlib -nostartfiles -lm -lgcc -T $SRC_DIR/link.ld -I$RISCV/include
        timeout --foreground 10s spike $BASE_NAME.riscv 1> $BASE_NAME.spike.out 2> $BASE_NAME.spike.log
    else
        riscv64-unknown-elf-gcc $BASE_NAME.c -I$RISCV/include -o $BASE_NAME.riscv -w
        timeout --foreground 1m spike pk $BASE_NAME.riscv 1> $BASE_NAME.spike.out 2> $BASE_NAME.spike.log
    fi
    RV=$?
    if [ $RV -ne 0 ]; then
        echo "[$1] Spike timed out. Discard and start over."
        rm $BASE_NAME.*
        return 0
    fi

    # Compare x86-64 and Spike
    cmp -s <(tr [a-z] [A-Z] <$BASE_NAME.spike.out) <(tr [a-z] [A-Z] <$BASE_NAME.host.out)
    RV=$?
    if [ $RV -ne 0 ]; then
        echo "[$1] Spike produces wrong result compared to x86-64 binary. Discard and start over."
        rm $BASE_NAME.*
        return 0
    fi

    # Compare simulator output versus spike
    if [ $PK == false ]; then
        timeout 15m $SIM $BASE_NAME.riscv 1> $BASE_NAME.sim.out 2> $BASE_NAME.sim.log
    else
        timeout 120m $SIM pk $BASE_NAME.riscv +verbose +vcdplusfile=$BASE_NAME.vpd 1> $BASE_NAME.sim.out 2> $BASE_NAME.sim.log
    fi
    RV=$?
    if [ $RV == 124 ]; then
        echo "[$1] Simulator timed out. Discard and start over."
        rm $BASE_NAME.*
        return 0
    fi

    cmp -s <(grep checksum $BASE_NAME.sim.out) $BASE_NAME.spike.out
    RV=$?
    if [ $RV -ne 0 ]; then
        echo "[$1] Simulator produced wrong result."
        if [ $NDEBUG == false ]; then
            if [ $WDEBUG_EXT == true ]; then
                ${SIM}-debug $BASE_NAME.riscv +verbose +vcdplusfile=$BASE_NAME.vpd 1> $BASE_NAME.sim.out 2> $BASE_NAME.sim.log
            else
                $SIM $BASE_NAME.riscv +verbose +vcdplusfile=$BASE_NAME.vpd 1> $BASE_NAME.sim.out 2> $BASE_NAME.sim.log
            fi
            echo "[$1]  Vpd of error file:     $BASE_NAME.vpd"
            echo "[$1]  Simulator output file: $BASE_NAME.sim.out"
            echo "[$1]  Simulator log file:    $BASE_NAME.sim.log"
        fi
        kill_group
    else
        echo "[$1] Simulator and spike agree."
        rm $BASE_NAME.*
        return 0
    fi
}

# Run the test for a certain amount of times
# Also setup a random seed
#
# Args:
#   $1 instance that this is running on
run() {
    if [ $SEED == -1 ]; then
        if [ $RUN_AMT == -1 ]; then
            while true;
            do
                SEED=$(od -N 4 -t uL -An /dev/urandom | tr -d " ")
                run_once $1 $SEED
            done
        else
            for j in `seq 1 $RUN_AMT`;
            do
                SEED=$(od -N 4 -t uL -An /dev/urandom | tr -d " ")
                run_once $1 $SEED
            done
        fi
    else
        run_once $1 $SEED
    fi
}

# Parse arguments
while [ "$1" != "" ];
do
    case $1 in
        -s | --sim )        shift
                            SIM=$1
                            ;;
        -k | --pk )         PK=true
                            ;;
        -r | --run )        shift
                            RUN_AMT=$1
                            ;;
        -p | --parallel )   shift
                            P_INST=$1
                            ;;
        -e | --seed )       shift
                            SEED=$1
                            ;;
        --withdebugext | -d ) WDEBUG_EXT=true
                            ;;
        --nodebug | -n )    NDEBUG=true
                            ;;
        -h | --help )       usage
                            exit 0
    esac
    shift
done

# Start of script
if [ -z "$SIM" ]; then
    echo "Forgot simulator binary."
    usage
    exit 1
fi

if [ $SEED == -1 ]; then
    if [ $RUN_AMT == -1 ]; then
        echo "Spawning $P_INST instance(s), running csmith infinite times"
    else
        echo "Spawning $P_INST instance(s), running csmith $RUN_AMT times"
    fi

    for i in `seq 1 $P_INST`;
    do
        run $i &
    done
    wait
else
    RUN_AMT=1
    run 1
    wait
fi
