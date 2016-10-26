#define PWM_PERIOD 0x2000
#define PWM_DUTY 0x2008
#define PWM_ENABLE 0x2010

static inline void write_reg(unsigned long addr, unsigned long data)
{
	volatile unsigned long *ptr = (volatile unsigned long *) addr;
	*ptr = data;
}

static inline unsigned long read_reg(unsigned long addr)
{
	volatile unsigned long *ptr = (volatile unsigned long *) addr;
	return *ptr;
}

int main(void)
{
	write_reg(PWM_PERIOD, 20);
	write_reg(PWM_DUTY, 5);
	write_reg(PWM_ENABLE, 1);
}
