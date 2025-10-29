#include <vpi_user.h>
#include <svdpi.h>

#include <stdio.h>
#include <string.h>
#include <map>

#include "uart.h"

std::map<int, uart_t*> uarts;

extern "C" void uart_init(
        const char *filename,
        int uartno,
        int forcepty)
{
  bool use_pty = forcepty;
  s_vpi_vlog_info vinfo;
  if (!vpi_get_vlog_info(&vinfo))
    abort();
  for (int i = 1; i < vinfo.argc; i++) {
    std::string arg(vinfo.argv[i]);
    if (arg == "+uart-pty")
      use_pty = true;
  }
  if (uarts.find(uartno) != uarts.end()) {
    printf("Attempting to initialize multiple uarts with same uartno=%d, aborting\n", uartno);
    abort();
  }
  if (strlen(filename) != 0)
    uarts[uartno] = new uart_t(filename, uartno, use_pty);
  else
    uarts[uartno] = new uart_t(0, uartno, use_pty);
}

extern "C" void uart_tick(
                          int uartno,
                          unsigned char out_valid,
                          unsigned char *out_ready,
                          char out_bits,
                          unsigned char *in_valid,
                          unsigned char in_ready,
                          char *in_bits)
{
  uart_t* uart = uarts[uartno];
  if (uart == NULL) {
    *out_ready = 0;
    *in_valid = 0;
    return;
  }

  uart->tick(
             out_valid,
             out_ready,
             out_bits,

             in_valid,
             in_ready,
             in_bits);
}
