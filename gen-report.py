import re
import sys

ENTRY_RE = re.compile(r"(\w+) = (\d+)")

def calc_stats(fname):
    with open(fname) as f:
        counts = {}
        for line in f:
            m = ENTRY_RE.match(line)
            if m:
                name, val = m.groups()
                counts[name] = int(val)
        cpi = float(counts['mcycle']) / counts['minstret']
        pa = 1.0 - float(counts['mhpmcounter3']) / counts['mhpmcounter4']
        return cpi, pa

def main():
    for fname in sys.argv[1:]:
        (cpi, pa) = calc_stats(fname)
        print("==> {} <==".format(fname))
        print("CPI: {:0.2f}".format(cpi))
        print("Prediction Accuracy: {:0.2f}".format(pa))
        print("")

if __name__ == "__main__":
    main()
