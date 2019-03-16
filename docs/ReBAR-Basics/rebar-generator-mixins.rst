

SoC Generator Config Mix-ins:
==============================

Rocket Chip
-----------------------

+ System-on-Chip
    - HasTiles
    - HasClockDomainCrossing
    - HasResetVectorWire
    - HasNoiseMakerIO


+ Basic Core
    - HasRocketTiles
    - HasRocketCoreParameters
    - HasCoreIO


+ Branch Prediction
    - HasBtbParameters


+ Additional Compute
    - HasFPUCtrlSigs
    - HasFPUParameters
    - HasLazyRoCC
    - HasFpuOpt


+ Memory System
    - HasRegMap
    - HasCoreMemOp
    - HasHellaCache
    - HasL1ICacheParameters
    - HasICacheFrontendModule
    - HasAXI4ControlRegMap
    - HasTLControlRegMap
    - HasTLBusParams
    - HasTLXbarPhy


+ Interrupts
    - HasInterruptSources
    - HasExtInterrupts
    - HasAsyncExtInterrupts
    - HasSyncExtInterrupts


+ Periphery
    - HasPeripheryDebug
    - HasPeripheryBootROM
    - HasBuiltInDeviceParams


BOOM
-----------------------
+ Basic Core
    - HasBoomTiles
    - HasBoomCoreParameters
    - HasBoomCoreIO
    - HasBoomUOP
    - HasRegisterFileIO


+ Branch Prediction
    - HasGShareParameters
    - HasBoomBTBParameters


+ Memory System
    - HasL1ICacheBankedParameters
    - HasBoomICacheFrontend
    - HasBoomHellaCache


SiFive Blocks
-----------------------

+ Peripherals
    - HasPeripheryGPIO
    - HasPeripheryI2C
    - HasPeripheryMockAON
    - HasPeripheryPWM
    - HasPeripherySPI
    - HasSPIProtocol
        - HasSPIEndian
        - HasSPILength
        - HasSPICSMode 
    - HasPeripherySPIFlash 
    - HasPeripheryUART


testchipip
-----------------------

+ Peripherals
    - HasPeripheryBlockDevice
    - HasPeripherySerial
    - HasNoDebug


Icenet
-----------------------

+ Periphery Network Interface Controller
    - HasPeripheryIceNIC


AWL
-----------------------

+ IO
    - HasEncoding8b10b
    - HasTLBidirectionalPacketizer
    - HasTLController
    - HasGenericTransceiverSubsystem

+ Debug/Testing
    - HasBertDebug
    - HasPatternMemDebug
    - HasBitStufferDebug4Modes
    - HasBitReversalDebug










