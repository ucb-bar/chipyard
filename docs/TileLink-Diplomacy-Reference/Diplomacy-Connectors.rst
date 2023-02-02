Diplomacy Connectors
====================

Nodes in a Diplomacy graph are connected to each other with edges. The Diplomacy
library provides four operators that can be used to form edges between nodes.

:=
--

This is the basic connection operator. It is the same syntax as the Chisel
uni-directional connector, but it is not equivalent. This operator connects
Diplomacy nodes, not Chisel bundles.

The basic connection operator always creates a single edge between the two
nodes.

:=\*
----

This is a "query" type connection operator. It can create multiple edges
between nodes, with the number of edges determined by the client node
(the node on the right side of the operator). This can be useful if you
are connecting a multi-edge client to a nexus node or adapter node.

:\*=
----

This is a "star" type connection operator. It also creates multiple edges,
but the number of edges is determined by the manager (left side of operator),
rather than the client. It's useful for connecting nexus nodes to multi-edge
manager nodes.

:\*=\*
------

This is a "flex" connection operator. It creates multiple edges based on
whichever side of the operator has a known number of edges. This can be used
in generators where the type of node on either side isn't known until runtime.
