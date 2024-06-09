import sys

# PRINT_BUF = 0x4000 / 4
PRINT_BUF = 0x10000 / 4

def parse_log_file(log_file_path):
    target_string = ''  # Initialize the string we'll build from hex values
    print_buf = [0] * 65536

    with open(log_file_path, 'r') as file:
        for line in file:
            if 'core-req-wr' in line:
                # Check rs1_data's last element condition
                rs1_data_start = line.find('addr={') + len('addr={')
                rs1_data_end = line.find('}', rs1_data_start)
                rs1_data_elts = line[rs1_data_start:rs1_data_end].split(', ')

                byteen_start = line.find('byteen={') + len('byteen={')
                byteen_end = line.find('}', byteen_start)
                byteen_elts = line[byteen_start:byteen_end].split(', ')

                rs2_data_start = line.find('data={') + len('data={')
                rs2_data_end = line.find('}', rs2_data_start)
                rs2_data_elts = line[rs2_data_start:rs2_data_end].split(', ')

                # print(rs1_data_last_element)
                for rs1, rs2, byteen in zip(rs1_data_elts, rs2_data_elts, byteen_elts):
                    if int(rs1, 16) >> 18 == 0xff0:
                        offset = (int(rs1, 16) - PRINT_BUF) % 65536
                        if offset < 0 or offset >= 1024:
                            continue
                        else:
                            offset = offset % 16384
                        # Extract rs2_data's last element

                        hex_value = rs2[2:]  # Remove the '0x' prefix
                        if "x" in hex_value:
                            continue
                        byteen_int = int(byteen, 16)
                        hex_value = "0" * (8 - len(hex_value)) + hex_value
                        bytes_object = bytes.fromhex(hex_value) # .replace(b"\x00", b"")

                        masked_bytes_list = []
                            
                        assert(len(bytes_object) == 4)
                        
                        for i, byte in enumerate(bytes_object[::-1]):
                            if byteen_int & (1 << i):
                                masked_bytes_list.append(byte)

                        reversed_bytes = bytes(masked_bytes_list)
                        # print(reversed_bytes.decode('utf-8', errors="ignore"))
                        try:
                            # Decode bytes to string and append
                            target_string += reversed_bytes.decode('ascii', errors="ignore")
                            # print(byteen_int, rs2_data, reversed_bytes, reversed_bytes.decode('ascii', errors="ignore"))
                        except UnicodeDecodeError:
                            # Handle potential decode errors (e.g., invalid UTF-8 sequence)
                            print(f"Skipping invalid UTF-8 sequence: {hex_value}")

    return target_string
# return print_buf

def parse_out_file(out_file_path):
    target_string = ""

    with open(out_file_path, 'r') as file:
        for line in file:
            if 'TRACEWR' in line:
                tokens = line.strip().split()
                addr, data, mask = int(tokens[2], 16), int(tokens[3], 16), int(tokens[4], 16)
                assert((addr >> 20) == 0xff0)
                bytes_object = bytes.fromhex(tokens[3])
                masked_bytes_list = []
                assert(len(bytes_object) <= 4)
                for i, byte in enumerate(bytes_object[::-1]):
                    if mask & (1 << i):
                        masked_bytes_list.append(byte)
                reversed_bytes = bytes(masked_bytes_list)
                try:
                    target_string += reversed_bytes.decode('ascii', errors="ignore")
                except UnicodeDecodeError:
                    print(f"Skipping invalid UTF-8 sequence: {hex_value}")
    return target_string


def main():
    if len(sys.argv) != 2:
        print("Usage: python parse_printf.py <path_to_log_file>")
        sys.exit(1)

    log_file_path = sys.argv[1]
    if log_file_path[-4:] == ".log":
        print(parse_log_file(log_file_path))
    else:
        print(parse_out_file(log_file_path))

    # parsed_string = bytes(parse_log_file(log_file_path))
    # try:
    #     print(parsed_string.decode('utf-8', errors="ignore"))
    # except UnicodeDecodeError:
    #     print(f"Skipping invalid UTF-8 sequence: {hex_value}")

if __name__ == "__main__":
    main()

