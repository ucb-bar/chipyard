//See LICENSE for license details.
package chipyard.clocking

import freechips.rocketchip.prci._

class SimplePllConfigurationSpec extends org.scalatest.flatspec.AnyFlatSpec {

    def genConf(freqMHz: Iterable[Double]): SimplePllConfiguration = new SimplePllConfiguration(
      "testPLL",
      freqMHz.map({ f => ClockSinkParameters(
        name = Some(s"desiredFreq_$f"),
        take = Some(ClockParameters(f))) }).toSeq,
      maximumAllowableFreqMHz = 16000.0)

    def trySuccessfulConf(requestedFreqs: Seq[Double], expected: Double): Unit = {
      val freqStr = requestedFreqs.mkString(", ")
      it should s"select a reference of ${expected} MHz for ${freqStr} MHz" in {
        val conf = genConf(requestedFreqs)
        conf.emitSummaries
        assert(expected == conf.referenceFreqMHz)
      }
    }

    trySuccessfulConf(Seq(3200.0, 1600.0, 1000.0, 100.0), 16000.0)
    trySuccessfulConf(Seq(3200.0, 1600.0), 3200.0)
    trySuccessfulConf(Seq(3200.0, 1066.7), 3200.0)
    trySuccessfulConf(Seq(100, 50, 6.67), 100)
    trySuccessfulConf(Seq(1, 2, 3, 5, 7, 11, 13).map(_ * 10.0), 1560.0)
}
