#ifndef __TESTCHIP_UART_TSI_H
#include "testchip_tsi.h"

class testchip_uart_tsi_t : public testchip_tsi_t
{
public:
  testchip_uart_tsi_t(int argc, char** argv, char* tty,
		      uint64_t baud_rate,
		      bool verbose, bool do_self_check);
  virtual ~testchip_uart_tsi_t() {};

  bool handle_uart();
  bool check_connection();
  void load_program() override;
  void write_chunk(addr_t taddr, size_t nbytes, const void* src) override;

private:
  int ttyfd;
  std::deque<uint8_t> read_bytes;
  bool verbose;
  bool in_load_program;
  bool do_self_check;
};
#endif

