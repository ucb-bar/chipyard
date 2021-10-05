#!/bin/bash

cd /scratch/chick/chipyard && java \
    -Xmx8G \
    -Xss8M \
    -XX:MaxPermSize=256M \
    -Djava.io.tmpdir=/scratch/chick/chipyard/.java_tmp \
    -jar \
    /scratch/chick/chipyard/generators/rocket-chip/sbt-launch.jar \
    -Dsbt.sourcemode=true \
    -Dsbt.workspace=/scratch/chick/chipyard/tools \
    \
    ";project \
    tapeout; \
    runMain \
    barstools.tapeout.transforms.GenerateTopAndHarness \
    --output-file \
    /scratch/chick/chipyard/sims/verilator/generated-src/chipyard.TestHarness.RocketConfig/chipyard.TestHarness.RocketConfig.top.v \
    --harness-o \
    /scratch/chick/chipyard/sims/verilator/generated-src/chipyard.TestHarness.RocketConfig/chipyard.TestHarness.RocketConfig.harness.v \
    --input-file \
    /scratch/chick/chipyard/sims/verilator/generated-src/chipyard.TestHarness.RocketConfig/chipyard.TestHarness.RocketConfig.fir \
    --syn-top \
    ChipTop \
    --harness-top \
    TestHarness \
    --annotation-file \
    /scratch/chick/chipyard/sims/verilator/generated-src/chipyard.TestHarness.RocketConfig/chipyard.TestHarness.RocketConfig.anno.json \
    --top-anno-out \
    /scratch/chick/chipyard/sims/verilator/generated-src/chipyard.TestHarness.RocketConfig/chipyard.TestHarness.RocketConfig.top.anno.json \
    --top-dotf-out \
    /scratch/chick/chipyard/sims/verilator/generated-src/chipyard.TestHarness.RocketConfig/firrtl_black_box_resource_files.top.f \
    --top-fir \
    /scratch/chick/chipyard/sims/verilator/generated-src/chipyard.TestHarness.RocketConfig/chipyard.TestHarness.RocketConfig.top.fir \
    --harness-anno-out \
    /scratch/chick/chipyard/sims/verilator/generated-src/chipyard.TestHarness.RocketConfig/chipyard.TestHarness.RocketConfig.harness.anno.json \
    --harness-dotf-out \
    /scratch/chick/chipyard/sims/verilator/generated-src/chipyard.TestHarness.RocketConfig/firrtl_black_box_resource_files.harness.f \
    --harness-fir \
    /scratch/chick/chipyard/sims/verilator/generated-src/chipyard.TestHarness.RocketConfig/chipyard.TestHarness.RocketConfig.harness.fir \
    --infer-rw \
    --repl-seq-mem \
    -c:TestHarness:-o:/scratch/chick/chipyard/sims/verilator/generated-src/chipyard.TestHarness.RocketConfig/chipyard.TestHarness.RocketConfig.top.mems.conf \
    -thconf \
    /scratch/chick/chipyard/sims/verilator/generated-src/chipyard.TestHarness.RocketConfig/chipyard.TestHarness.RocketConfig.harness.mems.conf \
    --target-dir \
    /scratch/chick/chipyard/sims/verilator/generated-src/chipyard.TestHarness.RocketConfig \
    --log-level \
    error \
    "

