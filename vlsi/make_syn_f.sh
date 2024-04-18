#!/bin/bash

# Check if an argument is provided
if [ "$#" -ne 1 ]; then
    echo "Usage: $0 <directory_path>"
    exit 1
fi

directory_path=$1
gen_collateral_path="${directory_path}/gen-collateral"
output_file="${directory_path}/syn.f"

if [ ! -d "$gen_collateral_path" ]; then
    echo "The subdirectory gen-collateral does not exist in the provided directory."
    exit 1
fi

# find "$gen_collateral_path" -type f \( -name "*.v" -o -name "*.sv" \) -exec realpath {} \; > "$output_file"
cat "${directory_path}/"*.top.f > "$output_file"
cat "${directory_path}/"*.bb.f | grep -E ".*v$" >> "$output_file"
find "$gen_collateral_path" -type f \( -name "*.top.mems.v" \) -exec realpath {} \; >> "$output_file"

temp_file=$(mktemp)
grep "pkg" "$output_file" > "$temp_file"
grep "defs_div" "$output_file" >> "$temp_file"
cat "$output_file" | grep -v "pkg" | grep -v "defs_div" | grep -E -v "Sim.*.v" | sort -u >> "$temp_file"
mv "$temp_file" "$output_file"

echo "File paths have been written to $output_file."

