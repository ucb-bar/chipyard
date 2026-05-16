#!/usr/bin/env python3

import sys
import struct
import os

def parse_regions(regions_file):
    """
    Parses the regions.txt file and returns a list of tuples containing start addresses and sizes.
    """
    regions = []
    with open(regions_file, 'r') as f:
        for line in f:
            # Skip empty lines and comments
            if not line.strip() or line.startswith('#'):
                continue
            parts = line.strip().split()
            if len(parts) != 2:
                print(f"Warning: Invalid line format: {line.strip()}")
                continue
            start_str, size_str = parts
            try:
                start = int(start_str, 16)
                size = int(size_str, 10)
                regions.append( (start, size) )
            except ValueError:
                print(f"Warning: Invalid hexadecimal number in line: {line.strip()}")
                continue
    return regions

def read_elf_header(f):
    """
    Reads the ELF header from the file and returns a dictionary with relevant fields.
    """
    f.seek(0)
    # ELF header for 64-bit little endian
    elf_header_struct = struct.Struct('<16sHHIQQQIHHHHHH')
    elf_header_data = f.read(elf_header_struct.size)
    unpacked = elf_header_struct.unpack(elf_header_data)

    elf_header = {
        'e_ident': unpacked[0],
        'e_type': unpacked[1],
        'e_machine': unpacked[2],
        'e_version': unpacked[3],
        'e_entry': unpacked[4],
        'e_phoff': unpacked[5],
        'e_shoff': unpacked[6],
        'e_flags': unpacked[7],
        'e_ehsize': unpacked[8],
        'e_phentsize': unpacked[9],
        'e_phnum': unpacked[10],
        'e_shentsize': unpacked[11],
        'e_shnum': unpacked[12],
        'e_shstrndx': unpacked[13],
    }
    return elf_header

def read_program_headers(f, elf_header):
    """
    Reads all program headers and returns a list of dictionaries.
    """
    program_headers = []
    f.seek(elf_header['e_phoff'])
    ph_struct = struct.Struct('<IIQQQQQQ')  # For 64-bit ELF
    for _ in range(elf_header['e_phnum']):
        ph_data = f.read(elf_header['e_phentsize'])
        if len(ph_data) < ph_struct.size:
            print("Error: Incomplete program header.")
            sys.exit(1)
        unpacked = ph_struct.unpack(ph_data[:ph_struct.size])
        ph = {
            'p_type': unpacked[0],
            'p_flags': unpacked[1],
            'p_offset': unpacked[2],
            'p_vaddr': unpacked[3],
            'p_paddr': unpacked[4],
            'p_filesz': unpacked[5],
            'p_memsz': unpacked[6],
            'p_align': unpacked[7],
        }
        program_headers.append(ph)
    return program_headers

def extract_data(f, program_headers, start_va, size):
    """
    Extracts 'size' bytes of data from 'f' starting at virtual address 'start_va'.
    """
    data = bytearray()
    end_va = start_va + size
    for ph in program_headers:
        if ph['p_type'] != 1:  # PT_LOAD
            continue
        seg_start = ph['p_vaddr']
        seg_end = seg_start + ph['p_memsz']
        # Check if segment overlaps with the region
        if seg_end <= start_va or seg_start >= end_va:
            continue
        # Calculate overlap
        overlap_start = max(start_va, seg_start)
        overlap_end = min(end_va, seg_end)
        overlap_size = overlap_end - overlap_start
        # Calculate file offset
        offset = ph['p_offset'] + (overlap_start - ph['p_vaddr'])
        # Read the data
        f.seek(offset)
        chunk = f.read(overlap_size)
        if len(chunk) < overlap_size:
            print(f"Warning: Could not read enough data for VA 0x{overlap_start:X}")
            chunk += b'\x00' * (overlap_size - len(chunk))
        # Calculate where to place the data in the region
        region_offset = overlap_start - start_va
        # Ensure data array is big enough
        while len(data) < region_offset:
            data += b'\x00'
        # Insert data_chunk at the correct offset
        if len(data) < region_offset + overlap_size:
            data += b'\x00' * (region_offset + overlap_size - len(data))
        data[region_offset:region_offset + overlap_size] = chunk
    # After processing all segments, ensure data is exactly 'size' bytes
    if len(data) < size:
        data += b'\x00' * (size - len(data))
    elif len(data) > size:
        data = data[:size]
    return data

