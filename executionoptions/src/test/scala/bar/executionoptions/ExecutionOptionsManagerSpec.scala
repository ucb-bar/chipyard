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
    "The proper way to change an option in is to copy the existing sub-option with the desired new value" in {
      val manager = new ExecutionOptionsManager("test") {
        commonOptions = CommonOptions(targetDirName = "fox", topName = "dog")
      }
      val initialCommon = manager.commonOptions
      initialCommon.targetDirName should be ("fox")
      initialCommon.topName should be ("dog")

      manager.commonOptions = manager.commonOptions.copy(topName = "cat")

      val afterCommon = manager.commonOptions
      afterCommon.topName should be ("cat")
      afterCommon.targetDirName should be ("fox")
      initialCommon.topName should be ("dog")
    }
    "The following way of changing a manager should not be used, as it could alter other desired values" - {
      "Note that the initial setting targetDirName is lost when using this method" in {
        val manager = new ExecutionOptionsManager("test") {
          commonOptions = CommonOptions(targetDirName = "fox", topName = "dog")
        }
        val initialCommon = manager.commonOptions
        initialCommon.topName should be("dog")

        manager.commonOptions = CommonOptions(topName = "cat")

        val afterCommon = manager.commonOptions
        initialCommon.topName should be("dog")
        afterCommon.topName should be("cat")

        // This is probably bad
        afterCommon.targetDirName should not be "fox"
      }
    }
  }
}
