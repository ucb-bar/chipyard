package genevent

import chisel3._
import chisel3.util._

object GenEvent {
  var instance_ctr: Int = 0
  def apply(eventName: String, data: UInt, parent: Option[EventTag], id: Option[UInt] = None): EventTag = {
    var new_id = Wire(UInt(64.W))
    val id_ctr = RegInit(0.U(32.W))
    id_ctr := id_ctr + 1.U
    new_id := Cat(instance_ctr.asUInt(32.W), id_ctr)
    if (parent.isDefined) {
      if (id.isDefined) {
        printf(cf"{\"id\": \"0x${id.get}%x\", \"parents\": \"0x${parent.get.id}%x\", \"cycle\": \"$id_ctr\", \"event_name\": \"$eventName\", \"data\": \"0x$data%x\"}\n")
      } else {
        printf(cf"{\"id\": \"0x$new_id%x\", \"parents\": \"0x${parent.get.id}%x\", \"cycle\": \"$id_ctr\", \"event_name\": \"$eventName\", \"data\": \"0x$data%x\"}\n")
      }
    } else {
      if (id.isDefined) {
        printf(cf"{\"id\": \"0x${id.get}%x\", \"parents\": \"None\", \"cycle\": \"$id_ctr\", \"event_name\": \"$eventName\", \"data\": \"0x$data%x\"}\n")
      } else {
        printf(cf"{\"id\": \"0x$new_id%x\", \"parents\": \"None\", \"cycle\": \"$id_ctr\", \"event_name\": \"$eventName\", \"data\": \"0x$data%x\"}\n")
      }
    }
    instance_ctr += 1
    return EventTag(new_id)
  }
}

class EventTag extends Bundle {
  val id = UInt(64.W)
}

object EventTag {
  def apply(id: UInt): EventTag = {
    val tag = Wire(new EventTag)
    tag.id := id
    return tag
  }
}
