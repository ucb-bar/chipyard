// Linux PC-side raw-Ethernet file transfer tool.

#define _DEFAULT_SOURCE

#include "software/ethernet/protocol.h"

#include <arpa/inet.h>
#include <errno.h>
#include <linux/if_packet.h>
#include <net/ethernet.h>
#include <net/if.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <time.h>
#include <unistd.h>

enum {
  kAckRetries = 5,
  kReceiveTimeouts = 30,
  kSocketTimeoutSec = 1,
};

typedef struct ethernet_pc_socket {
  int fd;
  int ifindex;
  uint8_t mac[kEthernetMacLen];
} ethernet_pc_socket_t;

static const uint8_t kFpgaMac[kEthernetMacLen] = {0x02, 0x00, 0x00, 0x00, 0x00, 0x01};

// Open a raw Ethernet socket on `ifname` and capture its MAC address.
static int open_raw_socket(const char *ifname, ethernet_pc_socket_t *sock) {
  if (strlen(ifname) >= IFNAMSIZ) {
    fprintf(stderr, "[pc] interface name too long: %s\n", ifname);
    return -1;
  }

  int fd = socket(AF_PACKET, SOCK_RAW, htons(kEthernetEthertype));
  if (fd < 0) {
    perror("socket");
    return -1;
  }

  struct ifreq ifr;
  memset(&ifr, 0, sizeof(ifr));
  strncpy(ifr.ifr_name, ifname, IFNAMSIZ - 1);
  if (ioctl(fd, SIOCGIFINDEX, &ifr) < 0) {
    perror("SIOCGIFINDEX");
    close(fd);
    return -1;
  }
  int ifindex = ifr.ifr_ifindex;

  if (ioctl(fd, SIOCGIFHWADDR, &ifr) < 0) {
    perror("SIOCGIFHWADDR");
    close(fd);
    return -1;
  }

  struct sockaddr_ll bind_addr;
  memset(&bind_addr, 0, sizeof(bind_addr));
  bind_addr.sll_family = AF_PACKET;
  bind_addr.sll_protocol = htons(kEthernetEthertype);
  bind_addr.sll_ifindex = ifindex;
  if (bind(fd, (struct sockaddr *)&bind_addr, sizeof(bind_addr)) < 0) {
    perror("bind");
    close(fd);
    return -1;
  }

  struct timeval timeout = {
      .tv_sec = kSocketTimeoutSec,
      .tv_usec = 0,
  };
  if (setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout)) < 0) {
    perror("setsockopt SO_RCVTIMEO");
    close(fd);
    return -1;
  }

  sock->fd = fd;
  sock->ifindex = ifindex;
  memcpy(sock->mac, ifr.ifr_hwaddr.sa_data, kEthernetMacLen);
  return 0;
}

// Build one protocol frame into `frame`.
static int build_frame(const ethernet_pc_socket_t *sock,
                       const uint8_t dst[kEthernetMacLen],
                       const ethernet_packet_header_t *header,
                       const uint8_t *data,
                       uint32_t data_len,
                       uint8_t frame[kEthernetMaxFrameLen]) {
  for (int i = 0; i < kEthernetMacLen; i++) {
    frame[i] = dst[i];
    frame[kEthernetMacLen + i] = sock->mac[i];
  }
  frame[12] = (uint8_t)(kEthernetEthertype >> 8);
  frame[13] = (uint8_t)(kEthernetEthertype & 0xffu);

  if (ethernet_encode_header(&frame[kEthernetL2HeaderLen],
                             kEthernetHeaderLen + (size_t)data_len, header) < 0) {
    return -1;
  }
  if (data_len > 0) {
    memcpy(&frame[kEthernetL2HeaderLen + kEthernetHeaderLen], data, data_len);
  }

  uint32_t payload_len = kEthernetHeaderLen + data_len;
  uint32_t padded_payload_len = payload_len;
  if (padded_payload_len < kEthernetMinPayloadLen) {
    padded_payload_len = kEthernetMinPayloadLen;
  }
  for (uint32_t i = payload_len; i < padded_payload_len; i++) {
    frame[kEthernetL2HeaderLen + i] = 0;
  }
  return kEthernetL2HeaderLen + (int)padded_payload_len;
}

