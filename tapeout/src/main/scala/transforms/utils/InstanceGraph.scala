package firrtl.analyses

import scala.collection.mutable

import firrtl._
import firrtl.ir._
import firrtl.Utils._
import firrtl.Mappers._

class InstanceGraph(c: Circuit) {

  private def collectInstances(insts: mutable.Set[WDefInstance])(s: Statement): Statement = s match {
    case i: WDefInstance =>
      insts += i
      i
    case _ =>
      s map collectInstances(insts)
      s
  }

  val moduleMap = c.modules.map({m => (m.name,m) }).toMap
  val childInstances =
    new mutable.HashMap[String,mutable.Set[WDefInstance]]
  for (m <- c.modules) {
    childInstances(m.name) = new mutable.HashSet[WDefInstance]
    m map collectInstances(childInstances(m.name))
  }
  val instanceGraph = new MutableDiGraph[WDefInstance]
  val instanceQueue = new mutable.Queue[WDefInstance]
  val topInstance = WDefInstance(c.main,c.main) // top instance
  instanceQueue.enqueue(topInstance)
  while (!instanceQueue.isEmpty) {
    val current = instanceQueue.dequeue
    for (child <- childInstances(current.module)) {
      if (!instanceGraph.contains(child)) {
          instanceQueue.enqueue(child)
      }
      instanceGraph.addEdge(current,child)
    }
  }

  val graph = DiGraph(instanceGraph)
  
  lazy val fullHierarchy = graph.pathsInDAG(topInstance)

  def findInstancesInHierarchy(module: String): List[List[WDefInstance]] = {
    val instances = graph.getVertices.filter(_.module == module).toList
    instances flatMap { i => fullHierarchy(i) }
  }

}
