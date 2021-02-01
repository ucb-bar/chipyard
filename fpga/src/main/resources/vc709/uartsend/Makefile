build:
	CC=gcc
	$(CC) -c ./uartboot/crc16.c -o ./uartboot/crc16.o
	$(CC) -c serial.c -o serial.o
	$(CC) -o serial serial.o ./uartboot/crc16.o

clean:
	rm serial.o ./uartboot/crc16.o
	rm serial
