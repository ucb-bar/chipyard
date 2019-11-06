import math
import re
import sys
import argparse
from collections import defaultdict

STATE_RE = re.compile("(.+) state *(\d+) - *(\d+) cycles @ *(\d+)")

def state_cycle_table(f, arg_start, arg_end):
    histos = defaultdict(lambda : defaultdict(int))
    counts = defaultdict(lambda : defaultdict(int))

    for line in f:
        line = line.strip()
        m = STATE_RE.match(line)
        if m:
            statename, statestr, cyclestr, endstr = m.groups()
            state = int(statestr)
            cycles = int(cyclestr)
            end_cycle = int(endstr)
            start_cycle = end_cycle - cycles

            if start_cycle > arg_start and end_cycle < arg_end:
                histos[statename][state] += cycles
                counts[statename][state] += 1

    return histos, counts

def main():
    parser = argparse.ArgumentParser(description = "Process state transition output")
    parser.add_argument("--start", dest="start", type=int, default=0,
                        help = "Cycle to start histogram at")
    parser.add_argument("--end", dest="end", type=int, default= 1<<64,
                        help = "Cycle to end histogram at")
    parser.add_argument("--buckets", dest="buckets", type=int, default=20,
                        help = "Max number of buckets for each state")
    parser.add_argument("infile", help="Input log file")
    args = parser.parse_args()

    with open(args.infile, "r") as f:
        histos, counts = state_cycle_table(f, args.start, args.end)

        for (name, histo) in histos.items():
            total_cycles = sum(histo.values())
            max_state = max(histo.keys())

            if max_state > args.buckets:
                buckets = [0] * args.buckets
                states_per_bucket = int(math.ceil(float(max_state) / args.buckets))
                for (state, cycles) in histo.items():
                    k = state / states_per_bucket
                    buckets[k] += cycles
                for (k, cycles) in enumerate(buckets):
                    start = k * states_per_bucket
                    end = min((k + 1) * states_per_bucket - 1, max_state)
                    percent = float(cycles) / total_cycles * 100
                    print("{} {}-{}: {} cycles ({:.01f}%)".format(
                        name, start, end, cycles, percent))
            else:
                for (state, cycles) in histo.items():
                    count = counts[name][state]
                    percent = float(cycles) / total_cycles * 100
                    average = float(cycles) / count
                    print("{} {}: {} cycles ({:.01f}%) {:.01f} cycles avg".format(
                        name, state, cycles, percent, average))
            print("-------------------------------------")

if __name__ == "__main__":
    main()
