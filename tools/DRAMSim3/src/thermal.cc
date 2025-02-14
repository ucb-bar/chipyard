#include "thermal.h"

extern "C" double *steady_thermal_solver(double ***powerM, double W, double Lc,
                                         int numP, int dimX, int dimZ,
                                         double **Midx, int count,
                                         double Tamb_);
extern "C" double *transient_thermal_solver(double ***powerM, double W,
                                            double L, int numP, int dimX,
                                            int dimZ, double **Midx,
                                            int MidxSize, double *Cap,
                                            int CapSize, double time, int iter,
                                            double *T_trans, double Tamb_);
extern "C" double **calculate_Midx_array(double W, double Lc, int numP,
                                         int dimX, int dimZ, int *MidxSize,
                                         double Tamb_);
extern "C" double *calculate_Cap_array(double W, double Lc, int numP, int dimX,
                                       int dimZ, int *CapSize);
extern "C" double *initialize_Temperature(double W, double Lc, int numP,
                                          int dimX, int dimZ, double Tamb_);

namespace dramsim3 {

std::function<Address(const Address &addr)> GetPhyAddress;

ThermalCalculator::ThermalCalculator(const Config &config)
    : config_(config),
      time_iter0(10),
      sample_id(0),
      background_energy_(config_.channels,
                         std::vector<double>(config_.ranks, 0)),
      avg_logic_power_(0.0) {
    // Initialize dimX, dimY, numP
    // The dimension of the chip is determined such that the floorplan is
    // as square as possilbe. If a square floorplan cannot be reached,
    // x-dimension is larger
    if (config_.IsHMC()) {
        numP = config_.num_dies + 1;  // add logic layer for HMC
        bank_x = 1;
        bank_y = 2;

        double xd = bank_x * config_.bank_asr;
        double yd = bank_y * 1.0;
        vault_x = determineXY(xd, yd, config_.channels);
        vault_y = config_.channels / vault_x;

        dimX = vault_x * bank_x * config_.num_x_grids;
        dimY = vault_y * bank_y * config_.num_y_grids;

        num_case = 1;
    } else if (config_.IsHBM()) {
        numP = config_.num_dies + 1;  // add logic layer for HBM
        bank_x = 8;
        bank_y = 2;
        vault_x = 1;
        vault_y = 2;
        dimX = vault_x * bank_x * config_.num_x_grids;
        dimY = vault_y * bank_y * config_.num_y_grids;

        num_case = 1;
    } else {
        numP = 1;
        bank_x = determineXY(config_.bank_asr, 1.0, config_.banks);
        bank_y = config_.banks / bank_x;

        dimX = bank_x * config_.num_x_grids;
        dimY = bank_y * config_.num_y_grids;

        num_case = config_.ranks * config_.channels;
    }

    Tamb = config_.amb_temp + T0;

    std::cout << "bank aspect ratio = " << config_.bank_asr << std::endl;
    // std::cout << "#rows = " << config_.rows << "; #columns = " <<
    // config_.rows / config_.bank_asr << std::endl;
    std::cout << "num_x_grids = " << config_.num_x_grids
              << "; num_y_grids = " << config_.num_y_grids << std::endl;
    std::cout << "vault_x = " << vault_x << "; vault_y = " << vault_y
              << std::endl;
    std::cout << "bank_x = " << bank_x << "; bank_y = " << bank_y << std::endl;
    std::cout << "dimX = " << dimX << "; dimY = " << dimY << "; numP = " << numP
              << std::endl;
    std::cout << "number of devices is " << config_.devices_per_rank
              << std::endl;

    SetPhyAddressMapping();

    // Initialize the vectors
    accu_Pmap = std::vector<std::vector<double>>(
        num_case, std::vector<double>(numP * dimX * dimY, 0));
    cur_Pmap = std::vector<std::vector<double>>(
        num_case, std::vector<double>(numP * dimX * dimY, 0));
    T_size = (numP * 3 + 1) * (dimX + num_dummy) * (dimY + num_dummy);
    T_trans = new double *[num_case];
    T_final = new double *[num_case];
    for (int i = 0; i < num_case; i++) {
        T_trans[i] = new double[T_size];
    }

    InitialParameters();

    refresh_count = std::vector<std::vector<int>>(
        config_.channels * config_.ranks, std::vector<int>(config_.banks, 0));

    if (config_.output_level >= 0) {
        // Initialize the output file
        final_temperature_file_csv_.open(config_.output_prefix +
                                         "final_temp.csv");
        PrintCSVHeader_final(final_temperature_file_csv_);

        // print bank position
        bank_position_csv_.open(config_.output_prefix + "bank_pos.csv");
        PrintCSV_bank(bank_position_csv_);

        // a quick preview of max temperature for each layer of each epoch
        epoch_max_temp_file_csv_.open(config_.output_prefix +
                                      "epoch_max_temp.csv");
        epoch_max_temp_file_csv_ << "layer,max_temp,epoch_time" << std::endl;
    }

    // print header to csv files
    if (config_.output_level >= 2) {
        epoch_temperature_file_csv_.open(config_.output_prefix +
                                         "epoch_temp.csv");
        epoch_temperature_file_csv_
            << "rank_channel_index,x,y,z,power,temperature,epoch" << std::endl;
    }
}

ThermalCalculator::~ThermalCalculator() {}

void ThermalCalculator::SetPhyAddressMapping() {
    std::string mapping_string = config_.loc_mapping;
    if (mapping_string.empty()) {
        // if no location mapping specified, then do not map and use default
        // mapping...
        GetPhyAddress = [](const Address &addr) { return Address(addr); };
        return;
    }
    std::vector<std::string> bit_fields = StringSplit(mapping_string, ',');
    if (bit_fields.size() != 6) {
        std::cerr << "loc_mapping should have 6 fields!" << std::endl;
        std::exit(1);
    }
    std::vector<std::vector<int>> mapped_pos(bit_fields.size(),
                                             std::vector<int>());
    for (unsigned i = 0; i < bit_fields.size(); i++) {
        std::vector<std::string> bit_pos = StringSplit(bit_fields[i], '-');
        for (unsigned j = 0; j < bit_pos.size(); j++) {
            if (!bit_pos[j].empty()) {
                auto colon_pos = bit_pos[j].find(":");
                if (colon_pos ==
                    std::string::npos) {  // no "start:end" short cuts
                    int pos = std::stoi(bit_pos[j]);
                    mapped_pos[i].push_back(pos);
                } else {
                    // for string like start:end (both inclusive), push all
                    // numbers in between into mapped_pos
                    int start_pos = std::stoi(bit_pos[j].substr(0, colon_pos));
                    int end_pos = std::stoi(
                        bit_pos[j].substr(colon_pos + 1, std::string::npos));
                    if (start_pos > end_pos) {  // seriously there is no smart
                                                // way in c++ to do this?
                        for (int k = start_pos; k >= end_pos; k--) {
                            mapped_pos[i].push_back(k);
                        }
                    } else {
                        for (int k = start_pos; k <= end_pos; k++) {
                            mapped_pos[i].push_back(k);
                        }
                    }
                }
            }
        }
    }

#ifdef DEBUG_LOC_MAPPING
    std::cout << "final mapped pos:";
    for (unsigned i = 0; i < mapped_pos.size(); i++) {
        for (unsigned j = 0; j < mapped_pos[i].size(); j++) {
            std::cout << mapped_pos[i][j] << " ";
        }
        std::cout << std::endl;
    }
    std::cout << std::endl;
#endif  // DEBUG_LOC_MAPPING

    int column_offset = LogBase2(config_.BL);

    GetPhyAddress = [mapped_pos, column_offset](const Address &addr) {
        uint64_t new_hex = 0;
        // ch - ra - bg - ba - ro - co
        int origin_pos[] = {addr.channel, addr.rank, addr.bankgroup,
                            addr.bank,    addr.row,  addr.column};
        int new_pos[] = {0, 0, 0, 0, 0, 0};
        for (unsigned i = 0; i < mapped_pos.size(); i++) {
            int field_width = mapped_pos[i].size();
            for (int j = 0; j < field_width; j++) {
                uint64_t this_bit =
                    GetBitInPos(origin_pos[i], field_width - j - 1);
                uint64_t new_bit = (this_bit << mapped_pos[i][j]);
                new_hex |= new_bit;
#ifdef DEBUG_LOC_MAPPING
                std::cout << "mapping " << this_bit << " to "
                          << mapped_pos[i][j] << ", result:" << std::hex
                          << new_hex << std::dec << std::endl;
#endif  // DEBUG_LOC_MAPPING
            }
        }

        int pos = column_offset;
        for (int i = mapped_pos.size() - 1; i >= 0; i--) {
            new_pos[i] = ModuloWidth(new_hex, mapped_pos[i].size(), pos);
            pos += mapped_pos[i].size();
        }

#ifdef DEBUG_LOC_MAPPING
        std::cout << "new channel " << new_pos[0] << " vs old channel "
                  << addr.channel << std::endl;
        std::cout << "new rank " << new_pos[1] << " vs old rank " << addr.rank
                  << std::endl;
        std::cout << "new bg " << new_pos[2] << " vs old bg " << addr.bankgroup
                  << std::endl;
        std::cout << "new bank " << new_pos[3] << " vs old bank " << addr.bank_
                  << std::endl;
        std::cout << "new row " << new_pos[4] << " vs old row " << addr.row
                  << std::endl;
        std::cout << "new col " << new_pos[5] << " vs old col " << addr.column
                  << std::endl;
        std::cout << std::dec;
#endif

        return Address(new_pos[0], new_pos[1], new_pos[2], new_pos[3],
                       new_pos[4], new_pos[5]);
    };
}

std::pair<int, int> ThermalCalculator::MapToVault(int channel_id) {
    int vault_id_x = 0;
    int vault_id_y = 0;
    if (config_.IsHMC()) {
        int vault_factor = vault_y;
        if (config_.bank_order == 0) {
            vault_factor = vault_x;
        }
        vault_id_x = channel_id / vault_factor;
        vault_id_y = channel_id % vault_factor;
        if (config_.bank_order == 0) {
            std::swap(vault_id_x, vault_id_y);
        }
    } else if (config_.IsHBM()) {
        vault_id_y = channel_id % 2;
        vault_id_x = 0;
    }
    return std::make_pair(vault_id_x, vault_id_y);
}

// bank_id is the bank_id within a group, capped at config_.bankg_per_group
std::pair<int, int> ThermalCalculator::MapToBank(int bankgroup_id,
                                                 int bank_id) {
    int bank_id_x, bank_id_y;
    // we're gonna assume bankgroup_id and bank_id are valid input
    int abs_bank_id = bankgroup_id * config_.banks_per_group + bank_id;
    int bank_factor = config_.bank_order ? bank_y : bank_x;

    if (config_.IsHMC()) {
        int num_bank_per_layer = config_.banks / config_.num_dies;
        int bank_same_layer = abs_bank_id % num_bank_per_layer;
        bank_id_x = bank_same_layer / bank_factor;
        bank_id_y = bank_same_layer % bank_factor;
        if (config_.bank_order == 0) {
            std::swap(bank_id_x, bank_id_y);
        }
    } else if (config_.IsHBM()) {
        bank_id_x = bankgroup_id * 2 + bank_id / 2;
        bank_id_y = bank_id % 2;
    } else {
        if (config_.bankgroups > 1) {
            // banks in a group always form like a square
            // bank_groups are arranged in a line -- either in x or y direction
            // default calculate bank_order y, reverse it later if not
            bank_id_x = bank_id / 2;
            bank_id_y = bank_id % 2;
            if (config_.bank_order == 0) {
                std::swap(bank_id_x, bank_id_y);
            }
            if (bank_x <= bank_y)
                bank_id_y += bankgroup_id * 2;
            else
                bank_id_x += bankgroup_id * 2;
        } else {
            bank_id_x = abs_bank_id / bank_factor;
            bank_id_y = abs_bank_id % bank_factor;
            if (config_.bank_order == 0) {
                std::swap(bank_id_x, bank_id_y);
            }
        }
    }
    return std::make_pair(bank_id_x, bank_id_y);
}

int ThermalCalculator::MapToZ(int channel_id, int bank_id) {
    int z;
    if (config_.IsHMC()) {
        int num_bank_per_layer = config_.banks / config_.num_dies;
        if (config_.bank_layer_order == 0)
            z = bank_id / num_bank_per_layer;
        else
            z = numP - bank_id / num_bank_per_layer - 2;
    } else if (config_.IsHBM()) {
        z = channel_id / 2;
    } else {
        z = 0;
    }
    return z;
}

std::pair<std::vector<int>, std::vector<int>> ThermalCalculator::MapToXY(
    const Command &cmd, int vault_id_x, int vault_id_y, int bank_id_x,
    int bank_id_y) {
    std::vector<int> x;
    std::vector<int> y;

    int row_id = cmd.Row();
    int col_tile_id = row_id / config_.tile_row_num;
    int grid_id_x = row_id / config_.mat_dim_x / config_.row_tile;

    Address temp_addr = Address(cmd.addr);
    for (int i = 0; i < config_.BL; i++) {
        Address phy_loc = GetPhyAddress(temp_addr);
        int col_id = phy_loc.column * config_.device_width;
        int bank_x_offset = bank_x * config_.num_x_grids;
        int bank_y_offset = bank_y * config_.num_y_grids;
        for (int j = 0; j < config_.device_width; j++) {
            int grid_id_y =
                col_id / config_.mat_dim_y +
                col_tile_id * (config_.num_y_grids / config_.row_tile);
            int temp_x = vault_id_x * bank_x_offset +
                         bank_id_x * config_.num_x_grids + grid_id_x;
            x.push_back(temp_x);
            int temp_y = vault_id_y * bank_y_offset +
                         bank_id_y * config_.num_y_grids + grid_id_y;
            y.push_back(temp_y);
            col_id++;
        }
        temp_addr.column++;
    }
    return std::make_pair(x, y);
}

void ThermalCalculator::LocationMappingANDaddEnergy(const int channel,
                                                    const Command &cmd,
                                                    int bank0, int row0,
                                                    int caseID_,
                                                    double add_energy) {
    // get vault x y first
    int vault_id_x, vault_id_y;
    std::tie(vault_id_x, vault_id_y) = MapToVault(channel);

    // get bank id x y
    int bank_id_x, bank_id_y;
    std::tie(bank_id_x, bank_id_y) = MapToBank(cmd.Bankgroup(), cmd.Bank());

    // calculate x y z
    auto xy = MapToXY(cmd, vault_id_x, vault_id_y, bank_id_x, bank_id_y);
    auto &x = xy.first;
    auto &y = xy.second;
    int z = MapToZ(channel, cmd.Bank());

    int z_offset = z * dimX * dimY;
    double energy = add_energy / config_.device_width;
    // add energy to engergy map
    // iterate x y (they have same size)
    for (size_t i = 0; i < x.size(); i++) {
        int y_offset = y[i] * dimX;
        int idx = z_offset + y_offset + x[i];
        accu_Pmap[caseID_][idx] += energy;
        cur_Pmap[caseID_][idx] += energy;
    }
}

void ThermalCalculator::LocationMappingANDaddEnergy_RF(const int channel,
                                                       const Command &cmd,
                                                       int bank0, int row0,
                                                       int caseID_,
                                                       double add_energy) {
    // the caller will provide bank and row that needs to be refreshed
    // so we get a new address and modify it to obtain the actual mapping
    // bank0 passed here is the absolute bank index within a rank, need to
    // reverse it
    int bankgroup_id = bank0 / config_.banks_per_group;
    int bank_id = bank0 % config_.banks_per_group;
    Address new_addr = Address(cmd.addr);
    new_addr.row = row0;
    new_addr.bankgroup = bankgroup_id;
    new_addr.bank = bank_id;

    int vault_id_x, vault_id_y;
    std::tie(vault_id_x, vault_id_y) = MapToVault(channel);

    // get bank id x y
    int bank_id_x, bank_id_y;
    std::tie(bank_id_x, bank_id_y) = MapToBank(bankgroup_id, bank_id);

    int z = MapToZ(channel, bank_id);

    Address phy_addr = GetPhyAddress(new_addr);  // actual row after mapping
    // calculate x y z
    int row_id = phy_addr.row;
    int col_id = 0;  // refresh all units
    int col_tile_id = row_id / config_.tile_row_num;
    int grid_id_x = row_id / config_.mat_dim_x / config_.row_tile;
    int grid_id_y = col_id / config_.mat_dim_y +
                    col_tile_id * (config_.num_y_grids / config_.row_tile);
    int x = vault_id_x * (bank_x * config_.num_x_grids) +
            bank_id_x * config_.num_x_grids + grid_id_x;
    int y = vault_id_y * (bank_y * config_.num_y_grids) +
            bank_id_y * config_.num_y_grids + grid_id_y;

    int z_offset = z * (dimX * dimY);
    for (int i = 0; i < config_.num_y_grids; i++) {
        int y_offset = y * dimX;
        int idx = z_offset + y_offset + x;
        accu_Pmap[caseID_][idx] += add_energy;
        cur_Pmap[caseID_][idx] += add_energy;
        y++;
    }
}

void ThermalCalculator::UpdatePowerMaps(double add_energy, bool trans,
                                        uint64_t clk) {
    auto &p_map = trans ? cur_Pmap : accu_Pmap;
    double period = trans ? static_cast<double>(config_.epoch_period)
                          : static_cast<double>(clk);
    for (int j = 0; j < num_case; j++) {
        // universally update power map
        for (int i = 0; i < dimX * dimY * (numP - 1); i++) {
            p_map[j][i] += add_energy;
        }
        // update logic power map
        // UpdateLogicPower();
        for (int i = dimX * dimY * (numP - 1); i < dimX * dimY * numP; i++) {
            p_map[j][i] += avg_logic_power_ / dimX / dimY * period;
        }
    }
}

void ThermalCalculator::UpdateCMDPower(const int channel, const Command &cmd,
                                       const uint64_t clk) {
    int rank = cmd.Rank();
    // int channel = cmd.Channel();
    int case_id;
    double device_scale;
    if (config_.IsHMC() || config_.IsHBM()) {
        device_scale = 1;
        case_id = 0;
    } else {
        device_scale = (double)config_.devices_per_rank;
        case_id = channel * config_.ranks + rank;
    }

    double energy = 0.0;
    if (cmd.cmd_type == CommandType::REFRESH) {
        int rank_idx = channel * config_.ranks + rank;
        for (int ib = 0; ib < config_.banks; ib++) {
            int row_s = refresh_count[rank_idx][ib] * config_.num_row_refresh;
            refresh_count[rank_idx][ib]++;
            if (refresh_count[rank_idx][ib] * config_.num_row_refresh ==
                config_.rows)
                refresh_count[rank_idx][ib] = 0;
            energy = config_.ref_energy_inc / config_.num_row_refresh /
                     config_.banks / config_.num_y_grids;
            for (int ir = row_s; ir < row_s + config_.num_row_refresh; ir++) {
                LocationMappingANDaddEnergy_RF(channel, cmd, ib, ir, case_id,
                                               energy / 1000.0 / device_scale);
            }
        }
    } else if (cmd.cmd_type == CommandType::REFRESH_BANK) {
        int ib = cmd.Bank();
        int rank_idx = channel * config_.ranks + rank;
        int row_s = refresh_count[rank_idx][ib] * config_.num_row_refresh;
        refresh_count[rank_idx][ib]++;
        if (refresh_count[rank_idx][ib] * config_.num_row_refresh ==
            config_.rows)
            refresh_count[rank_idx][ib] = 0;
        energy = config_.refb_energy_inc / config_.num_row_refresh /
                 config_.num_y_grids;
        for (int ir = row_s; ir < row_s + config_.num_row_refresh; ir++) {
            LocationMappingANDaddEnergy_RF(channel, cmd, ib, ir, case_id,
                                           energy / 1000.0 / device_scale);
        }
    } else {
        switch (cmd.cmd_type) {
            case CommandType::ACTIVATE:
                energy = config_.act_energy_inc;
                break;
            case CommandType::READ:
            case CommandType::READ_PRECHARGE:
                energy = config_.read_energy_inc;
                break;
            case CommandType::WRITE:
            case CommandType::WRITE_PRECHARGE:
                energy = config_.write_energy_inc;
                break;
            default:
                energy = 0.0;
                break;
        }
        if (energy > 0) {
            energy /= config_.BL;
            LocationMappingANDaddEnergy(channel, cmd, -1, -1, case_id,
                                        energy / 1000.0 / device_scale);
        }
    }
    return;
}

void ThermalCalculator::UpdateBackgroundEnergy(const int channel,
                                               const int rank,
                                               const double energy) {
    background_energy_[channel][rank] = energy;
}

void ThermalCalculator::UpdateEpoch(uint64_t clk) {
    if (config_.IsHBM() || config_.IsHMC()) {
        double bg_energy = 0;
        for (const auto &vec_rank_energy : background_energy_) {
            for (const auto &rank_energy : vec_rank_energy) {
                bg_energy += rank_energy;
            }
        }
        bg_energy = bg_energy / (dimX * dimY * (numP - 1));
        UpdatePowerMaps(bg_energy / 1000, true, config_.epoch_period);
    } else {
        double num_devices = static_cast<double>(config_.devices_per_rank);
        for (int i = 0; i < config_.channels; i++) {
            for (int j = 0; j < config_.ranks; j++) {
                int case_id = i * config_.ranks + j;
                double bg_energy =
                    background_energy_[i][j] / (dimX * dimY * numP);
                for (int k = 0; k < dimX * dimY * numP; i++) {
                    cur_Pmap[case_id][k] += bg_energy / 1000 / num_devices;
                }
            }
        }
    }
    return;
}

void ThermalCalculator::SetLogicPower(double logic_power) {
    avg_logic_power_ = logic_power;
}

void ThermalCalculator::PrintTransPT(uint64_t clk) {
    UpdateEpoch(clk);
    double ms = clk * config_.tCK * 1e-6;
    for (int ir = 0; ir < num_case; ir++) {
        CalcTransT(ir);
        double maxT = 0;
        for (int layer = 0; layer < numP; layer++) {
            double maxT_layer = GetMaxTofCaseLayer(T_trans, ir, layer);
            epoch_max_temp_file_csv_ << layer << "," << maxT_layer << "," << ms
                                     << std::endl;
            // << layer << "," << stats_.average_power.epoch_value << ","
            // << maxT_layer << "," << bw_usage_ << "," << ms << std::endl;
            std::cout << "MaxT of case " << ir << " in layer " << layer
                      << " is " << maxT_layer << " [C]\n";
            maxT = maxT > maxT_layer ? maxT : maxT_layer;
        }
        std::cout << "MaxT of case " << ir << " is " << maxT << " [C] at " << ms
                  << " ms\n";
        // only outputs full file when output level >= 2
        if (config_.output_level >= 2) {
            PrintCSV_trans(epoch_temperature_file_csv_, cur_Pmap, T_trans, ir,
                           config_.epoch_period);
        }
    }
    for (size_t i = 0; i < cur_Pmap.size(); i++) {
        std::fill_n(cur_Pmap[i].begin(), numP * dimX * dimY, 0.0);
    }
    sample_id += 1;
}

void ThermalCalculator::PrintFinalPT(uint64_t clk) {
    if (config_.IsHBM() || config_.IsHMC()) {
        double bg_energy = 0;
        for (const auto &vec_rank_energy : background_energy_) {
            for (const auto &rank_energy : vec_rank_energy) {
                bg_energy += rank_energy;
            }
        }
        bg_energy /= (dimX * dimY * (numP - 1));
        UpdatePowerMaps(bg_energy / 1000, false, clk);
    } else {
        double num_devices = static_cast<double>(config_.devices_per_rank);
        for (int i = 0; i < config_.channels; i++) {
            for (int j = 0; j < config_.ranks; j++) {
                int case_id = i * config_.ranks + j;
                double bg_energy =
                    background_energy_[i][j] / (dimX * dimY * numP);
                for (int k = 0; k < dimX * dimY * numP; i++) {
                    accu_Pmap[case_id][i] += bg_energy / 1000 / num_devices;
                }
            }
        }
    }
    // calculate the final temperature for each case
    for (int ir = 0; ir < num_case; ir++) {
        CalcFinalT(ir, clk);
        double maxT = GetMaxTofCase(T_final, ir);
        std::cout << "MaxT of case " << ir << " is " << maxT << " [C]\n";
        // print to file
        PrintCSV_final(final_temperature_file_csv_, accu_Pmap, T_final, ir,
                       clk);
    }

    // close all the csv files
    final_temperature_file_csv_.close();
    epoch_max_temp_file_csv_.close();
    if (config_.output_level >= 2) {
        epoch_temperature_file_csv_.close();
    }
}

void ThermalCalculator::CalcTransT(int case_id) {
    double time = config_.epoch_period * config_.tCK * 1e-9;
    double ***powerM = InitPowerM(case_id, 0);
    double totP = GetTotalPower(powerM);
    std::cout << "total trans power is " << totP * 1000 << " [mW]" << std::endl;
    T_trans[case_id] = transient_thermal_solver(
        powerM, config_.chip_dim_x, config_.chip_dim_y, numP, dimX + num_dummy,
        dimY + num_dummy, Midx, MidxSize, Cap, CapSize, time, time_iter,
        T_trans[case_id], Tamb);
}

void ThermalCalculator::CalcFinalT(int case_id, uint64_t clk) {
    double ***powerM = InitPowerM(case_id, clk);
    double totP = GetTotalPower(powerM);
    std::cout << "total final power is " << totP * 1000 << " [mW]" << std::endl;
    double *T = steady_thermal_solver(
        powerM, config_.chip_dim_x, config_.chip_dim_y, numP, dimX + num_dummy,
        dimY + num_dummy, Midx, MidxSize, Tamb);
    T_final[case_id] = T;
}

double ***ThermalCalculator::InitPowerM(int case_id, uint64_t clk) {
    double ***powerM;
    // assert in powerM
    powerM = new double **[dimX + num_dummy];
    for (int i = 0; i < dimX + num_dummy; i++) {
        powerM[i] = new double *[dimY + num_dummy];
        for (int j = 0; j < dimY + num_dummy; j++) {
            powerM[i][j] = new double[numP];
        }
    }
    // initialize powerM
    for (int i = 0; i < dimX + num_dummy; i++)
        for (int j = 0; j < dimY + num_dummy; j++)
            std::fill_n(powerM[i][j], numP, 0.0);

    // when clk is 0 then it's trans otherwise it's final
    double div = clk == 0 ? (double)config_.epoch_period : (double)clk;
    auto &power_map = clk == 0 ? cur_Pmap : accu_Pmap;
    // fill in powerM
    for (int i = 0; i < dimX; i++) {
        for (int j = 0; j < dimY; j++) {
            for (int l = 0; l < numP; l++) {
                powerM[i + num_dummy / 2][j + num_dummy / 2][l] =
                    power_map[case_id][l * (dimX * dimY) + j * dimX + i] / div;
            }
        }
    }
    return powerM;
}

double ThermalCalculator::GetTotalPower(double ***powerM) {
    double total_power = 0.0;
    for (int i = 0; i < dimX; i++) {
        for (int j = 0; j < dimY; j++) {
            for (int l = 0; l < numP; l++) {
                total_power += powerM[i + num_dummy / 2][j + num_dummy / 2][l];
            }
        }
    }
    return total_power;
}

void ThermalCalculator::InitialParameters() {
    layerP = std::vector<int>(numP, 0);
    for (int l = 0; l < numP; l++) layerP[l] = l * 3;
    Midx = calculate_Midx_array(config_.chip_dim_x, config_.chip_dim_y, numP,
                                dimX + num_dummy, dimY + num_dummy, &MidxSize,
                                Tamb);
    Cap = calculate_Cap_array(config_.chip_dim_x, config_.chip_dim_y, numP,
                              dimX + num_dummy, dimY + num_dummy, &CapSize);
    calculate_time_step();

    for (int ir = 0; ir < num_case; ir++) {
        double *T =
            initialize_Temperature(config_.chip_dim_x, config_.chip_dim_y, numP,
                                   dimX + num_dummy, dimY + num_dummy, Tamb);
        for (int i = 0; i < T_size; i++) T_trans[ir][i] = T[i];
        free(T);
    }
}

int ThermalCalculator::square_array(int total_grids_) {
    int x, y, x_re = 1;
    for (x = 1; x <= sqrt(total_grids_); x++) {
        y = total_grids_ / x;
        if (x * y == total_grids_) x_re = x;
    }
    return x_re;
}

int ThermalCalculator::determineXY(double xd, double yd, int total_grids_) {
    int x, y, x_re = 1;
    double asr, asr_re = 1000;
    for (y = 1; y <= total_grids_; y++) {
        x = total_grids_ / y;
        if (x * y == total_grids_) {
            // total_grids_ can be factored by x and y
            asr = (x * xd >= y * yd) ? (x * xd / y / yd) : (y * yd / x / xd);
            if (asr < asr_re) {
                std::cout << "asr = " << asr << "; x = " << x << "; y = " << y
                          << "; xd = " << xd << std::endl;
                x_re = total_grids_ / y;
                asr_re = asr;
            }
        }
    }
    return x_re;
}

void ThermalCalculator::calculate_time_step() {
    double dt = 100.0;
    int layer_dim = (dimX + num_dummy) * (dimY + num_dummy);

    for (int j = 0; j < MidxSize; j++) {
        int idx0 = (int)(Midx[j][0] + 0.01);
        int idx1 = (int)(Midx[j][1] + 0.01);
        int idxC = idx0 / layer_dim;

        if (idx0 == idx1) {
            double g = Midx[j][2];
            double c = Cap[idxC];
            if (c / g < dt) dt = c / g;
        }
    }

    std::cout << "maximum dt is " << dt << std::endl;

    // calculate time_iter
    double power_epoch_time = config_.epoch_period * config_.tCK * 1e-9;  // [s]
    std::cout << "power_epoch_time = " << power_epoch_time << std::endl;
    time_iter = time_iter0;
    while (power_epoch_time / time_iter >= dt) time_iter++;
    // time_iter += 10;
    std::cout << "time_iter = " << time_iter << std::endl;
}

double ThermalCalculator::GetMaxTofCase(double **temp_map, int case_id) {
    double maxT = 0;
    for (int i = 0; i < T_size; i++) {
        if (temp_map[case_id][i] > maxT) {
            maxT = temp_map[case_id][i];
        }
    }
    return maxT;
}

double ThermalCalculator::GetMaxTofCaseLayer(double **temp_map, int case_id,
                                             int layer) {
    double maxT = 0;
    int layer_pos_offset =
        (layerP[layer] + 1) * ((dimX + num_dummy) * (dimY + num_dummy));
    for (int j = num_dummy / 2; j < dimY + num_dummy / 2; j++) {
        for (int i = num_dummy / 2; i < dimX + num_dummy / 2; i++) {
            double t = temp_map[case_id]
                               [layer_pos_offset + j * (dimX + num_dummy) + i] -
                       T0;
            maxT = maxT > t ? maxT : t;
        }
    }
    return maxT;
}

void ThermalCalculator::PrintCSV_trans(std::ofstream &csvfile,
                                       std::vector<std::vector<double>> P_,
                                       double **T_, int id, uint64_t scale) {
    for (int l = 0; l < numP; l++) {
        for (int j = num_dummy / 2; j < dimY + num_dummy / 2; j++) {
            for (int i = num_dummy / 2; i < dimX + num_dummy / 2; i++) {
                double pw =
                    P_[id][l * ((dimX) * (dimY)) +
                           (j - num_dummy / 2) * (dimX) + (i - num_dummy / 2)] /
                    (double)scale;
                double tm = T_[id][(layerP[l] + 1) * ((dimX + num_dummy) *
                                                      (dimY + num_dummy)) +
                                   j * (dimX + num_dummy) + i] -
                            T0;
                csvfile << id << "," << i - num_dummy / 2 << ","
                        << j - num_dummy / 2 << "," << l << "," << pw << ","
                        << tm << "," << sample_id << std::endl;
            }
        }
    }
}

void ThermalCalculator::PrintCSV_final(std::ofstream &csvfile,
                                       std::vector<std::vector<double>> P_,
                                       double **T_, int id, uint64_t scale) {
    for (int l = 0; l < numP; l++) {
        for (int j = num_dummy / 2; j < dimY + num_dummy / 2; j++) {
            for (int i = num_dummy / 2; i < dimX + num_dummy / 2; i++) {
                double pw =
                    P_[id][l * (dimX * dimY) + (j - num_dummy / 2) * dimX +
                           (i - num_dummy / 2)] /
                    (double)scale;
                double tm = T_[id][(layerP[l] + 1) * ((dimX + num_dummy) *
                                                      (dimY + num_dummy)) +
                                   j * (dimX + num_dummy) + i];
                csvfile << id << "," << i - num_dummy / 2 << ","
                        << j - num_dummy / 2 << "," << l << "," << pw << ","
                        << tm << std::endl;
            }
        }
    }
}

void ThermalCalculator::PrintCSV_bank(std::ofstream &csvfile) {
    // header
    csvfile << "vault_id,bank_id,start_x,end_x,start_y,end_y,z" << std::endl;

    for (int vault_id = 0; vault_id < config_.channels; vault_id++) {
        int vault_id_x, vault_id_y;
        std::tie(vault_id_x, vault_id_y) = MapToVault(vault_id);
        for (int bg = 0; bg < config_.bankgroups; bg++) {
            for (int bank = 0; bank < config_.banks_per_group; bank++) {
                int abs_bank_id = bg * config_.banks_per_group + bank;
                int z = MapToZ(vault_id, abs_bank_id);
                int bank_id_x, bank_id_y;
                std::tie(bank_id_x, bank_id_y) = MapToBank(bg, bank);

                int bank_offset = bank_x * config_.num_x_grids;
                int start_x =
                    vault_id_x * bank_offset + bank_id_x * config_.num_x_grids;
                int end_x = vault_id_x * bank_offset +
                            (bank_id_x + 1) * config_.num_x_grids - 1;

                bank_offset = bank_y * config_.num_y_grids;
                int start_y =
                    vault_id_y * bank_offset + bank_id_y * config_.num_y_grids;
                int end_y = vault_id_y * bank_offset +
                            (bank_id_y + 1) * config_.num_y_grids - 1;
                csvfile << vault_id << "," << abs_bank_id << "," << start_x
                        << "," << end_x << "," << start_y << "," << end_y << ","
                        << z << std::endl;
            }
        }
    }
}

void ThermalCalculator::PrintCSVHeader_final(std::ofstream &csvfile) {
    csvfile << "rank_channel_index,x,y,z,power,temperature" << std::endl;
}

}  // namespace dramsim3
