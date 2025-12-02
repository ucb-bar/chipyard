// See LICENSE for license details

#ifndef __TACITBRIDGE_H
#define __TACITBRIDGE_H

#include "core/bridge_driver.h"

#include <cstdint>
#include <memory>
#include <optional>
#include <signal.h>
#include <string>
#include <vector>

class StreamEngine;

/**
 * Structure carrying the addresses of all fixed MMIO ports.
 *
 * This structure is instantiated when all bridges are populated based on
 * the target configuration.
 */
struct TACITBRIDGEMODULE_struct {
};

class tacit_handler {
public:
  virtual ~tacit_handler() = default;
  virtual void put(uint8_t data) = 0;
  virtual void flush_buffer() = 0;
};

class tacit_t final : public streaming_bridge_driver_t {
public:
  /// The identifier for the bridge type used for casts.
  static char KIND;

  /// Creates a bridge which interacts with standard streams or PTY.
  tacit_t(simif_t &simif,
          StreamEngine &stream,
          int tacitno,
          const std::vector<std::string> &args,
          int stream_idx,
          int stream_depth);

  ~tacit_t() override;

  void tick() override;
  void finish() override;

private:
  std::unique_ptr<tacit_handler> handler;

  const int stream_idx;
  const int stream_depth;

  void drain_stream(size_t minimum_batch_bytes);
};

#endif // __TACITBRIDGE_H
