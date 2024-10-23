#include <elf.h>
#include <fcntl.h>
#include <gelf.h>
#include <libelf.h>
#include <unistd.h>
#include <cstdio>
#include <cstring>
#include <string>
#include <vector>
#include <iostream>
#include <fstream>
#include <algorithm>

struct MemoryRegion {
    Elf64_Addr start_addr;
    Elf64_Xword size;
};

void usage(const char *progname) {
    std::cerr << "Usage: " << progname << " <input_elf_file> <chunk_size_in_bytes> <output_file>" << std::endl;
}

int scan_memory_regions(const char *input_file, size_t chunk_size, const char *output_file) {
    if (elf_version(EV_CURRENT) == EV_NONE) {
        std::cerr << "ELF library initialization failed: " << elf_errmsg(-1) << std::endl;
        return 1;
    }

    int fd_in = open(input_file, O_RDONLY);
    if (fd_in < 0) {
        perror("Failed to open input file");
        return 1;
    }

    Elf *e_in = elf_begin(fd_in, ELF_C_READ, nullptr);
    if (!e_in) {
        std::cerr << "elf_begin() failed: " << elf_errmsg(-1) << std::endl;
        close(fd_in);
        return 1;
    }

    GElf_Ehdr ehdr;
    if (gelf_getehdr(e_in, &ehdr) != &ehdr) {
        std::cerr << "Failed to get ELF header: " << elf_errmsg(-1) << std::endl;
        elf_end(e_in);
        close(fd_in);
        return 1;
    }

    // SOOHYUK: Debug: Print ELF header information
    std::cout << "ELF Class: " << ((gelf_getclass(e_in) == ELFCLASS32) ? "32-bit" : "64-bit") << std::endl;
    std::cout << "ELF Data Encoding: " << ((ehdr.e_ident[EI_DATA] == ELFDATA2LSB) ? "Little Endian" : "Big Endian") << std::endl;

    // Retrieve the number of program headers
    size_t n_phdrs;
    if (elf_getphdrnum(e_in, &n_phdrs) != 0) {
        std::cerr << "Failed to get program header count: " << elf_errmsg(-1) << std::endl;
        elf_end(e_in);
        close(fd_in);
        return 1;
    }

    std::vector<MemoryRegion> useful_regions;

    // Iterate over each program header
    for (size_t i = 0; i < n_phdrs; ++i) {
        GElf_Phdr phdr;
        if (gelf_getphdr(e_in, i, &phdr) != &phdr) {
            std::cerr << "Failed to get program header: " << elf_errmsg(-1) << std::endl;
            elf_end(e_in);
            close(fd_in);
            return 1;
        }

        // SOOHYUK: Debug: Print program header information
        // std::cout << "\nProcessing Segment " << i << ":" << std::endl;
        // std::cout << "  Type: " << phdr.p_type << std::endl;
        // std::cout << "  Offset: 0x" << std::hex << phdr.p_offset << std::dec << std::endl;
        // std::cout << "  Virtual Address: 0x" << std::hex << phdr.p_vaddr << std::dec << std::endl;
        // std::cout << "  Physical Address: 0x" << std::hex << phdr.p_paddr << std::dec << std::endl;
        // std::cout << "  File Size: " << phdr.p_filesz << " bytes" << std::endl;
        // std::cout << "  Memory Size: " << phdr.p_memsz << " bytes" << std::endl;
        // std::cout << "  Flags: " << phdr.p_flags << std::endl;

        if (phdr.p_type != PT_LOAD) {
            // std::cout << "  Skipping non-loadable segment." << std::endl;
            continue;
        }

        // SOOHYUK: Debug: Print virtual address and size (to accomodate memory concatenations)
        // std::cout << "  Segment Virtual Address: 0x" << std::hex << phdr.p_vaddr << std::dec << std::endl;
        // std::cout << "  Segment Size (filesz): " << phdr.p_filesz << " bytes" << std::endl;

        // Seek to the segment's file offset
        if (lseek(fd_in, phdr.p_offset, SEEK_SET) < 0) {
            perror("Failed to seek in input file");
            elf_end(e_in);
            close(fd_in);
            return 1;
        }

        // Read the segment's data
        std::vector<unsigned char> segment_data(phdr.p_filesz);
        ssize_t bytes_read = read(fd_in, segment_data.data(), phdr.p_filesz);
        if (bytes_read != static_cast<ssize_t>(phdr.p_filesz)) {
            perror("Failed to read segment data");
            std::cerr << "Expected: " << phdr.p_filesz << ", Read: " << bytes_read << std::endl;
            elf_end(e_in);
            close(fd_in);
            return 1;
        }

        // Calculate the number of chunks in this segment
        size_t num_chunks = (phdr.p_filesz + chunk_size - 1) / chunk_size;

        for (size_t chunk_idx = 0; chunk_idx < num_chunks; ++chunk_idx) {
            size_t chunk_offset = chunk_idx * chunk_size;
            size_t chunk_end = std::min(chunk_offset + chunk_size, static_cast<size_t>(phdr.p_filesz));
            size_t actual_chunk_size = chunk_end - chunk_offset;

            // Check if the chunk is all zeros
            bool all_zero = std::all_of(segment_data.begin() + chunk_offset, segment_data.begin() + chunk_end,
                                        [](unsigned char c) { return c == 0; });

            // Compute the absolute start address
            Elf64_Addr absolute_start_addr = phdr.p_vaddr + chunk_offset;

            // Debug: Print chunk information
            // std::cout << "  Chunk " << chunk_idx << ": "
            //           << "Offset " << std::hex << chunk_offset
            //           << ", Size " << std::dec << actual_chunk_size
            //           << ", Absolute Start Address: 0x" << std::hex << absolute_start_addr
            //           << ", All Zero: " << (all_zero ? "Yes" : "No") << std::dec << std::endl;

            if (!all_zero) {
                // Record the useful memory region
                MemoryRegion region;
                region.start_addr = absolute_start_addr;
                region.size = actual_chunk_size;
                useful_regions.push_back(region);
            }
        }
    }

    // Clean up ELF resources
    elf_end(e_in);
    close(fd_in);

    // Sort the useful regions by starting address for clarity
    std::sort(useful_regions.begin(), useful_regions.end(),
              [](const MemoryRegion &a, const MemoryRegion &b) -> bool {
                  return a.start_addr < b.start_addr;
              });

    // Output the useful memory regions to the output file
    std::ofstream ofs(output_file);
    if (!ofs) {
        std::cerr << "Failed to open output file: " << output_file << std::endl;
        return 1;
    }

    for (const auto &region : useful_regions) {
        ofs << "0x" << std::hex << region.start_addr << " " << std::dec << region.size << std::endl;
    }

    ofs.close();

    std::cout << "\nELF file scan complete." << std::endl;
    // std::cout << "\nScan complete. Useful memory regions written to: " << output_file << std::endl;
    return 0;
}

int main(int argc, char **argv) {
    if (argc != 4) {
        usage(argv[0]);
        return 1;
    }
    const char *input_elf = argv[1];
    size_t chunk_size = std::stoul(argv[2]);
    const char *output_file = argv[3];

    return scan_memory_regions(input_elf, chunk_size, output_file);
}
