package chipyard.fpga.vcu118.bringup

import scala.collection.mutable.{LinkedHashMap}

object BringupGPIOs {
    // map of the pin name (akin to die pin name) to (fpga package pin, IOSTANDARD)
    val pinMapping = LinkedHashMap(
        // these connect to LEDs and switches on the VCU118 (and use 1.2V)
        "led0"  -> ("AT32", "LVCMOS12"), // 0
        "led1"  -> ("AV34", "LVCMOS12"), // 1
        "led2"  -> ("AY30", "LVCMOS12"), // 2
        "led3"  -> ("BB32", "LVCMOS12"), // 3
        "led4"  -> ("BF32", "LVCMOS12"), // 4
        "led5"  -> ("AU37", "LVCMOS12"), // 5
        "led6"  -> ("AV36", "LVCMOS12"), // 6
        "led7"  -> ("BA37", "LVCMOS12"), // 7
        "sw0"   -> ("B17", "LVCMOS12"), // 8
        "sw1"   -> ("G16", "LVCMOS12"), // 9
        "sw2"   -> ("J16", "LVCMOS12"), // 10
        "sw3"   -> ("D21", "LVCMOS12") // 11
    )

    // return list of names (ordered)
    def names: Seq[String] = pinMapping.keys.toSeq

    // return number of GPIOs
    def width: Int = pinMapping.size
}
