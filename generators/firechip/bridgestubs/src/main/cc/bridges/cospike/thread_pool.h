#ifndef __THREAD_POOL_H__
#define __THREAD_POOL_H__

/* https://stackoverflow.com/questions/15752659/thread-pooling-in-c11 */

#include "mem_pool.h"
#include <condition_variable>
#include <cstdint>
#include <fstream>
#include <functional>
#include <iostream>
#include <mutex>
#include <queue>
#include <string>
#include <thread>
#include <utility>
#include <vector>

// Create bitmask macro
#define BIT_MASK(__ITYPE__, __ONE_COUNT__)                                     \
  (((__ITYPE__)(-((__ONE_COUNT__) != 0))) &                                    \
   (((__ITYPE__)-1) >> ((sizeof(__ITYPE__) * 8) - (__ONE_COUNT__))))

#define TO_BYTES(__BITS__) ((__BITS__) / 8)

#define SHIFT_BITS(__RTYPE__, __BYTE_WIDTH__)                                  \
  ((sizeof(__RTYPE__) - (__BYTE_WIDTH__)) * 8)

#define SIGNED_EXTRACT_NON_ALIGNED(                                            \
    __ITYPE__, __RTYPE__, __BUF__, __BYTE_WIDTH__, __BYTE_OFFSET__)            \
  (*((__ITYPE__ *)((__BUF__) + (__BYTE_OFFSET__))) &                           \
   BIT_MASK(__RTYPE__, (__BYTE_WIDTH__)*8))

#define EXTRACT_ALIGNED(                                                       \
    __ITYPE__, __RTYPE__, __BUF__, __BYTE_WIDTH__, __BYTE_OFFSET__)            \
  ((((__ITYPE__)SIGNED_EXTRACT_NON_ALIGNED(                                    \
        __ITYPE__, __RTYPE__, __BUF__, __BYTE_WIDTH__, __BYTE_OFFSET__))       \
    << SHIFT_BITS(__RTYPE__, __BYTE_WIDTH__)) >>                               \
   SHIFT_BITS(__RTYPE__, __BYTE_WIDTH__))

struct trace_cfg_t {
  // in bytes
  uint32_t _time_width;
  uint32_t _valid_width;
  uint32_t _iaddr_width;
  uint32_t _insn_width;
  uint32_t _wdata_width;
  uint32_t _priv_width;
  uint32_t _exception_width;
  uint32_t _interrupt_width;
  uint32_t _cause_width;
  uint32_t _tval_width;

  // in bytes
  uint32_t _time_offset;
  uint32_t _valid_offset;
  uint32_t _iaddr_offset;
  uint32_t _insn_offset;
  uint32_t _wdata_offset;
  uint32_t _priv_offset;
  uint32_t _exception_offset;
  uint32_t _interrupt_offset;
  uint32_t _cause_offset;
  uint32_t _tval_offset;

  uint32_t _bits_per_trace;

  int _hartid;

  void init(uint32_t time_width,
            uint32_t valid_width,
            uint32_t iaddr_width,
            uint32_t insn_width,
            uint32_t exception_width,
            uint32_t interrupt_width,
            uint32_t cause_width,
            uint32_t wdata_width,
            uint32_t priv_width,
            uint32_t bits_per_trace,
            int hartid) {
    _time_width = time_width;
    _valid_width = valid_width;
    _iaddr_width = iaddr_width;
    _insn_width = insn_width;
    _exception_width = exception_width;
    _interrupt_width = interrupt_width;
    _cause_width = cause_width;
    _wdata_width = wdata_width;
    _priv_width = priv_width;

    // must align with how the trace is composed
    _time_offset = 0;
    _valid_offset = _time_offset + _time_width;
    _iaddr_offset = _valid_offset + _valid_width;
    _insn_offset = _iaddr_offset + _iaddr_width;
    _priv_offset = _insn_offset + _insn_width;
    _exception_offset = _priv_offset + _priv_width;
    _interrupt_offset = _exception_offset + _exception_width;
    _cause_offset = _interrupt_offset + _interrupt_width;
    _wdata_offset = _cause_offset + _cause_width;

    _bits_per_trace = bits_per_trace;
    _hartid = hartid;
  }

  void print() {
    printf("trace_cfg_t: %d %d %d %d %d %d %d %d %d %d\n",
           _time_width,
           _valid_width,
           _iaddr_width,
           _insn_width,
           _exception_width,
           _interrupt_width,
           _cause_width,
           _wdata_width,
           _priv_width,
           _bits_per_trace);
  }
};

struct trace_t {
  buffer_t *buf;
  trace_cfg_t cfg;
};

template <class T, class S>
class threadpool_t {
  using job_t = std::function<void(T, S)>;

public:
  void start(uint32_t max_concurrency) {
    const uint32_t num_threads = std::max(
        std::thread::hardware_concurrency() / 16,
        std::min(std::thread::hardware_concurrency(), max_concurrency));
    for (uint32_t ii = 0; ii < num_threads; ++ii) {
      threads.emplace_back(std::thread(&threadpool_t::threadloop, this));
    }
  }

  void queue_job(const job_t &job, const T &trace, S &oname) {
    {
      std::unique_lock<std::mutex> lock(queue_mutex);
      jobs.push(job);
      traces.push(trace);
      ofnames.push(oname);
    }
    mutex_condition.notify_one();
  }

  void stop() {
    {
      std::unique_lock<std::mutex> lock(queue_mutex);
      should_terminate = true;
    }
    mutex_condition.notify_all();
    for (std::thread &active_thread : threads) {
      active_thread.join();
    }
    threads.clear();
  }

  bool busy() {
    bool poolbusy;
    {
      std::unique_lock<std::mutex> lock(queue_mutex);
      poolbusy = !jobs.empty();
    }
    return poolbusy;
  }

private:
  void threadloop() {
    while (true) {
      job_t job;
      T trace;
      S oname;
      {
        std::unique_lock<std::mutex> lock(queue_mutex);
        mutex_condition.wait(
            lock, [this] { return !jobs.empty() || should_terminate; });
        if (should_terminate) {
          return;
        }
        job = jobs.front();
        jobs.pop();

        trace = traces.front();
        traces.pop();

        oname = ofnames.front();
        ofnames.pop();
      }
      job(trace, oname);
    }
  }

  bool should_terminate = false; // Tells threads to stop looking for jobs
  std::mutex queue_mutex;        // Prevents data races to the job queue
  std::condition_variable
      mutex_condition; // Allows threads to wait on new jobs or termination
  std::vector<std::thread> threads;
  std::queue<job_t> jobs;
  std::queue<T> traces;
  std::queue<S> ofnames;
};

void print_insn_logs(trace_t trace, const std::string &oname);
void print_buf(buffer_t *buf, const std::string &ofname);

#endif //__THREAD_POOL_H__
