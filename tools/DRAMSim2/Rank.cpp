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




#include "Rank.h"
#include "MemoryController.h"
#include <algorithm>

using namespace std;
using namespace DRAMSim;

Rank::Rank(ostream &dramsim_log_) :
	id(-1),
	dramsim_log(dramsim_log_),
	isPowerDown(false),
	refreshWaiting(false),
	readReturnCountdown(0),
	banks(NUM_BANKS, Bank(dramsim_log_)),
	bankStates(NUM_BANKS, BankState(dramsim_log_))

{

	memoryController = NULL;
	outgoingDataPacket = NULL;
	dataCyclesLeft = 0;
	currentClockCycle = 0;

#ifndef NO_STORAGE
#endif

}

// mutators
void Rank::setId(int id)
{
	this->id = id;
}

// attachMemoryController() must be called before any other Rank functions
// are called
void Rank::attachMemoryController(MemoryController *memoryController)
{
	this->memoryController = memoryController;
}
Rank::~Rank()
{
	for (size_t i=0; i<readReturnPacket.size(); i++)
	{
		delete readReturnPacket[i];
	}
	readReturnPacket.clear(); 
	delete outgoingDataPacket; 
}
void Rank::receiveFromBus(BusPacket *packet)
{
	if (DEBUG_BUS)
	{
		PRINTN(" -- R" << this->id << " Receiving On Bus    : ");
		packet->print();
	}
	if (VERIFICATION_OUTPUT)
	{
		packet->print(currentClockCycle,false);
	}

	switch (packet->busPacketType)
	{
	case READ:
		//make sure a read is allowed
		if (bankStates[packet->bank].currentBankState != RowActive ||
		        currentClockCycle < bankStates[packet->bank].nextRead ||
		        packet->row != bankStates[packet->bank].openRowAddress)
		{
			packet->print();
			ERROR("== Error - Rank " << id << " received a READ when not allowed");
			exit(0);
		}

		//update state table
		bankStates[packet->bank].nextPrecharge = max(bankStates[packet->bank].nextPrecharge, currentClockCycle + READ_TO_PRE_DELAY);
		for (size_t i=0;i<NUM_BANKS;i++)
		{
			bankStates[i].nextRead = max(bankStates[i].nextRead, currentClockCycle + max(tCCD, BL/2));
			bankStates[i].nextWrite = max(bankStates[i].nextWrite, currentClockCycle + READ_TO_WRITE_DELAY);
		}

		//get the read data and put it in the storage which delays until the appropriate time (RL)
#ifndef NO_STORAGE
		banks[packet->bank].read(packet);
#else
		packet->busPacketType = DATA;
#endif
		readReturnPacket.push_back(packet);
		readReturnCountdown.push_back(RL);
		break;
	case READ_P:
		//make sure a read is allowed
		if (bankStates[packet->bank].currentBankState != RowActive ||
		        currentClockCycle < bankStates[packet->bank].nextRead ||
		        packet->row != bankStates[packet->bank].openRowAddress)
		{
			ERROR("== Error - Rank " << id << " received a READ_P when not allowed");
			exit(-1);
		}

		//update state table
		bankStates[packet->bank].currentBankState = Idle;
		bankStates[packet->bank].nextActivate = max(bankStates[packet->bank].nextActivate, currentClockCycle + READ_AUTOPRE_DELAY);
		for (size_t i=0;i<NUM_BANKS;i++)
		{
			//will set next read/write for all banks - including current (which shouldnt matter since its now idle)
			bankStates[i].nextRead = max(bankStates[i].nextRead, currentClockCycle + max(BL/2, tCCD));
			bankStates[i].nextWrite = max(bankStates[i].nextWrite, currentClockCycle + READ_TO_WRITE_DELAY);
		}

		//get the read data and put it in the storage which delays until the appropriate time (RL)
#ifndef NO_STORAGE
		banks[packet->bank].read(packet);
#else
		packet->busPacketType = DATA;
#endif

		readReturnPacket.push_back(packet);
		readReturnCountdown.push_back(RL);
		break;
	case WRITE:
		//make sure a write is allowed
		if (bankStates[packet->bank].currentBankState != RowActive ||
		        currentClockCycle < bankStates[packet->bank].nextWrite ||
		        packet->row != bankStates[packet->bank].openRowAddress)
		{
			ERROR("== Error - Rank " << id << " received a WRITE when not allowed");
			bankStates[packet->bank].print();
			exit(0);
		}

		//update state table
		bankStates[packet->bank].nextPrecharge = max(bankStates[packet->bank].nextPrecharge, currentClockCycle + WRITE_TO_PRE_DELAY);
		for (size_t i=0;i<NUM_BANKS;i++)
		{
			bankStates[i].nextRead = max(bankStates[i].nextRead, currentClockCycle + WRITE_TO_READ_DELAY_B);
			bankStates[i].nextWrite = max(bankStates[i].nextWrite, currentClockCycle + max(BL/2, tCCD));
		}

		//take note of where data is going when it arrives
		incomingWriteBank = packet->bank;
		incomingWriteRow = packet->row;
		incomingWriteColumn = packet->column;
		delete(packet);
		break;
	case WRITE_P:
		//make sure a write is allowed
		if (bankStates[packet->bank].currentBankState != RowActive ||
		        currentClockCycle < bankStates[packet->bank].nextWrite ||
		        packet->row != bankStates[packet->bank].openRowAddress)
		{
			ERROR("== Error - Rank " << id << " received a WRITE_P when not allowed");
			exit(0);
		}

		//update state table
		bankStates[packet->bank].currentBankState = Idle;
		bankStates[packet->bank].nextActivate = max(bankStates[packet->bank].nextActivate, currentClockCycle + WRITE_AUTOPRE_DELAY);
		for (size_t i=0;i<NUM_BANKS;i++)
		{
			bankStates[i].nextWrite = max(bankStates[i].nextWrite, currentClockCycle + max(tCCD, BL/2));
			bankStates[i].nextRead = max(bankStates[i].nextRead, currentClockCycle + WRITE_TO_READ_DELAY_B);
		}

		//take note of where data is going when it arrives
		incomingWriteBank = packet->bank;
		incomingWriteRow = packet->row;
		incomingWriteColumn = packet->column;
		delete(packet);
		break;
	case ACTIVATE:
		//make sure activate is allowed
		if (bankStates[packet->bank].currentBankState != Idle ||
		        currentClockCycle < bankStates[packet->bank].nextActivate)
		{
			ERROR("== Error - Rank " << id << " received an ACT when not allowed");
			packet->print();
			bankStates[packet->bank].print();
			exit(0);
		}

		bankStates[packet->bank].currentBankState = RowActive;
		bankStates[packet->bank].nextActivate = currentClockCycle + tRC;
		bankStates[packet->bank].openRowAddress = packet->row;

		//if AL is greater than one, then posted-cas is enabled - handle accordingly
		if (AL>0)
		{
			bankStates[packet->bank].nextWrite = currentClockCycle + (tRCD-AL);
			bankStates[packet->bank].nextRead = currentClockCycle + (tRCD-AL);
		}
		else
		{
			bankStates[packet->bank].nextWrite = currentClockCycle + (tRCD-AL);
			bankStates[packet->bank].nextRead = currentClockCycle + (tRCD-AL);
		}

		bankStates[packet->bank].nextPrecharge = currentClockCycle + tRAS;
		for (size_t i=0;i<NUM_BANKS;i++)
		{
			if (i != packet->bank)
			{
				bankStates[i].nextActivate = max(bankStates[i].nextActivate, currentClockCycle + tRRD);
			}
		}
		delete(packet); 
		break;
	case PRECHARGE:
		//make sure precharge is allowed
		if (bankStates[packet->bank].currentBankState != RowActive ||
		        currentClockCycle < bankStates[packet->bank].nextPrecharge)
		{
			ERROR("== Error - Rank " << id << " received a PRE when not allowed");
			exit(0);
		}

		bankStates[packet->bank].currentBankState = Idle;
		bankStates[packet->bank].nextActivate = max(bankStates[packet->bank].nextActivate, currentClockCycle + tRP);
		delete(packet); 
		break;
	case REFRESH:
		refreshWaiting = false;
		for (size_t i=0;i<NUM_BANKS;i++)
		{
			if (bankStates[i].currentBankState != Idle)
			{
				ERROR("== Error - Rank " << id << " received a REF when not allowed");
				exit(0);
			}
			bankStates[i].nextActivate = currentClockCycle + tRFC;
		}
		delete(packet); 
		break;
	case DATA:
		// TODO: replace this check with something that works?
		/*
		if(packet->bank != incomingWriteBank ||
			 packet->row != incomingWriteRow ||
			 packet->column != incomingWriteColumn)
			{
				cout << "== Error - Rank " << id << " received a DATA packet to the wrong place" << endl;
				packet->print();
				bankStates[packet->bank].print();
				exit(0);
			}
		*/
#ifndef NO_STORAGE
		banks[packet->bank].write(packet);
#else
		// end of the line for the write packet
#endif
		delete(packet);
		break;
	default:
		ERROR("== Error - Unknown BusPacketType trying to be sent to Bank");
		exit(0);
		break;
	}
}

