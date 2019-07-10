#ifndef __NIC_H__
#define __NIC_H__

struct ethernet_header {
	uint16_t padding;
	uint8_t dstmac[6];
	uint8_t srcmac[6];
	uint16_t ethtype;
};

#define SIMPLENIC_BASE 0x10016000L
#define SIMPLENIC_SEND_REQ (SIMPLENIC_BASE + 0)
#define SIMPLENIC_RECV_REQ (SIMPLENIC_BASE + 8)
#define SIMPLENIC_SEND_COMP (SIMPLENIC_BASE + 16)
#define SIMPLENIC_RECV_COMP (SIMPLENIC_BASE + 18)
#define SIMPLENIC_COUNTS (SIMPLENIC_BASE + 20)
#define SIMPLENIC_MACADDR (SIMPLENIC_BASE + 24)
#define SIMPLENIC_INTMASK (SIMPLENIC_BASE + 32)
#define SIMPLENIC_CKSUM_COUNTS (SIMPLENIC_BASE + 36)
#define SIMPLENIC_CKSUM_RESP (SIMPLENIC_BASE + 38)
#define SIMPLENIC_CKSUM_REQ (SIMPLENIC_BASE + 40)

static inline int nic_send_req_avail(void)
{
	return reg_read32(SIMPLENIC_COUNTS) & 0xff;
}

static inline int nic_recv_req_avail(void)
{
	return (reg_read32(SIMPLENIC_COUNTS) >> 8) & 0xff;
}

static inline int nic_send_comp_avail(void)
{
	return (reg_read32(SIMPLENIC_COUNTS) >> 16) & 0xff;
}

static inline int nic_recv_comp_avail(void)
{
	return (reg_read32(SIMPLENIC_COUNTS) >> 24) & 0xff;
}

static void nic_send(void *data, unsigned long len)
{
	uintptr_t addr = ((uintptr_t) data) & ((1L << 48) - 1);
	unsigned long packet = (len << 48) | addr;

	while (nic_send_req_avail() == 0);
	reg_write64(SIMPLENIC_SEND_REQ, packet);

	while (nic_send_comp_avail() == 0);
	reg_read16(SIMPLENIC_SEND_COMP);
}

static int nic_recv(void *dest)
{
	uintptr_t addr = (uintptr_t) dest;
	int len;

	while (nic_recv_req_avail() == 0);
	reg_write64(SIMPLENIC_RECV_REQ, addr);

	// Poll for completion
	while (nic_recv_comp_avail() == 0);
	len = reg_read16(SIMPLENIC_RECV_COMP);
	asm volatile ("fence");

	return len;
}

static void nic_post_send(void *data, unsigned long len, int part)
{
	uintptr_t addr = ((uintptr_t) data) & ((1L << 48) - 1);
	unsigned long lenpart = (part << 15) | (len & 0x7fff);
	unsigned long packet = (lenpart << 48) | addr;

	reg_write64(SIMPLENIC_SEND_REQ, packet);
}

static void nic_complete_send(void)
{
	reg_read16(SIMPLENIC_SEND_COMP);
}

static void nic_post_recv(void *dest)
{
	uintptr_t addr = (uintptr_t) dest;
	reg_write64(SIMPLENIC_RECV_REQ, addr);
}

static int nic_complete_recv(void)
{
	return reg_read16(SIMPLENIC_RECV_COMP);
}

static inline uint64_t nic_macaddr(void)
{
	return reg_read64(SIMPLENIC_MACADDR);
}

#endif
