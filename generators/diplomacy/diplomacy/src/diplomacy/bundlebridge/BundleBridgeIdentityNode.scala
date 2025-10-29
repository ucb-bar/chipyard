package org.chipsalliance.diplomacy.bundlebridge

import chisel3.Data

import org.chipsalliance.diplomacy.ValName
import org.chipsalliance.diplomacy.nodes.IdentityNode

case class BundleBridgeIdentityNode[T <: Data](
)(
  implicit valName: ValName)
    extends IdentityNode(new BundleBridgeImp[T])()
