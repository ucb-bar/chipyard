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

//Transaction.cpp
//
//Class file for transaction object
//	Transaction is considered requests sent from the CPU to
//	the memory controller (read, write, etc.)...

#include "Transaction.h"
#include "PrintMacros.h"

using std::endl;
using std::hex; 
using std::dec; 

namespace DRAMSim {

Transaction::Transaction(TransactionType transType, uint64_t addr, void *dat) :
	transactionType(transType),
	address(addr),
	data(dat)
{}

Transaction::Transaction(const Transaction &t)
	: transactionType(t.transactionType)
	  , address(t.address)
	  , data(NULL)
	  , timeAdded(t.timeAdded)
	  , timeReturned(t.timeReturned)
{
	#ifndef NO_STORAGE
	ERROR("Data storage is really outdated and these copies happen in an \n improper way, which will eventually cause problems. Please send an \n email to dramninjas [at] gmail [dot] com if you need data storage");
	abort(); 
	#endif
}

ostream &operator<<(ostream &os, const Transaction &t)
{
	if (t.transactionType == DATA_READ)
	{
		os<<"T [Read] [0x" << hex << t.address << "]" << dec <<endl;
	}
	else if (t.transactionType == DATA_WRITE)
	{
		os<<"T [Write] [0x" << hex << t.address << "] [" << dec << t.data << "]" <<endl;
	}
	else if (t.transactionType == RETURN_DATA)
	{
		os<<"T [Data] [0x" << hex << t.address << "] [" << dec << t.data << "]" <<endl;
	}
	return os; 
}
}

