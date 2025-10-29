package org.chipsalliance.diplomacy.aop

import org.chipsalliance.cde.config.Parameters
import chisel3.Data
import org.chipsalliance.diplomacy.nodes.OutwardNode

/** Contains information about an inward edge of a node */
case class InwardEdge[Bundle <: Data, EdgeInParams](
  params: Parameters,
  bundle: Bundle,
  edge:   EdgeInParams,
  node:   OutwardNode[_, _, Bundle])
