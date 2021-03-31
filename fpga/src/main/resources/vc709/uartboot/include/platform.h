// See LICENSE for license details.

#ifndef _SIFIVE_PLATFORM_H
#define _SIFIVE_PLATFORM_H

#include "const.h"
#include "riscv_test_defaults.h"
#include "devices/clint.h"
#include "devices/gpio.h"
#include "devices/plic.h"
#include "devices/spi.h"
#include "devices/uart.h"

 // Some things missing from the official encoding.h
#if __riscv_xlen == 32
  #define MCAUSE_INT         0x80000000UL
  #define MCAUSE_CAUSE       0x7FFFFFFFUL
#else
   #define MCAUSE_INT         0x8000000000000000UL
   #define MCAUSE_CAUSE       0x7FFFFFFFFFFFFFFFUL
#endif

/****************************************************************************
 * Platform definitions
 *****************************************************************************/

// CPU info
#define NUM_CORES 1
#define GLOBAL_INT_SIZE 15
#define GLOBAL_INT_MAX_PRIORITY 7

// Memory map
#define AXI_PCIE_HOST_1_00_A_CTRL_ADDR _AC(0x50000000,UL)
#define AXI_PCIE_HOST_1_00_A_CTRL_SIZE _AC(0x4000000,UL)
#define CLINT_CTRL_ADDR _AC(0x2000000,UL)
#define CLINT_CTRL_SIZE _AC(0x10000,UL)
#define DEBUG_CTRL_ADDR _AC(0x0,UL)
#define DEBUG_CTRL_SIZE _AC(0x1000,UL)
#define ERROR_MEM_ADDR _AC(0x3000,UL)
#define ERROR_MEM_SIZE _AC(0x1000,UL)
#define GPIO_CTRL_ADDR _AC(0x64002000,UL)
#define GPIO_CTRL_SIZE _AC(0x1000,UL)
#define MASKROM_MEM_ADDR _AC(0x10000,UL)
#define MASKROM_MEM_SIZE _AC(0x2000,UL)
#define MEMORY_MEM_ADDR _AC(0x80000000,UL)
#define MEMORY_MEM_SIZE _AC(0x100000000,UL)
#define PLIC_CTRL_ADDR _AC(0xc000000,UL)
#define PLIC_CTRL_SIZE _AC(0x4000000,UL)
#define SPI_CTRL_ADDR _AC(0x64001000,UL)
#define SPI_CTRL_SIZE _AC(0x1000,UL)
#define TEST_CTRL_ADDR _AC(0x4000,UL)
#define TEST_CTRL_SIZE _AC(0x1000,UL)
#define UART_CTRL_ADDR _AC(0x64000000,UL)
#define UART_CTRL_SIZE _AC(0x1000,UL)

// IOF masks


// Interrupt numbers
#define UART_INT_BASE 1
#define SPI_INT_BASE 2
#define GPIO_INT_BASE 3
#define AXI_PCIE_HOST_1_00_A_INT_BASE 7

// Helper functions
#define _REG64(p, i) (*(volatile uint64_t *)((p) + (i)))
#define _REG32(p, i) (*(volatile uint32_t *)((p) + (i)))
#define _REG16(p, i) (*(volatile uint16_t *)((p) + (i)))
// Bulk set bits in `reg` to either 0 or 1.
// E.g. SET_BITS(MY_REG, 0x00000007, 0) would generate MY_REG &= ~0x7
// E.g. SET_BITS(MY_REG, 0x00000007, 1) would generate MY_REG |= 0x7
#define SET_BITS(reg, mask, value) if ((value) == 0) { (reg) &= ~(mask); } else { (reg) |= (mask); }
#define AXI_PCIE_HOST_1_00_A_REG(offset) _REG32(AXI_PCIE_HOST_1_00_A_CTRL_ADDR, offset)
#define CLINT_REG(offset) _REG32(CLINT_CTRL_ADDR, offset)
#define DEBUG_REG(offset) _REG32(DEBUG_CTRL_ADDR, offset)
#define ERROR_REG(offset) _REG32(ERROR_CTRL_ADDR, offset)
#define GPIO_REG(offset) _REG32(GPIO_CTRL_ADDR, offset)
#define MASKROM_REG(offset) _REG32(MASKROM_CTRL_ADDR, offset)
#define MEMORY_REG(offset) _REG32(MEMORY_CTRL_ADDR, offset)
#define PLIC_REG(offset) _REG32(PLIC_CTRL_ADDR, offset)
#define SPI_REG(offset) _REG32(SPI_CTRL_ADDR, offset)
#define TEST_REG(offset) _REG32(TEST_CTRL_ADDR, offset)
#define UART_REG(offset) _REG32(UART_CTRL_ADDR, offset)
#define AXI_PCIE_HOST_1_00_A_REG64(offset) _REG64(AXI_PCIE_HOST_1_00_A_CTRL_ADDR, offset)
#define CLINT_REG64(offset) _REG64(CLINT_CTRL_ADDR, offset)
#define DEBUG_REG64(offset) _REG64(DEBUG_CTRL_ADDR, offset)
#define ERROR_REG64(offset) _REG64(ERROR_CTRL_ADDR, offset)
#define GPIO_REG64(offset) _REG64(GPIO_CTRL_ADDR, offset)
#define MASKROM_REG64(offset) _REG64(MASKROM_CTRL_ADDR, offset)
#define MEMORY_REG64(offset) _REG64(MEMORY_CTRL_ADDR, offset)
#define PLIC_REG64(offset) _REG64(PLIC_CTRL_ADDR, offset)
#define SPI_REG64(offset) _REG64(SPI_CTRL_ADDR, offset)
#define TEST_REG64(offset) _REG64(TEST_CTRL_ADDR, offset)
#define UART_REG64(offset) _REG64(UART_CTRL_ADDR, offset)

// Misc


#endif /* _SIFIVE_PLATFORM_H */