def create_binary_file(data, output_bin):
    """
    Writes the binary data to 'output_bin'.
    """
    with open(output_bin, 'wb') as f:
        f.write(data)

def create_assembly_file(symbol_name, section_name, data_bin, output_asm):
    """
    Creates an assembly file that defines a section containing the binary data.
    """
    with open(output_asm, 'w') as f:
        f.write(f"/* {output_asm} - Auto-generated Assembly File */\n\n")
        f.write(f"    .section {section_name}, \"aw\", @progbits\n")
        f.write(f"    .global {symbol_name}\n")
        f.write(f"{symbol_name}:\n")
        f.write(f"    .incbin \"{data_bin}\"\n\n")

def assemble_section(asm_file, obj_file):
    """
    Assembles the assembly file into an object file using the RISC-V assembler.
    """
    import subprocess
    cmd = ['riscv64-unknown-elf-as', '-o', obj_file, asm_file]
    try:
        subprocess.check_call(cmd)
    except subprocess.CalledProcessError as e:
        print(f"Error: Assembly failed for {asm_file}: {e}")
        sys.exit(1)

def main():
    if len(sys.argv) != 3:
        print("Usage: python3 extract_regions.py <mem.elf> <regions.txt>")
        sys.exit(1)

    mem_elf = sys.argv[1]
    regions_file = sys.argv[2]

    # Check if mem.elf exists
    if not os.path.isfile(mem_elf):
        print(f"Error: File '{mem_elf}' does not exist.")
        sys.exit(1)

    # Check if regions.txt exists
    if not os.path.isfile(regions_file):
        print(f"Error: File '{regions_file}' does not exist.")
        sys.exit(1)

    regions = parse_regions(regions_file)
    if not regions:
        print("Error: No valid regions found in regions.txt.")
        sys.exit(1)

    # Open mem.elf
    with open(mem_elf, 'rb') as f:
        elf_header = read_elf_header(f)
        # Verify ELF Magic Number
        if elf_header['e_ident'][:4] != b'\x7fELF':
            print("Error: Not a valid ELF file.")
            sys.exit(1)
        # Verify 64-bit ELF
        if elf_header['e_ident'][4] != 2:
            print("Error: Only 64-bit ELF files are supported.")
            sys.exit(1)
        # Parse program headers
        program_headers = read_program_headers(f, elf_header)

        for idx, (start, size) in enumerate(regions):
            # print(f"Processing region {idx}: Start=0x{start:X}, Size=0x{size:X}")
            data = extract_data(f, program_headers, start, size)

            # Create binary file
            data_bin = f"sparse_elf_workdir/data_mem{idx}.bin"
            create_binary_file(data, data_bin)
            # print(f"  Created binary file: {data_bin}")

            # Create assembly file
            section_name = f".data_mem{idx}"
            symbol_name = f"data_mem{idx}"  # Changed symbol name to avoid leading '.'
            asm_file = f"sparse_elf_workdir/data_mem{idx}.S"
            create_assembly_file(symbol_name, section_name, data_bin, asm_file)
            # print(f"  Created assembly file: {asm_file}")

            # Assemble into .o file
            obj_file = f"sparse_elf_workdir/data_mem{idx}.o"
            assemble_section(asm_file, obj_file)
            # print(f"  Assembled object file: {obj_file}")

    print("All memory regions processed successfully.")

if __name__ == "__main__":
    main()
