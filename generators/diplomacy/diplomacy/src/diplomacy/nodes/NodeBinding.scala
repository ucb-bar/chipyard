package org.chipsalliance.diplomacy.nodes

/** Enumeration of types of binding operations. */
sealed trait NodeBinding

/** Only connects a single edge. */
case object BIND_ONCE extends NodeBinding {
  override def toString: String = "once"
}

/** Connects N (N >= 0) edges.
  *
  * The other side of the edge determines cardinality.
  */
case object BIND_QUERY extends NodeBinding {
  override def toString: String = "query"
}

/** Connect N (N >= 0) edges.
  *
  * Our side of the edge determines cardinality.
  */
case object BIND_STAR extends NodeBinding {
  override def toString: String = "star"
}

/** Connect N (N >= 0) connections.
  *
  * The number of edges N will be determined by either the right or left side, once the direction ([[BIND_STAR]] or
  * [[BIND_QUERY]]) is determined by the other connections as well.
  */
case object BIND_FLEX extends NodeBinding {
  override def toString: String = "flex"
}
