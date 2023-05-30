package spinal.lib.bus.tilelink.crossbar

import spinal.core._
import spinal.core.fiber.Elab
import spinal.lib._
import spinal.lib.bus.misc.SizeMapping
import spinal.lib.bus.tilelink._
import spinal.lib.bus.tilelink
import spinal.lib.logic.{Masked, Symplify}
import spinal.lib.system.tag.{MappedNode, MappedTransfers, MemoryConnection, MemoryTransfers}

import scala.collection.mutable.ArrayBuffer

class TransferFilter() extends Area{
  val up = Node.slave()
  val down = Node.master()

  new MemoryConnection {
    override def m = up
    override def s = down
    override def offset = 0
    override def mapping = List(SizeMapping(0, BigInt(1) << up.m2s.parameters.addressWidth))
    override def sToM(downs: MemoryTransfers, args: MappedNode) = downs
    populate()
  }

  val logic = Elab build new Area{
    down.m2s.proposed.load(up.m2s.proposed)
    up.m2s.supported.load(M2sSupport(
      transfers = up.m2s.proposed.transfers,
      addressWidth = up.m2s.proposed.addressWidth,
      dataWidth = down.m2s.supported.dataWidth
    ))
    down.m2s.parameters.load(up.m2s.parameters)

    up.s2m.proposed.load(down.s2m.proposed)
    down.s2m.supported.load(up.s2m.supported)
    up.s2m.parameters.load(up.m2s.parameters.withBCE match {
      case true => S2mParameters(
        down.s2m.parameters.slaves :+ S2mAgent(
          name = TransferFilter.this,
          sinkId = SizeMapping(1 << down.s2m.parameters.sinkWidth, 1 << down.s2m.parameters.sinkWidth),
          emits = S2mTransfers.none
        )
      )
      case false => S2mParameters.none()
    })

    val spec = MemoryConnection.getMemoryTransfers(up)
    val core = new tilelink.TransferFilter(
      up.bus.p.node,
      down.bus.p.node,
      spec
    )
    core.io.up << up.bus
    core.io.down >> down.bus
  }
}
