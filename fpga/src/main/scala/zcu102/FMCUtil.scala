package chipyard.fpga.zcu102

import scala.collection.immutable.HashMap

// TODO: was typed by hand, so this needs a once-over before it can be considered trustworthy

object FMCMap {
    // Take an FMC pin name and return the VCU118 package pin
    // See https://www.xilinx.com/support/documentation/boards_and_kits/vcu118/ug1224-vcu118-eval-bd.pdf
    // Pages 97-98
    // Omitted pins are not connected to a GPIO
    // ZCU106 Updates: https://docs.xilinx.com/v/u/en-US/ug1244-zcu106-eval-bd
    // Pages 105-109
    def apply(fmcPin: String): String = HashMap(
        //pg 105
        "A2"  -> "J4",
        "A3"  -> "J3",
        "A6"  -> "F2",
        "A7"  -> "F1",
        "A10" -> "K2",
        "A11" -> "K1",
        "A14" -> "L4",
        "A15" -> "L3",
        "A18" -> "P2",
        "A19" -> "P1",
        "A22" -> "H6",
        "A23" -> "H5",
        "A26" -> "F6",
        "A27" -> "F5",
        "A30" -> "K6",
        "A31" -> "K5",
        "A34" -> "M6",
        "A35" -> "M5",
        "A38" -> "P6",
        "A39" -> "P5",
        "B12" -> "M2",
        "B13" -> "M1",
        "B16" -> "T2",
        "B17" -> "T1",
        "B20" -> "L8",
        "B21" -> "L7",
        "B32" -> "N4",
        "B33" -> "N3",
        "B36" -> "R4",
        "B37" -> "R3",
        //pg 106
        "C2"  -> "G4",
        "C3"  -> "G3",
        "C6"  -> "H2",
        "C7"  -> "H1",
        "C10" -> "AC2",
        "C11" -> "AC1",
        "C14" -> "W5",
        "C15" -> "W4",
        "C18" -> "AC7",
        "C19" -> "AC6",
        "C22" -> "N9",
        "C23" -> "N8",
        "C26" -> "M10",
        "C27" -> "L10",
        "D4"  -> "G8",
        "D5"  -> "G7",
        "D8"  -> "AB4",
        "D9"  -> "AC4",
        "D11" -> "AB3",
        "D12" -> "AC3",
        "D14" -> "W2",
        "D15" -> "W1",
        "D17" -> "AB8",
        "D18" -> "AC8",
        "D20" -> "P11",
        "D21" -> "N11",
        "D23" -> "L16",
        "D24" -> "K16",
        "D26" -> "L15",
        "D27" -> "K15",
        //pg 108
        "G2"  -> "T8",
        "G3"  -> "R8",
        "G6"  -> "Y4",
        "G7"  -> "Y3",
        "G9"  -> "Y2",
        "G10" -> "Y1",
        "G12" -> "V4",
        "G13" -> "V3",
        "G15" -> "W7",
        "G16" -> "W6",
        "G18" -> "Y12",
        "G19" -> "AA12",
        "G21" -> "N13",
        "G22" -> "M13",
        "G24" -> "M15",
        "G25" -> "M14",
        "G27" -> "M11",
        "G28" -> "L11",
        "G30" -> "U9",
        "G31" -> "U8",
        "G33" -> "V8",
        "G34" -> "V7",
        "G36" -> "V12",
        "G37" -> "V11",
        "H4"  -> "AA7",
        "H5"  -> "AA6",
        "H7"  -> "V2",
        "H8"  -> "V1",
        "H10" -> "AA2",
        "H11" -> "AA1",
        "H13" -> "U5",
        "H14" -> "U4",
        "H16" -> "AB6",
        "H17" -> "AB5",
        "H19" -> "Y10",
        "H20" -> "Y9",
        "H22" -> "L13",
        "H23" -> "K13",
        "H25" -> "P12",
        "H26" -> "N12",
        "H28" -> "L12",
        "H29" -> "K12",
        "H31" -> "T7",
        "H32" -> "T6",
        "H34" -> "V6",
        "H35" -> "U6",
        "H37" -> "U11",
        "H38" -> "T11"
    )(fmcPin)
}