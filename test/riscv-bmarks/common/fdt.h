#ifndef FDT_H
#define FDT_H

#define FDT_MAGIC	0xd00dfeed
#define FDT_VERSION	17

struct fdt_header {
  uint32_t magic;
  uint32_t totalsize;
  uint32_t off_dt_struct;
  uint32_t off_dt_strings;
  uint32_t off_mem_rsvmap;
  uint32_t version;
  uint32_t last_comp_version; /* <= 17 */
  uint32_t boot_cpuid_phys;
  uint32_t size_dt_strings;
  uint32_t size_dt_struct;
};

#define FDT_BEGIN_NODE	1
#define FDT_END_NODE	2
#define FDT_PROP	3
#define FDT_NOP		4
#define FDT_END		9

struct fdt_scan_node {
  const struct fdt_scan_node *parent;
  const char *name;
  int address_cells;
  int size_cells;
};

struct fdt_scan_prop {
  const struct fdt_scan_node *node;
  const char *name;
  uint32_t *value;
  int len; // in bytes of value
};

struct fdt_cb {
  void (*open)(const struct fdt_scan_node *node, void *extra);
  void (*prop)(const struct fdt_scan_prop *prop, void *extra);
  void (*done)(const struct fdt_scan_node *node, void *extra); // last property was seen
  int  (*close)(const struct fdt_scan_node *node, void *extra); // -1 => delete the node + children
  void *extra;
};

// Scan the contents of FDT
void fdt_scan(uintptr_t fdt, const struct fdt_cb *cb);
uint32_t fdt_size(uintptr_t fdt);

// Extract fields
const uint32_t *fdt_get_address(const struct fdt_scan_node *node, const uint32_t *base, uint64_t *value);
const uint32_t *fdt_get_size(const struct fdt_scan_node *node, const uint32_t *base, uint64_t *value);
int fdt_string_list_index(const struct fdt_scan_prop *prop, const char *str); // -1 if not found

// Find number of harts
int query_harts(uintptr_t fdt);

#endif
