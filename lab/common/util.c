#include "util.h"

int barrier(void)
{
    static volatile int sense;
    static volatile int count = 0;
    static __thread int threadsense = 0;
    int rc;

    __sync_synchronize();
    threadsense = !threadsense;
    if (__sync_fetch_and_add(&count, 1) == nharts-1) {
        count = 0;
        sense = threadsense;
	rc = 1;
    } else {
        while (sense != threadsense);
	rc = 0;
    }
    __sync_synchronize();
    return rc;
}
