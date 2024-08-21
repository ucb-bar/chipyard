#ifndef __TRACERV_DWARF_H
#define __TRACERV_DWARF_H

#include <cstdint>
#include <libdwarf.h>
#include <map>
#include <memory>
#include <string>
#include <vector>

struct callsite_t {
  uint64_t pc;
  std::string name;

  callsite_t(uint64_t pc) : pc(pc) {}
  callsite_t(uint64_t pc, const char *name) : pc(pc), name(std::string(name)) {}
};

struct subroutine_t {
  std::string name;
  std::vector<callsite_t> callsites;
  uint64_t pc_end;
  bool function;

  subroutine_t(const char *name, uint64_t pc_end, bool function)
      : name(std::string(name)), pc_end(pc_end), function(function) {}

  void print(uint64_t) const;
};

using subroutine_map = std::map<uint64_t, subroutine_t>;

const subroutine_t *subroutine_find(const subroutine_map &, uint64_t);

class dwarf_t {
public:
  dwarf_t(Elf *);
  virtual ~dwarf_t(void) {
    if (this->dbg) {
      dwarf_finish(this->dbg, nullptr);
    }
  }

  void subroutines(subroutine_map &);

private:
  Dwarf_Debug dbg;

  // Encapsulate raw libdwarf pointers for memory management
  class dwarf_deleter;
  using die_ptr = std::unique_ptr<Dwarf_Die_s, dwarf_deleter>;
  using attr_ptr = std::unique_ptr<Dwarf_Attribute_s, dwarf_deleter>;
  using str_ptr = std::unique_ptr<char[], dwarf_deleter>;

  template <typename T>
  void siblings(die_ptr, void (dwarf_t::*)(Dwarf_Die, T &), T &);

  void die_subprogram(Dwarf_Die, subroutine_map &);
  void die_callsite(Dwarf_Die, std::vector<callsite_t> &);

  die_ptr die_origin(Dwarf_Die);
  str_ptr die_name(Dwarf_Die);
};

#endif // __TRACERV_DWARF_H
