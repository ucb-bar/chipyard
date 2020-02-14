#include <stdio.h>
#include <stdlib.h>
#include <errno.h>
#include <fcntl.h>
#ifdef TIMING_OUTPUT
#include <time.h>
#include <sys/time.h>
#endif /* TIMING_OUTPUT */
#include <string.h>
#if !defined(SPEC_CPU_WINDOWS)
# include <unistd.h>
#endif
#include "spec.h"

#define SPEC_BZIP

#define  ALREADY_SPEC

#define Bool unsigned char
/* Prototypes for stuff in bzip2.c */
Bool uncompressStream ( int zStream, int stream );
void compressStream ( int zStream, int stream );
void allocateCompressStructures ( void );

#define DEBUG

#ifdef DEBUG
int dbglvl=4;
#define debug(level,str)           { if (level<dbglvl) printf(str); }
#define debug1(level,str, a)       { if (level<dbglvl) printf(str, a); }
#define debug2(level,str, a, b)    { if (level<dbglvl) printf(str, a,b); }
#define debug3(level,str, a, b, c) { if (level<dbglvl) printf(str,a,b,c); }
#define debug4(level,str, a, b, c, d) { if (level<dbglvl) printf(str,a,b,c,d); }
#else
#define debug(level,str)           
#define debug1(level,str, a)       
#define debug2(level,str, a, b)    
#define debug3(level,str, a, b, c) 
#define debug4(level,str, a, b, c, d) 
#endif

#define FUDGE_BUF (1024*1024)
#define VALIDATE_SKIP 1027
#define MAX_SPEC_FD 3
struct spec_fd_t {
    int limit;
    int len;
    int pos;
    unsigned char *buf;
} spec_fd[MAX_SPEC_FD];

long int seedi;
double ran()
/* See "Random Number Generators: Good Ones Are Hard To Find", */
/*     Park & Miller, CACM 31#10 October 1988 pages 1192-1201. */
/***********************************************************/
/* THIS IMPLEMENTATION REQUIRES AT LEAST 32 BIT INTEGERS ! */
/***********************************************************/
#define _A_MULTIPLIER  16807L
#define _M_MODULUS     2147483647L /* (2**31)-1 */
#define _Q_QUOTIENT    127773L     /* 2147483647 / 16807 */
#define _R_REMAINDER   2836L       /* 2147483647 % 16807 */
{
	long lo;
	long hi;
	long test;

	hi = seedi / _Q_QUOTIENT;
	lo = seedi % _Q_QUOTIENT;
	test = _A_MULTIPLIER * lo - _R_REMAINDER * hi;
	if (test > 0) {
		seedi = test;
	} else {
		seedi = test + _M_MODULUS;
	}
	return ( (float) seedi / _M_MODULUS);
}


int spec_init () {
    int i, j;
    debug(3,"spec_init\n");

    /* Clear the spec_fd structure */

    /* Allocate some large chunks of memory, we can tune this later */
    for (i = 0; i < MAX_SPEC_FD; i++) {
	int limit = spec_fd[i].limit;
	memset(&spec_fd[i], 0, sizeof(*spec_fd));
	spec_fd[i].limit = limit;
	spec_fd[i].buf = (unsigned char *)malloc(limit+FUDGE_BUF);
	if (spec_fd[i].buf == NULL) {
	    printf ("spec_init: Error mallocing memory!\n");
	    exit(0);
	}
	for (j = 0; j < limit; j+=1024) {
	    spec_fd[i].buf[j] = 0;
	}
    }
    return 0;
}

int spec_random_load (int fd) {
    /* Now fill up the first chunk with random data, if this data is truly
       random then we will not get much of a boost out of it */
#define RANDOM_CHUNK_SIZE (128*1024)
#define RANDOM_CHUNKS     (32)
    /* First get some "chunks" of random data, because the gzip
	algorithms do not look past 32K */
    int i, j;
    char random_text[RANDOM_CHUNKS][RANDOM_CHUNK_SIZE];

    debug(4,"Creating Chunks\n");
    for (i = 0; i < RANDOM_CHUNKS; i++) {
	debug1(5,"Creating Chunk %d\n", i);
	for (j = 0; j < RANDOM_CHUNK_SIZE; j++) {
	    random_text[i][j] = (int)(ran()*256);
	}
    }

    debug(4,"Filling input file\n");
    /* Now populate the input "file" with random chunks */
    for (i = 0 ; i < spec_fd[fd].limit; i+= RANDOM_CHUNK_SIZE) {
	memcpy(spec_fd[fd].buf + i, random_text[(int)(ran()*RANDOM_CHUNKS)],
		RANDOM_CHUNK_SIZE);
    }
    /* TODO-REMOVE: Pretend we only did 1M */
    spec_fd[fd].len = 1024*1024;
    return 0;
}

