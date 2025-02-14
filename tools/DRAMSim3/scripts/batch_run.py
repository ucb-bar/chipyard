#!/usr/bin/python

"""
Run a batch of configs
"""

import argparse
import os
import shlex
import subprocess
import sys
import tempfile
# project modules
import analysis
import parse_config

def process_configs(args):
    """given parsed args, return the config files in a list"""
    configs = []
    config_dirs = []
    if not args.input:
        print "please specify at least one input from -i option"
        exit(1)
        
    for item in args.input:
        if not os.path.exists(item):
            print "Input ", item, " not exists!"
            exit(1)

        if os.path.isfile(item):
            if item[-4:] != ".ini":
                print "INFO: ignoring non-ini file:", item
            else:
                configs.append(item)
        elif os.path.isdir(item):
            for f in os.listdir(item):
                if f[-4:] != ".ini":
                    print "INFO: ignoring non-ini file:", f
                else:
                    configs.append(os.path.join(item, f))
        else:
            print "???"
            exit(1)
    return configs


def build_summary(config_files, stats_csvs, summary_name):
    # get some info from configs
    protocols = []
    for c in config_files:
        protocols.append(parse_config.get_protocol(c))
    
    density = []
    for c in config_files:
        density.append(parse_config.get_density(c))

    page_sizes = []
    for c in config_files:
        page_sizes.append(parse_config.get_page_size(c))

    pure_config_names = []
    for f in config_files:
        pure_config_names.append(os.path.basename(f)[:-4])

    summary_df = analysis.get_summary_df(stats_csvs)
    summary_df = summary_df.assign(config=pure_config_names,
                                   protocol=protocols,
                                   density=density,
                                   page_size=page_sizes)
    summary_df.set_index("config", inplace=True)
    summary_df.to_csv(summary_name)
   

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Run a batch of simulation"
                                     "with a given set of config files "
                                     "and possibliy redirect output, -h for help")
    parser.add_argument("executable", help="executable location")
    parser.add_argument("-o", "--output-dir", nargs="?", default="", help="override the 'output-prefix'"
                        "and the outputs will be 'output-dir/config-name-post-fix'"
                        "this won't mess the original file by using tempfiles")
    parser.add_argument("-i", "--input", nargs="+", help="configs input, could be a dir or list of files"
                        "non-ini files will be ignored")
    parser.add_argument("-n", default=100000, type=int, help="num of cycles, applied to all configs")
    parser.add_argument("--cpu-type", default="random", choices=["random", "trace", "stream"],
                        help="cpu type")
    parser.add_argument("-t", "--trace-file", help="trace file, only for trace cpu")
    parser.add_argument("-q", "--quiet", action="store_true", help="Silent output from simulation (not this script)" )
    parser.add_argument("-s", "--summary", default="summary.csv", help="Summary sheet name")
    args = parser.parse_args()

    # some input sanity check
    if not os.path.exists(args.executable):
        print "Exicutable: ", args.executable, " not exits!"
        exit(1)

    configs = process_configs(args)   

    if args.output_dir:
        if not os.path.exists(args.output_dir):
            try:
                os.mkdir(args.output_dir)
                print "WARNING: output dir not exists, creating one..."
            except OSError:
                print "cannot make directory: ", args.output_dir
                exit(1)
        print "INFO: Overriding the output directory to: ", args.output_dir
    
    if args.cpu_type == "trace":
        if not os.path.exists(args.trace_file):
            print "trace file not found for trace cpu"
    
    output_prefixs = []
    pure_config_names = []
    for c in configs:
        mem_type = "default"
        if "hmc" in c.lower():
            mem_type = "hmc"

        config_file = c
        pure_config_name = os.path.basename(c)[:-4]
        pure_config_names.append(pure_config_name)

        prefix = parse_config.get_val_from_file(c, "other", "output_prefix")
        if args.output_dir:
            prefix = os.path.join(args.output_dir, prefix)
        output_prefixs.append(prefix)

        cmd_str = "%s --memory-type=%s --cpu-type=%s -c %s -n %d --output-dir %s" % \
                  (args.executable, mem_type, args.cpu_type, config_file, args.n, args.output_dir)
        
        if args.cpu_type == "trace":
            cmd_str += " --trace-file=%s" % args.trace_file

        print "EXECUTING:", cmd_str
        temp_stdout = None
        if args.quiet:
            temp_stdout = tempfile.TemporaryFile()
        else:
            temp_stdout = sys.stdout
        try:
            subprocess.call(shlex.split(cmd_str), stdout=temp_stdout)
        except KeyboardInterrupt:
            print "skipping this one..."

    print "INFO: Finished execution phase, generating results summary..."

    # prepare building summary csv
    if args.output_dir:
        summary_dir = args.output_dir
    else:
        summary_dir = "."
    stats_csvs = []
    for output_pre in output_prefixs:
        stats_csvs.append(output_pre+"stats.csv")

    build_summary(configs, stats_csvs, os.path.join(args.output_dir, args.summary))

    