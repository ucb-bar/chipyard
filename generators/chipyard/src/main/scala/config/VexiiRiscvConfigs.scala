package chipyard

import chisel3._

import org.chipsalliance.cde.config.{Config}

// ---------------------
// VexiiRiscv Configs
// ---------------------

class VexiiRiscvConfig extends Config(
  new vexiiriscv.WithNVexiiRiscvCores(1) ++
  new chipyard.config.AbstractConfig)
