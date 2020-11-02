//See LICENSE for license details.
package chipyard.clocking

import freechips.rocketchip.prci._

class SimplePllConfigurationSpec extends org.scalatest.FlatSpec {

    def conf(freqMHz: Iterable[Double]): SimplePllConfiguration = new SimplePllConfiguration("test",
      freqMHz.map({ f => ClockSinkParameters(
        name = Some(s"desiredFreq_$f"),
        take = Some(ClockParameters(f))) }).toSeq)

    def tryConf(freqMHz: Double*): Unit = {
      val freqStr = freqMHz.mkString(", ")
      it should s"configure for ${freqStr} MHz" in { conf(freqMHz) }
    }

    tryConf(3200.0, 1600.0, 1000.0, 100.0)
    tryConf(3200.0, 1600.0)
    tryConf(3200.0, 1066.7)
}
