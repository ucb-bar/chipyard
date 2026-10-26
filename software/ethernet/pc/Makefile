CC       ?= cc
CPPFLAGS ?= -I../../..
CFLAGS   ?= -std=c11 -Os -Wall -Wextra

TARGETS := ethernet_pc ethernet_udp_pc
SRCS   := ../protocol.c
HDRS   := ../protocol.h

all: $(TARGETS)

ethernet_pc: ethernet_pc.c $(SRCS) $(HDRS)
	$(CC) $(CPPFLAGS) $(CFLAGS) ethernet_pc.c $(SRCS) -o $@

ethernet_udp_pc: ethernet_udp_pc.c $(SRCS) $(HDRS)
	$(CC) $(CPPFLAGS) $(CFLAGS) ethernet_udp_pc.c $(SRCS) -o $@

clean:
	rm -f $(TARGETS)

.PHONY: all clean
