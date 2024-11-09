#include "mem_pool.h"
#include <assert.h>
#include <stdio.h>

#define PAGE_SIZE_BYTES 4096

buffer_t::buffer_t(size_t sz, size_t max_input_sz) {
  size_t remain_bytes = (sz % PAGE_SIZE_BYTES) == 0 ? 0 : PAGE_SIZE_BYTES;
  this->sz = (sz / PAGE_SIZE_BYTES) * PAGE_SIZE_BYTES + remain_bytes;
  this->data = (uint8_t *)aligned_alloc(PAGE_SIZE_BYTES, this->sz);
  this->offset = 0;
  this->max_input_sz = max_input_sz;

  assert(sz >= max_input_sz);
}

buffer_t::~buffer_t() { free(data); }

bool buffer_t::almost_full() { return (sz - offset) < max_input_sz; }

void buffer_t::clear() { offset = 0; }

uint8_t *buffer_t::next_empty() { return (data + offset); }

void buffer_t::fill(size_t amount) {
  assert(offset + amount <= sz);
  offset += amount;
}

uint8_t *buffer_t::get_data() { return data; }

size_t buffer_t::bytes() { return offset; }

mempool_t::mempool_t(int buf_cnt, size_t buf_sz, size_t max_input_sz) {
  this->head = 0;
  this->count = buf_cnt;
  for (int i = 0; i < buf_cnt; i++) {
    this->buffers.push_back(new buffer_t(buf_sz, max_input_sz));
  }
  printf("Allocating a total of %ld Bytes\n", buf_cnt * buf_sz);
}

mempool_t::~mempool_t() {
  for (auto &b : buffers) {
    delete b;
  }
  buffers.clear();
}

bool mempool_t::full() {
  buffer_t *buf = buffers[head];
  return buf->almost_full();
}

uint8_t *mempool_t::next_empty() {
  buffer_t *buf = buffers[head];
  return buf->next_empty();
}

void mempool_t::fill(size_t amount) {
  buffer_t *buf = buffers[head];
  buf->fill(amount);
}

buffer_t *mempool_t::cur_buf() {
  buffer_t *buf = buffers[head];
  return buf;
}

bool mempool_t::next_buffer_full() {
  int next_head = (head + 1) % count;
  buffer_t *next_buffer = buffers[next_head];
  return next_buffer->almost_full();
}

void mempool_t::advance_buffer() {
  head = (head + 1) % count;
  assert(!buffers[head]->almost_full());
}
