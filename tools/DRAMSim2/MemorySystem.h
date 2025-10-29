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



#ifndef MEMORYSYSTEM_H
#define MEMORYSYSTEM_H

//MemorySystem.h
//
//Header file for JEDEC memory system wrapper
//

#include "SimulatorObject.h"
#include "SystemConfiguration.h"
#include "MemoryController.h"
#include "Rank.h"
#include "Transaction.h"
#include "Callback.h"
#include "CSVWriter.h"
#include <deque>

namespace DRAMSim
{
typedef CallbackBase<void,unsigned,uint64_t,uint64_t> Callback_t;
class MemorySystem : public SimulatorObject
{
	ostream &dramsim_log;
public:
	//functions
	MemorySystem(unsigned id, unsigned megsOfMemory, CSVWriter &csvOut_, ostream &dramsim_log_);
	virtual ~MemorySystem();
	void update();
	bool addTransaction(Transaction *trans);
	bool addTransaction(bool isWrite, uint64_t addr);
	void printStats(bool finalStats);
	bool WillAcceptTransaction();
	void RegisterCallbacks(
	    Callback_t *readDone,
	    Callback_t *writeDone,
	    void (*reportPower)(double bgpower, double burstpower, double refreshpower, double actprepower));

	//fields
	MemoryController *memoryController;
	vector<Rank *> *ranks;
	deque<Transaction *> pendingTransactions; 


	//function pointers
	Callback_t* ReturnReadData;
	Callback_t* WriteDataDone;
	//TODO: make this a functor as well?
	static powerCallBack_t ReportPower;
	unsigned systemID;

private:
	CSVWriter &csvOut;
};
}

#endif

