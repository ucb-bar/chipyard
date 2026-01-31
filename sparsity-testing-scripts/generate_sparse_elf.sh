#!/bin/bash

set -x

# generate_sparse_elf.sh

# Description:
# This script takes an input ELF file and generates a final sparse ELF file.
# It automates the scanning of memory regions, generation of the linker script,
# extraction of data sections, assembly, and linking, including handling
# 'tohost' and 'fromhost' symbols.

# Usage:
#   ./generate_sparse_elf.sh <input_elf> <output_elf>

# Check for correct number of arguments
if [ "$#" -ne 2 ]; then
    echo "Usage: $0 <input_elf> <output_elf>"
    exit 1
fi

# Input and output ELF files
INPUT_ELF="$1"
OUTPUT_ELF="$2"

# Temporary and intermediate files directory
WORK_DIR="sparse_elf_workdir"
rm -rf "$WORK_DIR"
mkdir -p "$WORK_DIR"

# Paths to scripts (assuming they are in the same directory)
SCRIPT_DIR="$(dirname "$0")"
EXTRACT_REGIONS_SCRIPT="$SCRIPT_DIR/create_mem_regions.py"
GENERATE_LINKER_SCRIPT="$SCRIPT_DIR/linker_script_gen.py"
MEMORY_SCANNER_CPP="$SCRIPT_DIR/memory_region_scanner.cpp"
MEMORY_SCANNER_EXEC="$WORK_DIR/memory_region_scanner"
LINKER_SCRIPT="$WORK_DIR/sparse_mem.ld"
REGIONS_FILE="$WORK_DIR/regions.txt"

# Toolchain prefix (adjust if necessary)
RISCV_PREFIX="riscv64-unknown-elf-"
AS="${RISCV_PREFIX}as"
LD="${RISCV_PREFIX}ld"
NM="${RISCV_PREFIX}nm"
OBJCOPY="${RISCV_PREFIX}objcopy"

# Step 1: Compile the memory region scanner
echo "Compiling memory region scanner..."
g++ -o "$MEMORY_SCANNER_EXEC" "$MEMORY_SCANNER_CPP" -lelf
if [ $? -ne 0 ]; then
    echo "Error: Failed to compile memory_region_scanner.cpp"
    exit 1
fi

# Step 2: Scan the input ELF file to generate regions.txt
echo "Scanning $INPUT_ELF to generate memory regions..."
CHUNK_SIZE=1024  # Adjust chunk size as needed
"$MEMORY_SCANNER_EXEC" "$INPUT_ELF" "$CHUNK_SIZE" "$REGIONS_FILE"
if [ $? -ne 0 ]; then
    echo "Error: Failed to scan memory regions."
    exit 1
fi

echo "Memory regions written to $REGIONS_FILE"

# Step 3: Generate the linker script
echo "Generating linker script..."
python3 "$GENERATE_LINKER_SCRIPT" "$REGIONS_FILE" "$LINKER_SCRIPT"
if [ $? -ne 0 ]; then
    echo "Error: Failed to generate linker script."
    exit 1
fi

# Step 4: Extract data sections and create assembly files
echo "Extracting data sections and creating assembly files..."
python3 "$EXTRACT_REGIONS_SCRIPT" "$INPUT_ELF" "$REGIONS_FILE"
if [ $? -ne 0 ]; then
    echo "Error: Failed to extract data sections."
    exit 1
fi

# Step 5: Find 'tohost' and 'fromhost' symbols in the input ELF
echo "Finding 'tohost' and 'fromhost' symbols in $INPUT_ELF..."
TOHOST_ADDR=$("$NM" "$INPUT_ELF" | grep " tohost$" | awk '{print $1}')
FROMHOST_ADDR=$("$NM" "$INPUT_ELF" | grep " fromhost$" | awk '{print $1}')

if [ -z "$TOHOST_ADDR" ] || [ -z "$FROMHOST_ADDR" ]; then
    echo "Error: 'tohost' or 'fromhost' symbols not found in $INPUT_ELF"
    exit 1
fi

echo "tohost address: 0x$TOHOST_ADDR"
echo "fromhost address: 0x$FROMHOST_ADDR"

# Step 6: Link all object files into the final ELF
echo "Linking object files to create $OUTPUT_ELF..."

# Collect all object files
OBJ_FILES=("$WORK_DIR"/data_mem*.o)

# Build the linker command
LINKER_CMD=("$LD" -T "$LINKER_SCRIPT" "--defsym" "tohost=0x$TOHOST_ADDR" "--defsym" "fromhost=0x$FROMHOST_ADDR" -o "$OUTPUT_ELF")
LINKER_CMD+=("${OBJ_FILES[@]}")

# Optionally include the main program object file if needed
# If you have a main.o, include it here:
# MAIN_OBJ="main.o"
# LINKER_CMD+=("$MAIN_OBJ")

# Run the linker command
"${LINKER_CMD[@]}"
if [ $? -ne 0 ]; then
    echo "Error: Linking failed."
    exit 1
fi

echo "Final sparse ELF file created: $OUTPUT_ELF"

# Optional: Clean up the work directory
# Uncomment the following line if you want to remove intermediate files
rm -rf "$WORK_DIR"

exit 0
