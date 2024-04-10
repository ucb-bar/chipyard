#define BLKDEV_BASE 0x10015000
#define BLKDEV_ADDR BLKDEV_BASE
#define BLKDEV_OFFSET (BLKDEV_BASE + 8)
#define BLKDEV_LEN (BLKDEV_BASE + 12)
#define BLKDEV_WRITE (BLKDEV_BASE + 16)
#define BLKDEV_REQUEST (BLKDEV_BASE + 17)
#define BLKDEV_NREQUEST (BLKDEV_BASE + 18)
#define BLKDEV_COMPLETE (BLKDEV_BASE + 19)
#define BLKDEV_NCOMPLETE (BLKDEV_BASE + 20)
#define BLKDEV_NSECTORS (BLKDEV_BASE + 24)
#define BLKDEV_MAX_REQUEST_LENGTH (BLKDEV_BASE + 28)
#define BLKDEV_SECTOR_SIZE 512
#define BLKDEV_SECTOR_SHIFT 9

static inline size_t blkdev_nsectors(void)
{
	return reg_read32(BLKDEV_NSECTORS);
}

static inline size_t blkdev_max_req_len(void)
{
	return reg_read32(BLKDEV_MAX_REQUEST_LENGTH);
}

static inline unsigned int blkdev_send_request(
		unsigned long addr,
		unsigned int offset,
		unsigned int len,
		unsigned char write)
{
		reg_write64(BLKDEV_ADDR, addr);
		reg_write32(BLKDEV_OFFSET, offset);
		reg_write32(BLKDEV_LEN, len);
		reg_write8(BLKDEV_WRITE, write);

		asm volatile ("fence");
		return reg_read8(BLKDEV_REQUEST);
}
