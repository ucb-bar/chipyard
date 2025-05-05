// See LICENSE for license details

#include "ctc.h"
#include "core/simif.h"

#include <fcntl.h>
#include <sys/stat.h>

#include <cassert>

#include <stdio.h>
#include <stdlib.h>

#include <unistd.h>

char ctc_t::KIND;

// "Serial" tilelink

ctc_t::ctc_t(simif_t &simif,
                    const CTCBRIDGEMODULE_struct &mmio_addrs,
                    int chipno, // maybe this is a stupid naming scheme
                    const std::vector<std::string> &args)
    : bridge_driver_t(simif, &KIND), mmio_addrs(mmio_addrs) {

  // Read plusargs
  std::string macaddr_arg = std::string("+macaddr") + std::to_string(chipno) + std::string("=");

  // Borrowed from simplenic.cc
  for (auto &arg : args) {
    if (arg.find(macaddr_arg) == 0) {
      int mac_octets[6];
      char *macstring = nullptr;
      macstring = const_cast<char *>(arg.c_str()) + macaddr_arg.length();
      char trailingjunk;

      // convert mac address from string to 48 bit int
      if (6 == sscanf(macstring,
                      "%x:%x:%x:%x:%x:%x%c",
                      &mac_octets[0],
                      &mac_octets[1],
                      &mac_octets[2],
                      &mac_octets[3],
                      &mac_octets[4],
                      &mac_octets[5],
                      &trailingjunk)) {
        chip_id = mac_octets[5] - 2;
      } else {
        fprintf(stderr, "INVALID MAC ADDRESS SUPPLIED WITH +macaddrN=\n");
        chip_id = 0;
      }
    }
  }

  const std::string num_equals = std::to_string(chip_id) + std::string("=");
  //const std::string num_equals1 = std::to_string(chip1no) + std::string("=");
  const std::string chip1_arg = std::string("+connectid") + num_equals;
  std::string chip1no = "";

  for (auto &arg : args) {
    if(arg.find(chip1_arg) == 0) {
      printf("CHIP%d: got connectid argstr%s\n", chip_id, arg.c_str());
      chip1no = const_cast<char *>(arg.c_str()) + chip1_arg.length();
      printf("CHIP%d: got connectid %s\n", chip_id, chip1no.c_str());
      chip1_id = std::stoi(chip1no, nullptr, 0);
    }
  }

  const std::string chip0fifo_arg = std::string("+fifofile") + num_equals;
  const std::string chip1fifo_arg = std::string("+fifofile") + chip1no + std::string("=");

  fifo0_path = "";
  fifo1_path = "";
  // fifo0_fd = 0
  // fifo1_fd = 0

  for (auto &arg : args) {
    if(arg.find(chip0fifo_arg) == 0) {
      fifo0_path = const_cast<char *>(arg.c_str()) + chip0fifo_arg.length();
    }
    if(arg.find(chip1fifo_arg) == 0) {
      fifo1_path = const_cast<char *>(arg.c_str()) + chip1fifo_arg.length();
    }
  }

  printf("CHIP%d: got fifo0 path %s\n", chip_id, fifo0_path.c_str());
  printf("CHIP%d: got fifo1 path %s\n", chip_id, fifo1_path.c_str());

  fifo0_path = fifo0_path + std::string("fifo") + std::to_string(chip_id);
  fifo1_path = fifo1_path + std::string("fifo") + chip1no;

  printf("CHIP%d: got fifo0 file %s\n", chip_id, fifo0_path.c_str());
  printf("CHIP%d: got fifo1 file %s\n", chip_id, fifo1_path.c_str());

  mkfifo(fifo0_path.c_str(), 0666);
}

// Uh idk if I need this anymore...
void ctc_t::init() {
  // Open my fifo as RO
  // fifo0_fd = open(fifo0_path.c_str(), O_RDONLY);
  // printf("CHIP%d: opened fifo0", chip_id);
  // // Open the other chip's fifo as WO
  // fifo1_fd = open(fifo1_path.c_str(), O_WRONLY);
  // printf("CHIP%d: opened fifo1", chip_id);

  // Switch order to prevent deadlock?
  if (chip_id > chip1_id) {
    fifo0_fd = open(fifo0_path.c_str(), O_RDONLY);
    printf("CHIP%d: opened rd fifo0", chip_id);
    fifo1_fd = open(fifo1_path.c_str(), O_WRONLY);
    printf("CHIP%d: opened wr fifo1", chip_id);
  } else {
    fifo1_fd = open(fifo1_path.c_str(), O_WRONLY);
    printf("CHIP%d: opened wr fifo1", chip_id);
    fifo0_fd = open(fifo0_path.c_str(), O_RDONLY);
    printf("CHIP%d: opened rd fifo0", chip_id);
  }

  assert(fifo0_fd != -1 && "fifofile0 couldn't be opened\n");
  assert(fifo1_fd != -1 && "fifofile1 couldn't be opened\n");
}

