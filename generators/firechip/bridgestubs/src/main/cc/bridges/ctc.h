// See LICENSE for license details

#ifndef __CTC_H
#define __CTC_H

#include "bridges/serial_data.h"
#include "core/bridge_driver.h"
#include "core/stream_engine.h"

#include <cstdint>
#include <memory>
#include <optional>
#include <signal.h>
#include <string>
#include <vector>

// Placeholder MMIO register
struct CTCBRIDGEMODULE_struct {
  uint64_t done;
};

class ctc_t final : public streaming_bridge_driver_t {
public:
  static char KIND;
  ctc_t(simif_t &simif,
        StreamEngine &stream,
        const CTCBRIDGEMODULE_struct &mmio_addrs,
        int chipno,
        const std::vector<std::string> &args,
        int stream_to_cpu_idx,
        int stream_to_cpu_depth,
        int stream_from_cpu_idx,
        int stream_from_cpu_depth
      ); 
  ~ctc_t() override;
                  
  void init() override;
  void tick() override;
  void finish() override;

private:
  const CTCBRIDGEMODULE_struct mmio_addrs;
  std::string fifo0_path;
  std::string fifo1_path;
  int fifo0_fd;
  int fifo1_fd;
  int chip_id;
  int chip1_id;
  char *buf;

  int LINKLATENCY;

  const int stream_to_cpu_idx;
  const int stream_from_cpu_idx;
};

#endif // __CTC_H
