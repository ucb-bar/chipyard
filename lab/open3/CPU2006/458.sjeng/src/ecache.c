/*
    Sjeng - a chess variants playing program
    Copyright (C) 2000-2003 Gian-Carlo Pascutto

    File: ecache.c                                             
    Purpose: handling of the evaluation cache

*/

#include "sjeng.h"
#include "protos.h"
#include "extvars.h"

typedef struct  
{
    unsigned int stored_hash;
    unsigned int hold_hash;
    int score;
} ECacheType;


ECacheType *ECache;

unsigned int ECacheProbes;
unsigned int ECacheHits;

void storeECache(int score)
{
  int ecindex;

  ecindex = hash % ECacheSize;

  ECache[ecindex].stored_hash = hash;
  ECache[ecindex].hold_hash = hold_hash;
  ECache[ecindex].score = score;
}

void checkECache(int *score, int *in_cache)
{
  int ecindex;

  ECacheProbes++;

  ecindex = hash % ECacheSize;

  if(ECache[ecindex].stored_hash == hash &&
	  ECache[ecindex].hold_hash == hold_hash)
    
    {
      ECacheHits++;  

      *in_cache = 1;
      *score = ECache[ecindex].score;
    }
}

void reset_ecache(void)
{
  memset(ECache, 0, sizeof(ECacheType) * ECacheSize );
  return;
}

void alloc_ecache(void)
{
  ECache = (ECacheType*)malloc(sizeof(ECacheType)*ECacheSize);

  if (ECache == NULL)
  {
    printf("Out of memory allocating ECache.\n");
    exit(EXIT_FAILURE);
  }
  
/*  printf("Allocated %i eval cache entries, totalling %i bytes.\n",
          ECacheSize, (int)sizeof(ECacheType)*ECacheSize);
  */
   return;
}

void free_ecache(void)
{
  free(ECache);
  return;
}
