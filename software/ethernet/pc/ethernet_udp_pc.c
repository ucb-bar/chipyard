#include "software/ethernet/protocol.h"

#include <arpa/inet.h>
#include <errno.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <unistd.h>

enum {
  kDutPort = 1234,
  kHostPort = 5678,
  kSocketTimeoutSeconds = 1,
  kMaximumRetries = 5,
};

typedef struct {
  int fd;
  struct sockaddr_in dut;
} udp_client_t;

static int send_packet(
    const udp_client_t *client, const ethernet_packet_header_t *header,
    const uint8_t *payload, size_t payload_length) {
  uint8_t packet[kEthernetHeaderLen + kEthernetMaxChunkLen];
  const size_t length = kEthernetHeaderLen + payload_length;
  if (payload_length > kEthernetMaxChunkLen ||
      ethernet_encode_header(packet, length, header) < 0) {
    return -1;
  }
  if (payload_length != 0) {
    memcpy(packet + kEthernetHeaderLen, payload, payload_length);
  }
  const ssize_t sent = sendto(
      client->fd, packet, length, 0,
      (const struct sockaddr *)&client->dut, sizeof(client->dut));
  return sent == (ssize_t)length ? 0 : -1;
}

static int receive_packet(
    const udp_client_t *client, ethernet_packet_header_t *header,
    uint8_t *payload, size_t *payload_length) {
  uint8_t packet[kEthernetHeaderLen + kEthernetMaxChunkLen];
  struct sockaddr_in source;
  socklen_t source_length = sizeof(source);
  const ssize_t received = recvfrom(
      client->fd, packet, sizeof(packet), 0,
      (struct sockaddr *)&source, &source_length);
  if (received < 0) {
    return errno == EAGAIN || errno == EWOULDBLOCK ? 1 : -1;
  }
  if (source.sin_addr.s_addr != client->dut.sin_addr.s_addr ||
      source.sin_port != client->dut.sin_port ||
      received < kEthernetHeaderLen ||
      ethernet_decode_header(packet, (size_t)received, header) < 0) {
    return 2;
  }
  *payload_length = (size_t)received - kEthernetHeaderLen;
  if (*payload_length != 0) {
    memcpy(payload, packet + kEthernetHeaderLen, *payload_length);
  }
  return 0;
}

static int send_control(
    const udp_client_t *client, uint8_t type, uint8_t code,
    uint32_t transfer_id, uint32_t sequence) {
  const ethernet_packet_header_t header = {
      .type = type,
      .code = code,
      .transfer_id = transfer_id,
      .sequence = sequence,
  };
  return send_packet(client, &header, NULL, 0);
}

static int send_with_ack(
    const udp_client_t *client, const ethernet_packet_header_t *header,
    const uint8_t *payload, size_t payload_length) {
  for (int retry = 0; retry <= kMaximumRetries; retry++) {
    if (send_packet(client, header, payload, payload_length) < 0) {
      return -1;
    }
    while (true) {
      ethernet_packet_header_t response;
      uint8_t ignored[kEthernetMaxChunkLen];
      size_t ignored_length = 0;
      const int result = receive_packet(
          client, &response, ignored, &ignored_length);
      if (result == 1) {
        break;
      }
      if (result != 0) {
        if (result < 0) {
          return -1;
        }
        continue;
      }
      if (response.type == kEthernetPacketTypeAck &&
          response.code == header->type &&
          response.transfer_id == header->transfer_id &&
          response.sequence == header->sequence) {
        return 0;
      }
      if (response.type == kEthernetPacketTypeError) {
        return -1;
      }
    }
  }
  return -1;
}

static int send_file(
    const udp_client_t *client, const uint8_t *data, size_t length,
    uint32_t transfer_id, uint32_t crc32) {
  ethernet_packet_header_t header = {
      .type = kEthernetPacketTypeStart,
      .transfer_id = transfer_id,
      .length = (uint32_t)length,
      .crc32 = crc32,
  };
  if (send_with_ack(client, &header, NULL, 0) < 0) {
    return -1;
  }

  size_t offset = 0;
  uint32_t sequence = 0;
  while (offset < length) {
    size_t chunk = length - offset;
    if (chunk > kEthernetMaxChunkLen) {
      chunk = kEthernetMaxChunkLen;
    }
    header.type = kEthernetPacketTypeData;
    header.sequence = sequence;
    header.offset = (uint32_t)offset;
    header.length = (uint32_t)chunk;
    header.crc32 = ethernet_crc32(data + offset, chunk);
    if (send_with_ack(client, &header, data + offset, chunk) < 0) {
      return -1;
    }
    offset += chunk;
    sequence++;
  }

  header.type = kEthernetPacketTypeEnd;
  header.sequence = sequence;
  header.offset = (uint32_t)length;
  header.length = (uint32_t)length;
  header.crc32 = crc32;
  return send_with_ack(client, &header, NULL, 0);
}

