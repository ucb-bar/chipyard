#ifndef __THERMAL_H
#define __THERMAL_H

#include <time.h>
#include <cmath>
#include <fstream>
#include <functional>
#include <iostream>
#include <vector>
#include "bankstate.h"
#include "common.h"
#include "configuration.h"
#include "thermal_config.h"

namespace dramsim3 {

extern std::function<Address(const Address &addr)> GetPhyAddress;

class ThermalCalculator {
   public:
    ThermalCalculator(const Config &config);
    ~ThermalCalculator();
    void UpdateCMDPower(const int channel, const Command &cmd,
                        const uint64_t clk);
    void UpdateBackgroundEnergy(const int channel, const int rank,
                                const double energy);
    // assuming evenly distributed logic layer power
    void UpdateEpoch(const uint64_t clk);
    void SetLogicPower(double logic_power);
    void PrintTransPT(uint64_t clk);
    void PrintFinalPT(uint64_t clk);
    void UpdateLogicPower(double logic_power);

   private:
    // Initialization
    double ***InitPowerM(int case_id, uint64_t clk);
    void InitialParameters();

    // location mapping functions
    void SetPhyAddressMapping();
    std::pair<int, int> MapToVault(int channel_id);
    std::pair<int, int> MapToBank(int bankgroup_id, int bank_id);
    int MapToZ(int channel_id, int bank_id);
    std::pair<std::vector<int>, std::vector<int>> MapToXY(const Command &cmd,
                                                          int vault_id_x,
                                                          int vault_id_y,
                                                          int bank_id_x,
                                                          int bank_id_y);
    void LocationMappingANDaddEnergy_RF(const int channel, const Command &cmd,
                                        int bank0, int row0, int caseID_,
                                        double add_energy);
    void LocationMappingANDaddEnergy(const int channel, const Command &cmd,
                                     int bank0, int row0, int caseID_,
                                     double add_energy);
    void UpdatePowerMaps(double add_energy, bool trans, uint64_t clk);

    // calculations
    void CalcTransT(int case_id);
    void CalcFinalT(int case_id, uint64_t clk);
    double GetTotalPower(double ***powerM);
    int square_array(int total_grids_);
    int determineXY(double xd, double yd, int total_grids_);
    double GetMaxTofCase(double **temp_map, int case_id);
    double GetMaxTofCaseLayer(double **temp_map, int case_id, int layer);
    void calculate_time_step();

    // print to csv-files
    void PrintCSV_trans(std::ofstream &csvfile,
                        std::vector<std::vector<double>> P_, double **T_,
                        int id, uint64_t scale);
    void PrintCSV_final(std::ofstream &csvfile,
                        std::vector<std::vector<double>> P_, double **T_,
                        int id, uint64_t scale);
    void PrintCSVHeader_final(std::ofstream &csvfile);
    void PrintCSV_bank(std::ofstream &csvfile);


    const Config &config_;

    int time_iter0, time_iter;
    double Tamb;  // The ambient temperature in Kelvin
    const int num_dummy = 2;  // dummy cells around the calculatd die

    int dimX, dimY, numP;   // Dimension of the memory
    double **Midx;          // Midx storing thermal conductance
    double *Cap;            // Cap storing the thermal capacitance
    int MidxSize, CapSize;  // first dimension size of Midx and Cap
    int T_size;
    double **T_trans, **T_final;

    int sample_id;  // index of the sampling power

    std::vector<std::vector<double>> accu_Pmap;  // accumulative power map
    std::vector<std::vector<double>> cur_Pmap;   // current power map

    std::vector<std::vector<int>> refresh_count;

    // other intermediate parameters
    // not need to be defined here but it will be easy to use if it is defined
    int vault_x, vault_y, bank_x, bank_y;
    int num_case;  // number of different cases where the thermal simulation is
                   // performed
    std::vector<int> layerP;

    // Output files
    std::ofstream epoch_max_temp_file_csv_;
    std::ofstream epoch_temperature_file_csv_;
    std::ofstream final_temperature_file_csv_;
    std::ofstream bank_position_csv_;

    std::vector<std::vector<double>> background_energy_;
    double avg_logic_power_;
};
}  // namespace dramsim3

#endif
