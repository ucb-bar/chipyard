# Nexys Video RGMII implementation constraints.

set rgmii_reset_cells [get_cells -quiet -hierarchical -filter \
  {NAME =~ *rgmii_phy_if_inst/*_rst_reg_reg* || NAME =~ *rxRstReg_reg* || NAME =~ *txRstReg_reg*}]

set rgmii_clk_oddr_cells [get_cells -quiet -hierarchical -filter \
  {NAME =~ *rgmii_phy_if_inst/clk_oddr_inst/oddr[0].oddr_inst || NAME =~ *clkOddr/oddrInst}]

set rgmii_txc_1_cells [get_cells -quiet -hierarchical -filter \
  {NAME =~ *rgmii_phy_if_inst/rgmii_tx_clk_1_reg || NAME =~ *rgmiiTxClk1_reg}]

set rgmii_txc_2_cells [get_cells -quiet -hierarchical -filter \
  {NAME =~ *rgmii_phy_if_inst/rgmii_tx_clk_2_reg || NAME =~ *rgmiiTxClk2_reg}]

set rgmii_txc_cells [get_cells -quiet -hierarchical -filter \
  {NAME =~ *rgmii_phy_if_inst/rgmii_tx_clk_1_reg || \
   NAME =~ *rgmii_phy_if_inst/rgmii_tx_clk_2_reg || \
   NAME =~ *rgmiiTxClk1_reg || NAME =~ *rgmiiTxClk2_reg}]

set_property -quiet ASYNC_REG TRUE $rgmii_reset_cells
set_property -quiet ASYNC_REG TRUE $rgmii_clk_oddr_cells

set_max_delay -from $rgmii_txc_1_cells -to $rgmii_clk_oddr_cells -datapath_only 2.000
set_max_delay -from $rgmii_txc_2_cells -to $rgmii_clk_oddr_cells -datapath_only 2.000

create_pblock pblock_rgmii_txc
add_cells_to_pblock [get_pblocks pblock_rgmii_txc] $rgmii_txc_cells
resize_pblock [get_pblocks pblock_rgmii_txc] -add {SLICE_X0Y80:SLICE_X3Y99}

set_false_path -quiet -to [get_pins -quiet -of_objects $rgmii_reset_cells -filter {IS_PRESET || IS_RESET}]
