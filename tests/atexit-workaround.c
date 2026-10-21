/*
 * GCC 16's nano Newlib dereferences a null _on_exit_args pointer when the
 * callback registered by crt0 is run.  These bare-metal tests do not need
 * process-exit callbacks, so keep the callback successful but omit it.
 */
#include <stdlib.h>

int __wrap_atexit(void (*function)(void))
{
    (void)function;
    return 0;
}
