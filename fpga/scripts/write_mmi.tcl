proc write_mmi {filepath inst} {
    current_instance
    current_instance $inst
    set chn [open $filepath w]
    puts $chn "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
    puts $chn "<MemInfo Version=\"1\" Minor=\"0\">"
    puts $chn "\t<Processor Endianness=\"Little\" InstPath=\"${inst}\">"
    set brams [dict create]
    foreach cell [get_cells -hierarchical -filter { PRIMITIVE_GROUP =~ BLOCKRAM }] {
        set name [get_property RTL_RAM_NAME $cell]
        dict update brams $name name {
            dict lappend name cells $cell
            dict set name size [get_property RTL_RAM_BITS $cell]
        }
    }
    proc compare {a b} {
        set a_addr [get_property bram_addr_begin $a]
        set b_addr [get_property bram_addr_begin $b]
        if {$a_addr > $b_addr} {
            return 1
        } elseif {$a_addr < $b_addr} {
            return -1
        }
        set a_slice [get_property bram_slice_begin $a]
        set b_slice [get_property bram_slice_begin $b]
        if {$a_slice > $b_slice} {
            return 1
        } elseif {$a_slice < $b_slice} {
            return -1
        }
        return 0
    }
    dict for {name desc} $brams {
        dict with desc {
            puts $chn "\t\t<AddressSpace Name=\"${name}\" Begin=\"0\" End=\"[expr $size >> 3]\">"
            puts $chn "\t\t\t<BusBlock>"
            foreach cell [lsort -command compare $cells] {
                set type [switch [get_property REF_NAME $cell] \
                    RAMB36E2 {expr {"RAMB32"}} \
                    RAMB36E1 {expr {"RAMB32"}}]
                set loc [lindex [split [get_property LOC $cell] "_"] 1]
                set lsb [get_property bram_slice_begin $cell]
                set msb [get_property bram_slice_end $cell]
                set addr_bgn [get_property bram_addr_begin $cell]
                set addr_end [get_property bram_addr_end $cell]
                puts $chn "\t\t\t\t<BitLane MemType=\"${type}\" Placement=\"${loc}\">"
                puts $chn "\t\t\t\t\t<DataWidth MSB=\"${msb}\" LSB=\"${lsb}\"/>"
                puts $chn "\t\t\t\t\t<AddressRange Begin=\"${addr_bgn}\" End=\"${addr_end}\"/>"
                puts $chn "\t\t\t\t\t<Parity ON=\"false\" NumBits=\"0\"/>"
                puts $chn "\t\t\t\t</BitLane>"
            }
            puts $chn "\t\t\t</BusBlock>"
            puts $chn "\t\t</AddressSpace>"
        }
    }
    puts $chn "\t</Processor>"
    puts $chn "\t<Config>"
    puts $chn "\t\t<Option Name=\"Part\" Val=\"[get_property PART [current_project]]\"/>"
    puts $chn "\t</Config>"
    puts $chn "</MemInfo>"
    close $chn
    current_instance

}

if {$argc != 3} {
    puts $argc
    puts {Error: Invalid number of arguments}
    puts {Usage: write_mmi.tcl checkpoint mmi_file instance}
}

lassign $argv checkpoint mmi_file instance

open_checkpoint $checkpoint
write_mmi $mmi_file $instance