// Send one encoded protocol packet to `dst`.
static int send_packet(const ethernet_pc_socket_t *sock,
                       const uint8_t dst[kEthernetMacLen],
                       const ethernet_packet_header_t *header,
                       const uint8_t *data,
                       uint32_t data_len) {
  uint8_t frame[kEthernetMaxFrameLen];
  int frame_len = build_frame(sock, dst, header, data, data_len, frame);
  if (frame_len < 0) {
    return -1;
  }

  struct sockaddr_ll addr;
  memset(&addr, 0, sizeof(addr));
  addr.sll_family = AF_PACKET;
  addr.sll_protocol = htons(kEthernetEthertype);
  addr.sll_ifindex = sock->ifindex;
  addr.sll_halen = kEthernetMacLen;
  memcpy(addr.sll_addr, dst, kEthernetMacLen);

  ssize_t sent = sendto(sock->fd, frame, (size_t)frame_len, 0,
                        (struct sockaddr *)&addr, sizeof(addr));
  if (sent < 0) {
    perror("sendto");
    return -1;
  }
  if (sent != frame_len) {
    fprintf(stderr, "[pc] short send: %zd/%d\n", sent, frame_len);
    return -1;
  }
  return 0;
}

// Receive the next valid protocol packet from the expected peer.
static int recv_protocol_packet(const ethernet_pc_socket_t *sock,
                                const uint8_t *expected_src,
                                ethernet_packet_header_t *header,
                                uint8_t *payload,
                                uint32_t *payload_len,
                                uint8_t src[kEthernetMacLen]) {
  while (1) {
    uint8_t frame[kEthernetMaxFrameLen];
    struct sockaddr_ll addr;
    socklen_t addr_len = sizeof(addr);
    ssize_t len = recvfrom(sock->fd, frame, sizeof(frame), 0,
                           (struct sockaddr *)&addr, &addr_len);
    if (len < 0) {
      if (errno == EAGAIN || errno == EWOULDBLOCK) {
        return 1;
      }
      perror("recvfrom");
      return -1;
    }

    if (addr.sll_pkttype == PACKET_OUTGOING) {
      continue;
    }
    if (len < kEthernetL2HeaderLen + kEthernetHeaderLen) {
      continue;
    }
    uint16_t ethertype = (uint16_t)(((uint16_t)frame[12] << 8) | frame[13]);
    if (ethertype != kEthernetEthertype) {
      continue;
    }
    if (expected_src != NULL && memcmp(&frame[6], expected_src, kEthernetMacLen) != 0) {
      continue;
    }
    if (ethernet_decode_header(&frame[kEthernetL2HeaderLen],
                               (size_t)(len - kEthernetL2HeaderLen), header) < 0) {
      continue;
    }

    uint32_t body_len = (uint32_t)(len - kEthernetL2HeaderLen - kEthernetHeaderLen);
    if (body_len > kEthernetMaxChunkLen) {
      continue;
    }
    if (src != NULL) {
      memcpy(src, &frame[6], kEthernetMacLen);
    }
    if (payload != NULL && body_len > 0) {
      memcpy(payload, &frame[kEthernetL2HeaderLen + kEthernetHeaderLen], body_len);
    }
    if (payload_len != NULL) {
      *payload_len = body_len;
    }
    return 0;
  }
}

// Send one ACK or ERROR control packet.
static int send_control(const ethernet_pc_socket_t *sock,
                        const uint8_t dst[kEthernetMacLen],
                        uint8_t type,
                        uint8_t code,
                        uint32_t transfer_id,
                        uint32_t sequence,
                        uint32_t offset,
                        uint32_t length,
                        uint32_t crc32) {
  ethernet_packet_header_t header = {
      .type = type,
      .code = code,
      .transfer_id = transfer_id,
      .sequence = sequence,
      .offset = offset,
      .length = length,
      .crc32 = crc32,
  };
  return send_packet(sock, dst, &header, NULL, 0);
}

// Acknowledge receipt of one protocol packet.
static int send_ack(const ethernet_pc_socket_t *sock,
                    const uint8_t dst[kEthernetMacLen],
                    uint32_t transfer_id,
                    uint8_t acked_type,
                    uint32_t sequence) {
  return send_control(sock, dst, kEthernetPacketTypeAck, acked_type, transfer_id, sequence,
                      0, 0, 0);
}

// Report a protocol error to the peer.
static int send_error(const ethernet_pc_socket_t *sock,
                      const uint8_t dst[kEthernetMacLen],
                      uint32_t transfer_id,
                      uint8_t code,
                      uint32_t sequence) {
  return send_control(sock, dst, kEthernetPacketTypeError, code, transfer_id, sequence,
                      0, 0, 0);
}

