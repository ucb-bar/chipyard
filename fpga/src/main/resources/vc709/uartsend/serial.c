#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h> //文件控制定义
#include <termios.h>//终端控制定义
#include <errno.h>
#include <unistd.h>
#include <string.h>
#include <stdint.h>
#include <math.h>

#include "../uartboot/include/serial.h"

#define DEVICE "/dev/ttyUSB1"
#define S_TIMEOUT 1

int serial_fd = 0;

//打开串口并初始化设置
int init_serial(char *device)
{
    serial_fd = open(device, O_RDWR | O_NOCTTY | O_NDELAY);
    if (serial_fd < 0) {
        perror("open");
        return -1;
    }

    //串口主要设置结构体termios <termios.h>
    struct termios options;
        
    /**1. tcgetattr函数用于获取与终端相关的参数。
    *参数fd为终端的文件描述符，返回的结果保存在termios结构体中
    */

    tcgetattr(serial_fd, &options);
    /**2. 修改所获得的参数*/
    options.c_cflag |= (CLOCAL | CREAD);   //设置控制模式状态，本地连接，接收使能
    options.c_cflag &= ~CSIZE;             //字符长度，设置数据位之前一定要屏掉这个位
    options.c_cflag &= ~CRTSCTS;           //无硬件流控
    options.c_cflag |= CS8;                //8位数据长度
    options.c_cflag &= ~CSTOPB;            //1位停止位
    options.c_iflag |= IGNPAR;             //无奇偶检验位
    options.c_oflag = 0;                   //输出模式
    options.c_lflag = 0;                   //不激活终端模式
    cfsetospeed(&options, B115200);        //设置波特率
        
    /**3. 设置新属性，TCSANOW：所有改变立即生效*/
    tcflush(serial_fd, TCIFLUSH);          //溢出数据可以接收，但不读
    tcsetattr(serial_fd, TCSANOW, &options);
        
    return 0;
}
    
/**
*串口发送数据
*@fd:串口描述符
*@data:待发送数据
*@datalen:数据长度
*/
unsigned int total_send = 0 ;
int uart_send(int fd, uint8_t *data, int datalen)
{
    int len = 0;
    len = write(fd, data, datalen);//实际写入的长度
    if(len == datalen) {
        total_send += len;
        return len;
    } else {
        tcflush(fd, TCOFLUSH);//TCOFLUSH刷新写入的数据但不传送
        return -1;
    }
    return 0;
}
    
/**
*串口接收数据
*要求启动后，在pc端发送ascii文件
*/
unsigned int total_length = 0 ;
int uart_recv(int fd, uint8_t *data, int datalen)
{
    int len=0, ret = 0;
    fd_set fs_read;
    struct timeval tv_timeout;
        
    FD_ZERO(&fs_read);
    FD_SET(fd, &fs_read);

#ifdef S_TIMEOUT    
    tv_timeout.tv_sec = (10*20/115200+2);
    tv_timeout.tv_usec = 0;
    ret = select(fd+1, &fs_read, NULL, NULL, NULL);
#elif
    ret = select(fd+1, &fs_read, NULL, NULL, tv_timeout);
#endif

    //如果返回0，代表在描述符状态改变前已超过timeout时间,错误返回-1
        
    if (FD_ISSET(fd, &fs_read)) {
        len = read(fd, data, datalen);
        total_length += len ;
        return len;
    } else {
        perror("select");
        return -1;
    }
        
    return 0;
}

void readline(char *p)
{
    char *q = p;
    do {
        uart_recv(serial_fd, (uint8_t *)q, sizeof(uint8_t));
    } while (*q++ != '\n');
    *q = '\0';
}

void write_cmd(cmd_t cmd)
{
    uart_send(serial_fd, (uint8_t *)&cmd, sizeof(cmd));
}

