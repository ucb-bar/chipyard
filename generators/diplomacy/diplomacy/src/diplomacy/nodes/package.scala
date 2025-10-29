package org.chipsalliance.diplomacy

import chisel3.Data

package object nodes {
  type SimpleNodeHandle[D, U, E, B <: Data] = NodeHandle[D, U, E, B, D, U, E, B]
  type AnyMixedNode                         = MixedNode[_, _, _, _ <: Data, _, _, _, _ <: Data]
}
