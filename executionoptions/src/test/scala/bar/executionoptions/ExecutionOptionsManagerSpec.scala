// See LICENSE for license details.

package bar.executionoptions

import org.scalatest.{Matchers, FreeSpec}

class ExecutionOptionsManagerSpec extends FreeSpec with Matchers {
  "ExecutionOptionsManager is a container for one more more ComposableOptions Block" - {
    "It has a default CommonOptionsBlock" in {
      val manager = new ExecutionOptionsManager("test")
      manager.commonOptions.targetDirName should be ("test_run_dir")
    }
    "But can override defaults like this" in {
      val manager = new ExecutionOptionsManager("test") { commonOptions = CommonOptions(topName = "dog") }
      manager.commonOptions shouldBe a [CommonOptions]
      manager.topName should be ("dog")
      manager.commonOptions.topName should be ("dog")
    }
    "The add method should put a new version of a given type the manager" in {
      val manager = new ExecutionOptionsManager("test") { commonOptions = CommonOptions(topName = "dog") }
      val initialCommon = manager.commonOptions
      initialCommon.topName should be ("dog")

      manager.commonOptions = CommonOptions(topName = "cat")

      val afterCommon = manager.commonOptions
      afterCommon.topName should be ("cat")
      initialCommon.topName should be ("dog")
    }
  }
}
