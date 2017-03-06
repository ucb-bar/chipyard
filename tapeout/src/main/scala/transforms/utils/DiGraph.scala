package firrtl

import scala.collection.immutable.{HashSet, HashMap}
import scala.collection.mutable
import scala.collection.mutable.MultiMap

class MutableDiGraph[T](
  val edgeData: MultiMap[T,T] = new mutable.HashMap[T, mutable.Set[T]] with MultiMap[T, T]) {
  def contains(v: T) = edgeData.contains(v)
  def getVertices = edgeData.keys
  def getEdges(v: T) = edgeData(v)
  def addVertex(v: T): T = {
    edgeData.getOrElseUpdate(v,new mutable.HashSet[T])
    v
  }
  // Add v to keys to maintain invariant
  def addEdge(u: T, v: T) = {
    edgeData.getOrElseUpdate(v, new mutable.HashSet[T])
    edgeData.addBinding(u,v)
  }
}

object DiGraph {
  def apply[T](mdg: MutableDiGraph[T]) = new DiGraph((mdg.edgeData mapValues { _.toSet }).toMap[T, Set[T]])
  def apply[T](edgeData: MultiMap[T,T]) = new DiGraph((edgeData mapValues { _.toSet }).toMap[T, Set[T]])
}

class DiGraph[T] (val edges: Map[T, Set[T]]) {
  
  def getVertices = edges.keys
  def getEdges(v: T) = edges.getOrElse(v, new HashSet[T])

  // Graph must be acyclic for valid linearization
  def linearize(root: T) = {
    val order = new mutable.ArrayBuffer[T]
    val visited = new mutable.HashSet[T]
    def explore(v: T): Unit = {
      visited += v
      for (u  <- getEdges(v)) {
        if (!visited.contains(u)) {
          explore(u)
        }
      }
      order.append(v)
    }
    explore(root)
    order.reverse.toList
  }

  def doBFS(root: T) = {
    val prev = new mutable.HashMap[T,T]
    val queue = new mutable.Queue[T]
    queue.enqueue(root)
    while (!queue.isEmpty) {
      val u = queue.dequeue
      for (v <- getEdges(u)) {
        if (!prev.contains(v)) {
          prev(v) = u
          queue.enqueue(v)
        }
      }
    }
    prev
  }

  def reachabilityBFS(root: T) = doBFS(root).keys.toSet

  def path(start: T, end: T) = {
    val nodePath = new mutable.ArrayBuffer[T]
    val prev = doBFS(start)
    nodePath += end
    while (nodePath.last != start) {
      nodePath += prev(nodePath.last)
    }
    nodePath.toList.reverse
  }

  def findSCCs = {
    var counter: BigInt = 0
    val stack = new mutable.Stack[T]
    val onstack = new mutable.HashSet[T]
    val indices = new mutable.HashMap[T, BigInt]
    val lowlinks = new mutable.HashMap[T, BigInt]
    val sccs = new mutable.ArrayBuffer[List[T]]

    def strongConnect(v: T): Unit = {
      indices(v) = counter
      lowlinks(v) = counter
      counter = counter + 1
      stack.push(v)
      onstack += v
      for (w <- getEdges(v)) {
        if (!indices.contains(w)) {
          strongConnect(w)
          lowlinks(v) = lowlinks(v).min(lowlinks(w))
        } else if (onstack.contains(w)) {
          lowlinks(v) = lowlinks(v).min(indices(w))
        }
      }
      if (lowlinks(v) == indices(v)) {
        val scc = new mutable.ArrayBuffer[T]
        do {
          val w = stack.pop
          onstack -= w
          scc += w
        }
        while (scc.last != v);
        sccs.append(scc.toList)
      }
    }

    for (v <- getVertices) {
      strongConnect(v)
    }

    sccs.toList
  }

  def pathsInDAG(start: T): Map[T,List[List[T]]] = {
    // paths(v) holds the set of paths from start to v
    val paths = new mutable.HashMap[T,mutable.Set[List[T]]] with mutable.MultiMap[T,List[T]]
    val queue = new mutable.Queue[T]
    val visited = new mutable.HashSet[T]
    paths.addBinding(start,List(start))
    queue.enqueue(start)
    visited += start
    while (!queue.isEmpty) {
      val current = queue.dequeue
      for (v <- getEdges(current)) {
        if (!visited.contains(v)) {
          queue.enqueue(v)
          visited += v
        }
        for (p <- paths(current)) {
          paths.addBinding(v, p :+ v)
        }
      }
    }
    (paths map { case (k,v) => (k,v.toList) }).toMap
  }

  def reverse = {
    val mdg = new MutableDiGraph[T]
    edges foreach { case (u,edges) => edges.foreach({ v => mdg.addEdge(v,u) }) }
    DiGraph(mdg)
  }

  def simplify(vprime: Set[T]) = {
    val eprime = vprime.map( v => (v,reachabilityBFS(v) & vprime) ).toMap
    new DiGraph(eprime)
  }

  def transformNodes[Q](f: (T) => Q): DiGraph[Q] = {
    val eprime = edges.map({ case (k,v) => (f(k),v.map(f(_))) })
    new DiGraph(eprime)
  }

}