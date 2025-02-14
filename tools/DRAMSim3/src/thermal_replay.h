#ifndef __THERMAL_REPLAY_H
#define __THERMAL_REPLAY_H

#include <fstream>
#include <string>
#include <vector>

#include "common.h"
#include "configuration.h"
#include "simple_stats.h"
#include "thermal.h"

namespace dramsim3 {

class ThermalReplay {
   public:
    ThermalReplay(std::string trace_name, std::string config_file,
                  std::string output_dir, uint64_t repeat);
    ~ThermalReplay();
    void Run();

   private:
    std::vector<std::pair<uint64_t, Command>> timed_commands_;
    Config config_;
    ThermalCalculator thermal_calc_;
    uint64_t repeat_;
    uint64_t last_clk_;
    std::vector<SimpleStats> channel_stats_;
    std::vector<std::vector<std::vector<std::vector<bool>>>> bank_active_;
    void ParseLine(std::string line, uint64_t &clk, Command &cmd);
    void ProcessCMD(Command &cmd, uint64_t clk);
    bool IsRankActive(int channel, int rank);
};

}  // namespace dramsim3

#endif
