set shell_vivado_tcl [file normalize [info script]]
set shell_vivado_idx [string last ".shell.vivado.tcl" $shell_vivado_tcl]
add_files -fileset [current_fileset -constrset] [string replace $shell_vivado_tcl $shell_vivado_idx 999 ".shell.sdc"]
add_files -fileset [current_fileset -constrset] [string replace $shell_vivado_tcl $shell_vivado_idx 999 ".shell.xdc"]
set extra_constr [string replace $shell_vivado_tcl $shell_vivado_idx 999 ".extra.shell.xdc"]
if [file exist $extra_constr] {
  add_files -fileset [current_fileset -constrset] [string replace $shell_vivado_tcl $shell_vivado_idx 999 ".extra.shell.xdc"]
}
