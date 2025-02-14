import argparse
from numpy import genfromtxt
from numpy import amax
import numpy as np
import matplotlib.pyplot as plt
from matplotlib.patches import Rectangle
import sys


parser = argparse.ArgumentParser()
parser.add_argument("-m", "--mem_name", help="type of memory")
parser.add_argument("-l", "--layer_str", help="specified layer")
parser.add_argument("-f", "--save_root", help="figure saved file")
args = parser.parse_args()

if len(sys.argv) != 7:
	parser.print_help()
	sys.exit(0)


PT_file = "../build/" + args.mem_name + "-output-final_power_temperature.csv"
pos_file = "../build/" + args.mem_name + "-output-bank_position.csv"

print "data file: " + PT_file 
print "bank position file: " + pos_file


PT_data = genfromtxt(PT_file, delimiter=',')
PT_data = PT_data[1:, :]

Bpos = genfromtxt(pos_file, delimiter=',')
Bpos = Bpos[1:, :]

X = int(amax(PT_data[:, 1]))
Y = int(amax(PT_data[:, 2]))
Z = int(amax(PT_data[:, 3]))

print "Dimension: (" + str(X) + ", " + str(Y) + ", " + str(Z) + ")"

power = np.empty((X+1, Y+1, Z+1))
temperature = np.empty((X+1, Y+1, Z+1))

for i in range(0, len(PT_data)):
	x_ = int(PT_data[i,1])
	y_ = int(PT_data[i,2])
	z_ = int(PT_data[i,3])
	power[x_, y_, z_] = PT_data[i,4]
	temperature[x_, y_, z_] = PT_data[i,5]

layer = int(args.layer_str)

if layer >= 0 and layer <= Z:
	plt.figure()
	plt.imshow(power[:,:,layer], aspect='auto')
	ca = plt.gca()
	for i in range(0, len(Bpos)):
		if Bpos[i,6] == layer:
			x_ = Bpos[i,2] 
			y_ = Bpos[i,4]
			w_ = Bpos[i,3] - Bpos[i,2] + 1
			l_ = Bpos[i,5] - Bpos[i,4] + 1
			ca.add_patch(Rectangle((y_-0.5, x_-0.5), l_, w_, fill=None, edgecolor='r'))
			if l_ > w_:
				rot = 0
			else:
				rot = 0

			ca.text(y_-0.5+l_/4, x_-0.5+w_/2, 'R'+str(int(Bpos[i,0]))+'B'+str(int(Bpos[i,1])), color='r', rotation=rot)


	ca.set_xlabel('Y (Column)')
	ca.set_ylabel('X (Row)')
	title_str = 'Power (layer' + str(layer) + ')'
	ca.set_title(title_str)
	plt.colorbar()
	plt.savefig(args.save_root + args.mem_name + '_final_power_layer' + str(layer) + '.png')

	plt.figure()
	plt.imshow(temperature[:,:,layer], aspect='auto')
	ca = plt.gca()
	for i in range(0, len(Bpos)):
		if Bpos[i,6] == layer:
			x_ = Bpos[i,2] 
			y_ = Bpos[i,4]
			w_ = Bpos[i,3] - Bpos[i,2] + 1
			l_ = Bpos[i,5] - Bpos[i,4] + 1
			ca.add_patch(Rectangle((y_-0.5, x_-0.5), l_, w_, fill=None, edgecolor='r'))
			if l_ > w_:
				rot = 0
			else:
				rot = 0

			ca.text(y_-0.5+l_/4, x_-0.5+w_/2, 'R'+str(int(Bpos[i,0]))+'B'+str(int(Bpos[i,1])), color='r', rotation=rot)


	ca.set_xlabel('Y (Column)')
	ca.set_ylabel('X (Row)')
	title_str = 'Power (layer' + str(layer) + ')'
	ca.set_title(title_str)
	plt.colorbar()
	plt.savefig(args.save_root + args.mem_name + '_final_temperature_layer' + str(layer) + '.png')

else:
	print "You should name a correct layer index"
	print "Layer index should be in the range of [" + str(0) + ", " + str(Z) + "]"

