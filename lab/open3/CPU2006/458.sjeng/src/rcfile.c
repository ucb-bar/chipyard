/*
    Sjeng - a chess variants playing program
    Copyright (C) 2003 Gian-Carlo Pascutto

    File: rcfile.c
    Purpose: Read in config file, allocate hash/caches                  

*/

#include "sjeng.h"
#include "protos.h"
#include "extvars.h"

FILE *rcfile;
char line[STR_BUFF];

int TTSize;
int ECacheSize;
int PBSize;

int cfg_booklearn;
int cfg_razordrop;
int cfg_cutdrop;
int cfg_ksafety[15][9];
int cfg_tropism[5][7];
int havercfile;
int cfg_futprune;
int cfg_devscale;
int cfg_onerep;
int cfg_recap;
int cfg_smarteval;
int cfg_attackeval;
float cfg_scalefac;

void read_rcfile (void) 
{
  unsigned int setc;
  
      TTSize = 3000000;
      ECacheSize = 1000000;
      PBSize = 200000;
      
      cfg_devscale = 1;
      cfg_scalefac = 1.0;
      cfg_razordrop = 1;
      cfg_cutdrop = 0;
      cfg_futprune = 1;
      cfg_smarteval = 1;
      cfg_attackeval = 0;
      cfg_onerep = 1;
      cfg_recap = 0;
      
      havercfile = 0;

      setc =   havercfile 
	    + (cfg_devscale << 1) 
	    + (1 << 2)
	    + (cfg_razordrop << 3)
	    + (cfg_cutdrop << 4)
	    + (cfg_futprune << 5)
	    + (cfg_smarteval << 6)
	    + (cfg_attackeval << 7);
	    
      
      sprintf(setcode, "%u", setc);
      
      initialize_eval();
      alloc_hash();
      alloc_ecache();
      
      return;
}