// Send one packet and retry until its ACK arrives.
static int send_and_wait_ack(const ethernet_pc_socket_t *sock,
                             const ethernet_packet_header_t *header,
                             const uint8_t *data,
                             uint32_t data_len,
                             uint8_t acked_type,
                             uint32_t sequence) {
  for (int attempt = 1; attempt <= kAckRetries; attempt++) {
    if (send_packet(sock, kFpgaMac, header, data, data_len) < 0) {
      return -1;
    }

    while (1) {
      ethernet_packet_header_t rx_header;
      int ret = recv_protocol_packet(sock, kFpgaMac, &rx_header, NULL, NULL, NULL);
      if (ret == 1) {
        printf("[pc] retry type=%u seq=%lu attempt=%d/%d\n", acked_type,
               (unsigned long)sequence, attempt, kAckRetries);
        break;
      }
      if (ret < 0) {
        return -1;
      }
      if (rx_header.transfer_id != header->transfer_id) {
        continue;
      }
      if (rx_header.type == kEthernetPacketTypeAck && rx_header.code == acked_type &&
          rx_header.sequence == sequence) {
        return 0;
      }
      if (rx_header.type == kEthernetPacketTypeError) {
        fprintf(stderr, "[pc] FPGA ERROR code=%u seq=%lu\n", rx_header.code,
                (unsigned long)rx_header.sequence);
        return -1;
      }
    }
  }

  fprintf(stderr, "[pc] no ACK for type=%u seq=%lu\n", acked_type,
          (unsigned long)sequence);
  return -1;
}

// Read the complete input file into memory after size validation.
static int read_input_file(const char *path, uint8_t **data, uint32_t *size) {
  FILE *fp = fopen(path, "rb");
  if (fp == NULL) {
    perror(path);
    return -1;
  }

  if (fseek(fp, 0, SEEK_END) != 0) {
    perror("fseek");
    fclose(fp);
    return -1;
  }
  long file_len = ftell(fp);
  if (file_len < 0) {
    perror("ftell");
    fclose(fp);
    return -1;
  }
  if (file_len > kEthernetMaxFileSize) {
    fprintf(stderr, "[pc] input file too large: %ld bytes > %d bytes\n", file_len,
            kEthernetMaxFileSize);
    fclose(fp);
    return -1;
  }
  rewind(fp);

  size_t alloc_len = (file_len == 0) ? 1u : (size_t)file_len;
  uint8_t *buf = malloc(alloc_len);
  if (buf == NULL) {
    perror("malloc");
    fclose(fp);
    return -1;
  }

  if (file_len > 0) {
    size_t got = fread(buf, 1, (size_t)file_len, fp);
    if (got != (size_t)file_len) {
      fprintf(stderr, "[pc] short read: %zu/%ld\n", got, file_len);
      free(buf);
      fclose(fp);
      return -1;
    }
  }

  fclose(fp);
  *data = buf;
  *size = (uint32_t)file_len;
  return 0;
}

// Write the echoed bytes to the output file.
static int write_output_file(const char *path, const uint8_t *data, uint32_t size) {
  FILE *fp = fopen(path, "wb");
  if (fp == NULL) {
    perror(path);
    return -1;
  }

  if (size > 0) {
    size_t wrote = fwrite(data, 1, size, fp);
    if (wrote != size) {
      fprintf(stderr, "[pc] short write: %zu/%lu\n", wrote, (unsigned long)size);
      fclose(fp);
      return -1;
    }
  }

  if (fclose(fp) != 0) {
    perror("fclose");
    return -1;
  }
  return 0;
}

// Send the input file to the FPGA as START/DATA/END packets.
static int send_file(const ethernet_pc_socket_t *sock,
                     const uint8_t *data,
                     uint32_t file_size,
                     uint32_t transfer_id,
                     uint32_t file_crc32) {
  ethernet_packet_header_t header = {
      .type = kEthernetPacketTypeStart,
      .code = 0,
      .transfer_id = transfer_id,
      .sequence = 0,
      .offset = 0,
      .length = file_size,
      .crc32 = file_crc32,
  };

  printf("[pc] tx START size=%lu crc=0x%08lx\n", (unsigned long)file_size,
         (unsigned long)file_crc32);
  if (send_and_wait_ack(sock, &header, NULL, 0, kEthernetPacketTypeStart, 0) < 0) {
    return -1;
  }

  uint32_t sequence = 0;
  uint32_t offset = 0;
  while (offset < file_size) {
    uint32_t chunk_len = file_size - offset;
    if (chunk_len > kEthernetMaxChunkLen) {
      chunk_len = kEthernetMaxChunkLen;
    }

    header.type = kEthernetPacketTypeData;
    header.code = 0;
    header.sequence = sequence;
    header.offset = offset;
    header.length = chunk_len;
    header.crc32 = ethernet_crc32(&data[offset], chunk_len);
    if (send_and_wait_ack(sock, &header, &data[offset], chunk_len, kEthernetPacketTypeData,
                          sequence) < 0) {
      return -1;
    }

    offset += chunk_len;
    sequence++;
  }

  header.type = kEthernetPacketTypeEnd;
  header.code = 0;
  header.sequence = sequence;
  header.offset = file_size;
  header.length = file_size;
  header.crc32 = file_crc32;
  if (send_and_wait_ack(sock, &header, NULL, 0, kEthernetPacketTypeEnd, sequence) < 0) {
    return -1;
  }

  printf("[pc] tx complete chunks=%lu\n", (unsigned long)sequence);
  return 0;
}

