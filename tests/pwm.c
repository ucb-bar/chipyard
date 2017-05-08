#define PWM_PERIOD 0x2000
#define PWM_DUTY 0x2004
#define PWM_ENABLE 0x2008

static inline void write_reg(unsigned long addr, unsigned int data)
{
	volatile unsigned int *ptr = (volatile unsigned int *) addr;
	*ptr = data;
}

static inline unsigned long read_reg(unsigned long addr)
{
	volatile unsigned int *ptr = (volatile unsigned int *) addr;
	return *ptr;
}

int main(void)
{
	write_reg(PWM_PERIOD, 20);
	write_reg(PWM_DUTY, 5);
	write_reg(PWM_ENABLE, 1);

	return 0;
}
