#include <vector>
#include <queue>
#include <cstdlib>
#include <fesvr/context.h>
#include <cstdint>

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

class BlockDevice {
  public:
    BlockDevice(const char *filename, uint32_t ntags);
    ~BlockDevice(void);

    uint32_t nsectors(void) { return _nsectors; }
    uint32_t max_request_length(void) { return MAX_REQ_LEN; }
    void tick(
        uint8_t  req_valid,
        uint8_t  req_bits_write,
        uint32_t req_bits_offset,
        uint32_t req_bits_len,
        uint32_t req_bits_tag,

        uint8_t  data_valid,
        uint64_t data_bits_data,
        uint32_t data_bits_tag,

        uint8_t  resp_ready);

    bool req_ready() { return true; }
    bool data_ready() { return true; }
    bool resp_valid() { return !responses.empty(); }
    uint64_t resp_data() { return (resp_valid()) ? responses.front().data : 0; }
    uint32_t resp_tag() { return (resp_valid()) ? responses.front().tag : 0; }

    void send_request(struct blkdev_request &req);
    void send_data(struct blkdev_data &data);
    struct blkdev_data recv_response(void);

    void switch_to_host() { host.switch_to(); }

  private:
    uint32_t _ntags;
    uint32_t _nsectors;
    FILE *_file;
    std::queue<blkdev_request> requests;
    std::queue<blkdev_data> req_data;
    std::queue<blkdev_data> responses;
    std::vector<blkdev_write_tracker> write_trackers;

    void do_read(struct blkdev_request &req);
    void do_write(struct blkdev_request &req);
    bool can_accept(struct blkdev_data &data);
    void handle_data(struct blkdev_data &data);

    static void host_thread(void *arg);
    void run(void);

    context_t* target;
    context_t host;
};
