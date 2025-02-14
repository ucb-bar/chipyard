#include <iostream>

#include "fmt/format.h"
#include "simple_stats.h"

namespace dramsim3 {

template <class T>
void PrintStatText(std::ostream& where, std::string name, T value,
                   std::string description) {
    // not making this a class method because we need to calculate
    // power & bw later, which are not BaseStat members
    where << fmt::format("{:<30}{:^3}{:>12}{:>5}{}", name, " = ", value, " # ",
                         description)
          << std::endl;
    return;
}

SimpleStats::SimpleStats(const Config& config, int channel_id)
    : config_(config), channel_id_(channel_id) {
    // counter stats
    InitStat("num_cycles", "counter", "Number of DRAM cycles");
    InitStat("epoch_num", "counter", "Number of epochs");
    InitStat("num_reads_done", "counter", "Number of read requests issued");
    InitStat("num_writes_done", "counter", "Number of read requests issued");
    InitStat("num_write_buf_hits", "counter", "Number of write buffer hits");
    InitStat("num_read_row_hits", "counter", "Number of read row buffer hits");
    InitStat("num_write_row_hits", "counter",
             "Number of write row buffer hits");
    InitStat("num_read_cmds", "counter", "Number of READ/READP commands");
    InitStat("num_write_cmds", "counter", "Number of WRITE/WRITEP commands");
    InitStat("num_act_cmds", "counter", "Number of ACT commands");
    InitStat("num_pre_cmds", "counter", "Number of PRE commands");
    InitStat("num_ondemand_pres", "counter", "Number of ondemend PRE commands");
    InitStat("num_ref_cmds", "counter", "Number of REF commands");
    InitStat("num_refb_cmds", "counter", "Number of REFb commands");
    InitStat("num_srefe_cmds", "counter", "Number of SREFE commands");
    InitStat("num_srefx_cmds", "counter", "Number of SREFX commands");
    InitStat("hbm_dual_cmds", "counter", "Number of cycles dual cmds issued");

    // double stats
    InitStat("act_energy", "double", "Activation energy");
    InitStat("read_energy", "double", "Read energy");
    InitStat("write_energy", "double", "Write energy");
    InitStat("ref_energy", "double", "Refresh energy");
    InitStat("refb_energy", "double", "Refresh-bank energy");

    // Vector counter stats
    InitVecStat("all_bank_idle_cycles", "vec_counter",
                "Cyles of all bank idle in rank", "rank", config_.ranks);
    InitVecStat("rank_active_cycles", "vec_counter", "Cyles of rank active",
                "rank", config_.ranks);
    InitVecStat("sref_cycles", "vec_counter", "Cyles of rank in SREF mode",
                "rank", config_.ranks);

    // Vector of double stats
    InitVecStat("act_stb_energy", "vec_double", "Active standby energy", "rank",
                config_.ranks);
    InitVecStat("pre_stb_energy", "vec_double", "Precharge standby energy",
                "rank", config_.ranks);
    InitVecStat("sref_energy", "vec_double", "SREF energy", "rank",
                config_.ranks);

    // Histogram stats
    InitHistoStat("read_latency", "Read request latency (cycles)", 0, 200, 10);
    InitHistoStat("write_latency", "Write cmd latency (cycles)", 0, 200, 10);
    InitHistoStat("interarrival_latency",
                  "Request interarrival latency (cycles)", 0, 100, 10);

    // some irregular stats
    InitStat("average_bandwidth", "calculated", "Average bandwidth");
    InitStat("total_energy", "calculated", "Total energy (pJ)");
    InitStat("average_power", "calculated", "Average power (mW)");
    InitStat("average_read_latency", "calculated",
             "Average read request latency (cycles)");
    InitStat("average_interarrival", "calculated",
             "Average request interarrival latency (cycles)");
}

void SimpleStats::AddValue(const std::string name, const int value) {
    auto& epoch_counts = epoch_histo_counts_[name];
    if (epoch_counts.count(value) <= 0) {
        epoch_counts[value] = 1;
    } else {
        epoch_counts[value] += 1;
    }
}

std::string SimpleStats::GetTextHeader(bool is_final) const {
    std::string header =
        "###########################################\n## Statistics of "
        "Channel " +
        std::to_string(channel_id_);
    if (!is_final) {
        header += " of epoch " + std::to_string(counters_.at("epoch_num"));
    }
    header += "\n###########################################\n";
    return header;
}

double SimpleStats::RankBackgroundEnergy(const int rank) const{
    return vec_doubles_.at("act_stb_energy")[rank] +
           vec_doubles_.at("pre_stb_energy")[rank] +
           vec_doubles_.at("sref_energy")[rank];
}

void SimpleStats::PrintEpochStats() {
    UpdateEpochStats();
    if (config_.output_level >= 1) {
        std::ofstream j_out(config_.json_epoch_name, std::ofstream::app);
        j_out << j_data_;
    }
    if (config_.output_level >= 2) {
        std::cout << GetTextHeader(false);
        for (const auto& it : print_pairs_) {
            PrintStatText(std::cout, it.first, it.second,
                          header_descs_[it.first]);
        }
    }
    print_pairs_.clear();
}

void SimpleStats::PrintFinalStats() {
    UpdateFinalStats();

    if (config_.output_level >= 0) {
        std::ofstream j_out(config_.json_stats_name, std::ofstream::app);
        j_out << "\"" << std::to_string(channel_id_) << "\":";
        j_out << j_data_;
    }

    if (config_.output_level >= 1) {
        // HACK: overwrite existing file if this is first channel
        auto perm = channel_id_ == 0 ? std::ofstream::out : std::ofstream::app;
        std::ofstream txt_out(config_.txt_stats_name, perm);
        txt_out << GetTextHeader(true);
        for (const auto& it : print_pairs_) {
            PrintStatText(txt_out, it.first, it.second,
                          header_descs_[it.first]);
        }
    }

    print_pairs_.clear();
}

void SimpleStats::Reset() {
    for (auto& it : counters_) {
        it.second = 0;
    }
    for (auto& it : epoch_counters_) {
        it.second = 0;
    }
    for (auto& vec : vec_counters_) {
        std::fill(vec.second.begin(), vec.second.end(), 0);
    }
    for (auto& vec : epoch_vec_counters_) {
        std::fill(vec.second.begin(), vec.second.end(), 0);
    }
    for (auto& it : doubles_) {
        it.second = 0.0;
    }
    for (auto& vec : vec_doubles_) {
        std::fill(vec.second.begin(), vec.second.end(), 0.0);
    }
    for (auto& it : calculated_) {
        it.second = 0.0;
    }
    for (auto& it : histo_counts_) {
        it.second.clear();
    }
    for (auto& it : epoch_histo_counts_) {
        it.second.clear();
    }
}

void SimpleStats::InitStat(std::string name, std::string stat_type,
                           std::string description) {
    header_descs_.emplace(name, description);
    if (stat_type == "counter") {
        counters_.emplace(name, 0);
        epoch_counters_.emplace(name, 0);
    } else if (stat_type == "double") {
        doubles_.emplace(name, 0.0);
    } else if (stat_type == "calculated") {
        calculated_.emplace(name, 0.0);
    }
}

void SimpleStats::InitVecStat(std::string name, std::string stat_type,
                              std::string description, std::string part_name,
                              int vec_len) {
    for (int i = 0; i < vec_len; i++) {
        std::string trailing = "." + std::to_string(i);
        std::string actual_name = name + trailing;
        std::string actual_desc = description + " " + part_name + trailing;
        header_descs_.emplace(actual_name, actual_desc);
    }
    if (stat_type == "vec_counter") {
        vec_counters_.emplace(name, std::vector<uint64_t>(vec_len, 0));
        epoch_vec_counters_.emplace(name, std::vector<uint64_t>(vec_len, 0));
    } else if (stat_type == "vec_double") {
        vec_doubles_.emplace(name, std::vector<double>(vec_len, 0));
    }
}

void SimpleStats::InitHistoStat(std::string name, std::string description,
                                int start_val, int end_val, int num_bins) {
    int bin_width = (end_val - start_val) / num_bins;
    bin_widths_.emplace(name, bin_width);
    histo_bounds_.emplace(name, std::make_pair(start_val, end_val));
    histo_counts_.emplace(name, std::unordered_map<int, uint64_t>());
    epoch_histo_counts_.emplace(name, std::unordered_map<int, uint64_t>());

    // initialize headers, descriptions
    std::vector<std::string> headers;
    auto header = fmt::format("{}[-{}]", name, start_val);
    headers.push_back(header);
    header_descs_.emplace(header, description);
    for (int i = 1; i < num_bins + 1; i++) {
        int bucket_start = start_val + (i - 1) * bin_width;
        int bucket_end = start_val + i * bin_width - 1;
        header = fmt::format("{}[{}-{}]", name, bucket_start, bucket_end);
        headers.push_back(header);
        header_descs_.emplace(header, description);
    }
    header = fmt::format("{}[{}-]", name, end_val);
    headers.push_back(header);
    header_descs_.emplace(header, description);

    histo_headers_.emplace(name, headers);

    // +2 for front and end
    histo_bins_.emplace(name, std::vector<uint64_t>(num_bins + 2, 0));
    epoch_histo_bins_.emplace(name, std::vector<uint64_t>(num_bins + 2, 0));
}

void SimpleStats::UpdateCounters() {
    for (const auto& it : epoch_counters_) {
        counters_[it.first] += it.second;
    }
    for (const auto& vec : epoch_vec_counters_) {
        for (size_t i = 0; i < vec.second.size(); i++) {
            vec_counters_[vec.first][i] += vec.second[i];
        }
    }
}

void SimpleStats::UpdateHistoBins() {
    for (auto& name_bins : epoch_histo_bins_) {
        const auto& name = name_bins.first;
        auto& bins = name_bins.second;
        std::fill(bins.begin(), bins.end(), 0);
        for (const auto it : epoch_histo_counts_[name]) {
            int value = it.first;
            uint64_t count = it.second;
            int bin_idx = 0;
            if (value < histo_bounds_[name].first) {
                bin_idx = 0;
            } else if (value > histo_bounds_[name].second) {
                bin_idx = bins.size() - 1;
            } else {
                bin_idx =
                    (value - histo_bounds_[name].first) / bin_widths_[name] + 1;
            }
            bins[bin_idx] += count;
        }
    }

    // update overall histogram counts based on epoch histo counts
    for (auto& name_counts : epoch_histo_counts_) {
        const auto& name = name_counts.first;
        auto& epoch_counts = name_counts.second;
        auto& final_counts = histo_counts_[name];
        for (const auto& val_cnt : epoch_counts) {
            if (final_counts.count(val_cnt.first) <= 0) {
                final_counts[val_cnt.first] = val_cnt.second;
            } else {
                final_counts[val_cnt.first] += val_cnt.second;
            }
        }
        auto& final_bins = histo_bins_[name];
        for (size_t i = 0; i < final_bins.size(); i++) {
            final_bins[i] += epoch_histo_bins_[name][i];
        }
    }
}

double SimpleStats::GetHistoAvg(const HistoCount& hist_counts) const {
    uint64_t accu_sum = 0;
    uint64_t count = 0;
    for (auto i = hist_counts.begin(); i != hist_counts.end(); i++) {
        accu_sum += i->first * i->second;
        count += i->second;
    }
    return count == 0
               ? 0.0
               : static_cast<double>(accu_sum) / static_cast<double>(count);
}

void SimpleStats::UpdatePrints(bool epoch) {
    j_data_["channel"] = channel_id_;

    std::unordered_map<std::string, uint64_t>& ref_counters =
        epoch ? epoch_counters_ : counters_;
    for (const auto& it : ref_counters) {
        print_pairs_.emplace_back(it.first, std::to_string(it.second));
        j_data_[it.first] = it.second;
    }
    j_data_["epoch_num"] = counters_["epoch_num"];

    VecStat& ref_vcounter = epoch ? epoch_vec_counters_ : vec_counters_;
    for (const auto& it : ref_vcounter) {
        Json j_list;
        for (size_t i = 0; i < it.second.size(); i++) {
            std::string name = it.first + "." + std::to_string(i);
            print_pairs_.emplace_back(name, std::to_string(it.second[i]));
            j_list[std::to_string(i)] = it.second[i];
        }
        j_data_[it.first] = j_list;
    }
    VecStat& ref_hbins = epoch ? epoch_histo_bins_ : histo_bins_;
    for (const auto& it : ref_hbins) {
        const auto& names = histo_headers_[it.first];
        for (size_t i = 0; i < it.second.size(); i++) {
            print_pairs_.emplace_back(names[i], std::to_string(it.second[i]));
            j_data_[names[i]] = it.second[i];
        }
    }

    // if we dump complete histogram data each epoch the output file will be
    // huge therefore we only put aggregated histo in each epoch but
    // complete data at the end
    if (!epoch) {
        for (const auto& name_hist : histo_counts_) {
            Json j_list;
            for (const auto& it : name_hist.second) {
                j_list[std::to_string(it.first)] = it.second;
            }
            j_data_[name_hist.first] = j_list;
        }
    }

    for (const auto& it : doubles_) {
        print_pairs_.emplace_back(it.first, fmt::format("{}", it.second));
        j_data_[it.first] = it.second;
    }

    for (const auto& it : vec_doubles_) {
        Json j_list;
        for (size_t i = 0; i < it.second.size(); i++) {
            std::string name = it.first + "." + std::to_string(i);
            print_pairs_.emplace_back(name, fmt::format("{}", it.second[i]));
            j_list[std::to_string(i)] = it.second[i];
        }
        j_data_[it.first] = j_list;
    }
    for (const auto& it : calculated_) {
        print_pairs_.emplace_back(it.first, fmt::format("{}", it.second));
        j_data_[it.first] = it.second;
    }
}

void SimpleStats::UpdateEpochStats() {
    // push counter values as is
    UpdateCounters();

    // update computed stats
    doubles_["act_energy"] =
        epoch_counters_["num_act_cmds"] * config_.act_energy_inc;
    doubles_["read_energy"] =
        epoch_counters_["num_read_cmds"] * config_.read_energy_inc;
    doubles_["write_energy"] =
        epoch_counters_["num_write_cmds"] * config_.write_energy_inc;
    doubles_["ref_energy"] =
        epoch_counters_["num_ref_cmds"] * config_.ref_energy_inc;
    doubles_["refb_energy"] =
        epoch_counters_["num_refb_cmds"] * config_.refb_energy_inc;

    // vector doubles, update first, then push
    double background_energy = 0.0;
    for (int i = 0; i < config_.ranks; i++) {
        double act_stb = epoch_vec_counters_["rank_active_cycles"][i] *
                         config_.act_stb_energy_inc;
        double pre_stb = epoch_vec_counters_["all_bank_idle_cycles"][i] *
                         config_.pre_stb_energy_inc;
        double sref_energy =
            epoch_vec_counters_["sref_cycles"][i] * config_.sref_energy_inc;
        vec_doubles_["act_stb_energy"][i] = act_stb;
        vec_doubles_["pre_stb_energy"][i] = pre_stb;
        vec_doubles_["sref_energy"][i] = sref_energy;
        background_energy += act_stb + pre_stb + sref_energy;
    }

    UpdateHistoBins();

    // calculated stats
    uint64_t total_reqs =
        epoch_counters_["num_reads_done"] + epoch_counters_["num_writes_done"];
    double total_time = epoch_counters_["num_cycles"] * config_.tCK;
    double avg_bw = total_reqs * config_.request_size_bytes / total_time;
    calculated_["average_bandwidth"] = avg_bw;

    double total_energy = doubles_["act_energy"] + doubles_["read_energy"] +
                          doubles_["write_energy"] + doubles_["ref_energy"] +
                          doubles_["refb_energy"] + background_energy;
    calculated_["total_energy"] = total_energy;
    calculated_["average_power"] = total_energy / epoch_counters_["num_cycles"];
    calculated_["average_read_latency"] =
        GetHistoAvg(epoch_histo_counts_.at("read_latency"));
    calculated_["average_interarrival"] =
        GetHistoAvg(epoch_histo_counts_.at("interarrival_latency"));

    UpdatePrints(true);
    for (auto& it : epoch_counters_) {
        it.second = 0;
    }
    for (auto& vec : epoch_vec_counters_) {
        std::fill(vec.second.begin(), vec.second.end(), 0);
    }
    for (auto& it : epoch_histo_counts_) {
        it.second.clear();
    }
    return;
}

void SimpleStats::UpdateFinalStats() {
    UpdateCounters();

    // update computed stats
    doubles_["act_energy"] = counters_["num_act_cmds"] * config_.act_energy_inc;
    doubles_["read_energy"] =
        counters_["num_read_cmds"] * config_.read_energy_inc;
    doubles_["write_energy"] =
        counters_["num_write_cmds"] * config_.write_energy_inc;
    doubles_["ref_energy"] = counters_["num_ref_cmds"] * config_.ref_energy_inc;
    doubles_["refb_energy"] =
        counters_["num_refb_cmds"] * config_.refb_energy_inc;

    // vector doubles, update first, then push
    double background_energy = 0.0;
    for (int i = 0; i < config_.ranks; i++) {
        double act_stb =
            vec_counters_["rank_active_cycles"][i] * config_.act_stb_energy_inc;
        double pre_stb = vec_counters_["all_bank_idle_cycles"][i] *
                         config_.pre_stb_energy_inc;
        double sref_energy =
            vec_counters_["sref_cycles"][i] * config_.sref_energy_inc;
        vec_doubles_["act_stb_energy"][i] = act_stb;
        vec_doubles_["pre_stb_energy"][i] = pre_stb;
        vec_doubles_["sref_energy"][i] = sref_energy;
        background_energy += act_stb + pre_stb + sref_energy;
    }

    // histograms
    UpdateHistoBins();

    // calculated stats
    uint64_t total_reqs =
        counters_["num_reads_done"] + counters_["num_writes_done"];
    double total_time = counters_["num_cycles"] * config_.tCK;
    double avg_bw = total_reqs * config_.request_size_bytes / total_time;
    calculated_["average_bandwidth"] = avg_bw;

    double total_energy = doubles_["act_energy"] + doubles_["read_energy"] +
                          doubles_["write_energy"] + doubles_["ref_energy"] +
                          doubles_["refb_energy"] + background_energy;
    calculated_["total_energy"] = total_energy;
    calculated_["average_power"] = total_energy / counters_["num_cycles"];
    // calculated_["average_read_latency"] = GetHistoAvg("read_latency");
    calculated_["average_read_latency"] =
        GetHistoAvg(histo_counts_.at("read_latency"));
    calculated_["average_interarrival"] =
        GetHistoAvg(histo_counts_.at("interarrival_latency"));

    UpdatePrints(false);
    return;
}

}  // namespace dramsim3