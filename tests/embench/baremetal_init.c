/* Minimal _init and exit for bare-metal Embench on Chipyard */
#include <stdint.h>
#include <string.h>

extern volatile uint64_t tohost;
extern volatile uint64_t fromhost;

void __attribute__((noreturn)) tohost_exit(uintptr_t code)
{
    tohost = (code << 1) | 1;
    while (1);
}

void _exit(int code)
{
    tohost_exit(code);
}

uintptr_t __attribute__((weak)) handle_trap(uintptr_t cause, uintptr_t epc, uintptr_t regs[32])
{
    tohost_exit(1337);
    __builtin_unreachable();
}

void __attribute__((weak)) thread_entry(int cid, int nc)
{
    while (cid != 0);
}

static void init_tls()
{
    register void* thread_pointer asm("tp");
    extern char _tdata_begin, _tdata_end, _tbss_end;
    size_t tdata_size = &_tdata_end - &_tdata_begin;
    memcpy(thread_pointer, &_tdata_begin, tdata_size);
    size_t tbss_size = &_tbss_end - &_tdata_end;
    memset(thread_pointer + tdata_size, 0, tbss_size);
}

void _init(int cid, int nc)
{
    init_tls();
    thread_entry(cid, nc);
    int ret = main(0, 0);
    _exit(ret);
}
