#ifndef __MEM_POOL_H__
#define __MEM_POOL_H__

#include <inttypes.h>
#include <stdlib.h>
#include <vector>

class buffer_t {
public:
  buffer_t(size_t sz, size_t max_input_sz);
  ~buffer_t();

  bool almost_full();
  void clear();
  uint8_t *next_empty();
  void fill(size_t amount);
  uint8_t *get_data();
  size_t bytes();

private:
  size_t max_input_sz;
  size_t sz;
  size_t offset;
  uint8_t *data;
};

class mempool_t {
public:
  mempool_t(int buf_cnt, size_t buf_sz, size_t max_input_sz);
  ~mempool_t();

  bool full();
  uint8_t *next_empty();
  void fill(size_t amount);
  buffer_t *cur_buf();
  bool next_buffer_full();
  void advance_buffer();

private:
  int count;
  int head;
  std::vector<buffer_t *> buffers;
};

#endif //__MEM_POOL_H__