static int receive_echo(
    const udp_client_t *client, uint8_t *output, size_t expected_length,
    uint32_t transfer_id, uint32_t expected_crc) {
  size_t received = 0;
  uint32_t expected_sequence = 0;
  bool started = false;

  while (true) {
    ethernet_packet_header_t header;
    uint8_t payload[kEthernetMaxChunkLen];
    size_t payload_length = 0;
    const int result = receive_packet(
        client, &header, payload, &payload_length);
    if (result != 0) {
      if (result == 1) {
        continue;
      }
      if (result < 0) {
        return -1;
      }
      continue;
    }
    if (header.transfer_id != transfer_id) {
      continue;
    }

    if (header.type == kEthernetPacketTypeStart &&
        header.length == expected_length && header.crc32 == expected_crc) {
      started = true;
      (void)send_control(
          client, kEthernetPacketTypeAck, kEthernetPacketTypeStart,
          transfer_id, header.sequence);
    } else if (started && header.type == kEthernetPacketTypeData &&
               header.sequence == expected_sequence &&
               header.offset == received && header.length == payload_length &&
               received + payload_length <= expected_length &&
               ethernet_crc32(payload, payload_length) == header.crc32) {
      memcpy(output + received, payload, payload_length);
      received += payload_length;
      expected_sequence++;
      (void)send_control(
          client, kEthernetPacketTypeAck, kEthernetPacketTypeData,
          transfer_id, header.sequence);
    } else if (started && header.type == kEthernetPacketTypeEnd &&
               header.sequence == expected_sequence &&
               received == expected_length &&
               header.length == expected_length &&
               header.crc32 == expected_crc &&
               ethernet_crc32(output, received) == expected_crc) {
      (void)send_control(
          client, kEthernetPacketTypeAck, kEthernetPacketTypeEnd,
          transfer_id, header.sequence);
      return 0;
    }
  }
}

static int read_input(const char *path, uint8_t **data, size_t *length) {
  FILE *file = fopen(path, "rb");
  if (file == NULL || fseek(file, 0, SEEK_END) != 0) {
    return -1;
  }
  const long file_length = ftell(file);
  if (file_length < 0 || file_length > (long)kEthernetMaxFileSize ||
      fseek(file, 0, SEEK_SET) != 0) {
    fclose(file);
    return -1;
  }
  *data = malloc((size_t)file_length == 0 ? 1 : (size_t)file_length);
  *length = (size_t)file_length;
  const bool ok = *data != NULL &&
                  fread(*data, 1, *length, file) == *length;
  fclose(file);
  return ok ? 0 : -1;
}

int main(int argc, char **argv) {
  if (argc != 4) {
    fprintf(stderr, "usage: %s DUT_IP TX_FILE RX_FILE\n", argv[0]);
    return 2;
  }

  uint8_t *input = NULL;
  size_t length = 0;
  if (read_input(argv[2], &input, &length) < 0) {
    fprintf(stderr, "cannot read input file %s\n", argv[2]);
    return 1;
  }
  uint8_t *echo = malloc(length == 0 ? 1 : length);
  if (echo == NULL) {
    fprintf(stderr, "cannot allocate receive buffer\n");
    free(input);
    return 1;
  }

  udp_client_t client;
  memset(&client, 0, sizeof(client));
  client.fd = socket(AF_INET, SOCK_DGRAM, 0);
  client.dut.sin_family = AF_INET;
  client.dut.sin_port = htons(kDutPort);
  if (client.fd < 0 || inet_pton(AF_INET, argv[1], &client.dut.sin_addr) != 1) {
    return 1;
  }
  struct sockaddr_in local = {
      .sin_family = AF_INET,
      .sin_port = htons(kHostPort),
      .sin_addr.s_addr = htonl(INADDR_ANY),
  };
  struct timeval timeout = {.tv_sec = kSocketTimeoutSeconds};
  if (bind(client.fd, (struct sockaddr *)&local, sizeof(local)) < 0 ||
      setsockopt(client.fd, SOL_SOCKET, SO_RCVTIMEO,
                 &timeout, sizeof(timeout)) < 0) {
    return 1;
  }

  const uint32_t transfer_id = UINT32_C(0x53494d31);
  const uint32_t crc32 = ethernet_crc32(input, length);
  if (send_file(&client, input, length, transfer_id, crc32) < 0 ||
      receive_echo(&client, echo, length, transfer_id, crc32) < 0 ||
      memcmp(input, echo, length) != 0) {
    fprintf(stderr, "UDP file transfer failed\n");
    return 1;
  }

  FILE *output = fopen(argv[3], "wb");
  if (output == NULL || fwrite(echo, 1, length, output) != length ||
      fclose(output) != 0) {
    return 1;
  }
  printf("UDP FILE TRANSFER PASS size=%lu crc=0x%08lx\n",
         (unsigned long)length, (unsigned long)crc32);
  close(client.fd);
  free(echo);
  free(input);
  return 0;
}
