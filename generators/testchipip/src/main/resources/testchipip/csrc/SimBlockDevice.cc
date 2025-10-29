#include <vpi_user.h>
#include <svdpi.h>

#include "blkdev.h"

BlockDevice *bdev = NULL;

extern "C" void block_device_init(
        const char *filename, int ntags, int *nsectors, int *max_req_len)
{
    bdev = new BlockDevice(filename, ntags);
    *nsectors = bdev->nsectors();
    *max_req_len = bdev->max_request_length();
}

extern "C" void block_device_tick(
        unsigned char req_valid,
        unsigned char *req_ready,
        unsigned char req_bits_write,
        int           req_bits_offset,
        int           req_bits_len,
        int           req_bits_tag,

        unsigned char data_valid,
        unsigned char *data_ready,
        long long     data_bits_data,
        int           data_bits_tag,

        unsigned char *resp_valid,
        unsigned char resp_ready,
        long long     *resp_bits_data,
        int           *resp_bits_tag)
{
    if (bdev == NULL) {
        *req_ready = 0;
        *data_ready = 0;
        *resp_valid = 0;
        return;
    }

    bdev->tick(
            req_valid,
            req_bits_write,
            req_bits_offset,
            req_bits_len,
            req_bits_tag,
            data_valid,
            data_bits_data,
            data_bits_tag,
            resp_ready);
    bdev->switch_to_host();

    *req_ready = bdev->req_ready();
    *data_ready = bdev->req_ready();
    *resp_valid = bdev->resp_valid();
    *resp_bits_data = bdev->resp_data();
    *resp_bits_tag = bdev->resp_tag();
}
