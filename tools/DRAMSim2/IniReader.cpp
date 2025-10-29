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








#include "IniReader.h"

using namespace std;

// these are the values that are extern'd in SystemConfig.h so that they
// have global scope even though they are set by IniReader

uint64_t TOTAL_STORAGE;
unsigned NUM_BANKS;
unsigned NUM_BANKS_LOG;
unsigned NUM_CHANS;
unsigned NUM_CHANS_LOG;
unsigned NUM_ROWS;
unsigned NUM_ROWS_LOG;
unsigned NUM_COLS;
unsigned NUM_COLS_LOG;
unsigned DEVICE_WIDTH;
unsigned BYTE_OFFSET_WIDTH;
unsigned TRANSACTION_SIZE;
unsigned THROW_AWAY_BITS;
unsigned COL_LOW_BIT_WIDTH;

unsigned REFRESH_PERIOD;
float tCK;
float Vdd;
unsigned CL;
unsigned AL;
unsigned BL;
unsigned tRAS;
unsigned tRCD;
unsigned tRRD;
unsigned tRC;
unsigned tRP;
unsigned tCCD;
unsigned tRTP;
unsigned tWTR;
unsigned tWR;
unsigned tRTRS;
unsigned tRFC;
unsigned tFAW;
unsigned tCKE;
unsigned tXP;
unsigned tCMD;

unsigned IDD0;
unsigned IDD1;
unsigned IDD2P;
unsigned IDD2Q;
unsigned IDD2N;
unsigned IDD3Pf;
unsigned IDD3Ps;
unsigned IDD3N;
unsigned IDD4W;
unsigned IDD4R;
unsigned IDD5;
unsigned IDD6;
unsigned IDD6L;
unsigned IDD7;


//in bytes
unsigned JEDEC_DATA_BUS_BITS;

//Memory Controller related parameters
unsigned TRANS_QUEUE_DEPTH;
unsigned CMD_QUEUE_DEPTH;

//cycles within an epoch
unsigned EPOCH_LENGTH;

//row accesses allowed before closing (open page)
unsigned TOTAL_ROW_ACCESSES;

// strings and their associated enums
string ROW_BUFFER_POLICY;
string SCHEDULING_POLICY;
string ADDRESS_MAPPING_SCHEME;
string QUEUING_STRUCTURE;

bool DEBUG_TRANS_Q;
bool DEBUG_CMD_Q;
bool DEBUG_ADDR_MAP;
bool DEBUG_BANKSTATE;
bool DEBUG_BUS;
bool DEBUG_BANKS;
bool DEBUG_POWER;
bool USE_LOW_POWER;
bool VIS_FILE_OUTPUT;

bool VERIFICATION_OUTPUT;

bool DEBUG_INI_READER=false;

