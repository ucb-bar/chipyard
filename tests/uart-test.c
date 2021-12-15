#include <stdio.h>
#include "uart.h"

int main (void){
    printf("Hello World!\n");

    UART_SET_BASE(0x54000000);
    UART_TX_CTRL(0x1);

    while (1) {
        UART_WRITE('a');
        UART_WRITE('b');
        UART_WRITE('c');
        UART_WRITE('d');
        UART_WRITE('e');
        UART_WRITE('f');
        UART_WRITE('g');
        UART_WRITE('h');
        UART_WRITE('i');
        UART_WRITE('j');
        UART_WRITE('k');
        UART_WRITE('l');
    }

    return 0;
}
