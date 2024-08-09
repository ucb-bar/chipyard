// See LICENSE for license details
#ifndef __BLOCKDEV_H
#define __BLOCKDEV_H

#include <queue>
#include <stdio.h>
#include <vector>

#include "core/bridge_driver.h"

struct BLOCKDEVBRIDGEMODULE_struct {
  uint64_t read_latency;
  uint64_t write_latency;
  uint64_t bdev_nsectors;
  uint64_t bdev_max_req_len;
  uint64_t bdev_req_valid;
  uint64_t bdev_req_write;
  uint64_t bdev_req_offset;
  uint64_t bdev_req_len;
  uint64_t bdev_req_tag;
  uint64_t bdev_req_ready;
  uint64_t bdev_data_valid;
  uint64_t bdev_data_data_upper;
  uint64_t bdev_data_data_lower;
  uint64_t bdev_data_tag;
  uint64_t bdev_data_ready;
  uint64_t bdev_rresp_data_upper;
  uint64_t bdev_rresp_data_lower;
  uint64_t bdev_rresp_tag;
  uint64_t bdev_rresp_valid;
  uint64_t bdev_rresp_ready;
  uint64_t bdev_wack_tag;
  uint64_t bdev_wack_valid;
  uint64_t bdev_wack_ready;
  uint64_t bdev_reqs_pending;
  uint64_t bdev_wack_stalled;
  uint64_t bdev_rresp_stalled;
};

#define SECTOR_SIZE 512
#define SECTOR_SHIFT 9
#define SECTOR_BEATS (SECTOR_SIZE / 8)
#define MAX_REQ_LEN 16

struct blkdev_request {
  bool write;
  uint32_t offset;
  uint32_t len;
  uint32_t tag;
};

struct blkdev_data {
  uint64_t data;
  uint32_t tag;
};

struct blkdev_write_tracker {
  uint64_t offset;
  uint64_t count;
  uint64_t size;
  uint64_t data[MAX_REQ_LEN * SECTOR_BEATS];
};

class blockdev_t : public bridge_driver_t {
public:
  /// The identifier for the bridge type used for casts.
  static char KIND;

  blockdev_t(simif_t &sim,
             const BLOCKDEVBRIDGEMODULE_struct &mmio_addrs,
             int blkdevno,
             const std::vector<std::string> &args,
             uint32_t num_trackers,
             uint32_t latency_bits);
  ~blockdev_t() override;

  uint32_t nsectors(void) { return _nsectors; }
  uint32_t max_request_length(void) { return MAX_REQ_LEN; }

  void init() override;
  void tick() override;

  void send();
  void recv();

private:
  const BLOCKDEVBRIDGEMODULE_struct mmio_addrs;

  // Set if, on the previous tick, we couldn't write back all of our response
  // data
  bool resp_data_pending = false;

  uint32_t _ntags;
  uint32_t _nsectors;
  FILE *_file, *logfile;
  char *filename = nullptr;
  std::queue<blkdev_request> requests;
  std::queue<blkdev_data> req_data;
  std::queue<blkdev_data> read_responses;
  std::queue<uint32_t> write_acks;

  std::vector<blkdev_write_tracker> write_trackers;

  void do_read(struct blkdev_request &req);
  void do_write(struct blkdev_request &req);
  bool can_accept(struct blkdev_data &data);
  void handle_data(struct blkdev_data &data);
  // Returns true if no widget interaction is required
  bool idle();

  // Default timing model parameters
  uint32_t read_latency = 4096;
  uint32_t write_latency = 4096;
};

#endif // __BLOCKDEV_H
