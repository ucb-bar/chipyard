import argparse
import sys

parser = argparse.ArgumentParser(description='BOOM branch trace analyzer')
parser.add_argument('-v', '--verbose', action='store_true',
                    help='echo log contents')
parser.add_argument('file', nargs='?', type=argparse.FileType('r'),
                    default=sys.stdin,
                    help='workload.out file')
parser.add_argument('n', nargs='?', type=int, default=1,
                    help='number of branches to print')

args = parser.parse_args()
n = args.n


class BranchInfo:
    def __init__(self, addr, mispredicted, is_br, is_jalr):
        self.count = 1
        self.addr = addr
        self.mispredicted = 1 if mispredicted else 0
        if is_br == '1':
            self.branch_type = "br"
        elif is_jalr == '1':
            self.branch_type = "jalr"
        else:
            self.branch_type = "X"
        self.lhist = ""

    def mispredict_rate(self):
        return self.mispredicted/self.count

    def __str__(self):
        return "({}, {}/{}, {:.4f}, {})".format(
            self.addr,
            self.mispredicted,
            self.count,
            self.mispredicted/self.count,
            self.branch_type)

branches = {}
ghist = '0'*128
for line in args.file:
    l = line.split(' ')
    if len(l) == 6:
        src, taken, is_br, is_jal, is_jalr, addr = map(str, l)
        addr = addr[:-1]
        label = 'br' if is_br == '1' else (
            'jal' if is_jal == '1' else (
            'jalr' if is_jalr == '1' else (
            'X')))
        #print(src, taken, addr, label, flush=True)
        mispredicted = (src == '4')
        if is_jal != '1':# and int(addr, 16) < 0x80000000:
            if addr not in branches:
                branches[addr] = BranchInfo(addr, mispredicted, is_br, is_jalr)
            else:
                branches[addr].count += 1
                branches[addr].mispredicted += (1 if mispredicted else 0)
                branches[addr].lhist += taken
            if is_jalr != '1':
                ghist = ghist[1:] + taken
    elif args.verbose:
        print(line, end='', flush=True)

if not branches:
    sys.exit(0)

blist = list(map(lambda kv:kv[1], branches.items()))
bad_branches       = list(sorted(blist, key=lambda b:(b.mispredict_rate(), b.count)))[::-1]
freq_bad_branches  = list(sorted(blist, key=lambda b:b.mispredicted))[::-1]
good_branches      = list(sorted(blist, key=lambda b:(-b.mispredict_rate(), b.count)))[::-1]
freq_good_branches = list(sorted(blist, key=lambda b:b.count - b.mispredicted))[::-1]
freq_branches      = list(sorted(blist, key=lambda b:b.count))[::-1]

print("Top {} mispredicted branches".format(n))
for i in bad_branches[:n]:
    print(i)

print("Top {} correctly predicted branches".format(n))
for i in good_branches[:n]:
    print(i)

print("Top {} frequently mispredicted branches".format(n))
for i in freq_bad_branches[:n]:
    print(i)

print("Top {} frequently correctly predicted branches".format(n))
for i in freq_good_branches[:n]:
    print(i)

print("Top {} most frequent branches".format(n))
for i in freq_branches[:n]:
    print(i)

jalrs = [b for b in blist if b.branch_type == "jalr"]
brs   = [b for b in blist if b.branch_type == "br"]
mp_jalrs     = sum([b.mispredicted for b in jalrs])
mp_brs       = sum([b.mispredicted for b in brs])
total_jalrs       = float(max(sum([b.count for b in jalrs]), 1))
total_brs         = sum([b.count for b in brs])
print("Mispredicted {}/{} jalrs, {}".format(mp_jalrs,
                                            total_jalrs,
                                            mp_jalrs / total_jalrs))
print("Mispredicted {}/{} brs, {}".format(mp_brs,
                                          total_brs,
                                          mp_brs / total_brs))
print("Mispredicted {}/{} all, {}".format(mp_jalrs + mp_brs,
                                          total_jalrs + total_brs,
                                          (mp_jalrs + mp_brs) / (total_jalrs + total_brs)))

