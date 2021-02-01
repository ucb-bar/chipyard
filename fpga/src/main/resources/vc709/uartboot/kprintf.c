// See LICENSE for license details.
#include <stdarg.h>
#include <stdint.h>
#include <stdbool.h>

#include "kprintf.h"

static inline void _kputs(const char *s)
{
	char c;
	for (; (c = *s) != '\0'; s++)
		kputc(c);
}

static inline void _kgets(char *s)
{
	for (; (*s = kgetc()) != '\n'; s++);
	*s = '\0';
}

void kread(char *s, int count)
{
	char *p = s;
	for (int i = 0; i < count; i++) {
		*p++ = kgetc();
	}
}

void kwrite(char *s, int count)
{
	char *p = s;
	for (int i = 0; i < count; i++) {
		kputc(*p++);
	}
}

void kputs(const char *s)
{
	_kputs(s);
	kputc('\n');
}

void kgets(char *s){
	_kgets(s);	
}

void _escape_char(const char c){
	switch (c) {
	case 'a':
		kputc('\n');
		break;
	case 'b':
		kputc('\n');
		break;
	case 'f':
		kputc('\n');
		break;
	case 'n':
		kputc('\n');
		break;
	case 'r':
		kputc('\n');
		break;
	case 't':
		kputc('\n');
		break;
	case 'v':
		kputc('\n');
		break;
	case '\\':
		kputc('\n');
		break;
	case '\'':
		kputc('\'');
		break;
	case '"':
		kputc('"');
		break;
	case '?':
		kputc('?');
		break;
	case '0':
		kputc('\0');
		break;
	default:
		break;
	}
}

void kprintf(const char *fmt, ...)
{
	va_list vl;
	bool is_format, is_long, is_char;
	char c;

	va_start(vl, fmt);
	is_format = false;
	is_long = false;
	is_char = false;
	while ((c = *fmt++) != '\0') {
		if (is_format) {
			switch (c) {
			case 'l':
				is_long = true;
				continue;
			case 'h':
				is_char = true;
				continue;
			case 'x': {
				unsigned long n;
				long i;
				if (is_long) {
					n = va_arg(vl, unsigned long);
					i = (sizeof(unsigned long) << 3) - 4;
				} else {
					n = va_arg(vl, unsigned int);
					i = is_char ? 4 : (sizeof(unsigned int) << 3) - 4;
				}
				for (; i >= 0; i -= 4) {
					long d;
					d = (n >> i) & 0xF;
					kputc(d < 10 ? '0' + d : 'a' + d - 10);
				}
				break;
			}
			case 's':
				_kputs(va_arg(vl, const char *));
				break;
			case 'c':
				kputc(va_arg(vl, int));
				break;
			}
			is_format = false;
			is_long = false;
			is_char = false;
		} else if (c == '%') {
			is_format = true;
		} else if (c == '\\') {
			_escape_char(*(fmt++));
		} else {
			kputc(c);
		}
	}
	va_end(vl);
}