size_t write_block(int serial_fd, char *buf)
{
    size_t retry = -1;
    char cmd = NAK;
    
    // calculate crc
    uint16_t crc_exp = crc16((uint8_t *)buf);
    do {
        retry++;
        // send file and crc
        uart_send(serial_fd, (uint8_t *)buf, CRC16_LEN);
        uart_send(serial_fd, (uint8_t *)&crc_exp, 2);
        // ACK/NAK
        uart_recv(serial_fd, (uint8_t *)&cmd, sizeof(char));
    } while (cmd != ACK);

    return retry;
}

char bar[101];

void update_progress(char *bar, uint8_t p){

    bar[p] = '#';
    bar[p + 1] = '\0';

    printf("send blocks: [%-100s][%3d%%]\r", bar, p);
}

size_t write_batch(int serial_id, char *buf, size_t num_blocks){
    
    size_t retry = 0;

    for (size_t i = 0; i < num_blocks; i++) {
        retry += write_block(serial_fd, buf + i * CRC16_LEN);
        update_progress(bar, i * 100 / num_blocks);
    }
    
    printf("\n");
    
    return retry;
}

void write_header(uint8_t *addr, long len)
{
    // send metadata
    package_t package;
    package.addr = addr;
    package.len = len;

    uart_send(serial_fd, (uint8_t *)&package, sizeof(package));
}

void write_file(FILE *fd)
{
    // send file
    size_t n_blocks = 0;
    size_t n_bytes = 0;
    size_t size;
    size_t retry = 0;

    char *buf = (char *)malloc(sizeof(char) * CRC16_LEN * NUM_BLOCKS);

    do {
        size = fread(buf, CRC16_LEN, NUM_BLOCKS, fd);
        if (size == -1) {
            perror("read");
            exit(1);
        }
        if (size > 0) {
            retry += write_batch(serial_fd, buf, size);
            n_blocks += size;
            n_bytes += size * CRC16_LEN;
            printf("send %5ld blocks, retry %5ld times.\n", size, retry);
        }
    } while (size != 0);

    if (size == 0) {
        memset(buf, 0, CRC16_LEN * NUM_BLOCKS);
        fseek(fd, CRC16_LEN * n_blocks, SEEK_SET);
        size = fread(buf, sizeof(char), CRC16_LEN, fd);
        if (size == -1) {
            perror("read");
            exit(1);
        }
        if (size > 0) {
            retry += write_block(serial_fd, buf);
            n_blocks += 1;
            n_bytes += size;
            printf("send %ld bytes, retry %ld times.\n", size, retry);
        }
    }
    free(buf);

    printf("send %ld blocks, %ld bytes, retry %ld times in total\n", n_blocks, n_bytes, retry);
}

int send_file(char *address, char *filename)
{
    FILE *fd = fopen(filename, "r");

    if (fd == NULL) {
        printf("open file failed.\n");
        perror("open");
        return -1;
    }

    printf("open file successfully.\n");

    // get file len
    long len;
    fseek(fd, 0L, SEEK_END);
    len = ftell(fd);
    fseek(fd, 0L, SEEK_SET);

    printf("file len: %ld\n", len);

    // parse address
    uint8_t *addr;
    sscanf(address, "%p", &addr);
    printf("start transfer at addr[%p].\n", addr);

    // send cmd, header and file
    write_cmd(UART_CMD_TRANSFER);
    write_header(addr, len);
    write_file(fd);
    
    fclose(fd);
    
    return 0;
}

char msg[256];

int main(int argc, char *argv[])
{
    for (int i = 0; i < argc; i++)
        printf("argv[%d]: %s\n", i, argv[i]);
        
    // init connection
    if (init_serial(argv[1]) != 0) {
        printf("open serial failed.\n");
        exit(-1);
    }
    printf("open serial successfully.\n");
    
    if (argc == 4) {
        // ./serial tty, address, filename
        send_file(argv[2], argv[3]);
        printf("transfer finished.\n");
    } else if (argc == 3) {
        // read message
        printf("reading message.\n");
        readline(msg);
        printf("%s\n", msg);
    } else {
        // ./serial tty
        write_cmd(UART_CMD_END);
        while (1) {
            readline(msg);
            printf("%s", msg);
        }
    }

    close(serial_fd);

    return 0;
}
