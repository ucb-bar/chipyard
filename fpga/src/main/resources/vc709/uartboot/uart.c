// See LICENSE for license details.
#include <stdint.h>

#include "./include/platform.h"
#include "common.h"

#define DEBUG

#include "kprintf.h"

#define is_num(c)       ((c>='0')&&(c<='9'))
#define is_lower(c)     ((c>='a')&&(c<='z'))
#define is_upper(c)     ((c>='A')&&(c<='Z'))
#define is_alpha(c)     (is_lower(c)||is_upper(c))

static int strcmp(const char *p, const char *q) {
    // equal
    for( ; *p == *q; ++p, ++q)
        if(*p == '\0')
            return (0);
    // not equal
    return (*(unsigned char *)p < *(unsigned char *)q) ? -1 : +1;
}

static int stoi(char *p) {
    int val = 0;
    if (*p == '0' && *(p+1) == 'x') {
        p += 2;
        for ( ; *p != '\0'; p++) {
            if (is_num(*p)) {
                val = (val << 4) + (*p - '0');
            } else if (is_lower(*p)) {
                val = (val << 4) + (*p - 'a' + 10);
            } else if (is_upper(*p)) {
                val = (val << 4) + (*p - 'A' + 10);
            } else {
                break;
            }
        }
    } else {
        for ( ; *p != '\0'; p++) {
            if (is_num(*p)) {
                val = val * 10 + (*p - '0');
            } else {
                break;
            }
        }
    }
    return val;
}