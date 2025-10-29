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




//MemorySystem.cpp
//
//Class file for JEDEC memory system wrapper
//

#include "MemorySystem.h"
#include "IniReader.h"
#ifndef _WIN32
#include <unistd.h>
#endif

using namespace std;


ofstream cmd_verify_out; //used in Rank.cpp and MemoryController.cpp if VERIFICATION_OUTPUT is set

unsigned NUM_DEVICES;
unsigned NUM_RANKS;
unsigned NUM_RANKS_LOG;

namespace DRAMSim {

powerCallBack_t MemorySystem::ReportPower = NULL;

MemorySystem::MemorySystem(unsigned id, unsigned int megsOfMemory, CSVWriter &csvOut_, ostream &dramsim_log_) :
		dramsim_log(dramsim_log_),
		ReturnReadData(NULL),
		WriteDataDone(NULL),
		systemID(id),
		csvOut(csvOut_)
{
	currentClockCycle = 0;

	DEBUG("===== MemorySystem "<<systemID<<" =====");


	//calculate the total storage based on the devices the user selected and the number of

	//calculate number of devices
	/************************
	  This code has always been problematic even though it's pretty simple. I'll try to explain it 
	  for my own sanity. 

	  There are two main variables here that we could let the user choose:
	  NUM_RANKS or TOTAL_STORAGE.  Since the density and width of the part is
	  fixed by the device ini file, the only variable that is really
	  controllable is the number of ranks. Users care more about choosing the
	  total amount of storage, but with a fixed device they might choose a total
	  storage that isn't possible. In that sense it's not as good to allow them
	  to choose TOTAL_STORAGE (because any NUM_RANKS value >1 will be valid).

	  However, users don't care (or know) about ranks, they care about total
	  storage, so maybe it's better to let them choose and just throw an error
	  if they choose something invalid. 

	  A bit of background: 

	  Each column contains DEVICE_WIDTH bits. A row contains NUM_COLS columns.
	  Each bank contains NUM_ROWS rows. Therefore, the total storage per DRAM device is: 
	  		PER_DEVICE_STORAGE = NUM_ROWS*NUM_COLS*DEVICE_WIDTH*NUM_BANKS (in bits)

	 A rank *must* have a 64 bit output bus (JEDEC standard), so each rank must have:
	  		NUM_DEVICES_PER_RANK = 64/DEVICE_WIDTH  
			(note: if you have multiple channels ganged together, the bus width is 
			effectively NUM_CHANS * 64/DEVICE_WIDTH)
	 
	If we multiply these two numbers to get the storage per rank (in bits), we get:
			PER_RANK_STORAGE = PER_DEVICE_STORAGE*NUM_DEVICES_PER_RANK = NUM_ROWS*NUM_COLS*NUM_BANKS*64 

	Finally, to get TOTAL_STORAGE, we need to multiply by NUM_RANKS
			TOTAL_STORAGE = PER_RANK_STORAGE*NUM_RANKS (total storage in bits)

	So one could compute this in reverse -- compute NUM_DEVICES,
	PER_DEVICE_STORAGE, and PER_RANK_STORAGE first since all these parameters
	are set by the device ini. Then, TOTAL_STORAGE/PER_RANK_STORAGE = NUM_RANKS 

	The only way this could run into problems is if TOTAL_STORAGE < PER_RANK_STORAGE,
	which could happen for very dense parts.
	*********************/

	// number of bytes per rank
	unsigned long megsOfStoragePerRank = ((((long long)NUM_ROWS * (NUM_COLS * DEVICE_WIDTH) * NUM_BANKS) * ((long long)JEDEC_DATA_BUS_BITS / DEVICE_WIDTH)) / 8) >> 20;

	// If this is set, effectively override the number of ranks
	if (megsOfMemory != 0)
	{
		NUM_RANKS = megsOfMemory / megsOfStoragePerRank;
		NUM_RANKS_LOG = dramsim_log2(NUM_RANKS);
		if (NUM_RANKS == 0)
		{
			PRINT("WARNING: Cannot create memory system with "<<megsOfMemory<<"MB, defaulting to minimum size of "<<megsOfStoragePerRank<<"MB");
			NUM_RANKS=1;
		}
	}

	NUM_DEVICES = JEDEC_DATA_BUS_BITS/DEVICE_WIDTH;
	TOTAL_STORAGE = (NUM_RANKS * megsOfStoragePerRank); 

	DEBUG("CH. " <<systemID<<" TOTAL_STORAGE : "<< TOTAL_STORAGE << "MB | "<<NUM_RANKS<<" Ranks | "<< NUM_DEVICES <<" Devices per rank");


	memoryController = new MemoryController(this, csvOut, dramsim_log);

	// TODO: change to other vector constructor?
	ranks = new vector<Rank *>();

	for (size_t i=0; i<NUM_RANKS; i++)
	{
		Rank *r = new Rank(dramsim_log);
		r->setId(i);
		r->attachMemoryController(memoryController);
		ranks->push_back(r);
	}

	memoryController->attachRanks(ranks);

}



MemorySystem::~MemorySystem()
{
	/* the MemorySystem should exist for all time, nothing should be destroying it */  
//	ERROR("MEMORY SYSTEM DESTRUCTOR with ID "<<systemID);
//	abort();

	delete(memoryController);

	for (size_t i=0; i<NUM_RANKS; i++)
	{
		delete (*ranks)[i];
	}
	ranks->clear();
	delete(ranks);

	if (VERIFICATION_OUTPUT)
	{
		cmd_verify_out.flush();
		cmd_verify_out.close();
	}
}

bool MemorySystem::WillAcceptTransaction()
{
	return memoryController->WillAcceptTransaction();
}

bool MemorySystem::addTransaction(bool isWrite, uint64_t addr)
{
	TransactionType type = isWrite ? DATA_WRITE : DATA_READ;
	Transaction *trans = new Transaction(type,addr,NULL);
	// push_back in memoryController will make a copy of this during
	// addTransaction so it's kosher for the reference to be local 

	if (memoryController->WillAcceptTransaction()) 
	{
		return memoryController->addTransaction(trans);
	}
	else
	{
		pendingTransactions.push_back(trans);
		return true;
	}
}

bool MemorySystem::addTransaction(Transaction *trans)
{
	return memoryController->addTransaction(trans);
}

//prints statistics
void MemorySystem::printStats(bool finalStats)
{
	memoryController->printStats(finalStats);
}


//update the memory systems state
void MemorySystem::update()
{

	//PRINT(" ----------------- Memory System Update ------------------");

	//updates the state of each of the objects
	// NOTE - do not change order
	for (size_t i=0;i<NUM_RANKS;i++)
	{
		(*ranks)[i]->update();
	}

	//pendingTransactions will only have stuff in it if MARSS is adding stuff
	if (pendingTransactions.size() > 0 && memoryController->WillAcceptTransaction())
	{
		memoryController->addTransaction(pendingTransactions.front());
		pendingTransactions.pop_front();
	}
	memoryController->update();

	//simply increments the currentClockCycle field for each object
	for (size_t i=0;i<NUM_RANKS;i++)
	{
		(*ranks)[i]->step();
	}
	memoryController->step();
	this->step();

	//PRINT("\n"); // two new lines
}

void MemorySystem::RegisterCallbacks( Callback_t* readCB, Callback_t* writeCB,
                                      void (*reportPower)(double bgpower, double burstpower,
                                                          double refreshpower, double actprepower))
{
	ReturnReadData = readCB;
	WriteDataDone = writeCB;
	ReportPower = reportPower;
}

} /*namespace DRAMSim */



// This function can be used by autoconf AC_CHECK_LIB since
// apparently it can't detect C++ functions.
// Basically just an entry in the symbol table
extern "C"
{
	void libdramsim_is_present(void)
	{
		;
	}
}

