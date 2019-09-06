CC = riscv-gcc
LDFLAGS=-lc
src_files=sha3.c sha3_example.c
OBJECTS=$(src_files:.c=.o)

all: sha3.rv

sha3.rv: Makefile $(OBJECTS)
	$(CC) $(LDFLAGS) $(OBJECTS) -o $@

.c.o:
	$(CC) -c $(CFLAGS) $< -o $@

run:
	spike pk sha3.rv

clean:
	rm $(OBJECTS) sha3.rv
