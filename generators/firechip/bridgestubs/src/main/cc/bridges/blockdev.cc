// See LICENSE for license details

#include "blockdev.h"
#include <inttypes.h>
#include <stdio.h>
#include <string.h>
#include <string>

char blockdev_t::KIND;

/* Block Device Endpoint Driver
 *
 * This works in conjunction with
 * testchipip/src/main/scala/BlockDevice.scala (Block Device RTL)
 * and
 * src/main/scala/bridges/BlockDevWidget.scala
 */

/* Uncomment to get DEBUG printing
 * TODO: better logging mechanism so that we don't need this */
// #define BLKDEV_DEBUG

#ifdef BLKDEV_DEBUG
#define blkdev_printf(...)                                                     \
  if (this->logfile) {                                                         \
    fprintf(this->logfile, __VA_ARGS__);                                       \
    fflush(this->logfile);                                                     \
  }
#else
#define blkdev_printf(...)                                                     \
  {}
#endif

/* Block Dev software driver constructor.
 * Setup software driver state:
 * Check if we have been given a file to use as a disk, record size and
 * number of sectors to pass to widget */
blockdev_t::blockdev_t(simif_t &sim,
                       const BLOCKDEVBRIDGEMODULE_struct &mmio_addrs,
                       int blkdevno,
                       const std::vector<std::string> &args,
                       uint32_t num_trackers,
                       uint32_t latency_bits)
    : bridge_driver_t(sim, &KIND), mmio_addrs(mmio_addrs) {
  this->_file = nullptr;
  this->logfile = nullptr;
  _ntags = num_trackers;
  long size;
  long mem_filesize = 0;

  const char *logname = nullptr;

  // construct arg parsing strings here. We basically append the bridge_driver
  // number to each of these base strings, to get args like +blkdev0 etc.
  std::string num_equals = std::to_string(blkdevno) + std::string("=");

  std::string blkdev_arg = std::string("+blkdev") + num_equals;
  std::string blkdevinmem_arg = std::string("+blkdev-in-mem") + num_equals;
  std::string blkdevwlatency_arg = std::string("+blkdev-wlatency") + num_equals;
  std::string blkdevrlatency_arg = std::string("+blkdev-rlatency") + num_equals;
  std::string blkdevlog_arg = std::string("+blkdev-log") + num_equals;

  for (auto &arg : args) {
    if (arg.find(blkdev_arg) == 0) {
      filename = const_cast<char *>(arg.c_str()) + blkdev_arg.length();
    }
    // Spoofs a file with fmemopen. Useful for testing
    if (arg.find(blkdevinmem_arg) == 0) {
      mem_filesize =
          atoi(const_cast<char *>(arg.c_str()) + blkdevinmem_arg.length());
    }
    if (arg.find(blkdevwlatency_arg) == 0) {
      write_latency =
          atoi(const_cast<char *>(arg.c_str()) + blkdevwlatency_arg.length());
    }
    if (arg.find(blkdevrlatency_arg) == 0) {
      read_latency =
          atoi(const_cast<char *>(arg.c_str()) + blkdevrlatency_arg.length());
    }
    if (arg.find(blkdevlog_arg) == 0) {
      logname = const_cast<char *>(arg.c_str()) + blkdevlog_arg.length();
    }
  }

  uint32_t max_latency = (1UL << latency_bits) - 1;
  if (write_latency > max_latency) {
    fprintf(stderr,
            "Requested blockdev write latency (%u) exceeds HW limit (%u).\n",
            write_latency,
            max_latency);
    abort();
  }

  if (read_latency > max_latency) {
    fprintf(stderr,
            "Requested blockdev read latency (%u) exceeds HW limit (%u).\n",
            read_latency,
            max_latency);
    abort();
  }

  if (logname) {
    logfile = fopen(logname, "w");
    if (logfile == nullptr) {
      fprintf(stderr, "Could not open %s\n", logname);
      abort();
    }
  }

  if (filename) {
    _file = fopen(filename, "r+");
    if (!_file) {
      fprintf(stderr, "Could not open %s\n", filename);
      abort();
    }
    if (fseek(_file, 0, SEEK_END)) {
      perror("fseek");
      abort();
    }
    size = ftell(_file);
    if (size < 0) {
      perror("ftell");
      abort();
    }
  } else if (mem_filesize > 0) {
    size = mem_filesize << SECTOR_SHIFT;
    _file = fmemopen(nullptr, size, "r+");
    if (!_file) {
      perror("fmemopen");
      abort();
    }
  } else {
    size = 0;
  }
  _nsectors = size >> SECTOR_SHIFT;

  write_trackers.resize(_ntags);
}

blockdev_t::~blockdev_t() {
  if (filename) {
    fclose(_file);
  }
  if (logfile)
    fclose(logfile);
}