int spec_load (int num, char *filename, int size) {
#define FILE_CHUNK (128*1024)
    int fd, rc, i;
#ifndef O_BINARY
#define O_BINARY 0
#endif
    fd = open(filename, O_RDONLY|O_BINARY);
    if (fd < 0) {
	fprintf(stderr, "Can't open file %s: %s\n", filename, strerror(errno));
	exit (0);
    }
    spec_fd[num].pos = spec_fd[num].len = 0;
    for (i = 0 ; i < size; i+= rc) {
	rc = read(fd, spec_fd[num].buf+i, FILE_CHUNK);
	if (rc == 0) break;
	if (rc < 0) {
	    fprintf(stderr, "Error reading from %s: %s\n", filename, strerror(errno));
	    exit (0);
	}
	spec_fd[num].len += rc;
    }
    close(fd);
    while (spec_fd[num].len < size) {
	int tmp = size - spec_fd[num].len;
	if (tmp > spec_fd[num].len) tmp = spec_fd[num].len;
	debug1(3,"Duplicating %d bytes\n", tmp);
	memcpy(spec_fd[num].buf+spec_fd[num].len, spec_fd[num].buf, tmp);
	spec_fd[num].len += tmp;
    }
    return 0;
}

int spec_read (int fd, unsigned char *buf, int size) {
    int rc = 0;
    debug3(4,"spec_read: %d, %p, %d = ", fd, (void *)buf, size);
    if (fd > MAX_SPEC_FD) {
	fprintf(stderr, "spec_read: fd=%d, > MAX_SPEC_FD!\n", fd);
	exit (0);
    }
    if (spec_fd[fd].pos >= spec_fd[fd].len) {
	debug(4,"EOF\n");
	return EOF;
    }
    if (spec_fd[fd].pos + size >= spec_fd[fd].len) {
	rc = spec_fd[fd].len - spec_fd[fd].pos;
    } else {
	rc = size;
    }
    memcpy(buf, &(spec_fd[fd].buf[spec_fd[fd].pos]), rc);
    spec_fd[fd].pos += rc;
    debug1(4,"%d\n", rc);
    return rc;
}
int spec_fread (unsigned char *buf, int size, int num, int fd) {
    int rc = 0;
    debug4(4,"spec_fread: %p, (%d x %d) fd %d =", (void *)buf, size, num, fd);
    if (fd > MAX_SPEC_FD) {
	fprintf(stderr, "spec_fread: fd=%d, > MAX_SPEC_FD!\n", fd);
	exit (0);
    }
    if (spec_fd[fd].pos >= spec_fd[fd].len) {
	debug(4,"EOF\n");
	return EOF;
    }
    if (spec_fd[fd].pos + (size*num) >= spec_fd[fd].len) {
	rc = (spec_fd[fd].len - spec_fd[fd].pos) / size;
    } else {
	rc = num;
    }
    memcpy(buf, &(spec_fd[fd].buf[spec_fd[fd].pos]), rc);
    spec_fd[fd].pos += rc * size;
    debug1(4,"%d\n", rc * size);
    return rc;
}
int spec_getc (int fd) {
    int rc = 0;
    debug1(4,"spec_getc: %d = ", fd);
    if (fd > MAX_SPEC_FD) {
	fprintf(stderr, "spec_read: fd=%d, > MAX_SPEC_FD!\n", fd);
	exit (0);
    }
    if (spec_fd[fd].pos >= spec_fd[fd].len) {
	debug(4,"EOF\n");
	return EOF;
    }
    rc = spec_fd[fd].buf[spec_fd[fd].pos++];
    debug1(4,"%d\n", rc);
    return rc;
}
int spec_ungetc (unsigned char ch, int fd) {
    int rc = 0;
    debug1(4,"spec_ungetc: %d = ", fd);
    if (fd > MAX_SPEC_FD) {
	fprintf(stderr, "spec_read: fd=%d, > MAX_SPEC_FD!\n", fd);
	exit (0);
    }
    if (spec_fd[fd].pos <= 0) {
	fprintf(stderr, "spec_ungetc: pos %d <= 0\n", spec_fd[fd].pos);
	exit (0);
    }
    if (spec_fd[fd].buf[--spec_fd[fd].pos] != ch) {
	fprintf(stderr, "spec_ungetc: can't unget something that wasn't what was in the buffer!\n");
	exit (0);
    }
    debug1(4,"%d\n", rc);
    return ch;
}
int spec_rewind(int fd) {
    spec_fd[fd].pos = 0;
    return 0;
}
int spec_reset(int fd) {
    memset(spec_fd[fd].buf, 0, spec_fd[fd].len);
    spec_fd[fd].pos = spec_fd[fd].len = 0;
    return 0;
}

