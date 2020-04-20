#!/usr/bin/env python3

import numpy

size = 1000

randf = lambda n: numpy.random.randint(100, size=n)

x = randf(size).astype(numpy.float64)
y = randf(size).astype(numpy.float64)
result = x + y

def print_array(name, data, data_type='data_t', data_fmt='{}', fold=10):
    print('static {} {}[DATA_SIZE] = {{'.format(data_type, name))
    for i in range(0, len(data), fold):
        print('  ', ', '.join(data_fmt.format(x) for x in data[i:i+fold]), ',', sep='')
    print('};')

print('''\
#ifndef _DATASET_H
#define _DATASET_H
''')
print('#define DATA_SIZE {}'.format(size))
print('''
typedef double data_t;

#ifdef __GNUC__
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wunused-variable"
#endif
''')

print_array('input1_data', x)
print_array('input2_data', y)
print_array('verify_data', result)

print('''
#ifdef __GNUC__
#pragma GCC diagnostic pop
#endif

#endif /* _DATASET_H */''')
