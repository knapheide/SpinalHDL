package spinal.tester.scalatest

import spinal.core._
import spinal.core.formal._
import spinal.lib._
import spinal.lib.formal._

class FormalMuxTester extends SpinalFormalFunSuite {
  def formalmux(selWithCtrl: Boolean = false) = {
    FormalConfig
      .withBMC(20)
      .withProve(20)
      .withCover(20)
      // .withDebug
      .doVerify(new Component {
        val portCount = 5
        val dataType = Bits(8 bits)
        val dut = FormalDut(new StreamMux(dataType, portCount))

        val reset = ClockDomain.current.isResetActive

        assumeInitial(reset)

        val muxSelect = anyseq(UInt(log2Up(portCount) bit))
        val muxInputs = Vec(slave(Stream(dataType)), portCount)
        val muxOutput = master(Stream(dataType))

        dut.io.select := muxSelect
        muxOutput << dut.io.output

        assumeInitial(muxSelect < portCount)
        val selStableCond = if (selWithCtrl) past(muxOutput.isStall) else null

        when(reset || past(reset)) {
          for (i <- 0 until portCount) {
            assume(muxInputs(i).valid === False)
          }
        }

        if (selWithCtrl) {
          cover(selStableCond)
          when(selStableCond) {
            assume(stable(muxSelect))
          }
        }
        muxOutput.formalAssertsMaster()
        muxOutput.formalCovers(5)

        for (i <- 0 until portCount) {
          muxInputs(i) >> dut.io.inputs(i)
          muxInputs(i).formalAssumesSlave()
        }

        cover(muxOutput.fire)

        for (i <- 0 until portCount) {
          cover(dut.io.select === i)
          muxInputs(i).formalAssumesSlave()
        }

        when(muxSelect < portCount) {
          assert(muxOutput === muxInputs(muxSelect))
        }
      })
  }
  test("mux_sel_with_control") {
    formalmux(true)
  }
  test("mux_sel_without_control") {
    shouldFail(formalmux(false))
  }

  test("mux_with_selector") {
    FormalConfig
      .withProve(20)
      .withCover(20)
      .doVerify(new Component {
        val portCount = 5
        val dataType = Bits(8 bits)
        val muxSelector = slave(Stream(UInt(log2Up(portCount) bit)))
        val muxInputs = Vec(slave(Stream(dataType)), portCount)
        val muxOutput = master(Stream(dataType))
        val dut = FormalDut(new Component {
          val io = new Bundle {
            val inputs = Vec(slave(Stream(dataType)), portCount)
            val selector = slave(Stream(UInt(log2Up(portCount) bit)))
            val output = master(Stream(dataType))
          }
          io.output << StreamMux(io.selector, io.inputs)
        })

        val reset = ClockDomain.current.isResetActive
        assumeInitial(reset)

        muxSelector >> dut.io.selector
        muxOutput << dut.io.output
        for (i <- 0 until portCount) {
          muxInputs(i) >> dut.io.inputs(i)
        }

        assumeInitial(muxSelector.payload < portCount)
        muxSelector.formalAssumesSlave()

        when(reset || past(reset)) {
          for (i <- 0 until portCount) {
            assume(muxInputs(i).valid === False)
          }
          assume(muxSelector.valid === False)
        }

        dut.io.output.formalAssertsMaster()
        dut.io.output.formalCovers(5)
        // cover(dut.io.select =/= muxSelector.payload)
        cover(changed(muxSelector.payload) & stable(muxSelector.valid) & muxSelector.valid)
        val readyBits = muxInputs.map(_.ready).asBits()
        assert((readyBits === 0) || (CountOne(readyBits) === 1))

        for (i <- 0 until portCount) {
          cover(muxSelector.payload === i)
          muxInputs(i).formalAssumesSlave()
        }

        when(muxSelector.payload < portCount && muxInputs(muxSelector.payload).valid && muxSelector.valid) {
          assert(dut.io.output === muxInputs(muxSelector.payload))

        }
        setDefinitionName("mux_with_selector")
      })

  }
}