int Rank::getId() const
{
	return this->id;
}

void Rank::update()
{

	// An outgoing packet is one that is currently sending on the bus
	// do the book keeping for the packet's time left on the bus
	if (outgoingDataPacket != NULL)
	{
		dataCyclesLeft--;
		if (dataCyclesLeft == 0)
		{
			//if the packet is done on the bus, call receiveFromBus and free up the bus
			memoryController->receiveFromBus(outgoingDataPacket);
			outgoingDataPacket = NULL;
		}
	}

	// decrement the counter for all packets waiting to be sent back
	for (size_t i=0;i<readReturnCountdown.size();i++)
	{
		readReturnCountdown[i]--;
	}


	if (readReturnCountdown.size() > 0 && readReturnCountdown[0]==0)
	{
		// RL time has passed since the read was issued; this packet is
		// ready to go out on the bus

		outgoingDataPacket = readReturnPacket[0];
		dataCyclesLeft = BL/2;

		// remove the packet from the ranks
		readReturnPacket.erase(readReturnPacket.begin());
		readReturnCountdown.erase(readReturnCountdown.begin());

		if (DEBUG_BUS)
		{
			PRINTN(" -- R" << this->id << " Issuing On Data Bus : ");
			outgoingDataPacket->print();
			PRINT("");
		}

	}
}

//power down the rank
void Rank::powerDown()
{
	//perform checks
	for (size_t i=0;i<NUM_BANKS;i++)
	{
		if (bankStates[i].currentBankState != Idle)
		{
			ERROR("== Error - Trying to power down rank " << id << " while not all banks are idle");
			exit(0);
		}

		bankStates[i].nextPowerUp = currentClockCycle + tCKE;
		bankStates[i].currentBankState = PowerDown;
	}

	isPowerDown = true;
}

//power up the rank
void Rank::powerUp()
{
	if (!isPowerDown)
	{
		ERROR("== Error - Trying to power up rank " << id << " while it is not already powered down");
		exit(0);
	}

	isPowerDown = false;

	for (size_t i=0;i<NUM_BANKS;i++)
	{
		if (bankStates[i].nextPowerUp > currentClockCycle)
		{
			ERROR("== Error - Trying to power up rank " << id << " before we're allowed to");
			ERROR(bankStates[i].nextPowerUp << "    " << currentClockCycle);
			exit(0);
		}
		bankStates[i].nextActivate = currentClockCycle + tXP;
		bankStates[i].currentBankState = Idle;
	}
}
