//******************************************************************************
// Copyright (c) 2015 - 2019, The Regents of the University of California (Regents).
// All Rights Reserved. See LICENSE and LICENSE.SiFive for license details.
//------------------------------------------------------------------------------
// Author: Christopher Celio, Abraham Gonzalez, Ben Korpan, Jerry Zhao
//------------------------------------------------------------------------------

package example

import chisel3._

import freechips.rocketchip.config.{Config}
import freechips.rocketchip.subsystem.{WithJtagDTM}

import boom.common._

// ---------------------
// BOOM Configs
// ---------------------

class SmallBoomConfig extends Config(
  new WithNormalBoomRocketTop ++
  new WithBootROM ++
  new boom.common.SmallBoomConfig)

class MediumBoomConfig extends Config(
  new WithNormalBoomRocketTop ++
  new WithBootROM ++
  new boom.common.MediumBoomConfig)

class LargeBoomConfig extends Config(
  new WithNormalBoomRocketTop ++
  new WithBootROM ++
  new boom.common.LargeBoomConfig)

class MegaBoomConfig extends Config(
  new WithNormalBoomRocketTop ++
  new WithBootROM ++
  new boom.common.MegaBoomConfig)

class jtagSmallBoomConfig extends Config(
  new WithDTMBoomRocketTop ++
  new WithBootROM ++
  new WithJtagDTM ++
  new boom.common.SmallBoomConfig)

class jtagMediumBoomConfig extends Config(
  new WithDTMBoomRocketTop ++
  new WithBootROM ++
  new WithJtagDTM ++
  new boom.common.MediumBoomConfig)

class jtagLargeBoomConfig extends Config(
  new WithDTMBoomRocketTop ++
  new WithBootROM ++
  new WithJtagDTM ++
  new boom.common.LargeBoomConfig)

class jtagMegaBoomConfig extends Config(
  new WithDTMBoomRocketTop ++
  new WithBootROM ++
  new WithJtagDTM ++
  new boom.common.MegaBoomConfig)

class SmallDualBoomConfig extends Config(
  new WithNormalBoomRocketTop ++
  new WithBootROM ++
  new boom.common.SmallDualBoomConfig)

class TracedSmallBoomConfig extends Config(
  new WithNormalBoomRocketTop ++
  new WithBootROM ++
  new boom.common.TracedSmallBoomConfig)

class SmallRV32UnifiedBoomConfig extends Config(
  new WithNormalBoomRocketTop ++
  new WithBootROM ++
  new boom.common.SmallRV32UnifiedBoomConfig)

// --------------------------
// BOOM + Rocket Configs
// --------------------------

class SmallBoomAndRocketConfig extends Config(
  new WithNormalBoomRocketTop ++
  new WithBootROM ++
  new boom.common.SmallBoomAndRocketConfig)

class MediumBoomAndRocketConfig extends Config(
  new WithNormalBoomRocketTop ++
  new WithBootROM ++
  new boom.common.MediumBoomAndRocketConfig)

class DualMediumBoomAndDualRocketConfig extends Config(
  new WithNormalBoomRocketTop ++
  new WithBootROM ++
  new boom.common.DualMediumBoomAndDualRocketConfig)
