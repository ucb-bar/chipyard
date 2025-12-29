#!/usr/bin/env bash
set -e

OUTPUT_DIR=$(pwd)/logs
RESULTS_DIR=$(pwd)/results
SCRIPTS_DIR=$(pwd)/scripts

mkdir -p $OUTPUT_DIR
mkdir -p $RESULTS_DIR

bmarks=("aha-mont64" "crc32" "cubic" "edn" "huffbench"
        "matmult-int" "minver" "nbody" "nettle-aes"
        "nettle-sha256" "nsichneu" "picojpeg"
        "qrduino" "sglib-combined" "slre" "st"
        "statemate" "ud" "wikisort")
for bmark in "${bmarks[@]}"
do
  spike -l build/$bmark 2>$OUTPUT_DIR/$bmark.log
  python3 $SCRIPTS_DIR/parse_log.py $OUTPUT_DIR/$bmark.log > $RESULTS_DIR/$bmark.txt
done