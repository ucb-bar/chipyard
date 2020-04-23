package chipyard.unittest

import freechips.rocketchip.config.Parameters
import freechips.rocketchip.util.{ElaborationArtefacts, PlusArgArtefacts}

class UnitTestSuite(implicit p: Parameters) extends freechips.rocketchip.unittest.UnitTestSuite {
  ElaborationArtefacts.add("plusArgs", PlusArgArtefacts.serialize_cHeader)
}
