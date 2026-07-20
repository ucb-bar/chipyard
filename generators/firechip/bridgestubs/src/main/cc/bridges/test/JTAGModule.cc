// See LICENSE for license details.

// Functional smoke test for the direct-JTAG bridge. A host-side remote_bitbang
// client (running in a helper thread) connects to the bridge's server, drives
// the target TAP through a Test-Logic-Reset and a Shift-DR, and shifts out the
// 32-bit IDCODE. The JTAGDUT wires the bridge pins to a real Rocket-Chip JTAG
// TAP whose only data register is IDCODE (version/part/mfr = 0), so a correct
// end-to-end path returns exactly 0x00000001. The sequence is run twice to
// catch dropped, duplicated, or stale commands, then the client disconnects
// cleanly.

#include "BridgeHarness.h"

#include "bridges/peek_poke.h"
#include "core/bridge_driver.h"

#include <arpa/inet.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <unistd.h>

#include <atomic>
#include <cerrno>
#include <chrono>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <string>
#include <thread>

namespace {

// Must match jtagbridge_t's default (DEFAULT_RBB_PORT + jtagno, jtagno == 0).
constexpr int kDefaultRbbPort = 25000;
constexpr uint32_t kExpectedIdcode = 0x00000001;
// Wall-clock ceilings so a broken path fails instead of hanging forever.
constexpr int kConnectTimeoutSec = 30;
constexpr int kRecvTimeoutSec = 30;

/// Minimal OpenOCD-style remote_bitbang client. One ASCII byte per host op:
///   '0'..'7' -> set pins, byte = '0' + (tck<<2 | tms<<1 | tdi)
///   'R'      -> sample TDO, server replies one byte '0'/'1'
///   'Q'      -> quit
class RemoteBitbangClient {
public:
  bool connect_with_retry(int port) {
    const auto deadline =
        std::chrono::steady_clock::now() + std::chrono::seconds(kConnectTimeoutSec);
    while (std::chrono::steady_clock::now() < deadline) {
      fd_ = ::socket(AF_INET, SOCK_STREAM, 0);
      if (fd_ < 0)
        return fail("socket() failed");

      struct sockaddr_in addr;
      memset(&addr, 0, sizeof(addr));
      addr.sin_family = AF_INET;
      addr.sin_port = htons(port);
      inet_pton(AF_INET, "127.0.0.1", &addr.sin_addr);

      if (::connect(fd_, (struct sockaddr *)&addr, sizeof(addr)) == 0) {
        int one = 1;
        setsockopt(fd_, IPPROTO_TCP, TCP_NODELAY, &one, sizeof(one));
        struct timeval tv;
        tv.tv_sec = kRecvTimeoutSec;
        tv.tv_usec = 0;
        setsockopt(fd_, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));
        return true;
      }
      ::close(fd_);
      fd_ = -1;
      std::this_thread::sleep_for(std::chrono::milliseconds(50));
    }
    return fail("could not connect to remote_bitbang server");
  }

  // Drive a full reset + Shift-DR IDCODE read; returns false on I/O failure.
  bool read_idcode(uint32_t &idcode) {
    // Test-Logic-Reset: >=5 TCK with TMS high reaches TLR from any state.
    for (int i = 0; i < 6; i++)
      if (!clock(1, 0))
        return false;
    // TLR -> Run-Test/Idle -> Select-DR -> Capture-DR -> Shift-DR.
    if (!clock(0, 0) || !clock(1, 0) || !clock(0, 0) || !clock(0, 0))
      return false;
    // Shift 32 bits LSB-first; hold TMS low except on the final bit.
    idcode = 0;
    for (int i = 0; i < 32; i++) {
      int bit = 0;
      if (!shift_bit(/*tms=*/i == 31 ? 1 : 0, /*tdi=*/0, bit))
        return false;
      idcode |= (static_cast<uint32_t>(bit) << i);
    }
    // Update-DR -> Run-Test/Idle.
    return clock(1, 0) && clock(0, 0);
  }

  bool quit() { return send_byte('Q'); }

  void close_socket() {
    if (fd_ >= 0) {
      ::close(fd_);
      fd_ = -1;
    }
  }

  const std::string &error() const { return error_; }

private:
  bool pins(int tck, int tms, int tdi) {
    const char c = static_cast<char>('0' + ((tck & 1) << 2 | (tms & 1) << 1 | (tdi & 1)));
    return send_byte(c);
  }

  bool clock(int tms, int tdi) { return pins(0, tms, tdi) && pins(1, tms, tdi); }