// Receive, verify, and buffer the FPGA echo transfer.
static int receive_echo_file(const ethernet_pc_socket_t *sock,
                             uint32_t transfer_id,
                             uint8_t **data_out,
                             uint32_t *size_out,
                             uint32_t *crc32_out) {
  uint8_t *data = NULL;
  uint32_t file_size = 0;
  uint32_t expected_crc32 = 0;
  uint32_t expected_sequence = 0;
  uint32_t received = 0;
  int timeouts = 0;

  printf("[pc] waiting for echoed START\n");
  while (1) {
    ethernet_packet_header_t header;
    int ret = recv_protocol_packet(sock, kFpgaMac, &header, NULL, NULL, NULL);
    if (ret == 1) {
      timeouts++;
      if (timeouts >= kReceiveTimeouts) {
        fprintf(stderr, "[pc] timeout waiting for echoed START\n");
        return -1;
      }
      continue;
    }
    if (ret < 0) {
      return -1;
    }
    if (header.transfer_id != transfer_id) {
      continue;
    }
    if (header.type == kEthernetPacketTypeError) {
      fprintf(stderr, "[pc] FPGA ERROR code=%u seq=%lu\n", header.code,
              (unsigned long)header.sequence);
      return -1;
    }
    if (header.type != kEthernetPacketTypeStart) {
      continue;
    }
    if (header.length > kEthernetMaxFileSize) {
      (void)send_error(sock, kFpgaMac, transfer_id, kEthernetErrorCodeMalformed,
                       header.sequence);
      return -1;
    }

    file_size = header.length;
    expected_crc32 = header.crc32;
    size_t alloc_len = (file_size == 0) ? 1u : (size_t)file_size;
    data = malloc(alloc_len);
    if (data == NULL) {
      perror("malloc");
      return -1;
    }
    if (send_ack(sock, kFpgaMac, transfer_id, kEthernetPacketTypeStart,
                 header.sequence) < 0) {
      free(data);
      return -1;
    }
    printf("[pc] rx START size=%lu crc=0x%08lx\n", (unsigned long)file_size,
           (unsigned long)expected_crc32);
    break;
  }

  timeouts = 0;
  while (1) {
    uint8_t payload[kEthernetMaxChunkLen];
    uint32_t payload_len = 0;
    ethernet_packet_header_t header;
    int ret = recv_protocol_packet(sock, kFpgaMac, &header, payload, &payload_len, NULL);
    if (ret == 1) {
      timeouts++;
      if (timeouts >= kReceiveTimeouts) {
        fprintf(stderr, "[pc] timeout waiting for echoed data\n");
        (void)send_error(sock, kFpgaMac, transfer_id, kEthernetErrorCodeTimeout,
                         expected_sequence);
        free(data);
        return -1;
      }
      continue;
    }
    if (ret < 0) {
      free(data);
      return -1;
    }
    if (header.transfer_id != transfer_id) {
      continue;
    }

    timeouts = 0;
    if (header.type == kEthernetPacketTypeData) {
      if (header.sequence != expected_sequence || header.offset != received ||
          header.length > kEthernetMaxChunkLen || header.length > payload_len ||
          header.length > file_size - received) {
        fprintf(stderr, "[pc] DATA sequence/range error seq=%lu expected=%lu\n",
                (unsigned long)header.sequence, (unsigned long)expected_sequence);
        (void)send_error(sock, kFpgaMac, transfer_id, kEthernetErrorCodeSequence,
                         header.sequence);
        free(data);
        return -1;
      }

      uint32_t chunk_crc32 = ethernet_crc32(payload, header.length);
      if (chunk_crc32 != header.crc32) {
        fprintf(stderr, "[pc] DATA checksum error seq=%lu\n",
                (unsigned long)header.sequence);
        (void)send_error(sock, kFpgaMac, transfer_id, kEthernetErrorCodeChecksum,
                         header.sequence);
        free(data);
        return -1;
      }

      if (header.length > 0) {
        memcpy(&data[received], payload, header.length);
      }
      received += header.length;
      if (send_ack(sock, kFpgaMac, transfer_id, kEthernetPacketTypeData,
                   header.sequence) < 0) {
        free(data);
        return -1;
      }
      expected_sequence++;
    } else if (header.type == kEthernetPacketTypeEnd) {
      uint32_t actual_crc32 = ethernet_crc32(data, received);
      if (header.sequence != expected_sequence || header.offset != received ||
          header.length != file_size || received != file_size ||
          header.crc32 != expected_crc32 || actual_crc32 != expected_crc32) {
        fprintf(stderr, "[pc] END verification error size=%lu received=%lu got_crc=0x%08lx expected_crc=0x%08lx\n",
                (unsigned long)file_size, (unsigned long)received,
                (unsigned long)actual_crc32, (unsigned long)expected_crc32);
        (void)send_error(sock, kFpgaMac, transfer_id, kEthernetErrorCodeChecksum,
                         header.sequence);
        free(data);
        return -1;
      }

      if (send_ack(sock, kFpgaMac, transfer_id, kEthernetPacketTypeEnd,
                   header.sequence) < 0) {
        free(data);
        return -1;
      }
      printf("[pc] rx complete chunks=%lu size=%lu crc=0x%08lx\n",
             (unsigned long)expected_sequence, (unsigned long)received,
             (unsigned long)actual_crc32);
      *data_out = data;
      *size_out = received;
      *crc32_out = actual_crc32;
      return 0;
    } else if (header.type == kEthernetPacketTypeError) {
      fprintf(stderr, "[pc] FPGA ERROR code=%u seq=%lu\n", header.code,
              (unsigned long)header.sequence);
      free(data);
      return -1;
    } else {
      fprintf(stderr, "[pc] unexpected packet type %u\n", header.type);
      (void)send_error(sock, kFpgaMac, transfer_id, kEthernetErrorCodeMalformed,
                       header.sequence);
      free(data);
      return -1;
    }
  }
}

