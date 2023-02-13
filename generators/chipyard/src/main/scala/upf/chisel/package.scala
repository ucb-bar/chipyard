// See LICENSE for license details
package chipyard.upf

package object chisel {

  import chisel3.experimental.{BaseModule}

  type UPFFunction = PartialFunction[BaseModule, Seq[ChiselUPFElement]]

}
