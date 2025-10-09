// See LICENSE for license details

#ifndef __TACITBRIDGE_H
#define __TACITBRIDGE_H

#include "bridges/serial_data.h"
#include "core/bridge_driver.h"

#include <cstdint>
#include <memory>
#include <optional>
#include <signal.h>
#include <string>
#include <vector>

/**
 * Structure carrying the addresses of all fixed MMIO ports.
 *
 * This structure is instantiated when all bridges are populated based on
 * the target configuration.
 */
struct TACITBRIDGEMODULE_struct {
  uint64_t out_bits;
  uint64_t out_valid;
  uint64_t out_ready;
};

class tacit_handler {
public:
  virtual ~tacit_handler() = default;
  virtual void put(uint8_t data) = 0;
};

class tacit_t final : public bridge_driver_t {
public:
  /// The identifier for the bridge type used for casts.
  static char KIND;

  /// Creates a bridge which interacts with standard streams or PTY.
  tacit_t(simif_t &simif,
         const TACITBRIDGEMODULE_struct &mmio_addrs,
         int tacitno,
         const std::vector<std::string> &args);

  ~tacit_t() override;

  void tick() override;

private:
  const TACITBRIDGEMODULE_struct mmio_addrs;
  std::unique_ptr<tacit_handler> handler;

  serial_data_t<uint8_t> data;

  void recv();
  void send();
};

#endif // __TACITBRIDGE_H
