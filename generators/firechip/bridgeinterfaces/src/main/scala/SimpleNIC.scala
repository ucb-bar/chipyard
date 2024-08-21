// See LICENSE for license details.

package firechip.bridgeinterfaces

import chisel3._
import chisel3.util._

object IceNetConsts {
  val NET_IF_WIDTH = 64
  val NET_IF_BYTES = NET_IF_WIDTH/8
  val NET_LEN_BITS = 16

  val NET_IP_ALIGN = 2
  val ETH_HEAD_BYTES = 16

  val ETH_STANDARD_MTU = 1500
  val ETH_STANDARD_MAX_BYTES = ETH_STANDARD_MTU + ETH_HEAD_BYTES
  val ETH_STANDARD_MAX_FLITS = (ETH_STANDARD_MAX_BYTES - 1) / NET_IF_BYTES + 1

  val ETH_JUMBO_MTU = 9000
  val ETH_JUMBO_MAX_BYTES = ETH_JUMBO_MTU + ETH_HEAD_BYTES
  val ETH_JUMBO_MAX_FLITS = (ETH_JUMBO_MAX_BYTES - 1) / NET_IF_BYTES + 1

  val ETH_SUPER_JUMBO_MTU = 64 << 10
  val ETH_SUPER_JUMBO_MAX_BYTES = ETH_SUPER_JUMBO_MTU + ETH_HEAD_BYTES
  val ETH_SUPER_JUMBO_MAX_FLITS = (ETH_SUPER_JUMBO_MAX_BYTES - 1) / NET_IF_BYTES + 1

  val ETH_MAC_BITS = 48
  val ETH_TYPE_BITS = 16
  val ETH_PAD_BITS = 16

  val IPV4_HEAD_BYTES = 20
  val UDP_HEAD_BYTES = 8
  val TCP_HEAD_BYTES = 20

  val RLIMIT_MAX_INC = 256
  val RLIMIT_MAX_PERIOD = 256
  val RLIMIT_MAX_SIZE = 256

  def NET_FULL_KEEP = ~0.U(NET_IF_BYTES.W)
  def ETH_BCAST_MAC = ~0.U(ETH_MAC_BITS.W)

  val IPV4_ETHTYPE = 0x0008
  val TCP_PROTOCOL = 0x06
}

class StreamChannel(val w: Int) extends Bundle {
  val data = UInt(w.W)
  val keep = UInt((w/8).W)
  val last = Bool()
}

class RateLimiterSettings extends Bundle {
  val incBits = log2Ceil(IceNetConsts.RLIMIT_MAX_INC)
  val periodBits = log2Ceil(IceNetConsts.RLIMIT_MAX_PERIOD)
  val sizeBits = log2Ceil(IceNetConsts.RLIMIT_MAX_SIZE)

  /*
   * Given a clock frequency of X, you can achieve an output bandwidth
   * of Y = X * (N / D), where N <= D, by setting inc to N and period to (D - 1).
   * The field size should be set to the number of consecutive beats that
   * can be sent before rate-limiting kicks in.
   */
  val inc = UInt(incBits.W)
  val period = UInt(periodBits.W)
  val size = UInt(sizeBits.W)
}

class PauserSettings extends Bundle {
  val threshold = UInt(16.W)
  val quanta    = UInt(16.W)
  val refresh   = UInt(16.W)
}

class NICIOvonly extends Bundle {
  val in = Flipped(Valid(new StreamChannel(IceNetConsts.NET_IF_WIDTH)))
  val out = Valid(new StreamChannel(IceNetConsts.NET_IF_WIDTH))
  val macAddr = Input(UInt(IceNetConsts.ETH_MAC_BITS.W))
  val rlimit = Input(new RateLimiterSettings)
  val pauser = Input(new PauserSettings)
}

class NICBridgeTargetIO extends Bundle {
  val clock = Input(Clock())
  val nic = Flipped(new NICIOvonly)
}
