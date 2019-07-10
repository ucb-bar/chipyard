#ifndef __MEMBLADE_H__
#define __MEMBLADE_H__

#define MB_REQ_ETH_TYPE 0x0408
#define MB_RESP_ETH_TYPE 0x0508
#define MB_DRAFT_VERSION 1

#define MB_OC_SPAN_READ 0
#define MB_OC_SPAN_WRITE 1
#define MB_OC_WORD_READ 2
#define MB_OC_WORD_WRITE 3
#define MB_OC_ATOMIC_ADD 4
#define MB_OC_COMP_SWAP 5

#define MB_RC_SPAN_OK 0x80
#define MB_RC_NODATA_OK 0x81
#define MB_RC_WORD_OK 0x82
#define MB_RC_ERROR 0x83

#define RMEM_CLIENT_BASE   0x10018000L
#define RMEM_CLIENT_SRC_ADDR   (RMEM_CLIENT_BASE + 0x00)
#define RMEM_CLIENT_DST_ADDR   (RMEM_CLIENT_BASE + 0x08)
#define RMEM_CLIENT_DSTMAC     (RMEM_CLIENT_BASE + 0x10)
#define RMEM_CLIENT_OPCODE     (RMEM_CLIENT_BASE + 0x16)
#define RMEM_CLIENT_SPANID     (RMEM_CLIENT_BASE + 0x18)
#define RMEM_CLIENT_REQ        (RMEM_CLIENT_BASE + 0x20)
#define RMEM_CLIENT_RESP       (RMEM_CLIENT_BASE + 0x24)
#define RMEM_CLIENT_NREQ       (RMEM_CLIENT_BASE + 0x28)
#define RMEM_CLIENT_NRESP      (RMEM_CLIENT_BASE + 0x2C)

struct memblade_request {
	uint8_t version;
	uint8_t opcode;
	uint16_t reserved;
	uint32_t xact_id;
	uint64_t pageno;
};

struct memblade_response {
	uint8_t version;
	uint8_t resp_code;
	uint16_t reserved;
	uint32_t xact_id;
};

struct combined_request {
	struct ethernet_header eth;
	struct memblade_request mbreq;
};

struct combined_response {
	struct ethernet_header eth;
	struct memblade_response mbresp;
};

static inline uint64_t memblade_make_exthead(int offset, int size)
{
	return ((offset & 0xfff) << 4) | (size & 0x3);
}

#endif
