// See LICENSE for license details

#include "tacit.h"
#include "core/simif.h"

#include <fcntl.h>
#include <sys/stat.h>

#include <stdio.h>
#include <stdlib.h>

char tacit_t::KIND;

class tacit_handler_impl final : public tacit_handler {
public:
  tacit_handler_impl(int tacitno, int tacitlogfd) {
    this->tacitlogfd = tacitlogfd;
  }
protected:
  int tacitlogfd;

  void put(uint8_t data) override;
};

void tacit_handler_impl::put(uint8_t data) {
  write(tacitlogfd, &data, 1);
}

static std::unique_ptr<tacit_handler>
create_handler(const std::vector<std::string> &args, int tacitno) {
  // open a new file for dumping the bytes
  std::string tacitlogname = std::string("tacit") + std::to_string(tacitno) + ".out";
  int tacitlogfd = open(tacitlogname.c_str(), O_WRONLY | O_CREAT, 0644);
  if (tacitlogfd == -1) {
    fprintf(stderr, "Failed to open TACIT%d log file: %s\n", tacitno, tacitlogname.c_str());
    return nullptr;
  }
  return std::make_unique<tacit_handler_impl>(tacitno, tacitlogfd);
}

tacit_t::tacit_t(simif_t &simif,
                 const TACITBRIDGEMODULE_struct &mmio_addrs,
                 int tacitno,
                 const std::vector<std::string> &args)
    : bridge_driver_t(simif, &KIND), mmio_addrs(mmio_addrs),
      handler(create_handler(args, tacitno)) {}

tacit_t::~tacit_t() = default;

void tacit_t::recv() {
  data.out.valid = read(mmio_addrs.out_valid);
  if (data.out.valid) {
    data.out.bits = read(mmio_addrs.out_bits);
  }
}

void tacit_t::tick() {
  data.out.ready = true;
  do {
    this->recv();

    if (data.out.fire()) {
      handler->put(data.out.bits);
    }
  } while (data.out.fire());
}