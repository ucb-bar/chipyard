package org.chipsalliance.diplomacy.nodes

import chisel3.Data

import org.chipsalliance.diplomacy.ValName

/** A node which does not modify the parameters nor the protocol for edges that pass through it.
  *
  * During hardware generation, [[IdentityNode]]s automatically connect their inputs to outputs.
  */
class IdentityNode[D, U, EO, EI, B <: Data](
  imp:              NodeImp[D, U, EO, EI, B]
)(
)(
  implicit valName: ValName)
    extends AdapterNode(imp)({ s => s }, { s => s }) {
  override def description           = "identity"
  override final def circuitIdentity = true
  override protected[diplomacy] def instantiate():  Seq[Dangle] = {
    val dangles = super.instantiate()
    (out.zip(in)).foreach { case ((o, _), (i, _)) => o :<>= i }
    dangles
  }
  override protected[diplomacy] def cloneDangles(): Seq[Dangle] = super.cloneDangles()
}
