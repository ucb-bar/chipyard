/* Prototypes for stuff in spec.c */
void spec_initbufs();
void spec_compress(int in, int out, int level);
void spec_uncompress(int in, int out, int level);
int spec_init ();
int spec_random_load (int fd);
int spec_load (int num, char *filename, int size);
int spec_read (int fd, unsigned char *buf, int size);
int spec_reset (int fd);
int spec_write (int fd, unsigned char *buf, int size);
int spec_getc (int fd);
int spec_ungetc (unsigned char ch, int fd);
int spec_fread (unsigned char *buf, int size, int num, int fd);
int spec_fwrite (unsigned char *buf, int size, int num, int fd);
int spec_rewind (int fd);
int spec_putc (unsigned char ch, int fd);
int debug_time();

