#!/usr/bin/env python
"""
replaces a `include with the full include file.

args
$1 - file which has includes to be replaced
$2 - file in which output will be written
$3 - list of directories to search for includes
     (note: NON-RECURSIVE must specify all dirs)
      includes are found relative to this path
      this is equivalent to something like +incdir+
"""

import os
import re
import shutil
import sys
import tempfile


def print_info(msg):
    """
    Print an info message.

    Args:
        msg (str): message to print
    """
    print(f"[INFO] {msg}")


def print_error(msg, critical=True):
    """
    Print an error message.

    Args:
        msg (str): message to print
        critical (bool): whether to exit after printing the message
    """
    if critical:
        sys.exit(f"[ERROR] {msg}")
    else:
        print(f"[ERROR] {msg}")


def find_include(file_name, inc_dirs):
    """
    Find the include file in the list of directories.

    Args:
        file_name (str): include file name
        inc_dirs (list): list of directories to search for includes

    Returns:
        str: full path to the include file
    """
    for d in inc_dirs:
        inc_file_name = d + "/" + file_name
        if os.path.exists(inc_file_name):
            return inc_file_name
    print_error(f"Include file {file_name} not found in {inc_dirs}")
    return None


def process_helper(in_fname, out_f, inc_dirs, replaced_includes):
    """
    Helper function to DFS through include files and replace includes.
    """
    include_regex = re.compile(r"^ *`include +\"(.*)\"")
    # slurp the input file.
    # this avoids having a bunch of fds open during recursion
    with open(in_fname, "r", encoding="utf-8") as in_file:
        lines = in_file.readlines()

    for num, line in enumerate(lines, 1):
        match = re.match(include_regex, line)
        if not match or match.group(1) == "uvm_macros.svh":
            # copy the line as is
            out_f.write(line)
            continue
        if match.group(1) in replaced_includes:
            print_info("Skipping duplicate include")
            continue

        print_info(
            f"Replacing includes for {match.group(1)}" f" at line {num}"
        )
        # search for include and replace
        inc_file_name = find_include(match.group(1), inc_dirs)
        replaced_includes.add(match.group(1))
        process_helper(inc_file_name, out_f, inc_dirs, replaced_includes)


def process(in_fname, out_fname, inc_dirs=None):
    """
    Replace include directives in a file with the full include file.

    Args:
        in_fname (str): input file name
        out_fname (str): output file name
        inc_dirs (list): list of directories to search for includes
    """
    replaced_includes = set()
    with open(out_fname, "w", encoding="utf-8") as out_file:
        process_helper(in_fname, out_file, inc_dirs, replaced_includes)


def main():
    """
    Entry point for the script.

    Args:
    <input file> <output file> <list of directories to search for includes>
    """
    in_vlog = sys.argv[1]
    out_vlog = sys.argv[2]

    if in_vlog == out_vlog:
        sys.exit("[ERROR] The input and output file cannot be the same.")

    # add directories to search list
    inc_dirs = sys.argv[3:]
    print("[INFO] Replaces includes from: " + str(in_vlog))
    print("[INFO] Searching following dirs for includes: " + str(inc_dirs))

    # make a copy of the input file
    _, temp_path = tempfile.mkstemp()
    shutil.copy2(in_vlog, temp_path)
    process(temp_path, out_vlog, inc_dirs)

    print("[INFO] Success. Output written to: " + str(out_vlog))


if __name__ == "__main__":
    main()
