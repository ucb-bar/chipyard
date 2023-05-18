package chipyard

import org.reflections.Reflections
import org.reflections.scanners.Scanners.SubTypes
import scala.jdk.CollectionConverters._
import scala.collection.{SortedSet}

import org.chipsalliance.cde.config.{Config}

object ConfigFinder {
    def main(args: Array[String]) = {
        val reflections = new Reflections()
        val classes = reflections.get(SubTypes.of(classOf[Config]).asClass()).asScala
        val sortedClasses = SortedSet[String]() ++ classes.map(_.getName)
        for (cls <- sortedClasses) {
            println(cls)
        }
    }
}
