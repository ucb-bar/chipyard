/* Bare-metal stubs for Embench on Chipyard RISC-V */
#include <stddef.h>
#include <stdint.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <errno.h>

/* _sbrk: heap allocator for newlib malloc */
extern char _end[];  /* from linker script */
static char *heap_ptr = 0;

void *_sbrk(ptrdiff_t incr) {
    if (heap_ptr == 0)
        heap_ptr = _end;
    char *prev = heap_ptr;
    heap_ptr += incr;
    return prev;
}

/* _write: send to nowhere (no UART) */
ssize_t _write(int fd, const void *buf, size_t count) {
    (void)fd; (void)buf;
    return count;
}

/* _read: no input */
ssize_t _read(int fd, void *buf, size_t count) {
    (void)fd; (void)buf; (void)count;
    return 0;
}

/* _close, _lseek, _fstat, _isatty: minimal stubs */
int _close(int fd) { (void)fd; return -1; }
off_t _lseek(int fd, off_t offset, int whence) { (void)fd; (void)offset; (void)whence; return -1; }
int _fstat(int fd, struct stat *st) { (void)fd; st->st_mode = S_IFCHR; return 0; }
int _isatty(int fd) { (void)fd; return 1; }

/* _kill, _getpid: minimal stubs for newlib */
int _kill(int pid, int sig) { (void)pid; (void)sig; errno = EINVAL; return -1; }
int _getpid(void) { return 1; }

/* clock(): return rdcycle value */
#include <time.h>
clock_t _times(void *buf) {
    (void)buf;
    uint64_t cycles;
    __asm__ volatile("rdcycle %0" : "=r"(cycles));
    return (clock_t)cycles;
}
