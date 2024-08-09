#include "trace_tracker.h"

//#define TRACETRACKER_LOG_PC_REGION

TraceTracker::TraceTracker(std::string binary_with_dwarf, FILE *tracefile) {
  this->bin_dump = new ObjdumpedBinary(binary_with_dwarf);
  this->tracefile = tracefile;
}

void TraceTracker::addInstruction(uint64_t inst_addr, uint64_t cycle) {
  Instr *this_instr = this->bin_dump->getInstrFromAddr(inst_addr);

#ifdef TRACETRACKER_LOG_PC_REGION
  if (!this_instr) {
    fprintf(
        this->tracefile, "addr:%" PRIx64 ", fn:%s\n", inst_addr, "USERSPACE");
  } else {
    fprintf(this->tracefile,
            "addr:%" PRIx64 ", fn:%s\n",
            inst_addr,
            this_instr->function_name.c_str());
  }
  return;
#endif

  if (!this_instr) {
    if ((label_stack.size() == 1) &&
        (std::string("USERSPACE_ALL")
             .compare(label_stack[label_stack.size() - 1]->label) == 0)) {
      LabelMeta *last_label = label_stack[label_stack.size() - 1];
      last_label->end_cycle = cycle;
    } else {
      while (label_stack.size() > 0) {
        LabelMeta *pop_label = label_stack[label_stack.size() - 1];
        label_stack.pop_back();
        pop_label->post_print(this->tracefile);
        delete pop_label;
        if (label_stack.size() > 0) {
          LabelMeta *last_label = label_stack[label_stack.size() - 1];
          last_label->end_cycle = cycle;
        }
      }
      LabelMeta *new_label = new LabelMeta();
      new_label->label = std::string("USERSPACE_ALL");
      new_label->start_cycle = cycle;
      new_label->end_cycle = cycle;
      new_label->indent = label_stack.size() + 1;
      new_label->asm_sequence = false;
      label_stack.push_back(new_label);
      new_label->pre_print(this->tracefile);
    }
  } else {
    std::string label = this_instr->function_name;

    if ((label_stack.size() > 0) &&
        (std::string("USERSPACE_ALL")
             .compare(label_stack[label_stack.size() - 1]->label) == 0)) {
      LabelMeta *pop_label = label_stack[label_stack.size() - 1];
      label_stack.pop_back();
      pop_label->post_print(this->tracefile);
      delete pop_label;
    }

    if ((label_stack.size() > 0) &&
        (label.compare(label_stack[label_stack.size() - 1]->label) == 0)) {
      LabelMeta *last_label = label_stack[label_stack.size() - 1];
      last_label->end_cycle = cycle;
    } else {
      if ((label_stack.size() > 0) and this_instr->in_asm_sequence and
          label_stack[label_stack.size() - 1]->asm_sequence) {

        LabelMeta *pop_label = label_stack[label_stack.size() - 1];
        label_stack.pop_back();
        pop_label->post_print(this->tracefile);
        delete pop_label;

        LabelMeta *new_label = new LabelMeta();
        new_label->label = label;
        new_label->start_cycle = cycle;
        new_label->end_cycle = cycle;
        new_label->indent = label_stack.size() + 1;
        new_label->asm_sequence = this_instr->in_asm_sequence;
        label_stack.push_back(new_label);
        new_label->pre_print(this->tracefile);
      } else if ((label_stack.size() > 0) and
                 (this_instr->is_callsite or !(this_instr->is_fn_entry))) {
        uint64_t unwind_start_level = (uint64_t)(-1);
        while (
            (label_stack.size() > 0) and
            (label_stack[label_stack.size() - 1]->label.compare(label) != 0)) {
          LabelMeta *pop_label = label_stack[label_stack.size() - 1];
          label_stack.pop_back();
          pop_label->post_print(this->tracefile);
          if (unwind_start_level == (uint64_t)(-1)) {
            unwind_start_level = pop_label->indent;
          }
          delete pop_label;
          if (label_stack.size() > 0) {
            LabelMeta *last_label = label_stack[label_stack.size() - 1];
            last_label->end_cycle = cycle;
          }
        }
        if (label_stack.size() == 0) {
          fprintf(this->tracefile,
                  "WARN: STACK ZEROED WHEN WE WERE LOOKING FOR LABEL: %s, "
                  "iaddr 0x%" PRIx64 "\n",
                  label.c_str(),
                  inst_addr);
          fprintf(this->tracefile,
                  "WARN: is_callsite was: %d, is_fn_entry was: %d\n",
                  this_instr->is_callsite,
                  this_instr->is_fn_entry);
          fprintf(this->tracefile,
                  "WARN: Unwind started at level: dec %" PRIu64 "\n",
                  unwind_start_level);
          fprintf(this->tracefile, "WARN: Last instr was\n");
          this->last_instr->printMeFile(this->tracefile, std::string("WARN: "));
        }
      } else {
        LabelMeta *new_label = new LabelMeta();
        new_label->label = label;
        new_label->start_cycle = cycle;
        new_label->end_cycle = cycle;
        new_label->indent = label_stack.size() + 1;
        new_label->asm_sequence = this_instr->in_asm_sequence;
        label_stack.push_back(new_label);
        new_label->pre_print(this->tracefile);
      }
    }
    this->last_instr = this_instr;
  }
}

#ifdef TRACERV_TOP_MAIN
int main() {
  std::string tracefile = "/home/centos/trace2/TRACEFILE";

  std::ifstream is(tracefile);
  std::string line;

  TraceTracker *t =
      new TraceTracker("/home/centos/trace2/vmlinux-dwarf", stdout);
  while (getline(is, line)) {
    std::string addr_str = line.substr(0, 16);
    std::string cycle_str = line.substr(16, 32);
    uint64_t addr = (uint64_t)strtoull(addr_str.c_str(), NULL, 16);
    uint64_t cycle = (uint64_t)strtoull(cycle_str.c_str(), NULL, 16);
    t->addInstruction(addr, cycle);
  }
}
#endif
