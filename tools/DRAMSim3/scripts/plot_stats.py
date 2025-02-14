#!/usr/bin/env python3
"""
Generate time series graphs of power/bandwidth/energy...
"""

import argparse
import json
import os
import sys
import numpy as np
import matplotlib.pyplot as plt


def extract_epoch_data(json_data, label, merge_channel=True):
    """
    TODO enable merge_channel=False option later
    """
    if merge_channel:
        merged_data = {}
        for line in json_data:
            epoch_num = line["epoch_num"]
            if epoch_num in merged_data:
                merged_data[epoch_num] += line[label]
            else:
                merged_data[epoch_num] = line[label]
        return [v for (k, v) in sorted(merged_data.items(),
                                       key=lambda t: t[0])]


def plot_epochs(json_data, label, unit="", output=None):
    """
    plot the time series of a specified stat serie (e.g. bw, power, etc)
    """
    print('ploting {}'.format(label))
    cycles_per_epoch = json_data[0]['num_cycles']
    y_data = extract_epoch_data(json_data, label)
    x_ticks = [i * cycles_per_epoch for i in range(len(y_data))]

    plt.plot(x_ticks, y_data)

    plt.title(label)
    plt.ticklabel_format(style='sci', axis='x', scilimits=(0, 0))
    plt.xlabel('Cycles')
    plt.ylabel('{} ({})'.format(label, unit))
    plt.ylim(bottom=0, top=1.1*max(y_data))
    if output:
        plt.savefig(output+'_epochs_{}.pdf'.format(label))
        plt.clf()
    else:
        plt.show()
    return


def extract_histo_data(data, label):
    array = []
    for chan, channel_data in data.items():
        for key, count in channel_data[label].items():
            val = int(key)
            array.extend([val for _ in range(count)])
    return array


def plot_histogram(json_data, label, unit='', output=None):
    histo_data = extract_histo_data(json_data, label)
    histo_data = sorted(histo_data)
    total_cnt = len(histo_data)
    existing_nums = set()
    unique_vals = 0
    for i in range(int(0.90 * total_cnt)):
        if histo_data[i] in existing_nums:
            continue
        else:
            existing_nums.add(histo_data[i])
            unique_vals += 1
    print('90-Percentile unique {} values: {}'.format(label, unique_vals))
    x_min = min(histo_data)
    x_max = max(histo_data)
    x_99 = int(0.99 * len(histo_data))
    mark_99 = histo_data[x_99]
    avg = np.average(histo_data)
    histo_data = histo_data[0:x_99]
    
    # doane seems to provide better esitmates for bins
    plt.hist(histo_data, bins='doane', density=True)

    line_avg = plt.axvline(x=avg, linestyle='--', c='g',
                           label='Average:{0:.1f}'.format(avg))
    line_99 = plt.axvline(x=mark_99, linestyle='-.', c='r',
                          label='99 Percentile:{0:.1f}'.format(mark_99))
    plt.title(label)
    plt.xlabel(label + ' [max: ' + str(x_max) + '](' + unit + ')')
    plt.ylabel('Density')
    plt.legend(handles=[line_avg, line_99])
    if output:
        plt.savefig(output+'_histo_{}.pdf'.format(label))
        plt.clf()
    else:
        plt.show()
    return


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Plot time serie graphs from '
                                     'stats outputs, type -h for more options')
    parser.add_argument('json', help='stats json file')
    parser.add_argument('-d', '--dir', help='output dir', default='.')
    parser.add_argument('-o', '--output',
                        help='output name (withouth extension name)',
                        default='dramsim')
    parser.add_argument('-k', '--key',
                        help='plot a specific key name in epoch stats, '
                        'use the name in JSON')
    args = parser.parse_args()

    with open(args.json, 'r') as j_file:
        is_epoch = False
        try:
            j_data = json.load(j_file)
        except:
            print('cannot load file ' + args.json)
            exit(1)
        if isinstance(j_data, list):
            is_epoch = True
        else:
            is_epoch = False

    prefix = os.path.join(args.dir, args.output)
    if is_epoch:
        data_units = {'average_bandwidth': 'GB/s',
                      'average_power': 'mW',
                      'average_read_latency': 'cycles'}
        if args.key:
            data_units[args.key] = ''
        for label, unit in data_units.items():
            plot_epochs(j_data, label, unit, prefix)
    else:
        data_units = {'read_latency': 'cycles',
                      'write_latency': 'cycles',
                      'interarrival_latency': 'cycles'}
        for label, unit in data_units.items():
            plot_histogram(j_data, label, unit, prefix)
