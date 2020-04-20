#!/usr/bin/env python3

import numpy

size = 32

randf = lambda n: numpy.random.randint(0, 100, size=n)

A = randf((size, size)).astype(numpy.int32)
B = randf((size, size)).astype(numpy.int32)
result = numpy.matmul(A, B)

def print_array(name, data, data_size, data_type='data_t', data_fmt='{}', fold=10):
    print('static {} {}[{}] = {{'.format(data_type, name, data_size))
    for i in range(0, len(data), fold):
        print('  ', ', '.join(data_fmt.format(x) for x in data[i:i+fold]), ',', sep='')
    print('};')

print('''\
#ifndef _DATASET_H
#define _DATASET_H
''')
print('#define DIM_SIZE {}'.format(size))
print('''#define ARRAY_SIZE (DIM_SIZE * DIM_SIZE)

typedef int data_t;

#ifdef __GNUC__
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wunused-variable"
#endif
''')

matrix_size = size * size
print_array('input1_data', A.flatten(), matrix_size)
print_array('input2_data', B.flatten(), matrix_size)
print_array('verify_data', result.flatten(), matrix_size)

print('''
#ifdef __GNUC__
#pragma GCC diagnostic pop
#endif

#endif /* _DATASET_H */''')