/* "init" for blockdev widget that gets called right before target_reset.
 * Here, we set control regs e.g. for # sectors, allowed request length
 * at boot */
void blockdev_t::init() {
  // setup blk dev widget
  write(mmio_addrs.bdev_nsectors, nsectors());
  write(mmio_addrs.bdev_max_req_len, max_request_length());
  write(mmio_addrs.read_latency, read_latency);
  write(mmio_addrs.write_latency, write_latency);
}

/* Take a read request, get data from the disk file, and fill the beats
 * into the response queue from which data will be written to the block device
 * widget on the FPGA */
void blockdev_t::do_read(struct blkdev_request &req) {
  uint64_t offset, nbeats;
  uint64_t blk_data[MAX_REQ_LEN * SECTOR_BEATS];

  offset = req.offset;
  offset <<= SECTOR_SHIFT;
  nbeats = req.len;
  nbeats *= SECTOR_BEATS;

  /* Check that the request is valid. */
  if ((req.offset + req.len) > nsectors()) {
    fprintf(stderr,
            "Read range %u - %u out of bounds\n",
            req.offset,
            req.offset + req.len);
    abort();
  }
  if (req.len == 0) {
    fprintf(stderr, "Read request cannot have 0 length\n");
    abort();
  }
  if (req.len > MAX_REQ_LEN) {
    fprintf(stderr,
            "Read request length too large: %u > %u\n",
            req.len,
            MAX_REQ_LEN);
    abort();
  }
  if (req.tag >= _ntags) {
    fprintf(stderr, "Read request tag %d too large.\n", req.tag);
    abort();
  }

  /* Seek to correct place in the file. */
  if (fseek(_file, offset, SEEK_SET)) {
    fprintf(stderr, "Could not seek to %" PRIx64 "\n", offset);
    abort();
  }

  /* Perform the read from file. */
  if (fread(blk_data, SECTOR_SIZE, req.len, _file) < req.len) {
    fprintf(stderr, "Cannot read data at %" PRIx64 "\n", offset);
    abort();
  }

  /* Populate response queue from data that has been read from file. Response
   * queue will be consumed when writing to FPGA. */
  for (uint64_t i = 0; i < nbeats; i++) {
    struct blkdev_data resp;
    resp.data = blk_data[i];
    resp.tag = req.tag;
    read_responses.push(resp);
  }
}

/* Take a write request and set up a write_tracker to process it.
 * Later, handle_data will be called to actually perform the writes
 * to file. */
void blockdev_t::do_write(struct blkdev_request &req) {
  if (req.tag >= _ntags) {
    /* Check that req.tag is in range.
     * This check must happen before we index into write_trackers */
    fprintf(stderr, "Write request tag %d too large.\n", req.tag);
    abort();
  }

  struct blkdev_write_tracker &tracker = write_trackers[req.tag];

  /* Check request sanity */
  if ((req.offset + req.len) > nsectors()) {
    fprintf(stderr,
            "Write range %u - %u out of bounds\n",
            req.offset,
            req.offset + req.len);
    abort();
  }
  if (req.len == 0) {
    fprintf(stderr, "Write request cannot have 0 length\n");
    abort();
  }
  if (req.len > MAX_REQ_LEN) {
    fprintf(stderr, "Write request too large: %u > %u\n", req.len, MAX_REQ_LEN);
    abort();
  }

  /* Setup tracker state */
  tracker.offset = req.offset;
  tracker.offset *= SECTOR_SIZE;
  tracker.count = 0;
  tracker.size = req.len;
  tracker.size *= SECTOR_BEATS;
}

/* Confirm that a write_tracker has been setup for a chunk of data that
 * we have received from the block device widget, to be written to file */
bool blockdev_t::can_accept(struct blkdev_data &data) {
  return write_trackers[data.tag].size > 0;
}

void blockdev_t::handle_data(struct blkdev_data &data) {
  if (data.tag >= _ntags) {
    /* Check that data.tag is in range.
     * This check must happen before we index into write_trackers */
    fprintf(stderr, "Data tag %d too large.\n", data.tag);
    abort();
  }

  struct blkdev_write_tracker &tracker = write_trackers[data.tag];

  /* Copy data into the write tracker */
  tracker.data[tracker.count] = data.data;
  tracker.count++;

  if (tracker.count < tracker.size) {
    /* We are still waiting to receive all the data for this write
     * request, so return. */
    return;
  }

  /* Seek to the right place to begin the write to file. */
  if (fseek(_file, tracker.offset, SEEK_SET)) {
    fprintf(stderr, "Could not seek to %" PRIx64 "\n", tracker.offset);
    abort();
  }

  /* Perform the write to file. */
  if (fwrite(tracker.data, sizeof(uint64_t), tracker.count, _file) <
      tracker.count) {
    fprintf(stderr, "Cannot write data at %" PRIx64 "\n", tracker.offset);
    abort();
  }

  /* Clear the tracker state */
  tracker.offset = 0;
  tracker.count = 0;
  tracker.size = 0;

  /* Send an ack to the block device.
   * TODO: should a block device do this?  Biancolin: Yes.*/
  write_acks.push(data.tag);
}

