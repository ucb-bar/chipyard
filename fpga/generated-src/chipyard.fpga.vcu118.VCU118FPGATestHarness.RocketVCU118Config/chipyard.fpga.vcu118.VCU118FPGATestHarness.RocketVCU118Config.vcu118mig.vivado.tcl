 
      set scriptPath [info script]
      set scriptDir [file dirname $scriptPath]
      set dirName [file tail $scriptDir]
  	  set newFolderPath [file join $scriptDir "ip"]
	    file mkdir $newFolderPath
      puts "scriptPath name: $scriptPath"
      puts "scriptDir name: $scriptDir"
      puts "Directory name: $dirName" 
  	  set currentDir [pwd]
	    set ipdir [file join $currentDir $dirName $newFolderPath]
	    puts "ipdir Path: $ipdir"

      create_ip -vendor xilinx.com -library ip -version 2.2 -name ddr4 -module_name vcu118mig -dir $ipdir -force
      set_property -dict [list \
      CONFIG.AL_SEL                               {0} \
      CONFIG.C0.ADDR_WIDTH                        {17} \
      CONFIG.C0.BANK_GROUP_WIDTH                  {1} \
      CONFIG.C0.CKE_WIDTH                         {1} \
      CONFIG.C0.CK_WIDTH                          {1} \
      CONFIG.C0.CS_WIDTH                          {1} \
      CONFIG.C0.ControllerType                    {DDR4_SDRAM} \
      CONFIG.C0.DDR4_AUTO_AP_COL_A3               {false} \
      CONFIG.C0.DDR4_AutoPrecharge                {false} \
      CONFIG.C0.DDR4_AxiAddressWidth              {33} \
      CONFIG.C0.DDR4_AxiArbitrationScheme         {RD_PRI_REG} \
      CONFIG.C0.DDR4_AxiDataWidth                 {64} \
      CONFIG.C0.DDR4_AxiIDWidth                   {4} \
      CONFIG.C0.DDR4_AxiNarrowBurst               {false} \
      CONFIG.C0.DDR4_AxiSelection                 {true} \
      CONFIG.C0.DDR4_BurstLength                  {8} \
      CONFIG.C0.DDR4_BurstType                    {Sequential} \
      CONFIG.C0.DDR4_CLKFBOUT_MULT                {8} \
      CONFIG.C0.DDR4_CLKOUT0_DIVIDE               {5} \
      CONFIG.C0.DDR4_Capacity                     {512} \
      CONFIG.C0.DDR4_CasLatency                   {11} \
      CONFIG.C0.DDR4_CasWriteLatency              {9} \
      CONFIG.C0.DDR4_ChipSelect                   {true} \
      CONFIG.C0.DDR4_Clamshell                    {false} \
      CONFIG.C0.DDR4_CustomParts                  {no_file_loaded} \
      CONFIG.C0.DDR4_DIVCLK_DIVIDE                {2} \
      CONFIG.C0.DDR4_DataMask                     {DM_NO_DBI} \
      CONFIG.C0.DDR4_DataWidth                    {64} \
      CONFIG.C0.DDR4_Ecc                          {false} \
      CONFIG.C0.DDR4_MCS_ECC                      {false} \
      CONFIG.C0.DDR4_Mem_Add_Map                  {ROW_COLUMN_BANK} \
      CONFIG.C0.DDR4_MemoryName                   {MainMemory} \
      CONFIG.C0.DDR4_MemoryPart                   {MT40A1G16WBU-083E} \
      CONFIG.C0.DDR4_MemoryType                   {Components} \
      CONFIG.C0.DDR4_MemoryVoltage                {1.2V} \
      CONFIG.C0.DDR4_OnDieTermination             {RZQ/6} \
      CONFIG.C0.DDR4_Ordering                     {Normal} \
      CONFIG.C0.DDR4_OutputDriverImpedenceControl {RZQ/7} \
      CONFIG.C0.DDR4_PhyClockRatio                {4:1} \
      CONFIG.C0.DDR4_SAVE_RESTORE                 {false} \
      CONFIG.C0.DDR4_SELF_REFRESH                 {false} \
      CONFIG.C0.DDR4_Slot                         {Single} \
      CONFIG.C0.DDR4_Specify_MandD                {true} \
      CONFIG.C0.DDR4_TimePeriod                   {1250} \
      CONFIG.C0.DDR4_UserRefresh_ZQCS             {false} \
      CONFIG.C0.DDR4_isCKEShared                  {false} \
      CONFIG.C0.DDR4_isCustom                     {false} \
      CONFIG.C0.LR_WIDTH                          {1} \
      CONFIG.C0.ODT_WIDTH                         {1} \
      CONFIG.C0.StackHeight                       {1} \
      CONFIG.C0_CLOCK_BOARD_INTERFACE             {Custom} \
      CONFIG.C0_DDR4_BOARD_INTERFACE              {Custom} \
      CONFIG.DCI_Cascade                          {false} \
      CONFIG.DIFF_TERM_SYSCLK                     {false} \
      CONFIG.Debug_Signal                         {Disable} \
      CONFIG.Default_Bank_Selections              {false} \
      CONFIG.Enable_SysPorts                      {true} \
      CONFIG.IOPowerReduction                     {OFF} \
      CONFIG.IO_Power_Reduction                   {false} \
      CONFIG.IS_FROM_PHY                          {1} \
      CONFIG.MCS_DBG_EN                           {false} \
      CONFIG.No_Controller                        {1} \
      CONFIG.PARTIAL_RECONFIG_FLOW_MIG            {false} \
      CONFIG.PING_PONG_PHY                        {1} \
      CONFIG.Phy_Only                             {Complete_Memory_Controller} \
      CONFIG.RECONFIG_XSDB_SAVE_RESTORE           {false} \
      CONFIG.RESET_BOARD_INTERFACE                {Custom} \
      CONFIG.Reference_Clock                      {Differential} \
      CONFIG.SET_DW_TO_40                         {false} \
      CONFIG.System_Clock                         {No_Buffer} \
      CONFIG.TIMING_3DS                           {false} \
      CONFIG.TIMING_OP1                           {false} \
      CONFIG.TIMING_OP2                           {false} \
      ] [get_ips vcu118mig]