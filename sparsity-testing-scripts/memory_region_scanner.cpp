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
#include <cassert>
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
    std::cout << "\nProgram Headers: " << n_phdrs << std::endl;

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
        std::cout << "\nProcessing Segment " << i << ":" << std::endl;
        std::cout << "  Type: " << phdr.p_type << std::endl;
        std::cout << "  Offset: 0x" << std::hex << phdr.p_offset << std::dec << std::endl;
        std::cout << "  Virtual Address: 0x" << std::hex << phdr.p_vaddr << std::dec << std::endl;
        std::cout << "  Physical Address: 0x" << std::hex << phdr.p_paddr << std::dec << std::endl;
        std::cout << "  File Size: " << phdr.p_filesz << " bytes" << std::endl;
        std::cout << "  Memory Size: " << phdr.p_memsz << " bytes" << std::endl;
        std::cout << "  Flags: " << phdr.p_flags << std::endl;

        if (phdr.p_type != PT_LOAD) {
            // std::cout << "  Skipping non-loadable segment." << std::endl;
            continue;
        }

        // SOOHYUK: Debug: Print virtual address and size (to accomodate memory concatenations)
        std::cout << "  Segment Virtual Address: 0x" << std::hex << phdr.p_vaddr << std::dec << std::endl;
        std::cout << "  Segment Size (filesz): " << phdr.p_filesz << " bytes" << std::endl;

        // Seek to the segment's file offset
        if (lseek(fd_in, phdr.p_offset, SEEK_SET) < 0) {
            perror("Failed to seek in input file");
            elf_end(e_in);
            close(fd_in);
            return 1;
        }

        // Read the segment's data
        std::vector<unsigned char> chunk_data(chunk_size);
        std::cout << "  Start processing...\n" << std::endl;

        assert(phdr.p_filesz % chunk_size == 0);

        ssize_t total_bytes_read = 0;
        while (total_bytes_read != phdr.p_filesz) {
            ssize_t bytes_read = read(fd_in, chunk_data.data(), chunk_size);
            if (bytes_read == -1) {
                perror("Failed to read segment data");
                elf_end(e_in);
                close(fd_in);
                return 1;
            }

            // Check if the chunk is all zeros
            bool all_zero = std::all_of(chunk_data.begin(), chunk_data.end(),
                                        [](unsigned char c) { return c == 0; });

            // Compute the absolute start address
            Elf64_Addr absolute_start_addr = phdr.p_vaddr + total_bytes_read;

            if (!all_zero) {
                if (useful_regions.size() != 0) {
                    auto last_region = useful_regions.back();
                    // check to see if you can compact
                    if ((last_region.start_addr + last_region.size) == absolute_start_addr) {
                        useful_regions.back() = {last_region.start_addr, last_region.size + bytes_read};
                        //std::cout << "C";
                    } else {
                        useful_regions.push_back({absolute_start_addr, (Elf64_Xword)bytes_read});
                        //std::cout << "N";
                    }
                } else {
                    useful_regions.push_back({absolute_start_addr, (Elf64_Xword)bytes_read});
                    //std::cout << "N";
                }
            }

            total_bytes_read += bytes_read;
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

    size_t comp_size = 0;
    std::cout << "Regions Found: " << useful_regions.size() << std::endl;
    for (const auto &region : useful_regions) {
        ofs << "0x" << std::hex << region.start_addr << " " << std::dec << region.size << std::endl;
        comp_size += region.size;
    }
    std::cout << "(In theory) Compressed ELF Size: " << comp_size << "bytes" << std::endl;

    ofs.close();

    std::cout << "\nScan complete. Useful memory regions written to: " << output_file << std::endl;
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
