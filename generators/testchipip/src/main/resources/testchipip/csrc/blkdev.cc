#include "blkdev.h"
#include <cstdlib>
#include <cstring>
#include <cinttypes>
#include <cstdio>

void BlockDevice::host_thread(void *arg)
{
    BlockDevice *bdev = static_cast<BlockDevice*>(arg);
    bdev->run();

    while (true)
        bdev->target->switch_to();
}

BlockDevice::BlockDevice(const char *filename, uint32_t ntags)
    : _ntags(ntags)
{
    long size;

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
    _nsectors = size >> SECTOR_SHIFT;

    write_trackers.resize(ntags);

    target = context_t::current();
    host.init(host_thread, this);
}

BlockDevice::~BlockDevice(void)
{
    fclose(_file);
}

void BlockDevice::do_read(struct blkdev_request &req)
{
    uint64_t offset, nbeats;
    uint64_t blk_data[MAX_REQ_LEN * SECTOR_BEATS];

    offset = req.offset;
    offset <<= SECTOR_SHIFT;
    nbeats = req.len;
    nbeats *= SECTOR_BEATS;

    if ((req.offset + req.len) > nsectors()) {
        fprintf(stderr, "Read range %u - %u out of bounds\n",
                req.offset, req.offset + req.len);
        abort();
    }

    if (req.len == 0) {
        fprintf(stderr, "Read request cannot have 0 length\n");
        abort();
    }

    if (req.len > MAX_REQ_LEN) {
        fprintf(stderr, "Read request length too large: %u > %u\n",
                req.len, MAX_REQ_LEN);
        abort();
    }

    if (req.tag >= _ntags) {
        fprintf(stderr, "Read request tag %d too large.\n", req.tag);
        abort();
    }

    if (fseek(_file, offset, SEEK_SET)) {
        fprintf(stderr, "Could not seek to %" PRIx64 "\n", offset);
        abort();
    }

    if (fread(blk_data, SECTOR_SIZE, req.len, _file) < req.len) {
        fprintf(stderr, "Cannot read data at %" PRIx64 "\n", offset);
        abort();
    }

    for (uint64_t i = 0; i < nbeats; i++) {
        struct blkdev_data resp;
        resp.data = blk_data[i];
        resp.tag = req.tag;
        responses.push(resp);
    }
}

void BlockDevice::do_write(struct blkdev_request &req)
{
    struct blkdev_write_tracker &tracker = write_trackers[req.tag];

    if ((req.offset + req.len) > nsectors()) {
        fprintf(stderr, "Write range %u - %u out of bounds\n",
                req.offset, req.offset + req.len);
        abort();
    }

    if (req.len == 0) {
        fprintf(stderr, "Write request cannot have 0 length\n");
        abort();
    }

    if (req.len > MAX_REQ_LEN) {
        fprintf(stderr, "Write request too large: %u > %u\n",
                req.len, MAX_REQ_LEN);
        abort();
    }


    if (req.tag >= _ntags) {
        fprintf(stderr, "Write request tag %d too large.\n", req.tag);
        abort();
    }

    tracker.offset = req.offset;
    tracker.offset <<= SECTOR_SHIFT;
    tracker.count = 0;
    tracker.size = req.len;
    tracker.size *= SECTOR_BEATS;
}

bool BlockDevice::can_accept(struct blkdev_data &data)
{
    return write_trackers[data.tag].size > 0;
}

void BlockDevice::handle_data(struct blkdev_data &data)
{
    struct blkdev_write_tracker &tracker = write_trackers[data.tag];
    struct blkdev_data resp;

    tracker.data[tracker.count] = data.data;
    tracker.count++;

    if (tracker.count < tracker.size)
        return;

    if (fseek(_file, tracker.offset, SEEK_SET)) {
        fprintf(stderr, "Could not seek to %" PRIx64 "\n", tracker.offset);
        abort();
    }

    if (fwrite(tracker.data, sizeof(uint64_t), tracker.count, _file) < tracker.count) {
        fprintf(stderr, "Cannot write data at %" PRIx64 "\n", tracker.offset);
        abort();
    }

    tracker.offset = 0;
    tracker.count = 0;
    tracker.size = 0;

    resp.data = 0;
    resp.tag = data.tag;
    responses.push(resp);
}

void BlockDevice::run(void)
{
    while (true) {
        while (!requests.empty()) {
            struct blkdev_request &req = requests.front();
            if (req.write)
                do_write(req);
            else
                do_read(req);
            requests.pop();
        }
        while (!req_data.empty() && can_accept(req_data.front())) {
            handle_data(req_data.front());
            req_data.pop();
        }
        this->target->switch_to();
    }
}

void BlockDevice::send_request(struct blkdev_request &req)
{
    requests.push(req);
}

void BlockDevice::send_data(struct blkdev_data &data)
{
    req_data.push(data);
}

struct blkdev_data BlockDevice::recv_response(void)
{
    struct blkdev_data resp;

    resp = responses.front();
    responses.pop();

    return resp;
}

void BlockDevice::tick(
        uint8_t  req_valid,
        uint8_t  req_bits_write,
        uint32_t req_bits_offset,
        uint32_t req_bits_len,
        uint32_t req_bits_tag,

        uint8_t  data_valid,
        uint64_t data_bits_data,
        uint32_t data_bits_tag,

        uint8_t  resp_ready)
{
    if (req_valid && req_ready()) {
        struct blkdev_request req;
        req.write = req_bits_write;
        req.offset = req_bits_offset;
        req.len = req_bits_len;
        req.tag = req_bits_tag;
        requests.push(req);
    }

    if (data_valid && data_ready()) {
        struct blkdev_data data;
        data.data = data_bits_data;
        data.tag = data_bits_tag;
        req_data.push(data);
    }

    if (resp_valid() && resp_ready)
        responses.pop();
}
