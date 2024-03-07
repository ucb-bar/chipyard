#include "rocc.h"

static inline unsigned long adder_echo(unsigned long value)
{
	printf("Value passed to adder_echo is : %d\n", value);
    unsigned long output;
	// output = value;
	ROCC_INSTRUCTION_DS(0, output, value, 0);
    return output;
}

static inline unsigned long adder_add(unsigned long value1, unsigned long value2)
{
	printf("Values passed to adder_add are : %d and %d\n", value1, value2);
	unsigned long output;
	// output = value1 + value2;
	ROCC_INSTRUCTION_DSS(0, output, value1, value2, 1);
	return output;
}

unsigned long data1 = 0xBEEF;
unsigned long data2 = 0xDEAD;
unsigned long data3 = 0x1111;
unsigned long data4 = 0x2222;
unsigned long data5 = 0x3333;
unsigned long data6 = 0x4444;
unsigned long data7 = 0x5555;
unsigned long data8 = 0x6666; 

int main(void)
{
	unsigned long result;
	printf("Data 1 is : %d\n", data1);
	printf("Result is : %d\n", result);

	result = adder_echo(data1);
	printf("Result is : %d\n", result);
	printf("Expected is : %d\n", data1);
	
	if (result != data1)
		return 1;

	result = adder_add(data1, data2);
	printf("Result is : %d\n", result);
	printf("Expected is : %d\n", data1 + data2);
	if (result != data1 + data2)
		return 2;

	result = adder_add(data3, data4);
	printf("Result is : %d\n", result);
	printf("Expected is : %d\n", data3 + data4);
	if (result != data3 + data4)
		return 2;

	result = adder_add(data5, data6);
	printf("Result is : %d\n", result);
	printf("Expected is : %d\n", data5 + data6);
	if (result != data5 + data6)
		return 2;

	result = adder_add(data7, data8);
	printf("Result is : %d\n", result);
	printf("Expected is : %d\n", data7 + data8);
	if (result != data7 + data8)
		return 2;

	result += adder_echo(data1);
	printf("Result is : %d\n", result);
	printf("Expected is : %d\n", data7 + data8 + data1);
	if (result != data7 + data8 + data1)
		return 2;

	printf("PASSED\n");
	return 0;
}