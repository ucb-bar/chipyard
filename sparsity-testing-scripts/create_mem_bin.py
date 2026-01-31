# create_mem_bin.py
import struct

def create_mem_bin(filename, total_size=0x10000000, pattern=0xDEADBEEF):
    with open(filename, 'wb') as f:
        # Write first 16 bytes (4 repetitions of 0xDEADBEEF)
        for _ in range(4):
            f.write(struct.pack('<I', pattern))  # Little endian
        # Write the remaining bytes as zeros
        remaining = total_size - 16
        chunk_size = 4096  # Write in chunks to handle large sizes
        zero_chunk = b'\x00' * chunk_size
        while remaining > 0:
            write_size = min(chunk_size, remaining)
            f.write(zero_chunk[:write_size])
            remaining -= write_size

if __name__ == '__main__':
    create_mem_bin('mem.bin')
