// See LICENSE.Berkeley for license details.

// Generic OpenOCD-compatible remote_bitbang JTAG server. Derived from
// rocket-chip's src/main/resources/csrc/remote_bitbang.cc (originally from the
// Berkeley/Spike riscv-isa-sim project). The only functional change from that
// source is that accept()/execute_command() are non-blocking (they service at
// most one client action per call and return), which is required by the FireSim
// bridge tick model; no target- or bridge-specific logic has been added. The
// referenced LICENSE.Berkeley file is provided at the root of the firechip
// generator (generators/firechip/LICENSE.Berkeley).

#include <arpa/inet.h>
#include <errno.h>
#include <fcntl.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include <algorithm>
#include <cassert>
#include <cstdio>
#include <cstdlib>

#include "remote_bitbang.h"

/////////// remote_bitbang_t

remote_bitbang_t::remote_bitbang_t(uint16_t port) :
  err(0),
  socket_fd(0),
  client_fd(0),
  recv_start(0),
  recv_end(0)
{
  socket_fd = socket(AF_INET, SOCK_STREAM, 0);
  if (socket_fd == -1) {
    fprintf(stderr, "remote_bitbang failed to make socket: %s (%d)\n",
            strerror(errno), errno);
    abort();
  }

  fcntl(socket_fd, F_SETFL, O_NONBLOCK);
  int reuseaddr = 1;
  if (setsockopt(socket_fd, SOL_SOCKET, SO_REUSEADDR, &reuseaddr,
                 sizeof(int)) == -1) {
    fprintf(stderr, "remote_bitbang failed setsockopt: %s (%d)\n",
            strerror(errno), errno);
    abort();
  }

  struct sockaddr_in addr;
  memset(&addr, 0, sizeof(addr));
  addr.sin_family = AF_INET;
  addr.sin_addr.s_addr = INADDR_ANY;
  addr.sin_port = htons(port);

  if (::bind(socket_fd, (struct sockaddr *) &addr, sizeof(addr)) == -1) {
    fprintf(stderr, "remote_bitbang failed to bind socket: %s (%d)\n",
            strerror(errno), errno);
    abort();
  }

  if (listen(socket_fd, 1) == -1) {
    fprintf(stderr, "remote_bitbang failed to listen on socket: %s (%d)\n",
            strerror(errno), errno);
    abort();
  }

  socklen_t addrlen = sizeof(addr);
  if (getsockname(socket_fd, (struct sockaddr *) &addr, &addrlen) == -1) {
    fprintf(stderr, "remote_bitbang getsockname failed: %s (%d)\n",
            strerror(errno), errno);
    abort();
  }

  tck = 1;
  tms = 1;
  tdi = 1;
  trstn = 1;
  quit = 0;

  fprintf(stderr, "This emulator compiled with JTAG Remote Bitbang client. To enable, use +jtag_rbb_enable=1.\n");
  fprintf(stderr, "Listening on port %d\n",
         ntohs(addr.sin_port));
}

void remote_bitbang_t::accept()
{
  client_fd = ::accept(socket_fd, NULL, NULL);
  if (client_fd == -1) {
    if (errno == EAGAIN) {
      // No client waiting; return and let the simulation proceed.
    } else {
      fprintf(stderr, "failed to accept on socket: %s (%d)\n", strerror(errno),
              errno);
      abort();
    }
  } else {
    fcntl(client_fd, F_SETFL, O_NONBLOCK);
    fprintf(stderr, "Accepted successfully.\n");
  }
}

void remote_bitbang_t::tick(
                            unsigned char * jtag_tck,
                            unsigned char * jtag_tms,
                            unsigned char * jtag_tdi,
                            unsigned char * jtag_trstn,
                            unsigned char jtag_tdo
                            )
{
  if (client_fd > 0) {
    tdo = jtag_tdo;
    execute_command();
  } else {
    // client_fd <= 0 means no client connected; try non-blocking accept
    this->accept();
  }

  * jtag_tck = tck;
  * jtag_tms = tms;
  * jtag_tdi = tdi;
  * jtag_trstn = trstn;

}

void remote_bitbang_t::reset(){
  //trstn = 0;
}

void remote_bitbang_t::set_pins(char _tck, char _tms, char _tdi){
  tck = _tck;
  tms = _tms;
  tdi = _tdi;
}

void remote_bitbang_t::execute_command()
{
  char command;
  ssize_t num_read = read(client_fd, &command, sizeof(command));
  if (num_read == -1) {
    if (errno == EAGAIN) {
      // No data available right now; will try on next tick.
      return;
    } else {
      fprintf(stderr, "remote_bitbang failed to read on socket: %s (%d)\n",
              strerror(errno), errno);
      abort();
    }
  } else if (num_read == 0) {
    // Client disconnected; go back to accepting new clients.
    fprintf(stderr, "Remote end disconnected.\n");
    close(client_fd);
    client_fd = -1;
    return;
  }

  int dosend = 0;

  char tosend = '?';

  switch (command) {
  case 'B': /* fprintf(stderr, "*BLINK*\n"); */ break;
  case 'b': /* fprintf(stderr, "_______\n"); */ break;
  case 'r': reset(); break; // This is wrong. 'r' has other bits that indicated TRST and SRST.
  case '0': set_pins(0, 0, 0); break;
  case '1': set_pins(0, 0, 1); break;
  case '2': set_pins(0, 1, 0); break;
  case '3': set_pins(0, 1, 1); break;
  case '4': set_pins(1, 0, 0); break;
  case '5': set_pins(1, 0, 1); break;
  case '6': set_pins(1, 1, 0); break;
  case '7': set_pins(1, 1, 1); break;
  case 'R': dosend = 1; tosend = tdo ? '1' : '0'; break;
  case 'Q': quit = 1; break;
  default:
    fprintf(stderr, "remote_bitbang got unsupported command '%c'\n",
            command);
  }

  if (dosend){
    while (1) {
      ssize_t bytes = write(client_fd, &tosend, sizeof(tosend));
      if (bytes == -1) {
        fprintf(stderr, "failed to write to socket: %s (%d)\n", strerror(errno), errno);
        abort();
      }
      if (bytes > 0) {
        break;
      }
    }
  }

  if (quit) {
    // The remote disconnected.
    fprintf(stderr, "Remote end disconnected\n");
    close(client_fd);
    client_fd = 0;
  }
}
