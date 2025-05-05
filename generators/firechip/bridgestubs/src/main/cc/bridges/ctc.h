// See LICENSE for license details

#ifndef __CTC_H
#define __CTC_H

#include "bridges/serial_data.h"
#include "core/bridge_driver.h"

#include <cstdint>
#include <memory>
#include <optional>
#include <signal.h>
#include <string>
#include <vector>

// Using TSI to make my life easier
// TODO: FIXME
struct CTCBRIDGEMODULE_struct {
  uint64_t client_in_bits;
  uint64_t client_in_valid;
  uint64_t client_in_ready;
  uint64_t client_out_bits;
  uint64_t client_out_valid;
  uint64_t client_out_ready;
  uint64_t manager_in_bits;
  uint64_t manager_in_valid;
  uint64_t manager_in_ready;
  uint64_t manager_out_bits;
  uint64_t manager_out_valid;
  uint64_t manager_out_ready;
};

class ctc_t final : public bridge_driver_t {
public:
  /// The identifier for the bridge type used for casts.
  static char KIND;
  ctc_t(simif_t &simif,
                    const CTCBRIDGEMODULE_struct &mmio_addrs,
                    int chipno, // YOU
                    const std::vector<std::string> &args
                  ); 
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
};

#endif // __CTC_H
