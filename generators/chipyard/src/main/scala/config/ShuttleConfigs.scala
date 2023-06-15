package chipyard

import org.chipsalliance.cde.config.{Config}

//-----------------
// Shuttle Configs
//-----------------

class ShuttleConfig extends Config(
  new shuttle.common.WithNShuttleCores ++                        // 1x dual-issue shuttle core
  new chipyard.config.AbstractConfig)

class ShuttleCosimConfig extends Config(
  new chipyard.harness.WithCospike ++                            // attach spike-cosim
  new chipyard.config.WithTraceIO ++                             // enable trace-io for cosim
  new shuttle.common.WithShuttleDebugROB ++                      // enable shuttle debug ROB for cosim
  new shuttle.common.WithNShuttleCores ++
  new chipyard.config.AbstractConfig)

class dmiShuttleCosimConfig extends Config(
  new chipyard.harness.WithSerialTLTiedOff ++                    // don't attach anything to serial-tl
  new chipyard.harness.WithCospike ++                            // attach spike-cosim
  new chipyard.config.WithDMIDTM ++                              // have debug module expose a clocked DMI port
  new chipyard.config.WithTraceIO ++                             // enable traceio for cosim
  new shuttle.common.WithShuttleDebugROB ++                      // enable shuttle debug ROB for cosim
  new shuttle.common.WithNShuttleCores ++
  new chipyard.config.AbstractConfig)

class GemminiShuttleConfig extends Config(
  new gemmini.DefaultGemminiConfig ++                            // use Gemmini systolic array GEMM accel
  new shuttle.common.WithNShuttleCores ++
  new chipyard.config.AbstractConfig)
