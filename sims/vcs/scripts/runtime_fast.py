import re
import argparse

def parse_log(file_path):
    # Compile regular expressions for the required substrings and format
    pc_line_pattern = re.compile(r"^\s+(\d+):.*?PC=0x([\da-f]+).*?instr=(0xbe90a013|0xe0d0a013).*?$")
    commit_line_pattern = re.compile(r"^\s+(\d+):.*commit:.*$")
    # PC=0x800007a0, instr=0xbe90a013 
    # Initialize dictionaries to track first and last occurrences
    beg_const = "0xbe90a013"
    end_const = "0xe0d0a013"
    occurrences = {
        beg_const: {"first": None, "last": None},
        end_const: {"first": None, "last": None}
    }
    # pc = {
    #     "0xbe90a013": None,
    #     "0xe0d0a013": None
    # }

    with open(file_path, 'r') as log_file:
        for line in log_file:
            match = pc_line_pattern.search(line)
            if match:
                timestamp = int(match.group(1))
                pc_value = match.group(2)
                data_value = match.group(3)

                if occurrences[data_value]["first"] is None:
                    occurrences[data_value]["first"] = timestamp
                occurrences[data_value]["last"] = timestamp
           
    return occurrences

if __name__ == "__main__":
    # Set up command-line argument parsing
    parser = argparse.ArgumentParser(description="Parse log file for specific substrings and their timestamps.")
    parser.add_argument("file_path", help="Path to the log file to parse")
    
    # Parse command-line arguments
    args = parser.parse_args()
    
    # Parse the log file and print the result
    result = parse_log(args.file_path)
    # print(result)

    print(f"{int((result['0xe0d0a013']['last'] - result['0xbe90a013']['first']) * 0.4)}")

