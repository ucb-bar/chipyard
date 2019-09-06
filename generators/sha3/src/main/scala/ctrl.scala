//see LICENSE for license
//authors: Colin Schmidt, Adam Izraelevitz
package sha3

import Chisel._
import scala.collection.mutable.HashMap
import scala.collection.mutable.ArrayBuffer
import scala.util.Random
import Chisel.ImplicitConversions._
import scala.collection.mutable.HashMap
import freechips.rocketchip.tile.HasCoreParameters
import freechips.rocketchip.rocket.constants.MemoryOpConstants
import freechips.rocketchip.config._

class CtrlModule(val W: Int, val S: Int)(implicit val p: Parameters) extends Module
  with HasCoreParameters
  with MemoryOpConstants {
  val r = 2*256
  val c = 25*W - r
  val round_size_words = c/W
  val rounds = 24 //12 + 2l
  val hash_size_words = 256/W
  val bytes_per_word = W/8

  val io = new Bundle {
    val rocc_req_val      = Bool(INPUT)
    val rocc_req_rdy      = Bool(OUTPUT)
    val rocc_funct        = Bits(INPUT, 2)
    val rocc_rs1          = Bits(INPUT, 64)
    val rocc_rs2          = Bits(INPUT, 64)
    val rocc_rd           = Bits(INPUT, 5)

    val busy              = Bool(OUTPUT)

    val dmem_req_val      = Bool(OUTPUT)
    val dmem_req_rdy      = Bool(INPUT)
    val dmem_req_tag      = Bits(OUTPUT, 7)
    val dmem_req_addr     = Bits(OUTPUT, 32)
    val dmem_req_cmd      = Bits(OUTPUT, M_SZ)
    val dmem_req_size     = Bits(OUTPUT, log2Ceil(coreDataBytes + 1))

    val dmem_resp_val     = Bool(INPUT) 
    val dmem_resp_tag     = Bits(INPUT, 7)
    val dmem_resp_data    = Bits(INPUT, W)

    //Sha3 Specific signals
    val round       = UInt(OUTPUT,width=5)
    val stage       = UInt(OUTPUT,width=log2Up(S))
    val absorb      = Bool(OUTPUT)
    val aindex      = UInt(OUTPUT,width=log2Up(round_size_words))
    val init        = Bool(OUTPUT)
    val write       = Bool(OUTPUT)
    val windex      = UInt(OUTPUT,width=log2Up(hash_size_words+1))

    val buffer_out  = Bits(OUTPUT,width=W)
  }

  //RoCC HANDLER
  //rocc pipe state
  val r_idle :: r_eat_addr :: r_eat_len :: Nil = Enum(UInt(), 3)

  val msg_addr = Reg(init = UInt(0,64))
  val hash_addr= Reg(init = UInt(0,64))
  val msg_len  = Reg(init = UInt(0,64))

  val busy = Reg(init=Bool(false))

  val rocc_s = Reg(init=r_idle)
  //register inputs 
  val rocc_req_val_reg = Reg(next=io.rocc_req_val)
  val rocc_funct_reg = Reg(init = Bits(0,2))
  rocc_funct_reg := io.rocc_funct
  val rocc_rs1_reg = Reg(next=io.rocc_rs1)
  val rocc_rs2_reg = Reg(next=io.rocc_rs2)
  val rocc_rd_reg = Reg(next=io.rocc_rd)

  val dmem_resp_tag_reg = Reg(next=io.dmem_resp_tag)
  //memory pipe state
  val fast_mem = p(Sha3FastMem)
  val m_idle :: m_read :: m_wait :: m_pad :: m_absorb :: Nil = Enum(UInt(), 5)
  val mem_s = Reg(init=m_idle)

  val buffer_sram = p(Sha3BufferSram)
  //SRAM Buffer
  val buffer_mem = Mem(round_size_words, UInt(width = W))
  //Flip-Flop buffer
  val buffer = Reg(init=Vec.fill(round_size_words) { 0.U(W.W) })

  val buffer_raddr = Reg(UInt(width = log2Up(round_size_words)))
  val buffer_wen = Wire(Bool());
  buffer_wen := Bool(false) //Defaut value
  debug(buffer_wen)
  val buffer_waddr = Wire(UInt(width = W)); buffer_waddr := UInt(0)
  debug(buffer_waddr)
  val buffer_wdata = Wire(UInt(width = W)); buffer_wdata := UInt(0)
  debug(buffer_wdata)
  val buffer_rdata = Bits(width = W);
  debug(buffer_rdata)
  if(buffer_sram){ 
    when(buffer_wen) { buffer_mem.write(buffer_waddr, buffer_wdata) }
    buffer_rdata := buffer_mem(buffer_raddr)
  }

  //This is used to prevent the pad index from advancing if waiting for the sram to read
  //SRAM reads take 1 cycle
  val wait_for_sram = Reg(init = Bool(true))
  
  val buffer_valid = Reg(init = Bool(false))
  val buffer_count = Reg(init = UInt(0,5))
  val read    = Reg(init = UInt(0,32))
  val hashed  = Reg(init = UInt(0,32))
  val areg    = Reg(init = Bool(false))
  val mindex  = Reg(init = UInt(0,5))
  val windex  = Reg(init = UInt(0,log2Up(hash_size_words+1)))
  val aindex  = Reg(init = UInt(0,log2Up(round_size_words)))
  val pindex  = Reg(init = UInt(0,log2Up(round_size_words)))
  val writes_done  = Reg( init=Vec.fill(hash_size_words) { Bool(false) })
  val next_buff_val = Reg(init = Bool(false))
  if(fast_mem){
    next_buff_val := (buffer_count >= mindex) &&
                     (pindex >= UInt(round_size_words - 1))
  }else{
    next_buff_val := ((mindex >= UInt(round_size_words)) ||
                     (read >= msg_len)) &&
                     (pindex >= UInt(round_size_words - 1))
  }

  //Note that the output of io.aindex is delayed by 1 cycle
  io.aindex     := Reg(next = aindex)
  io.absorb     := areg
  areg          := Bool(false)
  if(buffer_sram){
    //when(areg) {
    //Note that the aindex used here is one cycle behind that is passed to the datapath (out of phase)
      buffer_raddr := aindex
    //}.elsewhen(mem_s === m_pad){
    when(mem_s === m_pad) {
      buffer_raddr := pindex
    }
    io.buffer_out := buffer_rdata
  }else{
    //Note that this uses the index that is passed to the datapath (in phase)
    io.buffer_out := buffer(io.aindex)
  }
  io.windex     := windex

  //misc padding signals
  val first_pad = Bits("b0000_0001")
  val last_pad  = Bits("b1000_0000")
  val both_pad  = first_pad | last_pad
  //last word with message in it
  val words_filled =// if(fast_mem){
    Mux(mindex > UInt(0), mindex - UInt(1), mindex)
  //}else{
    //mindex
  //}
  
  debug(words_filled)
  //last byte with message in it
  val byte_offset = (msg_len)%UInt(bytes_per_word)
  debug(byte_offset)

  //hasher state
  val s_idle :: s_absorb :: s_finish_abs :: s_hash :: s_write :: Nil = Enum(UInt(), 5)

  val state = Reg(init=s_idle)


  val rindex = Reg(init = UInt(rounds+1,5))
  val sindex = Reg(init = UInt(0,log2Up(S)))

  //default
  io.rocc_req_rdy := Bool(false)
  io.init := Bool(false)
  io.busy := busy
  io.round       := rindex
  io.stage       := sindex
  io.write       := Bool(true)
  io.dmem_req_val:= Bool(false)
  io.dmem_req_tag:= rindex
  io.dmem_req_addr:= Bits(0, 32)
  io.dmem_req_cmd:= M_XRD
  io.dmem_req_size:= log2Ceil(8).U

  val rindex_reg = Reg(next=rindex)


  switch(rocc_s) {
  is(r_idle) {
    io.rocc_req_rdy := !busy
    when(io.rocc_req_val && !busy){
      when(io.rocc_funct === UInt(0)){
        io.rocc_req_rdy := Bool(true)
        msg_addr  := io.rocc_rs1
        hash_addr := io.rocc_rs2
        println("Msg Addr: "+msg_addr+", Hash Addr: "+hash_addr)
        io.busy := Bool(true)
      }.elsewhen(io.rocc_funct === UInt(1)) {
        busy := Bool(true)
        io.rocc_req_rdy := Bool(true)
        io.busy := Bool(true)
        msg_len := io.rocc_rs1
      }
    }
  }
  }

  //END RoCC HANDLER
  //START MEM HANDLER


  switch(mem_s){
  is(m_idle){
  //we can start filling the buffer if we aren't writing and if we got a new message 
    //or the hashing started 
      //and there is more to read
      //and the buffer has been absorbed
    val canRead = busy && ( (read < msg_len || (read === msg_len && msg_len === UInt(0)) ) &&
                  (!buffer_valid && buffer_count === UInt(0)))
    when(canRead){
      //buffer := Vec.fill(round_size_words){Bits(0,W)}
      buffer_count := UInt(0)
      mindex := UInt(0)
      mem_s := m_read
    }.otherwise{
      mem_s := m_idle
    }
  }
  is(m_read) {
    //dmem signals
    //only read if we aren't writing
    when(state =/= s_write){
      io.dmem_req_val := read < msg_len && mindex < UInt(round_size_words)
      io.dmem_req_addr := msg_addr + (mindex << UInt(3))
      io.dmem_req_tag := mindex
      io.dmem_req_cmd := M_XRD
      io.dmem_req_size := log2Ceil(8).U

      when(io.dmem_req_rdy && io.dmem_req_val){
        mindex := mindex + UInt(1)
        read := read + UInt(8)//read 8 bytes
        if(!fast_mem){
          mem_s := m_wait
        }
      }.otherwise{
        if(!fast_mem){
          mem_s := m_read
        }
      }
      //TODO: don't like special casing this
      when(msg_len === UInt(0)){
        read := UInt(1)
        if(!fast_mem){
          mem_s := m_pad
            pindex := words_filled
        }
      }
    }
    if(fast_mem){
      //next state
      when(mindex < UInt(round_size_words - 1)){
        //TODO: in pad check buffer_count ( or move on to next thread?)
        when(msg_len > read){
          //not sure if this case will be used but this means we haven't
          //sent all the requests yet (maybe back pressure causes this)
          when((msg_len+UInt(8)) < read){
            buffer_valid := Bool(false)
            mem_s := m_pad
            pindex := words_filled
          }
          mem_s := m_read
        }.otherwise{
          //its ok we didn't send them all because the message wasn't big enough
          buffer_valid := Bool(false)
          mem_s := m_pad
            pindex := words_filled
        }
      }.otherwise{
        when(mindex < UInt(round_size_words) &&
             !(io.dmem_req_rdy && io.dmem_req_val)){
          //we are still waiting to send the last request
          mem_s := m_read
        }.otherwise{
          //we have reached the end of this chunk
          mindex := mindex + UInt(1)
          read := read + UInt(8)//read 8 bytes
          //we sent all the requests
          msg_addr := msg_addr + UInt(round_size_words << 3)
          when((msg_len < (read+UInt(8) ))){
            //but the buffer still isn't full
            buffer_valid := Bool(false)
            mem_s := m_pad
            pindex := words_filled
          }.otherwise{
            //we have more to read eventually
            mem_s := m_idle
          }
        }
      }
    }
  }
  is(m_wait){
    //the code to process read responses
    when(io.dmem_resp_val) {
      //This is read response
      if(buffer_sram){
        buffer_wen := Bool(true)
        buffer_waddr := mindex - UInt(1)
        buffer_wdata := io.dmem_resp_data
      }else{
        buffer(mindex-UInt(1)) := io.dmem_resp_data
      }
      buffer_count := buffer_count + UInt(1)

      //next state
      when(mindex < UInt(round_size_words- 1)){
        //TODO: in pad check buffer_count ( or move on to next thread?)
        when(msg_len > read){
          //not sure if this case will be used but this means we haven't
          //sent all the requests yet (maybe back pressure causes this)
          when((msg_len+UInt(8)) < read){
            buffer_valid := Bool(false)
            mem_s := m_pad
            pindex := words_filled
          }
          mem_s := m_read
        }.otherwise{
          //its ok we didn't send them all because the message wasn't big enough
          buffer_valid := Bool(false)
          mem_s := m_pad
          pindex := words_filled
        }
      }.otherwise{
        when(mindex < UInt(round_size_words) &&
             !(io.dmem_req_rdy && io.dmem_req_val)){
          //we are still waiting to send the last request
          mem_s := m_read
        }.otherwise{
          //we have reached the end of this chunk
          //mindex := mindex + UInt(1)
          //read := read + UInt(8)//read 8 bytes
          //we sent all the requests
          msg_addr := msg_addr + UInt(round_size_words << 3)
          when((msg_len < (read+UInt(8) ))){
            //but the buffer still isn't full
            buffer_valid := Bool(false)
            mem_s := m_pad
            pindex := words_filled
          }.otherwise{
            //we have more to read eventually
            buffer_valid := Bool(true)
            mem_s := m_idle
          }
        }
      }
    }
  }
  is(m_pad) {
    //local signals
    //make sure we have received all the responses for this message
    //TODO: update next_buff_val to use pindex
    buffer_valid := next_buff_val

    //only update the buffer if we have already written the mem resp to the word
    when(! (buffer_count < mindex && (pindex >= buffer_count)) ){
      //set everything to 0000_000 after end of message first
      when(pindex > words_filled && pindex < UInt(round_size_words-1)){
        //there is a special case where we need to pad on a word boundary
        //when we have to put in first_pad here rather than just all zeros
        when(byte_offset === UInt(0) && (pindex === words_filled+UInt(1))
             && words_filled > UInt(0)){
          if(buffer_sram){
            buffer_wen := Bool(true)
            buffer_waddr := pindex
            buffer_wdata := Cat(Bits(0,W-8),first_pad)
          }else{
            buffer(pindex) := Cat(Bits(0,W-8),first_pad)
          }
        }.otherwise{
          if(buffer_sram){
            buffer_wen := Bool(true)
            buffer_waddr := pindex
            buffer_wdata := Bits(0,W)
          }else{
            buffer(pindex) := Bits(0,W)
          }
        }
      }.elsewhen(pindex === UInt(round_size_words -1)){
        //this is normally when we write the last_pad but we might end up writing both_pad
        //we write both pad if we filled all of the words
          //and all but one of the bytes
        when(words_filled === UInt(round_size_words - 1)){
          when(byte_offset === UInt(bytes_per_word -1)){
            //together with the first pad
            if(buffer_sram){
              when(wait_for_sram === Bool(false)){
                buffer_wen := Bool(true)
                buffer_waddr := pindex
                buffer_wdata := Cat(both_pad, buffer_rdata(55,0))
              }
            }else{
              buffer(pindex) := Cat(both_pad, buffer(pindex)(55,0))
            }
          }.elsewhen(byte_offset === UInt(0)){
            //do nothing since we hit the exact size of the word
          }
        }.otherwise{
          //at the end of the last word
          //we clear the word if we didn't fill it
          when(words_filled < UInt(round_size_words - 1)){
            if(buffer_sram){
              buffer_wen := Bool(true)
              buffer_waddr := pindex
              buffer_wdata := Cat(last_pad, Bits(0,(bytes_per_word-1)*8))
            }else{
              buffer(pindex) := Cat(last_pad, Bits(0,(bytes_per_word-1)*8))
            }
          }.otherwise{
            if(buffer_sram){
              when(wait_for_sram === Bool(false)){
                buffer_wen := Bool(true)
                buffer_waddr := pindex
                buffer_wdata := Cat(last_pad, buffer_rdata((bytes_per_word-1)*8-1,0))
              }
            }else{
              buffer(pindex) := Cat(last_pad, buffer(pindex)((bytes_per_word-1)*8-1,0))
            }
          }
        }
      }.elsewhen(pindex === words_filled){
        //normally this is when we need to write the first_pad
        when(byte_offset =/= UInt(0)) {
          //not last byte so we put first pad here
          when(byte_offset === UInt(1)){
            if(buffer_sram){
              when(wait_for_sram === Bool(false)){
                //SRAM was allowed 1 cycle to read
                buffer_wen := Bool(true)
                buffer_waddr := pindex
                buffer_wdata := Cat(first_pad,buffer_rdata(7,0))
                //the pindex is still the same as last cycle and buffer_rdata contains buffer(pindex)
              }
            }else{
              buffer(pindex) := Cat(first_pad,buffer(pindex)(7,0))
            }
          }.elsewhen(byte_offset === UInt(2)){
            if(buffer_sram){
              when(wait_for_sram === Bool(false)){
                buffer_wen := Bool(true)
                buffer_waddr := pindex
                buffer_wdata := Cat(first_pad,buffer_rdata(15,0))
              }
            }else{
              buffer(pindex) := Cat(first_pad,buffer(pindex)(15,0))
            }
          }.elsewhen(byte_offset === UInt(3)){
            if(buffer_sram){
              when(wait_for_sram === Bool(false)){
                buffer_wen := Bool(true)
                buffer_waddr := pindex
                buffer_wdata := Cat(first_pad,buffer_rdata(23,0))
              }
            }else{
              buffer(pindex) := Cat(first_pad,buffer(pindex)(23,0))
            }
          }.elsewhen(byte_offset === UInt(4)){
            if(buffer_sram){
              when(wait_for_sram === Bool(false)){
                buffer_wen := Bool(true)
                buffer_waddr := pindex
                buffer_wdata := Cat(first_pad,buffer_rdata(31,0))
              }
            }else{
              buffer(pindex) := Cat(first_pad,buffer(pindex)(31,0))
            }
          }.elsewhen(byte_offset === UInt(5)){
            if(buffer_sram){
              when(wait_for_sram === Bool(false)){
                buffer_wen := Bool(true)
                buffer_waddr := pindex
                buffer_wdata := Cat(first_pad,buffer_rdata(39,0))
              }
            }else{
              buffer(pindex) := Cat(first_pad,buffer(pindex)(39,0))
            }
          }.elsewhen(byte_offset === UInt(6)){
            if(buffer_sram){
              when(wait_for_sram === Bool(false)){
                buffer_wen := Bool(true)
                buffer_waddr := pindex
                buffer_wdata := Cat(first_pad,buffer_rdata(47,0))
              }
            }else{
              buffer(pindex) := Cat(first_pad,buffer(pindex)(47,0))
            }
          }.elsewhen(byte_offset === UInt(7)){
            if(buffer_sram){
              when(wait_for_sram === Bool(false)){
                buffer_wen := Bool(true)
                buffer_waddr := pindex
                buffer_wdata := Cat(first_pad,buffer_rdata(55,0))
              }
            }else{
              buffer(pindex) := Cat(first_pad,buffer(pindex)(55,0))
            }
          }
        }.otherwise{
          //this is only valid if we didn't fill any words
          when(words_filled === UInt(0) && byte_offset === UInt(0)){
            if(buffer_sram){
              buffer_wen := Bool(true)
              buffer_waddr := pindex
              buffer_wdata := Cat(Bits(0,W-8),first_pad)
            }else{
              buffer(pindex) := Cat(Bits(0,W-8),first_pad)
            }
          }
        }
      }
    }

    //next state 
    when(next_buff_val){
    //we have received all responses so the buffer is as full as it will get
      mindex := UInt(0)//reset this for absorb
      when(areg){
        //we already started absorbing so skip to idle and go to next thread
        buffer_count := UInt(0)
        mindex := UInt(0)//reset this for absorb
        mem_s := m_idle
        pindex := UInt(0)
        wait_for_sram := Bool(true)
      }.otherwise{
        mem_s := m_absorb
        pindex := UInt(0)
        wait_for_sram := Bool(true)
      }
    }.otherwise{
      //don't move pindex if we haven't received a response for this index
      when(buffer_count < mindex && (pindex >= buffer_count) ){
        mem_s := m_pad
      }.otherwise{
        if(buffer_sram){
          //With SRAM, we need to increment pindex every other cycle
          when(wait_for_sram === Bool(false)){
            pindex := pindex + UInt(1)
            wait_for_sram := Bool(true)
          }.otherwise{
            wait_for_sram := Bool(false)
          }
        }
        else{
          //Register buffer does not need to wait a cycle
          pindex := pindex + UInt(1)
        }
        mem_s := m_pad
      }
    }
  }
  is(m_absorb){
    buffer_valid := Bool(true)
    //move to idle when we know this thread was absorbed
    when(aindex >= UInt(round_size_words-1)){
      mem_s := m_idle
    }
  }
  }
  //the code to process read responses
  if(fast_mem){
    when(io.dmem_resp_val) {
      when(io.dmem_resp_tag(4,0) < UInt(round_size_words)){
        //This is read response
        if(buffer_sram){
          buffer_wen := Bool(true)
          buffer_waddr := io.dmem_resp_tag(4,0)
          buffer_wdata := io.dmem_resp_data
        }else{
          buffer(io.dmem_resp_tag(4,0)) := io.dmem_resp_data
        }
        buffer_count := buffer_count + UInt(1)
      }
    }
    when(buffer_count >= (mindex) &&
         (mindex >= UInt(round_size_words))){// ||
         //read(i) > msg_len(i))){
      when(read > msg_len){
        //padding needed
      }.otherwise{
        //next cycle the buffer will be valid
        buffer_valid := Bool(true)
      }
    }
  }
  //END MEM HANDLER


  switch(state) {
  is(s_idle) {
  val canAbsorb = busy && (rindex_reg >= UInt(rounds) && buffer_valid && hashed <= msg_len)
    when(canAbsorb){
      busy  := Bool(true)
      state := s_absorb
    }.otherwise{
      state := s_idle
    }
  }
  is(s_absorb){
    io.write := !areg //Bool(false)
    areg := Bool(true)
    aindex := aindex + UInt(1)
    when(io.aindex >= UInt(round_size_words-1)){
      rindex := UInt(0)
      sindex := UInt(0)
      aindex := UInt(0)
      //Delayed 1 cycle for sram
      //areg   := Bool(false)
      buffer_valid := Bool(false)
      buffer_count := UInt(0)
      hashed := hashed + UInt(8*round_size_words)
      state := s_finish_abs
    }.otherwise{
      state := s_absorb
    }
  }
  is(s_finish_abs){
    //There is a 1 cycle delay for absorb to finish (since the SRAM read is delayed by 1 cycle)
    areg  := Bool(false)
    state := s_hash
  }
  is(s_hash){
    when(rindex < UInt(rounds)){
      when(sindex < UInt(S-1)){
        sindex := sindex + UInt(1)
        io.round := rindex
        io.stage := sindex
      io.write := Bool(false)
        state := s_hash
      }.otherwise{
      sindex := UInt(0)
      rindex := rindex + UInt(1)
      io.round := rindex
      io.write := Bool(false)
      state := s_hash
      }
    }.otherwise{
      io.write := Bool(true)
      when(hashed > msg_len || (hashed === msg_len && rindex === UInt(rounds))){
        windex := UInt(0)
        state := s_write
      }.otherwise{
        state := s_idle
      }
    }
  }
  is(s_write){
    //we are writing 
    //request
    io.dmem_req_val := windex < UInt(hash_size_words)
    io.dmem_req_addr := hash_addr + (windex << UInt(3))
    io.dmem_req_tag := UInt(round_size_words) + windex
    io.dmem_req_cmd := M_XWR

    when(io.dmem_req_rdy){
      windex := windex + UInt(1)
    }

    //response
    when(io.dmem_resp_val){
      //there is a response from memory
      when(dmem_resp_tag_reg(4,0) >= UInt(round_size_words)) {
        //this is a response to a write
        writes_done(dmem_resp_tag_reg(4,0)-UInt(round_size_words)) := Bool(true)
      }
    }
    when(writes_done.reduce(_&&_)){
      //all the writes have been responded to
      //this is essentially reset time
      busy := Bool(false)

      writes_done := Vec.fill(hash_size_words){Bool(false)}
      windex := UInt(hash_size_words)
      rindex := UInt(rounds+1)
      buffer_count := UInt(0)
      msg_addr := UInt(0)
      hash_addr := UInt(0)
      msg_len := UInt(0)
      hashed := UInt(0)
      read := UInt(0)
      buffer_valid := Bool(false)
      io.init := Bool(true)
      state := s_idle
    }.otherwise{
      state := s_write
    }
  }
  }
}
