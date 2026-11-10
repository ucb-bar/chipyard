package chipyard.fpga.vcu118

import org.chipsalliance.cde.config.{Config, Field, View}
import freechips.rocketchip.subsystem.PeripheryBusKey

import sifive.blocks.devices.spi.PeripherySPIKey

/** SPI clock divisors for the sdboot boot ROM, which the vcu118, vc707 and zcu104 targets share.
  *
  * By default sdboot derives both divisors from the peripheral bus clock, which is the SPI
  * controller's clock when it is attached through PeripherySPIKey. A design that attaches the SPI
  * controller another way, to another bus or across a clock crossing, sets them here instead.
  * `init` is used for card identification, which must run at 100-400 kHz; `copy` for the payload
  * transfer. The controller produces SCLK = f_spi / (2 * (div + 1)).
  */
case class SDBootSPIDivisors(init: Int, copy: Int)
case object SDBootSPIDivisorsKey extends Field[Option[SDBootSPIDivisors]](None)

class WithSDBootSPIDivisors(init: Int, copy: Int) extends Config((site, here, up) => {
  case SDBootSPIDivisorsKey => Some(SDBootSPIDivisors(init, copy))
})

object SDBoot {
  /** The make variables that build sdboot for this design. */
  def makeVars(site: View): String = {
    // the SPI controller is on the peripheral bus, so its clock sets the SPI divisor
    val freqMHz = (site(PeripheryBusKey).dtsFrequency.get / (1000 * 1000)).toLong
    val divisors = site(SDBootSPIDivisorsKey).map { d =>
      val bits = site(PeripherySPIKey).headOption.map(_.divisorBits).getOrElse(12)
      val max = (1 << bits) - 1
      require(d.init >= 0 && d.init <= max && d.copy >= 0 && d.copy <= max,
        s"$d: each divisor must fit the SPI controller's $bits-bit sckdiv (0 to $max)")
      s" SPI_INIT_DIV=${d.init} SPI_DIV=${d.copy}"
    }.getOrElse("")
    s"PBUS_CLK=${freqMHz}${divisors}"
  }
}
