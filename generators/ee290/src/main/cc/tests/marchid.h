#ifndef MARCHID_H
#define MARCHID_H

const char* get_march(size_t marchid) {
  switch (marchid) {
  case 1:
    return "rocket";
  case 2:
    return "sonicboom";
  case 5:
    return "spike";
  default:
    return "unknown";
  }
}

#endif
