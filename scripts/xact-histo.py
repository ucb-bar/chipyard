import argparse
import numpy as np
import re
import sys
from collections import defaultdict

XACT_RE = re.compile("(.*) transaction *(\d+) to *(\d+)")
SUFFIX_RE = re.compile("_\d+")

def main():
    parser = argparse.ArgumentParser(
        description = "Generate transaction histogram from simulation output")
    parser.add_argument("--start", dest="start", type=int, default=0,
                        help = "Cycle to start histogram at")
    parser.add_argument("--end", dest="end", type=int, default= 1<<64,
                        help = "Cycle to end histogram at")
    parser.add_argument("--buckets", dest="buckets", type=int, default=10,
                        help = "Number of histogram buckets")
    parser.add_argument("infile", help="Input log file")
    args = parser.parse_args()

    with open(args.infile, "r") as f:
        xact_groups = defaultdict(list)
        for line in f:
            line = line.strip()
            m = XACT_RE.match(line)
            if m:
                name, startstr, endstr = m.groups()
                realname = SUFFIX_RE.sub("", name)
                start_cycle = int(startstr)
                end_cycle = int(endstr)
                if start_cycle > args.start and end_cycle < args.end:
                    xact_groups[realname].append(end_cycle - start_cycle)

        for (name, xacts) in xact_groups.items():
            (hist, edges) = np.histogram(xacts, args.buckets)

            for i, count in enumerate(hist):
                start, end = tuple(edges[i:i+2])
                print("{} {} - {}: {}".format(name, start, end, count))

            avg = np.mean(xacts)
            dev = np.std(xacts)

            print("{} mean {:.2f} +/- {:.2f}".format(name, avg, dev))

if __name__ == "__main__":
    main()