namespace DRAMSim
{
RowBufferPolicy rowBufferPolicy;
SchedulingPolicy schedulingPolicy;
AddressMappingScheme addressMappingScheme;
QueuingStructure queuingStructure;


//Map the string names to the variables they set
static ConfigMap configMap[] =
{
	//DEFINE_UINT_PARAM -- see IniReader.h
	DEFINE_UINT_PARAM(NUM_BANKS,DEV_PARAM),
	DEFINE_UINT_PARAM(NUM_ROWS,DEV_PARAM),
	DEFINE_UINT_PARAM(NUM_COLS,DEV_PARAM),
	DEFINE_UINT_PARAM(DEVICE_WIDTH,DEV_PARAM),
	DEFINE_UINT_PARAM(REFRESH_PERIOD,DEV_PARAM),
	DEFINE_FLOAT_PARAM(tCK,DEV_PARAM),
	DEFINE_UINT_PARAM(CL,DEV_PARAM),
	DEFINE_UINT_PARAM(AL,DEV_PARAM),
	DEFINE_UINT_PARAM(BL,DEV_PARAM),
	DEFINE_UINT_PARAM(tRAS,DEV_PARAM),
	DEFINE_UINT_PARAM(tRCD,DEV_PARAM),
	DEFINE_UINT_PARAM(tRRD,DEV_PARAM),
	DEFINE_UINT_PARAM(tRC,DEV_PARAM),
	DEFINE_UINT_PARAM(tRP,DEV_PARAM),
	DEFINE_UINT_PARAM(tCCD,DEV_PARAM),
	DEFINE_UINT_PARAM(tRTP,DEV_PARAM),
	DEFINE_UINT_PARAM(tWTR,DEV_PARAM),
	DEFINE_UINT_PARAM(tWR,DEV_PARAM),
	DEFINE_UINT_PARAM(tRTRS,DEV_PARAM),
	DEFINE_UINT_PARAM(tRFC,DEV_PARAM),
	DEFINE_UINT_PARAM(tFAW,DEV_PARAM),
	DEFINE_UINT_PARAM(tCKE,DEV_PARAM),
	DEFINE_UINT_PARAM(tXP,DEV_PARAM),
	DEFINE_UINT_PARAM(tCMD,DEV_PARAM),
	DEFINE_UINT_PARAM(IDD0,DEV_PARAM),
	DEFINE_UINT_PARAM(IDD1,DEV_PARAM),
	DEFINE_UINT_PARAM(IDD2P,DEV_PARAM),
	DEFINE_UINT_PARAM(IDD2Q,DEV_PARAM),
	DEFINE_UINT_PARAM(IDD2N,DEV_PARAM),
	DEFINE_UINT_PARAM(IDD3Pf,DEV_PARAM),
	DEFINE_UINT_PARAM(IDD3Ps,DEV_PARAM),
	DEFINE_UINT_PARAM(IDD3N,DEV_PARAM),
	DEFINE_UINT_PARAM(IDD4W,DEV_PARAM),
	DEFINE_UINT_PARAM(IDD4R,DEV_PARAM),
	DEFINE_UINT_PARAM(IDD5,DEV_PARAM),
	DEFINE_UINT_PARAM(IDD6,DEV_PARAM),
	DEFINE_UINT_PARAM(IDD6L,DEV_PARAM),
	DEFINE_UINT_PARAM(IDD7,DEV_PARAM),
	DEFINE_FLOAT_PARAM(Vdd,DEV_PARAM),

	DEFINE_UINT_PARAM(NUM_CHANS,SYS_PARAM),
	DEFINE_UINT_PARAM(JEDEC_DATA_BUS_BITS,SYS_PARAM),

	//Memory Controller related parameters
	DEFINE_UINT_PARAM(TRANS_QUEUE_DEPTH,SYS_PARAM),
	DEFINE_UINT_PARAM(CMD_QUEUE_DEPTH,SYS_PARAM),

	DEFINE_UINT_PARAM(EPOCH_LENGTH,SYS_PARAM),
	//Power
	DEFINE_BOOL_PARAM(USE_LOW_POWER,SYS_PARAM),

	DEFINE_UINT_PARAM(TOTAL_ROW_ACCESSES,SYS_PARAM),
	DEFINE_STRING_PARAM(ROW_BUFFER_POLICY,SYS_PARAM),
	DEFINE_STRING_PARAM(SCHEDULING_POLICY,SYS_PARAM),
	DEFINE_STRING_PARAM(ADDRESS_MAPPING_SCHEME,SYS_PARAM),
	DEFINE_STRING_PARAM(QUEUING_STRUCTURE,SYS_PARAM),
	// debug flags
	DEFINE_BOOL_PARAM(DEBUG_TRANS_Q,SYS_PARAM),
	DEFINE_BOOL_PARAM(DEBUG_CMD_Q,SYS_PARAM),
	DEFINE_BOOL_PARAM(DEBUG_ADDR_MAP,SYS_PARAM),
	DEFINE_BOOL_PARAM(DEBUG_BANKSTATE,SYS_PARAM),
	DEFINE_BOOL_PARAM(DEBUG_BUS,SYS_PARAM),
	DEFINE_BOOL_PARAM(DEBUG_BANKS,SYS_PARAM),
	DEFINE_BOOL_PARAM(DEBUG_POWER,SYS_PARAM),
	DEFINE_BOOL_PARAM(VIS_FILE_OUTPUT,SYS_PARAM),
	DEFINE_BOOL_PARAM(VERIFICATION_OUTPUT,SYS_PARAM),
	{"", NULL, _UINT, SYS_PARAM, false} // tracer value to signify end of list; if you delete it, epic fail will result
};

void IniReader::WriteParams(std::ofstream &visDataOut, paramType type)
{
	for (size_t i=0; configMap[i].variablePtr != NULL; i++)
	{
		if (configMap[i].parameterType == type)
		{
			visDataOut<<configMap[i].iniKey<<"=";
			switch (configMap[i].variableType)
			{
				//parse and set each type of variable
			case _UINT:
				visDataOut << *((unsigned *)configMap[i].variablePtr);
				break;
			case _UINT64:
				visDataOut << *((uint64_t *)configMap[i].variablePtr);
				break;
			case _FLOAT:
				visDataOut << *((float *)configMap[i].variablePtr);
				break;
			case _STRING:
				visDataOut << *((string *)configMap[i].variablePtr);
				break;
			case _BOOL:
				if (*((bool *)configMap[i].variablePtr))
				{
					visDataOut <<"true";
				}
				else
				{
					visDataOut <<"false";
				}
				break;
			}
			visDataOut << endl;
		}
	}
	if (type == SYS_PARAM)
	{
		visDataOut<<"NUM_RANKS="<<NUM_RANKS <<"\n";
	}
}
void IniReader::WriteValuesOut(std::ofstream &visDataOut)
{
	visDataOut<<"!!SYSTEM_INI"<<endl;

	WriteParams(visDataOut, SYS_PARAM); 
	visDataOut<<"!!DEVICE_INI"<<endl;

	WriteParams(visDataOut, DEV_PARAM); 
	visDataOut<<"!!EPOCH_DATA"<<endl;

}

void IniReader::SetKey(string key, string valueString, bool isSystemParam, size_t lineNumber)
{
	size_t i;
	unsigned intValue;
	uint64_t int64Value;
	float floatValue;

	for (i=0; configMap[i].variablePtr != NULL; i++)
	{
		istringstream iss(valueString);
		// match up the string in the config map with the key we parsed
		if (key.compare(configMap[i].iniKey) == 0)
		{
			switch (configMap[i].variableType)
			{
				//parse and set each type of variable
			case _UINT:
				if ((iss >> dec >> intValue).fail())
				{
					ERROR("could not parse line "<<lineNumber<<" (non-numeric value '"<<valueString<<"')?");
				}
				*((unsigned *)configMap[i].variablePtr) = intValue;
				if (DEBUG_INI_READER)
				{
					DEBUG("\t - SETTING "<<configMap[i].iniKey<<"="<<intValue);
				}
				break;
			case _UINT64:
				if ((iss >> dec >> int64Value).fail())
				{
					ERROR("could not parse line "<<lineNumber<<" (non-numeric value '"<<valueString<<"')?");
				}
				*((uint64_t *)configMap[i].variablePtr) = int64Value;
				if (DEBUG_INI_READER)
				{
					DEBUG("\t - SETTING "<<configMap[i].iniKey<<"="<<int64Value);
				}
				break;
			case _FLOAT:
				if ((iss >> dec >> floatValue).fail())
				{
					ERROR("could not parse line "<<lineNumber<<" (non-numeric value '"<<valueString<<"')?");
				}
				*((float *)configMap[i].variablePtr) = floatValue;
				if (DEBUG_INI_READER)
				{
					DEBUG("\t - SETTING "<<configMap[i].iniKey<<"="<<floatValue);
				}
				break;
			case _STRING:
				*((string *)configMap[i].variablePtr) = string(valueString);
				if (DEBUG_INI_READER)
				{
					DEBUG("\t - SETTING "<<configMap[i].iniKey<<"="<<valueString);
				}

				break;
			case _BOOL:
				if (valueString == "true" || valueString == "1")
				{
					*((bool *)configMap[i].variablePtr) = true;
				}
				else
				{
					*((bool *)configMap[i].variablePtr) = false;
				}
			}
			// lineNumber == 0 implies that this is an override parameter from the command line, so don't bother doing these checks
			if (lineNumber > 0)
			{
				if (isSystemParam && configMap[i].parameterType == DEV_PARAM)
				{
					DEBUG("WARNING: Found device parameter "<<configMap[i].iniKey<<" in system config file");
				}
				else if (!isSystemParam && configMap[i].parameterType == SYS_PARAM)
				{
					DEBUG("WARNING: Found system parameter "<<configMap[i].iniKey<<" in device config file");
				}
			}
			// use the pointer stored in the config map to set the value of the variable
			// to make sure all parameters are in the ini file
			configMap[i].wasSet = true;
			break;
		}
	}

	if (configMap[i].variablePtr == NULL)
	{
		DEBUG("WARNING: UNKNOWN KEY '"<<key<<"' IN INI FILE");
	}
}

void IniReader::ReadIniFile(string filename, bool isSystemFile)
{
	ifstream iniFile;
	string line;
	string key,valueString;

	size_t commentIndex, equalsIndex;
	size_t lineNumber=0;

	iniFile.open(filename.c_str());
	if (iniFile.is_open())
	{
		while (!iniFile.eof())
		{
			lineNumber++;
			getline(iniFile, line);
			//this can happen if the filename is actually a directory
			if (iniFile.bad())
			{
				ERROR("Cannot read ini file '"<<filename<<"'");
				exit(-1);
			}
			// skip zero-length lines
			if (line.size() == 0)
			{
//					DEBUG("Skipping blank line "<<lineNumber);
				continue;
			}
			//search for a comment char
			if ((commentIndex = line.find_first_of(";")) != string::npos)
			{
				//if the comment char is the first char, ignore the whole line
				if (commentIndex == 0)
				{
//						DEBUG("Skipping comment line "<<lineNumber);
					continue;
				}
//					DEBUG("Truncating line at comment"<<line[commentIndex-1]);
				//truncate the line at first comment before going on
				line = line.substr(0,commentIndex);
			}
			// trim off the end spaces that might have been between the value and comment char
			size_t whiteSpaceEndIndex;
			if ((whiteSpaceEndIndex = line.find_last_not_of(" \t")) != string::npos)
			{
				line = line.substr(0,whiteSpaceEndIndex+1);
			}

			// at this point line should be a valid, commentless string

			// a line has to have an equals sign
			if ((equalsIndex = line.find_first_of("=")) == string::npos)
			{
				ERROR("Malformed Line "<<lineNumber<<" (missing equals)");
				abort();
			}
			size_t strlen = line.size();
			// all characters before the equals are the key
			key = line.substr(0, equalsIndex);
			// all characters after the equals are the value
			valueString = line.substr(equalsIndex+1,strlen-equalsIndex);

			IniReader::SetKey(key, valueString, isSystemFile, lineNumber);
			// got to the end of the config map without finding the key
		}
	}
	else
	{
		ERROR ("Unable to load ini file "<<filename);
		abort();
	}
	/* precompute frequently used values */
	NUM_BANKS_LOG		= dramsim_log2(NUM_BANKS);
	NUM_CHANS_LOG		= dramsim_log2(NUM_CHANS);
	NUM_ROWS_LOG		= dramsim_log2(NUM_ROWS);
	NUM_COLS_LOG		= dramsim_log2(NUM_COLS);
	BYTE_OFFSET_WIDTH	= dramsim_log2(JEDEC_DATA_BUS_BITS / 8);
	TRANSACTION_SIZE	= JEDEC_DATA_BUS_BITS / 8 * BL;
	THROW_AWAY_BITS		= dramsim_log2(TRANSACTION_SIZE);
	COL_LOW_BIT_WIDTH	= THROW_AWAY_BITS - BYTE_OFFSET_WIDTH;
}

void IniReader::OverrideKeys(const OverrideMap *map)
{
	if (!map)
		return; 

	OverrideIterator it = map->begin();
	DEBUG("Key overrides from command line:"); 
	for (it=map->begin(); it != map->end(); it++)
	{
		string key = it->first; 
		string value = it->second; 
		DEBUG("\t'"<< key <<"'->'"<< value<< "'"); 
		IniReader::SetKey(key,value);
	}
}

bool IniReader::CheckIfAllSet()
{
	// check to make sure all parameters that we exepected were set
	for (size_t i=0; configMap[i].variablePtr != NULL; i++)
	{
		if (!configMap[i].wasSet)
		{
			DEBUG("WARNING: KEY "<<configMap[i].iniKey<<" NOT FOUND IN INI FILE.");
			switch (configMap[i].variableType)
			{
				//the string and bool values can be defaulted, but generally we need all the numeric values to be set to continue
			case _UINT:
			case _UINT64:
			case _FLOAT:
				ERROR("Cannot continue without key '"<<configMap[i].iniKey<<"' set.");
				return false;
				break;
			case _BOOL:
				*((bool *)configMap[i].variablePtr) = false;
				DEBUG("\tSetting Default: "<<configMap[i].iniKey<<"=false");
				break;
			case _STRING:
				break;
			}
		}
	}
	return true;
}

/*
 * There is probably a way of doing this with templates, but since
 * we have the types defined as an enum, doing this with macros is trivial.
 *
 * Return value: 0 on success, -1 on error
 */
#define DEF_GETTER(_funcname, _type, _typename)				\
	int _funcname(const std::string& field, _type *val)		\
	{								\
		int i;							\
									\
		for (i=0; configMap[i].variablePtr != NULL; i++)	\
		{							\
			if (field.compare(configMap[i].iniKey))		\
				continue;				\
			if (configMap[i].variableType != _typename)	\
				return -1;				\
			*val = *(_type *)configMap[i].variablePtr;	\
			return 0;					\
		}							\
		return -1;						\
	}

/* TODO: getter for strings is missing. Probably not that useful though */
DEF_GETTER(IniReader::getBool, bool, _BOOL)
DEF_GETTER(IniReader::getUint, unsigned int, _UINT)
DEF_GETTER(IniReader::getUint64, uint64_t, _UINT64)
DEF_GETTER(IniReader::getFloat, float, _FLOAT)

void IniReader::InitEnumsFromStrings()
{
	if (ADDRESS_MAPPING_SCHEME == "scheme1")
	{
		addressMappingScheme = Scheme1;
		if (DEBUG_INI_READER) 
		{
			DEBUG("ADDR SCHEME: 1");
		}
	}
	else if (ADDRESS_MAPPING_SCHEME == "scheme2")
	{
		addressMappingScheme = Scheme2;
		if (DEBUG_INI_READER) 
		{
			DEBUG("ADDR SCHEME: 2");
		}
	}
	else if (ADDRESS_MAPPING_SCHEME == "scheme3")
	{
		addressMappingScheme = Scheme3;
		if (DEBUG_INI_READER) 
		{
			DEBUG("ADDR SCHEME: 3");
		}
	}
	else if (ADDRESS_MAPPING_SCHEME == "scheme4")
	{
		addressMappingScheme = Scheme4;
		if (DEBUG_INI_READER) 
		{
			DEBUG("ADDR SCHEME: 4");
		}
	}
	else if (ADDRESS_MAPPING_SCHEME == "scheme5")
	{
		addressMappingScheme = Scheme5;
		if (DEBUG_INI_READER) 
		{
			DEBUG("ADDR SCHEME: 5");
		}
	}
	else if (ADDRESS_MAPPING_SCHEME == "scheme6")
	{
		addressMappingScheme = Scheme6;
		if (DEBUG_INI_READER) 
		{
			DEBUG("ADDR SCHEME: 6");
		}
	}
	else if (ADDRESS_MAPPING_SCHEME == "scheme7")
	{
		addressMappingScheme = Scheme7;
		if (DEBUG_INI_READER) 
		{
			DEBUG("ADDR SCHEME: 7");
		}
	}
	else
	{
		cout << "WARNING: unknown address mapping scheme '"<<ADDRESS_MAPPING_SCHEME<<"'; valid values are 'scheme1'...'scheme7'. Defaulting to scheme1"<<endl;
		addressMappingScheme = Scheme1;
	}

	if (ROW_BUFFER_POLICY == "open_page")
	{
		rowBufferPolicy = OpenPage;
		if (DEBUG_INI_READER) 
		{
			DEBUG("ROW BUFFER: open page");
		}
	}
	else if (ROW_BUFFER_POLICY == "close_page")
	{
		rowBufferPolicy = ClosePage;
		if (DEBUG_INI_READER) 
		{
			DEBUG("ROW BUFFER: close page");
		}
	}
	else
	{
		cout << "WARNING: unknown row buffer policy '"<<ROW_BUFFER_POLICY<<"'; valid values are 'open_page' or 'close_page', Defaulting to Close Page."<<endl;
		rowBufferPolicy = ClosePage;
	}

	if (QUEUING_STRUCTURE == "per_rank_per_bank")
	{
		queuingStructure = PerRankPerBank;
		if (DEBUG_INI_READER) 
		{
			DEBUG("QUEUING STRUCT: per rank per bank");
		}
	}
	else if (QUEUING_STRUCTURE == "per_rank")
	{
		queuingStructure = PerRank;
		if (DEBUG_INI_READER) 
		{
			DEBUG("QUEUING STRUCT: per rank");
		}
	}
	else
	{
		cout << "WARNING: Unknown queueing structure '"<<QUEUING_STRUCTURE<<"'; valid options are 'per_rank' and 'per_rank_per_bank', defaulting to Per Rank Per Bank"<<endl;
		queuingStructure = PerRankPerBank;
	}

	if (SCHEDULING_POLICY == "rank_then_bank_round_robin")
	{
		schedulingPolicy = RankThenBankRoundRobin;
		if (DEBUG_INI_READER) 
		{
			DEBUG("SCHEDULING: Rank Then Bank");
		}
	}
	else if (SCHEDULING_POLICY == "bank_then_rank_round_robin")
	{
		schedulingPolicy = BankThenRankRoundRobin;
		if (DEBUG_INI_READER) 
		{
			DEBUG("SCHEDULING: Bank Then Rank");
		}
	}
	else
	{
		cout << "WARNING: Unknown scheduling policy '"<<SCHEDULING_POLICY<<"'; valid options are 'rank_then_bank_round_robin' or 'bank_then_rank_round_robin'; defaulting to Bank Then Rank Round Robin" << endl;
		schedulingPolicy = BankThenRankRoundRobin;
	}

}

} // namespace DRAMSim
