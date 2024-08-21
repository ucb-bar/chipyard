// See LICENSE for license details

#include "uart.h"
#include "core/simif.h"

#include <fcntl.h>
#include <sys/stat.h>

#ifndef _XOPEN_SOURCE
#define _XOPEN_SOURCE
#endif

#include <stdio.h>
#include <stdlib.h>

#ifndef _WIN32
#include <unistd.h>

char uart_t::KIND;

// name length limit for ptys
#define SLAVENAMELEN 256

/* There is no "backpressure" to the user input for sigs. only one at a time
 * non-zero value represents unconsumed special char input.
 *
 * Reset to zero once consumed.
 */

// This is fine for multiple UARTs because UARTs > uart 0 will use pty, not
// stdio
char specialchar = 0;

void sighand(int s) {
  switch (s) {
  case SIGINT:
    // ctrl-c
    specialchar = 0x3;
    break;
  default:
    specialchar = 0x0;
  }
}
#endif

/**
 * Helper class which links the UART stream to primitive streams.
 */
class uart_fd_handler : public uart_handler {
public:
  uart_fd_handler() = default;
  ~uart_fd_handler() override;

  std::optional<char> get() override;
  void put(char data) override;

protected:
  int inputfd;
  int outputfd;
  int loggingfd = 0;
};

uart_fd_handler::~uart_fd_handler() { close(this->loggingfd); }

std::optional<char> uart_fd_handler::get() {
  char inp;
  int readamt;
  if (specialchar) {
    // send special character (e.g. ctrl-c)
    // for stdin handling
    //
    // PTY should never trigger this
    inp = specialchar;
    specialchar = 0;
    readamt = 1;
  } else {
    // else check if we have input
    readamt = ::read(inputfd, &inp, 1);
  }

  if (readamt <= 0)
    return std::nullopt;
  return inp;
}

void uart_fd_handler::put(char data) {
  ::write(outputfd, &data, 1);
  if (loggingfd) {
    ::write(loggingfd, &data, 1);
  }
}

/**
 * UART handler which fetches data from stdin and outputs to stdout.
 */
class uart_stdin_handler final : public uart_fd_handler {
public:
  uart_stdin_handler() {
    // signal handler so ctrl-c doesn't kill simulation when UART is attached
    // to stdin/stdout
    struct sigaction sigIntHandler;
    sigIntHandler.sa_handler = sighand;
    sigemptyset(&sigIntHandler.sa_mask);
    sigIntHandler.sa_flags = 0;
    sigaction(SIGINT, &sigIntHandler, nullptr);
    printf("UART0 is here (stdin/stdout).\n");
    inputfd = STDIN_FILENO;
    outputfd = STDOUT_FILENO;
    // Don't block on reads if there is nothing typed in
    fcntl(inputfd, F_SETFL, fcntl(inputfd, F_GETFL) | O_NONBLOCK);
  }
};

/**
 * UART handler connected to PTY.
 */
class uart_pty_handler final : public uart_fd_handler {
public:
  uart_pty_handler(int uartno) {
    // for UARTs that are not UART0, use a PTY
    char slavename[SLAVENAMELEN];
    int ptyfd = posix_openpt(O_RDWR | O_NOCTTY);
    grantpt(ptyfd);
    unlockpt(ptyfd);
    ptsname_r(ptyfd, slavename, SLAVENAMELEN);

    // create symlink for reliable location to find uart pty
    std::string symlinkname = std::string("uartpty") + std::to_string(uartno);
    // unlink in case symlink already exists
    unlink(symlinkname.c_str());
    symlink(slavename, symlinkname.c_str());
    printf("UART%d is on PTY: %s, symlinked at %s\n",
           uartno,
           slavename,
           symlinkname.c_str());
    printf("Attach to this UART with 'sudo screen %s' or 'sudo screen %s'\n",
           slavename,
           symlinkname.c_str());
    inputfd = ptyfd;
    outputfd = ptyfd;

    // also, for these we want to log output to file here.
    std::string uartlogname = std::string("uartlog") + std::to_string(uartno);
    printf("UART logfile is being written to %s\n", uartlogname.c_str());
    this->loggingfd = open(uartlogname.c_str(), O_RDWR | O_CREAT, 0644);
    // Don't block on reads if there is nothing typed in
    fcntl(inputfd, F_SETFL, fcntl(inputfd, F_GETFL) | O_NONBLOCK);
  }
};

/**
 * UART handler connected to files.
 */
class uart_file_handler final : public uart_fd_handler {
public:
  uart_file_handler(const std::string &in_name, const std::string &out_name) {
    inputfd = open(in_name.c_str(), O_RDONLY);
    outputfd = open(out_name.c_str(), O_WRONLY | O_CREAT, 0644);
  }
};

static std::unique_ptr<uart_handler>
create_handler(const std::vector<std::string> &args, int uartno) {
  std::string in_arg = std::string("+uart-in") + std::to_string(uartno) + "=";
  std::string out_arg = std::string("+uart-out") + std::to_string(uartno) + "=";

  std::string in_name, out_name;
  for (const auto &arg : args) {
    if (arg.find(in_arg) == 0) {
      in_name = const_cast<char *>(arg.c_str()) + in_arg.length();
    }
    if (arg.find(out_arg) == 0) {
      out_name = const_cast<char *>(arg.c_str()) + out_arg.length();
    }
  }

  if (!in_name.empty() && !out_name.empty()) {
    return std::make_unique<uart_file_handler>(in_name, out_name);
  }
  if (uartno == 0) {
    return std::make_unique<uart_stdin_handler>();
  }
  return std::make_unique<uart_pty_handler>(uartno);
}

uart_t::uart_t(simif_t &simif,
               const UARTBRIDGEMODULE_struct &mmio_addrs,
               int uartno,
               const std::vector<std::string> &args)
    : bridge_driver_t(simif, &KIND), mmio_addrs(mmio_addrs),
      handler(create_handler(args, uartno)) {}

uart_t::~uart_t() = default;

void uart_t::send() {
  if (data.in.fire()) {
    write(mmio_addrs.in_bits, data.in.bits);
    write(mmio_addrs.in_valid, data.in.valid);
  }
  if (data.out.fire()) {
    write(mmio_addrs.out_ready, data.out.ready);
  }
}

void uart_t::recv() {
  data.in.ready = read(mmio_addrs.in_ready);
  data.out.valid = read(mmio_addrs.out_valid);
  if (data.out.valid) {
    data.out.bits = read(mmio_addrs.out_bits);
  }
}

void uart_t::tick() {
  data.out.ready = true;
  data.in.valid = false;
  do {
    this->recv();

    if (data.in.ready) {
      if (auto bits = handler->get()) {
        data.in.bits = *bits;
        data.in.valid = true;
      }
    }

    if (data.out.fire()) {
      handler->put(data.out.bits);
    }

    this->send();
    data.in.valid = false;
  } while (data.in.fire() || data.out.fire());
}
