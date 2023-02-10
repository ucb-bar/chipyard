package chipyard.upf

import freechips.rocketchip.diplomacy.LazyModule

import scala.collection.mutable.{Map, Set, ListBuffer}

case class PowerDomain (val name: String, val modules: ListBuffer[LazyModule], 
                        val isTop: Boolean, val isGated: Boolean,
                        val highVoltage: Double, val lowVoltage: Double) {
    val mainVoltage = isGated match {
        case true => highVoltage // gated nets should have access to high voltage rail (since they are being gated to optimize power)
        case false => lowVoltage // currently assuming non-gated nets are on low voltage rail
    }
}

class Node (var graph: PowerGraph, var nodeObj: Any) {
    var edges = Set[Edge]()
    graph.nodeMap += (nodeObj -> this)

    def addEdge(edge: Edge): Unit = {
        edges.add(edge)
    }

    def getChildren(): ListBuffer[Node] = {
        var childrenList = new ListBuffer[Node]()
        for (edge <- this.edges) {
            if (edge.info == "parent->child") {
                childrenList.append(edge.node2)
            }
        }
        return childrenList
    }
}

class Edge (var node1: Node, var node2: Node, var info: String)

class PowerGraph {

    var rootObj: Any = _
    var nodeMap = Map[Any, Node]() // 1:1 mapping of objects to nodes

    
    def createGraph(rootObj: Any): PowerGraph = {
        if (this.rootObj != null) {
            throw new Exception("Root object already exists for this graph.")
        }
        new Node(this, rootObj)
        this.rootObj = rootObj
        return this
    }

    def getNode(obj: Any): Node = {
        return nodeMap match {
            case x if !x.contains(obj) => new Node(this, obj)
            case _ => nodeMap(obj)
        }
    }

    def getAllNodes(): List[Node] = {
        return nodeMap.values.toList
    }

    def getAllObjs(): List[Any] = {
        return nodeMap.keys.toList
    }

    def addChild(parentObj: Any, childObj: Any): Unit = {
        val parentNode = getNode(parentObj)
        val childNode = getNode(childObj)
        val parentEdge = new Edge(parentNode, childNode, "parent->child") // Signifies node1 = parent, node2 = child. Need a more thorough method of defining this relationship.
        parentNode.addEdge(parentEdge)
        val childEdge = new Edge(childNode, parentNode, "child->parent")
        childNode.addEdge(childEdge)
    }

    def bfsVisitor(topObj: Any, action: (Node, PowerGraph) => Unit): Unit = {
        var stack = new ListBuffer[Node]()
        val topNode = getNode(topObj)
        stack.append(topNode)
        while (stack.length > 0) {
            val node = stack.remove(0)
            action(node, this)
            for (child <- node.getChildren()) {
                stack.append(child)
            }
        }
    }

    override def toString(): String = {
        var graphMap = Map[Int, ListBuffer[Node]]()
        var stack = new ListBuffer[(Int, Node)]()
        stack.append((0, getNode(this.rootObj)))
        while (stack.length > 0) {
            val tup = stack.remove(0)
            val lvl = tup._1
            val node = tup._2
            if (!graphMap.contains(lvl)) {
                graphMap(lvl) = new ListBuffer[Node]()
            }
            var lvlList = graphMap(lvl)
            lvlList.append(node)
            graphMap(lvl) = lvlList
            for (child <- node.getChildren()) {
                stack.append((lvl+1, child))
            }
        }
        return graphMap.toString()
    }

}