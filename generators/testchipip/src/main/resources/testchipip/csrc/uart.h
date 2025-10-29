#ifndef __SIMUART_H
#define __SIMUART_H

#include <signal.h>
#include <string.h>
#include <string>

class uart_t
{
    public:
        uart_t(
            const char* filename_prefix,
            int uartno,
            bool use_pty);
        ~uart_t();
        void tick(
            unsigned char out_valid,
            unsigned char *out_ready,
            char out_bits,

            unsigned char *in_valid,
            unsigned char in_ready,
            char *in_bits);

    private:
        bool print_file;
        int inputfd;
        int outputfd;
};

#endif // __SIMUART_H