/* Read all pending request data from the widget */
void blockdev_t::recv() {
  /* Read all pending requests from the widget */
  while (read(mmio_addrs.bdev_req_valid)) {
    /* Take a request from the FPGA and put it in SW processing queues */
    struct blkdev_request req;
    req.write = read(mmio_addrs.bdev_req_write);
    req.offset = read(mmio_addrs.bdev_req_offset);
    req.len = read(mmio_addrs.bdev_req_len);
    req.tag = read(mmio_addrs.bdev_req_tag);
    write(mmio_addrs.bdev_req_ready, true);
    requests.push(req);
    blkdev_printf("[disk] got req. write %x, offset %x, len %x, tag %x\n",
                  req.write,
                  req.offset,
                  req.len,
                  req.tag);
  }

  /* Read all pending data beats from the widget */
  while (read(mmio_addrs.bdev_data_valid)) {
    /* Take a data chunk from the FPGA and put it in SW processing queues */
    struct blkdev_data data;
    data.data = (((uint64_t)read(mmio_addrs.bdev_data_data_upper)) << 32) |
                (read(mmio_addrs.bdev_data_data_lower) & 0xFFFFFFFF);
    data.tag = read(mmio_addrs.bdev_data_tag);
    write(mmio_addrs.bdev_data_ready, true);
    req_data.push(data);
    blkdev_printf("[disk] got data. data %llx, tag %x\n", data.data, data.tag);
  }
}

/* This dumps as much read_response and write_ack data onto the widget as
 * possible In the event the widget buffers fill up; set resp_data_pending,
 * indicating that we must try again on the next tick() invocation */
void blockdev_t::send() {
  /* Return as many write acknowledgements as the blockdev widget can accept */
  while (!write_acks.empty() && read(mmio_addrs.bdev_wack_ready)) {
    uint32_t tag = write_acks.front();
    write(mmio_addrs.bdev_wack_tag, tag);
    write(mmio_addrs.bdev_wack_valid, true);
    blkdev_printf("[disk] sending W ack. tag %x\n", tag);
    write_acks.pop();
  }

  /* Send as much read reponse data as as the blockdev widget will accept */
  while (!read_responses.empty() && read(mmio_addrs.bdev_rresp_ready)) {
    struct blkdev_data resp;
    resp = read_responses.front();
    write(mmio_addrs.bdev_rresp_data_upper, (resp.data >> 32) & 0xFFFFFFFF);
    write(mmio_addrs.bdev_rresp_data_lower, resp.data & 0xFFFFFFFF);
    write(mmio_addrs.bdev_rresp_tag, resp.tag);
    write(mmio_addrs.bdev_rresp_valid, true);
    blkdev_printf(
        "[disk] sending R resp. data %llx, tag %x\n", resp.data, resp.tag);
    read_responses.pop();
  }

  /* Mark if finished */
  resp_data_pending = !read_responses.empty() || !write_acks.empty();
}

bool blockdev_t::idle() {
  return !resp_data_pending && !read(mmio_addrs.bdev_reqs_pending);
}

/* This method is called to service functional requests made by the widget.
 * No target time is modelled here; the widget will stall stimulation if
 * we have not yet serviced a transaction that is scheduled to be released. */
void blockdev_t::tick() {

  /* If there's nothing to do, early out and save a bunch of MMIO */
  if (idle()) {
    return;
  }

  /* If there's pending response data from the last invocation of tick(),
   * write that back first as it might be locking up the simulator */
  if (resp_data_pending) {
    this->send();
  }

  /* Collect all of the requests sitting in the widget queues */
  this->recv();

  /* Do software processing of request queues. (requests coming from the
   * block dev widget) */
  while (!requests.empty()) {
    struct blkdev_request &req = requests.front();
    if (req.write) {
      /* if write request, setup a write tracker */
      do_write(req);
    } else {
      /* if read request, perform read from file and put data into
       * read_responses queue. */
      do_read(req);
    }
    requests.pop();
  }

  /* Do software processing of write data queues. (data coming from the
   * block dev widget).
   *
   * If there is data in req_data (from the FPGA) and a tracker has been
   * properly setup for this write, then call handle_data for this beat
   * of the write. */
  while (!req_data.empty() && can_accept(req_data.front())) {
    handle_data(req_data.front());
    req_data.pop();
  }

  /* Write state back to block device widget */
  this->send();
}
