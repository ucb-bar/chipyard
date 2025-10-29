package org.chipsalliance.diplomacy.aop

import org.chipsalliance.cde.config.Parameters
import chisel3.Data
import org.chipsalliance.diplomacy.nodes.InwardNode

/** Contains information about an outward edge of a node */
case class OutwardEdge[Bundle <: Data, EdgeOutParams](
  params: Parameters,
  bundle: Bundle,
  edge:   EdgeOutParams,
  node:   InwardNode[_, _, Bundle])
