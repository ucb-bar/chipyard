
package chipyard.fpga.zcu102

/*
  This file has been replaced by 
  new chipyard.iobinders.WithUARTTSIPunchthrough ++
  new chipyard.iobinders.WithSPIIOPunchthrough ++
*/

// import chisel3._
// import chisel3.reflect.DataMirror

// import freechips.rocketchip.diplomacy.{ResourceBinding, Resource, ResourceAddress, InModuleBody}
// import freechips.rocketchip.subsystem.{BaseSubsystem}
// import freechips.rocketchip.util.{HeterogeneousBag}
// import freechips.rocketchip.tilelink.{TLBundle}

// import sifive.blocks.devices.uart._
// import sifive.blocks.devices.spi._

// import chipyard.{CanHaveMasterTLMemPort}
// import chipyard.iobinders.{OverrideIOBinder, OverrideLazyIOBinder}

// class WithUARTIOPassthrough extends OverrideIOBinder({
//   (system: HasPeripheryUART) => {
//     val io_uart_pins_temp = system.uart.zipWithIndex.map { case (dio, i) => IO(dio.cloneType).suggestName(s"uart_$i") }
//     (io_uart_pins_temp zip system.uart).map { case (io, sysio) =>
//       io <> sysio
//     }
//     (io_uart_pins_temp, Nil)
//   }
// })




// class WithSPIIOPassthrough  extends OverrideLazyIOBinder({
//   (system: HasPeripherySPI) => {
//     // attach resource to 1st SPI
//     ResourceBinding {
//       Resource(new MMCDevice(system.tlSpiNodes.head.device, 1), "reg").bind(ResourceAddress(0))
//     }

//     InModuleBody {
//       system.asInstanceOf[BaseSubsystem].module match { case system: HasPeripherySPIModuleImp => {
//         val io_spi_pins_temp = system.spi.zipWithIndex.map { case (dio, i) => IO(dio.cloneType).suggestName(s"spi_$i") }
//         (io_spi_pins_temp zip system.spi).map { case (io, sysio) =>
//           io <> sysio
//         }
//         (io_spi_pins_temp, Nil)
//       } }
//     }
//   }
// })

// class WithTLIOPassthrough extends OverrideIOBinder({
//   (system: CanHaveMasterTLMemPort) => {
//     val io_tl_mem_pins_temp = IO(DataMirror.internal.chiselTypeClone[HeterogeneousBag[TLBundle]](system.mem_tl)).suggestName("tl_slave")
//     io_tl_mem_pins_temp <> system.mem_tl
//     (Seq(io_tl_mem_pins_temp), Nil)
//   }
// })


