// ----------------------------------------------------------------
// SiFive Block UART
// ----------------------------------------------------------------
// ----------------------------------------------------------------

#ifndef UART_UTIL_H
#define UART_UTIL_H

#define TX_BASE (0x0)
#define TX_DATA_BIT_OFF (0)
#define TX_DATA_BITS (8)
#define TX_FIFO_FULL_OFF (31)

#define RX_BASE (0x4)
#define RX_DATA_OFF (0)
#define RX_DATA_BITS (8)
#define RX_FIFO_EMPTY_OFF (31)

#define TX_CTRL_BASE (0x8)
#define TX_ENB_BIT_OFF (0)
#define TX_STOP_BIT_OFF (1)

#define RX_CTRL_BASE (0xC)
#define RX_ENB_BIT_OFF (0)

#define TX_WTRMK_LVL_BASE (0xA)
#define RX_WTRMK_LVL_BASE (0xE)

#define WTRMK_INT_ENB_BASE (0x10)
#define WTRMK_TX_INT_ENB_BIT_OFF (0)
#define WTRMK_RX_INT_ENB_BIT_OFF (1)

#define WTRMK_INT_PEND (0x14)
#define WTRMK_TX_INT_PEND_BIT_OFF (0)
#define WTRMK_RX_INT_PEND_BIT_OFF (1)

#define BAUD_RATE_BASE (0x18)
#define BAUD_RATE_BITS (16)

// support up to 32b uart data
volatile uint8_t* uart_h;

#define UART_SET_BAUD_RATE(val) ({ \
            *((uint16_t*)(uart_h + BAUD_RATE_BASE)) = (val); \
        })

#define UART_TX_CTRL(val) ({ \
            *(uart_h + TX_CTRL_BASE) = (val); \
        })

#define UART_RX_CTRL(val) ({ \
            *(uart_h + RX_CTRL_BASE) = (val); \
        })

#define UART_SET_BASE(addr) ({ \
            uart_h = (uint8_t*)(addr); \
        })

#define UART_TX_FIFO_FULL() ({ \
            *((uint32_t*)(uart_h + TX_BASE)) >> TX_FIFO_FULL_OFF; \
        })

void UART_WRITE(uint8_t data) {
    uint32_t uart_i;
    do {
        uart_i = *((uint32_t*)(uart_h + TX_BASE));
        //printf("uart_i from UART: 0x%x\n", uart_i);
    } while (uart_i >> TX_FIFO_FULL_OFF);

    *((uint32_t*)(uart_h + TX_BASE)) = data & ((0x1 << TX_DATA_BITS) - 1);
}

uint8_t UART_READ() {
    uint32_t uart_o;
    uint8_t final_o;
    do {
        uart_o = *((uint32_t*)(uart_h + RX_BASE));
        //printf("uart_o from UART: 0x%x\n", uart_o);
    } while (uart_o >> RX_FIFO_EMPTY_OFF);

    final_o = uart_o & ((0x1 << RX_DATA_BITS) - 1);
    //printf("Final Data Received: 0x%x\n", final_o);

    return final_o;
}

#endif
