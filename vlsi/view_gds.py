#!/usr/bin/python3

import sys, os, subprocess

print('Loading GDS...')

try:
    # gdstk created SVG
    import gdstk
except ImportError:
    try:
        import gdspy
        import tkinter
    except ImportError:
        print('Bad gdspy (requires tkinter) installation!')
        sys.exit()

if 'gdstk' in sys.modules:
    svg_file = os.path.splitext(str(sys.argv[1]))[0] + '.svg'
    print('Opening {} with xdg-open...'.format(os.path.basename(svg_file)))
    subprocess.call(['xdg-open', svg_file])

elif 'gdspy' in sys.modules:
    gds_lib = gdspy.GdsLibrary().read_gds(infile=str(sys.argv[1]), units='import')

    # Comment to show layer
    hidden=[
            (1, 0),     #well
            (1, 251),   #well lbl
            (2, 0),     #fin
            (3, 0),     #psub
            (3, 251),   #psub lbl
            (7, 0),     #gate
            (8, 0),     #dummy
            (10, 0),    #gate cut
            (11, 0),    #active
            (12, 0),    #nselect
            (13, 0),    #pselect
            (16, 0),    #LIG
            (17, 0),    #LISD
            (18, 0),    #V0
            (19, 0),    #M1
            (19, 251),  #M1 lbl
            (21, 0),    #V1
            #(20, 0),    #M2
            (20, 251),  #M2 lbl
            #(25, 0),    #V2
            #(30, 0),    #M3
            (30, 251),  #M3 lbl
            #(35, 0),    #V3
            #(40, 0),    #M4
            (40, 251),  #M4 lbl
            (45, 0),    #V4
            (50, 0),    #M5
            (50, 251),  #M5 lbl
            (55, 0),    #V5
            (60, 0),    #M6
            (60, 251),  #M6 lbl
            (65, 0),    #V6
            (70, 0),    #M7
            (70, 251),  #M7 lbl
            (75, 0),    #V7
            (80, 0),    #M8
            (80, 251),  #M8 lbl
            (85, 0),    #V8
            (88, 0),    #SDT
            (90, 0),    #M9
            (90, 251),  #M9 lbl
            (95, 0),    #V9
            (96, 0),    #Pad
            (97, 0),    #SLVT
            (98, 0),    #LVT
            (99, 0),    #SRAMDRC
            (100, 0),   #BOUNDARY
            (101, 0),   #TEXT
            (110, 0),   #SRAMVT
            (235, 5)    #DIEAREA
            ]

    print('Opening layout in gdspy...')
    gdspy.LayoutViewer(gds_lib, hidden_types=hidden, depth=1)
