package chipyard.harness

import chisel3._

import chipyard._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.tile.NMI
import org.chipsalliance.cde.config._
import freechips.rocketchip.util.HeterogeneousBag

import testchipip._



class WithTileMasterHarness extends OverrideHarnessBinder({
  (system: HasTileMasterPort, th: HasHarnessSignalReferences, ports: Seq[HeterogeneousBag[TLBundle]]) => {
    ports.foreach({ port =>
      dontTouch(port)

      port.head.a.ready := false.B

      port.head.b.valid := false.B
      port.head.b.bits.opcode := 0.U
      port.head.b.bits.param := 0.U
      port.head.b.bits.size := 0.U
      port.head.b.bits.source := 0.U
      port.head.b.bits.address := 0.U
      port.head.b.bits.mask := 0.U
      port.head.b.bits.data := 0.U
      port.head.b.bits.corrupt := false.B

      port.head.c.ready := false.B

      port.head.d.valid := false.B
      port.head.d.bits.opcode := 0.U
      port.head.d.bits.param := 0.U
      port.head.d.bits.size := 0.U
      port.head.d.bits.source := 0.U
      port.head.d.bits.sink := 0.U
      port.head.d.bits.denied := false.B
      port.head.d.bits.data := 0.U
      port.head.d.bits.corrupt := false.B

      port.head.e.ready := false.B
    })
  }
})


class WithTileSlaveHarness extends OverrideHarnessBinder({
  (system: HasTileSlavePort, th: HasHarnessSignalReferences, ports: Seq[HeterogeneousBag[TLBundle]]) => {
    ports.foreach({ port =>
      dontTouch(port)

      port.head.a.valid := false.B
      port.head.a.bits.opcode := TLMessages.Get
      port.head.a.bits.param := 0.U
      port.head.a.bits.size := 0.U
      port.head.a.bits.source := 0.U
      port.head.a.bits.address := 0.U
      port.head.a.bits.mask := 0.U
      port.head.a.bits.data := 0.U
      port.head.a.bits.corrupt := false.B


      port.head.b.ready := false.B

      port.head.c.valid := false.B
      port.head.c.bits.opcode := 0.U
      port.head.c.bits.param := 0.U
      port.head.c.bits.size := 0.U
      port.head.c.bits.source := 0.U
      port.head.c.bits.address := 0.U
      port.head.c.bits.data := 0.U
      port.head.c.bits.corrupt := false.B

      port.head.d.ready := false.B

      port.head.e.valid := false.B
      port.head.e.bits.sink := 0.U
    })
  }
})

class WithTileIntSinkHarness extends OverrideHarnessBinder({
  (system: HasTileIntSinkPort, th: HasHarnessSignalReferences, ports: Seq[UInt]) => {
    ports.foreach({ port =>
      dontTouch(port)
      // port := 0.U
    })
  }
})

class WithTileIntSourceHarness extends OverrideHarnessBinder({
  (system: HasTileIntSourcePort, th: HasHarnessSignalReferences, ports: Seq[UInt]) => {
    ports.foreach({ port =>
      dontTouch(port)
      port := 0.U
    })
  }
})

class WithTileHaltSinkHarness extends OverrideHarnessBinder({
  (system: HasTileHaltSinkPort, th: HasHarnessSignalReferences, ports: Seq[UInt]) => {
    ports.foreach({ port =>
      dontTouch(port)
      // port := 0.U
    })
  }
})

class WithTileCeaseSinkHarness extends OverrideHarnessBinder({
  (system: HasTileCeaseSinkPort, th: HasHarnessSignalReferences, ports: Seq[UInt]) => {
    ports.foreach({ port =>
      dontTouch(port)
// port := 0.U
    })
  }
})

class WithTileWFISinkHarness extends OverrideHarnessBinder({
  (system: HasTileWFISinkPort, th: HasHarnessSignalReferences, ports: Seq[UInt]) => {
    ports.foreach({ port =>
      dontTouch(port)
      // port := 0.U
    })
  }
})

class WithTileHartIdSourceHarness extends OverrideHarnessBinder({
  (system: HasTileHartIdSourcePort, th: HasHarnessSignalReferences, ports: Seq[UInt]) => {
    ports.foreach({ port =>
      dontTouch(port)
      port := 0.U
    })
  }
})

class WithTileResetSourceHarness extends OverrideHarnessBinder({
  (system: HasTileResetSourcePort, th: HasHarnessSignalReferences, ports: Seq[UInt]) => {
    ports.foreach({ port =>
      dontTouch(port)
      port := 0.U(18.W)
    })
  }
})

class WithTileNMISourceHarness extends OverrideHarnessBinder({
  (system: HasTileNMISourcePort, th: HasHarnessSignalReferences, ports: Seq[HeterogeneousBag[NMI]]) => {
    ports.foreach({ port =>
      dontTouch(port)
      port.head.rnmi := false.B
      port.head.rnmi_interrupt_vector := 0.U
      port.head.rnmi_exception_vector := 0.U
    })
  }
})


class WithTileOnlyHarnessBinders extends Config({
    new WithTileSlaveHarness ++
    new WithTileMasterHarness ++
    new WithTileIntSinkHarness ++
    new WithTileIntSourceHarness ++
    new WithTileHaltSinkHarness ++
    new WithTileCeaseSinkHarness ++
    new WithTileWFISinkHarness ++
    new WithTileHartIdSourceHarness ++
    new WithTileResetSourceHarness ++
    new WithTileNMISourceHarness
})