void ctc_t::tick() {

  printf("[CTC] Inside tick\n");

  // NEW IMPLEMENTATION
  // Pulsify is super nice and makes my life EZ

  // MMIO READING (ex. client_out, manager_out)
  // Check if client_out_valid is high. 
    // If so, read client_out_bits
    //        set client_out_ready
    // ELSE set the buf to 0, or whatever
  // Write to the out fifo
  // MMIO WRITING (ex. client_in, manager_in)
  // Read everything out of the in fifo
  // If valid is high
    // If ready is low
      // PANIC!!! THIS IS SUPER BAD, but you just have to die
    // Write bits to in_bits MMIO
  
  // NOTE: We do not need to send any ready signals because the bridge handles this :)

  // Read RO (or "output") mmios and cast everything into a char array, should be 12 chars total
  // ALWAYS write to because the other chip must read from my fifo. This is the easiest way to synchronize.
  uint32_t buf_out[4]; // 0: cl_valid, 1: cl_ready, 2: cl_bits, 3: man_valid, 4: man_ready, 5: man_bits

  // If valid is high, read bits to send and assert ready
  buf_out[0] = bridge_driver_t::read(mmio_addrs.client_out_valid);
  if (buf_out[0]) {
    buf_out[1] = bridge_driver_t::read(mmio_addrs.client_out_bits);
    printf("[CTC] client_out valid - read mmio bits = %d\n", buf_out[1]);
    bridge_driver_t::write(mmio_addrs.client_out_ready, 1);
  } else {
    buf_out[1] = 0; // Send nothing
  }

  // Check valid high for manager
  buf_out[2] = bridge_driver_t::read(mmio_addrs.manager_out_valid);
  if (buf_out[2]) {
    buf_out[3] = bridge_driver_t::read(mmio_addrs.manager_out_bits);
    printf("[CTC] manager_out valid - read mmio bits = %d\n", buf_out[3]);
    bridge_driver_t::write(mmio_addrs.manager_out_ready, 1);
  } else {
    buf_out[3] = 0;
  }

  //buf_out[1] = bridge_driver_t::read(mmio_addrs.client_in_ready);
  //buf_out[2] = bridge_driver_t::read(mmio_addrs.client_out_bits);
  //buf_out[4] = bridge_driver_t::read(mmio_addrs.manager_in_ready);
  //buf_out[5] = bridge_driver_t::read(mmio_addrs.manager_out_bits);

  // printf("[CTC] read mmio\n");

  // Write entire "out" char array to the other chips's fifo
  int bytes_written = ::write(fifo1_fd, buf_out, sizeof(buf_out));
  if (bytes_written != sizeof(buf_out)) {
    printf("[CTC] Writing to fifo failed.\n");
    exit(1);
  }

  printf("[CTC] Wrote fifo\n");

  // Read my own fifo until I read all the "in" chars
  uint32_t buf_in[4];
  int bytes_read = ::read(fifo0_fd, buf_in, sizeof(buf_in));
  if (bytes_read != sizeof(buf_in)) {
    printf("[CTC] Reading from fifo failed.\n");
    for (int i=0; i<4; i++) {
      printf("Buf[%d] got: %d", i, buf_in[i]);
    }
    exit(1); // HOW DO I DO THIS FOR REAL
  }

  printf("[CTC] Read fifo\n");

  // Other client writes to my manager
  uint32_t manager_in_ready = bridge_driver_t::read(mmio_addrs.manager_in_ready);
  if (buf_in[0]) { // If client_in valid
    if (manager_in_ready) {
      printf("[CTC] manager_in valid - write mmio bits = %d\n", buf_in[1]);
      bridge_driver_t::write(mmio_addrs.manager_in_bits,  buf_in[1]);
      bridge_driver_t::write(mmio_addrs.manager_in_valid, 1);
    } else {
      printf("ERROR: manager_in_ready is LOW");
    }
  }

  // Other manager writes to my client
  uint32_t client_in_ready = bridge_driver_t::read(mmio_addrs.client_in_ready);
  if (buf_in[2]) { // If client_in valid
    if (client_in_ready) {
      printf("[CTC] client_in valid - write mmio bits = %d\n", buf_in[3]);
      bridge_driver_t::write(mmio_addrs.client_in_bits,  buf_in[3]);
      bridge_driver_t::write(mmio_addrs.client_in_valid, 1);
    } else {
      printf("ERROR: client_in_ready is LOW");
    }
  }

  // Write "in" chars to mmio
  // bridge_driver_t::write(mmio_addrs.client_in_valid,   buf_in[0]);
  // bridge_driver_t::write(mmio_addrs.client_out_ready,  buf_in[1]);
  // bridge_driver_t::write(mmio_addrs.client_in_bits,    buf_in[2]);
  // bridge_driver_t::write(mmio_addrs.manager_in_valid,  buf_in[3]);
  // bridge_driver_t::write(mmio_addrs.manager_out_ready, buf_in[4]);
  // bridge_driver_t::write(mmio_addrs.manager_in_bits,   buf_in[5]);

  printf("[CTC] leaving tick\n");
}

void ctc_t::finish() {
  // Close the fifos
  close(fifo0_fd);
  close(fifo1_fd);
}


