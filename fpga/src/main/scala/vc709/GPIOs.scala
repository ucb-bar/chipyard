package chipyard.fpga.vc709

import scala.collection.mutable.{LinkedHashMap}

object GPIOs {

    // map of the pin name (akin to die pin name) to (fpga package pin, IOSTANDARD)
    val pinMapping = LinkedHashMap(
        // these connect to LEDs and switches on the VC709 (and use 1.8V)
        "led0"  -> ("AM39", "LVCMOS18"), // 0
        "led1"  -> ("AN39", "LVCMOS18"), // 1
        "led2"  -> ("AR37", "LVCMOS18"), // 2
        "led3"  -> ("AT37", "LVCMOS18"), // 3
        "led4"  -> ("AR35", "LVCMOS18"), // 4
        "led5"  -> ("AP41", "LVCMOS18"), // 5
        "led6"  -> ("AP42", "LVCMOS18"), // 6
        "led7"  -> ("AU39", "LVCMOS18"), // 7
        "sw_n"  -> ("AR40", "LVCMOS18"), // N
        "sw_e"  -> ("AU38", "LVCMOS18"), // E
        "sw_s"  -> ("AP40", "LVCMOS18"), // S
        "sw_w"  -> ("AW40", "LVCMOS18"), // W
        "sw_c"  -> ("AV39", "LVCMOS18"), // C
        "sw_0"  -> ("AV30", "LVCMOS18"), // 0
        "sw_1"  -> ("AY33", "LVCMOS18"), // 1
        "sw_2"  -> ("BA31", "LVCMOS18"), // 2
        "sw_3"  -> ("BA32", "LVCMOS18"), // 3
        "sw_4"  -> ("AW30", "LVCMOS18"), // 4
        "sw_5"  -> ("AY30", "LVCMOS18"), // 5
        "sw_6"  -> ("BA30", "LVCMOS18"), // 6
        "sw_7"  -> ("BB31", "LVCMOS18")  // 7
    )

    // return list of names (ordered)
    def names: Seq[String] = pinMapping.keys.toSeq

    // return number of GPIOs
    def width: Int = pinMapping.size
}
