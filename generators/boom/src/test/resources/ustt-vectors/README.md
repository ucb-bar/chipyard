# uSTT STTTestGen.py vectors

Fixed regression corpus for `STTAgeMatrixUSTTVectorsSpec`. Each `.input` file is the unmodified output of `reference/uSTT/rtl/test/STTTestGen.py` at uSTT commit `663329abc336c556695c13fb9209cd222cea0b88`, run with `python3 -I` on 2026-11-15 (file names give the generation time). Parameters are the script's defaults: 10-wide groups, 31 architectural and 380 physical registers, 512-entry ROB, one vector per line, about 50 lines per file.

The generator seeds itself from `os.urandom`, so a new run cannot reproduce these files. Do not edit them; to add cases, run the generator again and add its output as a new file. The spec's fresh-run test does that and keeps each corpus under `logs/sttgen/`.
