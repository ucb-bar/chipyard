package motion

import firesim.firesim._
import freechips.rocketchip.config.Parameters

class FireSimTop(implicit params: Parameters) extends FireSimNoNIC()
with HasMotion { outer =>
  override lazy val module = new FireSimNoNICModuleImp(this) {
    val motion = IO(new MotionIO)
    motion <> outer.motion.module.io.motion
  }
}

