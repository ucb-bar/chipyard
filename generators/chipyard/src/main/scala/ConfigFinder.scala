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

/**
  * Lists only Chipyard configs suitable for use with make CONFIG=<Class>.
  * Filters to classes in the `chipyard` package hierarchy that extend Config.
  */
object ChipyardConfigFinder {
    def main(args: Array[String]) = {
        val reflections = new Reflections()
        val classes = reflections.get(SubTypes.of(classOf[Config]).asClass()).asScala
        val chipyardOnly = classes
          .map(_.getName)
          .filter { n =>
            val isChipyard = n.startsWith("chipyard.")
            val notExamples = !n.startsWith("chipyard.example.")
            val notHarness = !n.startsWith("chipyard.harness.")
            val notIOBinders = !n.startsWith("chipyard.iobinders.")
            val notConfigPkg = !n.startsWith("chipyard.config.")
            val endsWithConfig = n.split("\\.").lastOption.exists(_.endsWith("Config"))
            isChipyard && notExamples && notHarness && notIOBinders && notConfigPkg && endsWithConfig
          }
          .map(n => n.stripPrefix("chipyard."))
        val sorted = SortedSet[String]() ++ chipyardOnly
        sorted.foreach(println)
    }
}
