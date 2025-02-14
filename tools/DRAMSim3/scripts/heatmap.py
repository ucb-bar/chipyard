#!/usr/bin/env python3
import argparse
import os
import sys
from collections import OrderedDict

try:
    import numpy as np
    import pandas as pd
    import matplotlib
except:
    print("please install numpy, matplotlib and pandas")
    raise

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import matplotlib.patches as patches



def construct_mesh(df, val_key, xkey="x", ykey="y"):
    """
    to construct mesh data structure to be used for plotting later
    this should be as generic as possible
    the 3 keys in the args are used to locate data in the dataframe
    return a dict that can be used by pcolor/pcolormesh functions
    """
    x = df[xkey]
    y = df[ykey]
    t = df[val_key]
    xx, yy = np.meshgrid(x.unique(), y.unique())
    zz = []
    y_groups = df.groupby(ykey)
    for i in y.unique():
        group = y_groups.get_group(i)
        z = group[val_key]
        zz.append(z)
    zz = np.array(zz)
    res = {"x":xx, "y":yy, "val":zz}
    return res


def plot_heatmap(x, y, t, title, save_to=""):
    """
    quick access to single heatmap
    """
    fig = plt.figure()
    ax = fig.add_subplot(111)
    pcm = ax.pcolormesh(x, y, t, cmap="coolwarm")
    fig.colorbar(pcm, ax=ax, extend="max")
    fig.savefig("heatmap.png")


def plot_sub_heatmap(fig, ax, x, y, t, title):
    pcm = ax.pcolormesh(x, y, t, cmap="coolwarm")
    ax.set_title(title)
    # no offset used since temperature should be straightforward
    c_bar_formatter = matplotlib.ticker.ScalarFormatter(useOffset=False)
    cbar = fig.colorbar(pcm, ax=ax, extend="max", format=c_bar_formatter)
    return fig, ax

def prep_fig_axes(num_plots):
    """
    so for each z dimension we plot 1 fig and if
    there are multiple channels plot them as subplots
    returns a dict with fig as keys and axes list as values
    """
    fig = plt.figure(tight_layout=True)
    if num_plots == 1:
        n_rows = 1
        n_cols = 1
    elif num_plots == 2:
        n_rows = 1
        n_cols = 2
    elif num_plots == 4:
        n_rows = 2
        n_cols = 2
    elif num_plots ==8:
        n_rows = 2
        n_cols = 4
    elif num_plots == 16:
        n_rows = 4
        n_cols = 4
    elif num_plots == 32:
        n_rows = 4
        n_cols = 8
    elif num_plots == 64:
        n_rows = 8
        n_cols = 8
    else:
        n_rows = 1
        n_cols = 1
    # create axes in subplot
    plot_num = 1
    for i in range(num_plots):
        # args in add_subplot start from 1
        ax = fig.add_subplot(n_rows, n_cols, plot_num)
        plot_num += 1
    return fig, fig.get_axes()


def plot_multi_rank_heatmap(multi_rank_data, save_to=""):
    """
    this should also be a generic plot function 
    return figs and axes so that we can process it later
    """
    num_plots = len(multi_rank_data)
    fig, axes = prep_fig_axes(num_plots)
    plot_num = 0
    for ax in axes:
        data = multi_rank_data[plot_num]
        plot_sub_heatmap(fig, ax, data["x"], data["y"], data["val"], data["title"])
        plot_num += 1
    return fig, axes


def plot_bank_patch(bank_line_data, fig_axes, show_num=True):
    z = bank_line_data["z"]
    fig = fig_axes[z]["fig"]
    axes = fig_axes[z]["axes"]
    x_len = bank_line_data["end_x"] - bank_line_data["start_x"]
    y_len = bank_line_data["end_y"] - bank_line_data["start_y"]
    starting_coord = (bank_line_data["start_x"], bank_line_data["start_y"])
    for ax in axes:
        ax.add_patch(
            patches.Rectangle(
                starting_coord,
                x_len,
                y_len,
                fill=False,
                edgecolor=None,  # default
                linewidth=None  # default
            )
        )
        if show_num:
            ax.text(
                starting_coord[0] + 2, 
                starting_coord[1] + 2,
                "B" + str(bank_line_data["bank_id"]),
                fontsize=8,
                color="white"
            )

    return


def save_figs(figs, prefix, img_format="png"):
    for i, fig_axes in enumerate(figs):
        fig = fig_axes["fig"]
        fig_name = prefix + str(i) + "." + img_format
        print("generating ",fig_name)
        fig.savefig(fig_name)


def plot_simulation(stats_csv_file, bank_pos_file):
    """
    so for each z dimension we plot 1 fig and if
    there are multiple channels plot them as subplots
    """
    frame = pd.read_csv(stats_csv_file)
    die_frames = frame.groupby("z")
    power_data = []
    temp_data = []
    for z, z_frame in die_frames:
        rank_frames = z_frame.groupby("rank_channel_index")
        z_power_data = []
        z_temp_data = []
        for rank, rank_frame in rank_frames:
            rank_power_data = construct_mesh(rank_frame, "power", "x", "y")
            rank_power_data["title"] = "die_" + str(z) + "_channel_rank_" + str(rank) + "_power"
            rank_temp_data = construct_mesh(rank_frame, "temperature", "x", "y")
            rank_temp_data["title"] = "die_" + str(z) + "_channel_rank_" + str(rank) + "_temperature"
            z_power_data.append(rank_power_data)
            z_temp_data.append(rank_temp_data)
        power_data.append(z_power_data)
        temp_data.append(z_temp_data)
    
    num_figs = len(power_data)
    power_figs = []
    temp_figs = []
    for i in range(num_figs):
        fig, axes = plot_multi_rank_heatmap(power_data[i])
        power_figs.append({"fig":fig, "axes":axes})
        fig, axes = plot_multi_rank_heatmap(temp_data[i])
        temp_figs.append({"fig":fig, "axes":axes})

    bank_pos_frame = pd.read_csv(bank_pos_file)

    bank_pos = []
    for index, row in bank_pos_frame.iterrows():
        plot_bank_patch(row, power_figs)
        plot_bank_patch(row, temp_figs)
    return power_figs, temp_figs

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Plot power and temperature heatmap")
    parser.add_argument("-p", "--prefix", help="prefix of the simulation,"
                        "if this is provided then no need for other arguments",
                        default = "")
    parser.add_argument("-s", "--stats-csv", help="temp and power stats csv file")
    parser.add_argument("-b", "--bank-csv", help="bank postion csv file")
    args = parser.parse_args()
    prefix = args.prefix
    if prefix:
        csv_file = prefix + "final_power_temperature.csv"
        bank_pos_file = prefix + "bank_position.csv"
    else:
        if  args.stats_csv:
            print(args.stats_csv)
            csv_file = args.stats_csv
        else:
            print("need stats csv file for temp and power!")
            exit(1)
        if args.bank_csv:
            print(args.bank_csv)
            bank_pos_file = args.bank_csv
        else:
            print("need bank position file!")
            exit(1)
    p_figs, t_figs = plot_simulation(csv_file, bank_pos_file)
    save_figs(p_figs, prefix + "fig_power_")
    save_figs(t_figs, prefix + "fig_temp_")
