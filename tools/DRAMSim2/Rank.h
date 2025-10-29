/*********************************************************************************
*  Copyright (c) 2010-2011, Elliott Cooper-Balis
*                             Paul Rosenfeld
*                             Bruce Jacob
*                             University of Maryland 
*                             dramninjas [at] gmail [dot] com
*  All rights reserved.
*  
*  Redistribution and use in source and binary forms, with or without
*  modification, are permitted provided that the following conditions are met:
*  
*     * Redistributions of source code must retain the above copyright notice,
*        this list of conditions and the following disclaimer.
*  
*     * Redistributions in binary form must reproduce the above copyright notice,
*        this list of conditions and the following disclaimer in the documentation
*        and/or other materials provided with the distribution.
*  
*  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
*  ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
*  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
*  DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
*  FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
*  DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
*  SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
*  CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
*  OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
*  OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*********************************************************************************/




#ifndef RANK_H
#define RANK_H

#include "SimulatorObject.h"
#include "BusPacket.h"
#include "SystemConfiguration.h"
#include "Bank.h"
#include "BankState.h"

using namespace std;
using namespace DRAMSim;

namespace DRAMSim
{
class MemoryController; //forward declaration
class Rank : public SimulatorObject
{
private:
	int id;
	ostream &dramsim_log; 
	unsigned incomingWriteBank;
	unsigned incomingWriteRow;
	unsigned incomingWriteColumn;
	bool isPowerDown;

public:
	//functions
	Rank(ostream &dramsim_log_);
	virtual ~Rank(); 
	void receiveFromBus(BusPacket *packet);
	void attachMemoryController(MemoryController *mc);
	int getId() const;
	void setId(int id);
	void update();
	void powerUp();
	void powerDown();

	//fields
	MemoryController *memoryController;
	BusPacket *outgoingDataPacket;
	unsigned dataCyclesLeft;
	bool refreshWaiting;

	//these are vectors so that each element is per-bank
	vector<BusPacket *> readReturnPacket;
	vector<unsigned> readReturnCountdown;
	vector<Bank> banks;
	vector<BankState> bankStates;

};
}
#endif

