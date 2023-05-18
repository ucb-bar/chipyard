package chipyard.unittest

import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.util.{ElaborationArtefacts, PlusArgArtefacts}

class UnitTestSuite(implicit p: Parameters) extends freechips.rocketchip.unittest.UnitTestSuite {
  ElaborationArtefacts.add("plusArgs", PlusArgArtefacts.serialize_cHeader)
}
