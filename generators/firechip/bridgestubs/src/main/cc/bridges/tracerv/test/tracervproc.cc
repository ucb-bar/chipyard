#include "../../tracerv_processing.h"

int main(int argc, char *argv[]) {
  ObjdumpedBinary bin((argc > 1) ? argv[1]
                                 : "../../../../../../target-design/chipyard/"
                                   "software/firemarshal/riscv-linux/vmlinux");
}
