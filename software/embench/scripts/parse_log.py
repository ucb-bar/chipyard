# first arg is the log file
import re
import sys

BRANCH_OPCODES = ["beq", "bge", "bgeu", "blt", "bltu", "bne", "beqz", "bnez",
                                "bgez", "blez", "bltz", "bgtz", "bgt", "ble", "bgtu", "bleu",
                                "c.beqz", "c.bnez", "c.bltz", "c.bgez"]
IJ_OPCODES = ["jal", "j", "call", "tail", "c.j", "c.jal"]
UJ_OPCODES = ["jalr", "jr", "c.jr", "c.jalr", "ret"]

# example line:
# core   0: 0x0000000080000000 (0x00004081) c.li    ra, 0
# extract PC and opcode
pattern = r'core\s+0:\s+0x([0-9a-fA-F]+)\s+\((0x[0-9a-fA-F]+)\)\s+([a-zA-Z0-9_.]+)\s*(.*)'


log_file = sys.argv[1]

with open(log_file, 'r') as f:
    lines = f.readlines()


total_control_flow_change_counts = 0
branch_counts = 0
ij_counts = 0
uj_counts = 0

for line in lines:
    match = re.match(pattern, line)
    if match:
        pc = match.group(1)
        opcode = match.group(3)
        # print(f"PC: {pc}, Opcode: {opcode}")
        if opcode in BRANCH_OPCODES:
            total_control_flow_change_counts += 1
            branch_counts += 1
        elif opcode in IJ_OPCODES:
            ij_counts += 1
        elif opcode in UJ_OPCODES:
            uj_counts += 1

print(f"Total control flow change counts: {total_control_flow_change_counts}")
print(f"Branch counts: {branch_counts}")
print(f"IJ counts: {ij_counts}")
print(f"UJ counts: {uj_counts}")