// Create a lightweight transfer identifier for this run.
static uint32_t make_transfer_id(void) {
  return ((uint32_t)time(NULL) ^ ((uint32_t)getpid() << 16) ^ (uint32_t)getpid());
}

// Print the command-line usage string.
static void usage(const char *prog) {
  fprintf(stderr, "usage: sudo %s <ifname> [tx_file.txt] [rx_file.txt]\n", prog);
}

// Run the PC-side send/receive file transfer tool.
int main(int argc, char **argv) {
  if (argc < 2 || argc > 4) {
    usage(argv[0]);
    return 2;
  }

  const char *ifname = argv[1];
  const char *tx_path = (argc >= 3) ? argv[2] : "tx_file.txt";
  const char *rx_path = (argc >= 4) ? argv[3] : "rx_file.txt";

  uint8_t *tx_data = NULL;
  uint32_t tx_size = 0;
  if (read_input_file(tx_path, &tx_data, &tx_size) < 0) {
    return 1;
  }

  ethernet_pc_socket_t sock;
  if (open_raw_socket(ifname, &sock) < 0) {
    free(tx_data);
    return 1;
  }

  uint32_t transfer_id = make_transfer_id();
  uint32_t tx_crc32 = ethernet_crc32(tx_data, tx_size);
  printf("[pc] iface=%s src=%02x:%02x:%02x:%02x:%02x:%02x dst=%02x:%02x:%02x:%02x:%02x:%02x id=%lu\n",
         ifname, sock.mac[0], sock.mac[1], sock.mac[2], sock.mac[3], sock.mac[4],
         sock.mac[5], kFpgaMac[0], kFpgaMac[1], kFpgaMac[2], kFpgaMac[3], kFpgaMac[4],
         kFpgaMac[5], (unsigned long)transfer_id);

  int status = 1;
  uint8_t *rx_data = NULL;
  uint32_t rx_size = 0;
  uint32_t rx_crc32 = 0;
  if (send_file(&sock, tx_data, tx_size, transfer_id, tx_crc32) == 0 &&
      receive_echo_file(&sock, transfer_id, &rx_data, &rx_size, &rx_crc32) == 0) {
    if (rx_size == tx_size && rx_crc32 == tx_crc32 &&
        (tx_size == 0 || memcmp(tx_data, rx_data, tx_size) == 0)) {
      if (write_output_file(rx_path, rx_data, rx_size) == 0) {
        printf("[pc] wrote %s (%lu bytes)\n", rx_path, (unsigned long)rx_size);
        status = 0;
      }
    } else {
      fprintf(stderr, "[pc] echoed file mismatch\n");
    }
  }

  free(rx_data);
  free(tx_data);
  close(sock.fd);
  return status;
}
