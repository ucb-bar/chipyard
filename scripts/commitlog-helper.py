#!/usr/bin/env python

# script that takes on a commit log (say, from spike) and adds the dissassembly.
#
# input:
#   * a commit log text file.
#
# output:
#   * stdout


import optparse

def main():
    parser = optparse.OptionParser()
    parser.add_option('-f', '--file', dest='filename', help='input commit log file')
    (options, args) = parser.parse_args()
    if not options.filename:
        parser.error('Please give an input filename with -f')

    f = open(options.filename)

    for line in f:

        # lengths are either 57 or 34 (with or without WB data)
        l = len(line)
        if l < 34:
            print line,
            continue

        inst = line[22:32]
        if l == 57:
            # ignore newline
            new = line[:-1] + " DASM(" + inst + ")"
        else:
            # pad out to equal line sizes
            new = line[:-1] + "                        DASM(" + inst + ")"
        print new



if __name__ == '__main__':
    main()