  // In a Shift state: sample TDO while TCK is low, then pulse TCK high.
  bool shift_bit(int tms, int tdi, int &bit_out) {
    if (!pins(0, tms, tdi))
      return false;
    if (!read_tdo(bit_out))
      return false;
    return pins(1, tms, tdi);
  }

  bool send_byte(char c) {
    ssize_t n = ::send(fd_, &c, 1, 0);
    if (n != 1)
      return fail("send() failed");
    return true;
  }

  bool read_tdo(int &bit_out) {
    if (!send_byte('R'))
      return false;
    char reply = 0;
    ssize_t n = ::recv(fd_, &reply, 1, 0);
    if (n != 1)
      return fail("recv() of TDO failed or timed out");
    bit_out = (reply == '1') ? 1 : 0;
    return true;
  }

  bool fail(const char *msg) {
    if (error_.empty())
      error_ = std::string(msg) + " (errno=" + std::to_string(errno) + ")";
    return false;
  }

  int fd_ = -1;
  std::string error_;
};

} // namespace

class JTAGModuleTest final : public BridgeHarness {
public:
  JTAGModuleTest(widget_registry_t &registry, const std::vector<std::string> &args)
      : BridgeHarness(registry, args) {
    for (const auto &arg : args) {
      constexpr char kPrefix[] = "+jtag_rbb_port=";
      if (arg.rfind(kPrefix, 0) == 0)
        rbb_port_ = std::atoi(arg.c_str() + strlen(kPrefix));
    }
  }

  int simulation_run() override {
    auto &peek_poke = registry.get_widget<peek_poke_t>();

    // Reset the DUT.
    peek_poke.poke("reset", 1, /*blocking=*/true);
    peek_poke.step(1, /*blocking=*/true);
    peek_poke.poke("reset", 0, /*blocking=*/true);
    peek_poke.step(1, /*blocking=*/true);

    // Advance the target for the duration of the test (non-blocking).
    peek_poke.step(get_step_limit(), /*blocking=*/false);

    // Run the host JTAG client concurrently with the bridge tick loop.
    std::thread client([this] { client_body(); });

    unsigned ticks = 0;
    const unsigned tick_limit = get_tick_limit();
    while (ticks < tick_limit && !client_done_.load(std::memory_order_acquire) &&
           !peek_poke.is_done()) {
      for (auto &bridge : registry.get_all_bridges())
        bridge->tick();
      ++ticks;
    }
    client.join();

    if (!client_ok_) {
      fprintf(stderr, "[JTAGModuleTest] FAILED: %s\n", client_error_.c_str());
      return EXIT_FAILURE;
    }
    fprintf(stderr, "[JTAGModuleTest] PASSED: IDCODE=0x%08x read twice\n", idcode0_);
    return EXIT_SUCCESS;
  }

private:
  unsigned get_step_limit() const override { return 2000000; }
  unsigned get_tick_limit() const override { return 2000000; }

  void client_body() {
    RemoteBitbangClient client;
    auto finish = [&](bool ok, const std::string &msg) {
      client_ok_ = ok;
      if (!ok)
        client_error_ = msg.empty() ? client.error() : msg;
      client.close_socket();
      client_done_.store(true, std::memory_order_release);
    };

    if (!client.connect_with_retry(rbb_port_)) {
      finish(false, client.error());
      return;
    }

    uint32_t id0 = 0, id1 = 0;
    if (!client.read_idcode(id0) || !client.read_idcode(id1)) {
      finish(false, client.error());
      return;
    }
    idcode0_ = id0;
    idcode1_ = id1;

    // Clean shutdown regardless of result: tell the server to quit.
    client.quit();

    if (id0 != id1) {
      finish(false, "IDCODE not stable across two reads (" + hex(id0) + " vs " + hex(id1) + ")");
      return;
    }
    if ((id0 & 1u) != 1u) {
      finish(false, "IDCODE LSB is 0 (IEEE 1149.1 requires bit0 == 1): " + hex(id0));
      return;
    }
    if (id0 == 0u || id0 == 0xffffffffu) {
      finish(false, "IDCODE all-zero/all-one (TAP not responding): " + hex(id0));
      return;
    }
    if (id0 != kExpectedIdcode) {
      finish(false, "IDCODE " + hex(id0) + " != expected " + hex(kExpectedIdcode));
      return;
    }
    finish(true, "");
  }

  static std::string hex(uint32_t v) {
    char buf[16];
    snprintf(buf, sizeof(buf), "0x%08x", v);
    return std::string(buf);
  }

  int rbb_port_ = kDefaultRbbPort;
  std::atomic<bool> client_done_{false};
  bool client_ok_ = false;
  std::string client_error_;
  uint32_t idcode0_ = 0;
  uint32_t idcode1_ = 0;
};

TEST_MAIN(JTAGModuleTest)
