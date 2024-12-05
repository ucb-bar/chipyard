#!/usr/bin/env python3

# generate_linker_script.py

import sys

def generate_linker_script(regions, output_filename):
    with open(output_filename, 'w') as f:
        f.write("/* sparse_mem.ld - Auto-generated Linker Script */\n\n")

        # Define a single MEMORY region
        f.write("MEMORY\n")
        f.write("{\n")
        f.write("    MEM (rwx) : ORIGIN = 0x80000000, LENGTH = 0x10000000\n")
        f.write("}\n\n")

        # Define SECTIONS with specified origin addresses
        f.write("SECTIONS\n")
        f.write("{\n")
        for idx, (start, size, mem_region) in enumerate(regions):
            section_name = f".data_mem{idx}"
            f.write(f"    {section_name} 0x{start:X} :\n")
            f.write("    {\n")
            f.write(f"        KEEP(*({section_name}))\n")
            f.write("    } > ")
            f.write(f"{mem_region}\n\n")

        # Standard sections
        f.write("    .text :\n")
        f.write("    {\n")
        f.write("        *(.text)\n")
        f.write("    } > MEM\n\n")

        f.write("    .bss :\n")
        f.write("    {\n")
        f.write("        *(.bss)\n")
        f.write("        *(COMMON)\n")
        f.write("    } > MEM\n\n")

        f.write("    /* Additional sections can be defined here */\n")
        f.write("}\n")

def parse_regions_with_memory(regions_file):
    """
    Parses regions.txt and assigns each region to the single MEMORY region MEM.
    Assumes regions.txt has lines with: start_address size
    Sizes are specified in decimal.
    """
    regions = []
    with open(regions_file, 'r') as rf:
        for line in rf:
            parts = line.strip().split()
            if len(parts) != 2:
                continue
            start_str, size_str = parts
            try:
                start = int(start_str, 16)  # Start address in hexadecimal
                size = int(size_str, 10)    # Size in decimal
                mem_region = "MEM"          # Single memory region
                regions.append( (start, size, mem_region) )
            except ValueError:
                print(f"Warning: Invalid number format in line: {line.strip()}")
                continue
    return regions

def main():
    if len(sys.argv) != 3:
        print("Usage: python3 generate_linker_script.py <regions_file> <output_ld_file>")
        sys.exit(1)

    regions_file = sys.argv[1]
    output_ld_file = sys.argv[2]

    regions = parse_regions_with_memory(regions_file)
    if not regions:
        print("Error: No valid regions found in regions.txt.")
        sys.exit(1)

    generate_linker_script(regions, output_ld_file)
    print(f"Linker script '{output_ld_file}' generated successfully.")

if __name__ == "__main__":
    main()
