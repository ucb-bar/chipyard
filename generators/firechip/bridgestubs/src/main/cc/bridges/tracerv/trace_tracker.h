#ifndef __TRACE_TRACKER_H
#define __TRACE_TRACKER_H

#include "tracerv_processing.h"

//#define INDENT_SPACES

class LabelMeta {
public:
  std::string label;
  uint64_t start_cycle;
  uint64_t end_cycle;
  uint64_t indent;
  bool asm_sequence;

  LabelMeta() { this->asm_sequence = false; }

  void pre_print(FILE *tracefile) {
#ifdef INDENT_SPACES
    std::string ind(indent, ' ');
    fprintf(tracefile,
            "%sStart label: %s at %" PRIu64 " cycles.\n",
            ind.c_str(),
            label.c_str(),
            start_cycle);
#else
    fprintf(tracefile,
            "Indent: %" PRIu64 ", Start label: %s, At cycle: %" PRIu64 "\n",
            indent,
            label.c_str(),
            start_cycle);
#endif
  }

  void post_print(FILE *tracefile) {
#ifdef INDENT_SPACES
    std::string ind(indent, ' ');
    fprintf(tracefile,
            "%sEnd label: %s at %" PRIu64 " cycles.\n",
            ind.c_str(),
            label.c_str(),
            end_cycle);
#else
    fprintf(tracefile,
            "Indent: %" PRIu64 ", End label: %s, End cycle: %" PRIu64 "\n",
            indent,
            label.c_str(),
            end_cycle);
#endif
  }
};

class TraceTracker {
private:
  ObjdumpedBinary *bin_dump;
  std::vector<LabelMeta *> label_stack;
  FILE *tracefile;
  Instr *last_instr;

public:
  TraceTracker(std::string binary_with_dwarf, FILE *tracefile);
  void addInstruction(uint64_t inst_addr, uint64_t cycle);
};

#endif // __TRACE_TRACKER_H
