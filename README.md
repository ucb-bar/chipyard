# SKY 130 Chipyard

A SKY 130 tapeout harness built with [Chipyard](https://github.com/ucb-bar/chipyard).

## BWRC Setup

Install `conda` (say "yes" and press enter when prompted):

```
mkdir -m 0700 -p /tools/C/$USER
cd /tools/C/$USER
wget -O Miniforge3.sh \
"https://github.com/conda-forge/miniforge/releases/latest/download/Miniforge3-$(uname)-$(uname -m).sh"
bash Miniforge3.sh -p "/tools/C/${USER}/conda"
```

Then, run Chipyard setup from the root directory of this repo:

```
source ~/.bashrc
mamba install -n base conda-lock=1.4
mamba activate base
./build-setup.sh riscv-tools -s 6 -s 7 -s 8 -s 9 -s 10 --force
./scripts/init-vlsi sky130
```

Add the following to the generated `env.sh` file:

```
export PATH=/tools/C/rohankumar/circt/build/bin
```

To run synthesis, P&R, DRC, and LVS on a `TinyRocket` in SKY 130:

```
make syn
make par
make drc
make lvs
```

Make sure you can view the results and that they look correct (DRC and LVS should be clean).

## Development

This harness is currently under development. It is based on the [STAC tapeout](https://github.com/ucb-bar/stac-top/tree/stac-master) 
from June 2023, but aims to be more general-purpose and simple.

Relevant files from STAC:
- SKY 130 Chipyard additions 
    ([`stac-top/generators/chipyard/src/main/scala/sky130`](https://github.com/ucb-bar/stac-top/tree/stac-master/generators/chipyard/src/main/scala/sky130))
- Hammer input files ([`stac-top/vlsi/design-stac.yml`](https://github.com/ucb-bar/stac-top/blob/stac-master/vlsi/design-stac.yml), [`stac-top/vlsi/tech-sky130.yml`](https://github.com/ucb-bar/stac-top/blob/stac-master/vlsi/tech-sky130.yml))
- Hammer CLI driver ([`stac-top/vlsi/hammer-driver`](https://github.com/ucb-bar/stac-top/blob/stac-master/vlsi/hammer-driver))
- IO file generator ([`stac-top/vlsi/scripts/gen-io-file.py`](https://github.com/ucb-bar/stac-top/blob/stac-master/vlsi/scripts/gen-io-file.py))

Files in this harness that will need to be modified/added:
- SKY 130 Chipyard additions 
    ([`sky130-chipyard/generators/chipyard/src/main/scala/tech/sky130`](https://github.com/ucb-bar/sky130-chipyard/blob/main/generators/chipyard/src/main/scala/tech/sky130))
- Hammer input file ([`sky130-chipyard/vlsi/example-sky130-tapeout.yml`](https://github.com/ucb-bar/sky130-chipyard/blob/main/vlsi/example-sky130-tapeout.yml))
- Hammer CLI driver ([`sky130-chipyard/vlsi/example-vlsi-sky130-tapeout`](https://github.com/ucb-bar/sky130-chipyard/blob/main/vlsi/example-vlsi-sky130-tapeout))

