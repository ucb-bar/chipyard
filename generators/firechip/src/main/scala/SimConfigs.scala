//See LICENSE for license details.
package firesim.firesim

import freechips.rocketchip.config.{Parameters, Config, Field}

import midas.{EndpointKey}
import midas.widgets.{EndpointMap}
import midas.models._

import firesim.endpoints._
import firesim.configs._

/*******************************************************************************
* Full PLATFORM_CONFIG Configurations. These set simulator parameters.
*
* In general, if you're adding or removing features from any of these, you
* should CREATE A NEW ONE, WITH A NEW NAME. This is because the manager
* will store this name as part of the tags for the AGFI, so that later you can
* reconstruct what is in a particular AGFI. These tags are also used to
* determine which driver to build.
*******************************************************************************/
class FireSimConfig extends Config(
  new WithSerialWidget ++
  new WithUARTWidget ++
  new WithSimpleNICWidget ++
  new WithBlockDevWidget ++
  new WithDefaultMemModel ++
  new WithTracerVWidget ++
  new BasePlatformConfig)

class FireSimClockDivConfig extends Config(
  new WithDefaultMemModel(clockDivision = 2) ++
  new FireSimConfig)

class FireSimDDR3Config extends Config(
  new FCFS16GBQuadRank ++
  new FireSimConfig)

class FireSimDDR3LLC4MBConfig extends Config(
  new FCFS16GBQuadRankLLC4MB ++
  new FireSimConfig)

class FireSimDDR3FRFCFSConfig extends Config(
  new FRFCFS16GBQuadRank ++
  new FireSimConfig)

class FireSimDDR3FRFCFSLLC4MBConfig extends Config(
  new FRFCFS16GBQuadRankLLC4MB ++
  new FireSimConfig)

class FireSimDDR3FRFCFSLLC4MB3ClockDivConfig extends Config(
  new FRFCFS16GBQuadRankLLC4MB3Div ++
  new FireSimConfig)

