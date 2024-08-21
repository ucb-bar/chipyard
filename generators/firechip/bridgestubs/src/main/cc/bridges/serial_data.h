#ifndef __SERIAL_DATA_H
#define __SERIAL_DATA_H

template <class T>
struct serial_data_t {
  struct {
    T bits;
    bool valid;
    bool ready;
    bool fire() { return valid && ready; }
  } in;
  struct {
    T bits;
    bool ready;
    bool valid;
    bool fire() { return valid && ready; }
  } out;
};
#endif // __SERIAL_DATA_H