int spec_write(int fd, unsigned char *buf, int size) {
    debug3(4,"spec_write: %d, %p, %d = ", fd, (void *)buf, size);
    if (fd > MAX_SPEC_FD) {
	fprintf(stderr, "spec_write: fd=%d, > MAX_SPEC_FD!\n", fd);
	exit (0);
    }
    memcpy(&(spec_fd[fd].buf[spec_fd[fd].pos]), buf, size); 
    spec_fd[fd].len += size;
    spec_fd[fd].pos += size;
    debug1(4,"%d\n", size);
    return size;
}
int spec_fwrite(unsigned char *buf, int size, int num, int fd) {
    debug4(4,"spec_fwrite: %p, %d, %d, %d = ", (void *)buf, size, num, fd);
    if (fd > MAX_SPEC_FD) {
	fprintf(stderr, "spec_fwrite: fd=%d, > MAX_SPEC_FD!\n", fd);
	exit (0);
    }
    memcpy(&(spec_fd[fd].buf[spec_fd[fd].pos]), buf, size*num); 
    spec_fd[fd].len += size*num;
    spec_fd[fd].pos += size*num;
    debug1(4,"%d\n", num);
    return num;
}
int spec_putc(unsigned char ch, int fd) {
    debug2(4,"spec_putc: %d, %d = ", ch, fd);
    if (fd > MAX_SPEC_FD) {
	fprintf(stderr, "spec_write: fd=%d, > MAX_SPEC_FD!\n", fd);
	exit (0);
    }
    spec_fd[fd].buf[spec_fd[fd].pos++] = ch;
    spec_fd[fd].len ++;
    return ch;
}

#define MB (1024*1024)
#ifdef SPEC_CPU
int main (int argc, char *argv[]) {
    int i, level;
    int input_size=64, compressed_size;
    char *input_name="input.combined";
    unsigned char *validate_array;
    seedi = 10;

    if (argc > 1) input_name=argv[1];
    if (argc > 2) input_size=atoi(argv[2]);
    if (argc > 3) 
	compressed_size=atoi(argv[3]);
    else
	compressed_size=input_size;

    spec_fd[0].limit=input_size*MB;
    spec_fd[1].limit=compressed_size*MB;
    spec_fd[2].limit=input_size*MB;
    spec_init();

    debug_time();
    debug(2, "Loading Input Data\n");
    spec_load(0, input_name, input_size*MB);
    debug1(3, "Input data %d bytes in length\n", spec_fd[0].len);

    validate_array = (unsigned char *)malloc(input_size*MB/1024);
    if (validate_array == NULL) {
	printf ("main: Error mallocing memory!\n");
	exit (0);
    }
    /* Save off one byte every ~1k for validation */
    for (i = 0; i*VALIDATE_SKIP < input_size*MB; i++) {
	validate_array[i] = spec_fd[0].buf[i*VALIDATE_SKIP];
    }


#ifdef DEBUG_DUMP
    fd = open ("out.uncompressed", O_RDWR|O_CREAT, 0644);
    write(fd, spec_fd[0].buf, spec_fd[0].len);
    close(fd);
#endif

    spec_initbufs();

    for (level=5; level <= 9; level += 2) {
	debug_time();
	debug1(2, "Compressing Input Data, level %d\n", level);

	spec_compress(0,1, level);

	debug_time();
	debug1(3, "Compressed data %d bytes in length\n", spec_fd[1].len);

#ifdef DEBUG_DUMP
	{
	    char buf[256];
	    sprintf(buf, "out.compress.%d", level);
	    fd = open (buf, O_RDWR|O_CREAT, 0644);
	    write(fd, spec_fd[1].buf, spec_fd[1].len);
	    close(fd);
	}
#endif

	spec_reset(0);
	spec_rewind(1);

	debug_time();
	debug(2, "Uncompressing Data\n");
	spec_uncompress(1,0, level);
	debug_time();
	debug1(3, "Uncompressed data %d bytes in length\n", spec_fd[0].len);

#ifdef DEBUG_DUMP
	{
	    char buf[256];
	    sprintf(buf, "out.uncompress.%d", level);
	    fd = open (buf, O_RDWR|O_CREAT, 0644);
	    write(fd, spec_fd[0].buf, spec_fd[0].len);
	    close(fd);
	}
#endif

	for (i = 0; i*VALIDATE_SKIP < input_size*MB; i++) {
	    if (validate_array[i] != spec_fd[0].buf[i*VALIDATE_SKIP]) {
		printf ("Tested %dMB buffer: Miscompared!!\n", input_size);
		exit (0);
	    }
	}
	debug_time();
	debug(3, "Uncompressed data compared correctly\n");
	spec_reset(1);
	spec_rewind(0);
    }
    printf ("Tested %dMB buffer: OK!\n", input_size);

    return 0;
}

#if defined(SPEC_BZIP)
extern unsigned char smallMode;
extern int     verbosity;
extern int     bsStream;
extern int     workFactor, blockSize100k;
void spec_initbufs() {
   smallMode               = 0;
   verbosity               = 0;
   blockSize100k           = 9;
/*   bsStream                = 0; */
   workFactor              = 30;
/*   allocateCompressStructures(); */
}
void spec_compress(int in, int out, int lev) {
    blockSize100k           = lev;
    compressStream ( in, out );
}
void spec_uncompress(int in, int out, int lev) {
    blockSize100k           = 0;
    uncompressStream( in, out );
}
#else
#error You must have SPEC_BZIP defined!
#endif

int debug_time () {
#ifdef TIMING_OUTPUT
    static int last = 0;
    struct timeval tv;
    gettimeofday(&tv,NULL);
    debug2(2, "Time: %10d, %10d\n", tv.tv_sec, tv.tv_sec-last);
    last = tv.tv_sec;
#endif
    return 0;
}
#endif
