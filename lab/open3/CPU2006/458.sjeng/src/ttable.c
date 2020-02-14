/*
    Sjeng - a chess variants playing program
    Copyright (C) 2000-2003 Gian-Carlo Pascutto

    File: ttable.c                                       
    Purpose: handling of transposition tables and hashes

*/

#include "sjeng.h"
#include "protos.h"
#include "extvars.h"
#include "limits.h"

unsigned int zobrist[14][144];

unsigned int hash;

unsigned int TTProbes;
unsigned int TTHits;
unsigned int TTStores;

typedef struct 
{
  char Depth;  
  char OnMove;
  char Threat;
  char Type;
  unsigned short Bestmove;  
  unsigned int HashKey;
  unsigned int Hold_hash;
  int Bound;
}
TType;

typedef struct
{
  char OnMove;  
  char Type;  
  unsigned short Bestmove;  
  unsigned int HashKey;
  unsigned int Hold_hash;
  int Bound;
}
QTType;

TType *DP_TTable;
TType *AS_TTable;
QTType *QS_TTable;

void clear_tt(void)
{
  memset(DP_TTable, 0, sizeof(TType) * TTSize);
  memset(AS_TTable, 0, sizeof(TType) * TTSize);
  memset(QS_TTable, 0, sizeof(QTType) * TTSize);
}

void clear_dp_tt(void)
{
  memset(DP_TTable, 0, sizeof(TType) * TTSize);
}

void initialize_zobrist(void)
{
  int p, q;
  
  seedMT(31657);
  
  for(p = 0; p < 14; p++)
  {
    for(q = 0; q < 144; q++)
      {
	zobrist[p][q] = randomMT();
      }
  }
  /* our magic number */

  hash = 0xDEADBEEF;
  hold_hash = 0xC0FFEE00;
}

void initialize_hash(void)
{
  int p;
  
  hash = 0xDEADBEEF;
  
  for(p = 0; p < 144; p++)
    {
      if (board[p] != npiece && board[p] != frame)
	hash = hash ^ zobrist[board[p]][p];
    }

  hold_hash = 0xC0FFEE00;
  /* we need to set up hold_hash here, rely on ProcessHolding for now */

}

void QStoreTT(int score, int alpha, int beta, int best)
{
  unsigned int ttindex;
  
  TTStores++;

  ttindex = hash % TTSize;

  if (score <= alpha)     
    QS_TTable[ttindex].Type = UPPER;
  else if(score >= beta) 
    QS_TTable[ttindex].Type = LOWER;
  else                  
    QS_TTable[ttindex].Type = EXACT;
  
  QS_TTable[ttindex].HashKey = hash;
  QS_TTable[ttindex].Hold_hash = hold_hash;
  QS_TTable[ttindex].Bestmove = best;
  QS_TTable[ttindex].Bound = score;
  QS_TTable[ttindex].OnMove = ToMove;
    
  return;
}

void StoreTT(int score, int alpha, int beta, int best, int threat, int depth)
{
  unsigned int ttindex;
  
  TTStores++;

  ttindex = hash % TTSize;

  /* Prefer storing entries with more information */
  if ((      ((int)DP_TTable[ttindex].Depth < depth) 
        ||  (((int)DP_TTable[ttindex].Depth == depth) && 
	        (    ((DP_TTable[ttindex].Type == UPPER) && (score > alpha))
		 ||  ((score > alpha) && (score < beta))
		)
	    )
      )
      && !is_pondering)
    {
      if (score <= alpha)  
      {
	DP_TTable[ttindex].Type = UPPER;
	if (score < -INF+500) score = -INF+500;
      }
      else if(score >= beta) 
      {
	DP_TTable[ttindex].Type = LOWER;
	if (score > INF-500) score = INF-500;
      }
      else                  
      {
	DP_TTable[ttindex].Type = EXACT;
     
	/* normalize mate scores */
       if (score > (INF-500))
	  score += ply;
        else if (score < (-INF+500))
	  score -= ply;
      }
      
      DP_TTable[ttindex].HashKey = hash;
      DP_TTable[ttindex].Hold_hash = hold_hash;
      DP_TTable[ttindex].Depth = depth;
      DP_TTable[ttindex].Bestmove = best;
      DP_TTable[ttindex].Bound = score;
      DP_TTable[ttindex].OnMove = ToMove;
      DP_TTable[ttindex].Threat = threat;
    }
  else 
    {
      if (score <= alpha)  
      {
	AS_TTable[ttindex].Type = UPPER;
	if (score < -INF+500) score = -INF+500;
      }
      else if(score >= beta) 
      {
	AS_TTable[ttindex].Type = LOWER;
	if (score > INF-500) score = INF-500;
      }
      else                  
      {
	AS_TTable[ttindex].Type = EXACT;
     
	/* normalize mate scores */
       if (score > (INF-500))
	  score += ply;
        else if (score < (-INF+500))
	  score -= ply;
      }
      
      AS_TTable[ttindex].HashKey = hash;
      AS_TTable[ttindex].Hold_hash = hold_hash;
      AS_TTable[ttindex].Depth = depth;
      AS_TTable[ttindex].Bestmove = best;
      AS_TTable[ttindex].Bound = score;
      AS_TTable[ttindex].OnMove = ToMove;
      AS_TTable[ttindex].Threat = threat;
    };
  
  return;
}

void LearnStoreTT(int score, unsigned nhash, unsigned hhash, int tomove, int best, int depth)
{
  unsigned int ttindex;

  ttindex = nhash % TTSize;

  AS_TTable[ttindex].Depth = depth;
  
  if (Variant != Suicide && Variant != Losers)
  {
    AS_TTable[ttindex].Type = EXACT;
  }
  else
  {
    AS_TTable[ttindex].Type = UPPER;
  }
  
  AS_TTable[ttindex].HashKey = nhash;
  AS_TTable[ttindex].Hold_hash = hhash;
  AS_TTable[ttindex].Bestmove = best;
  AS_TTable[ttindex].Bound = score;
  AS_TTable[ttindex].OnMove = tomove;
  AS_TTable[ttindex].Threat = 0;

}

int ProbeTT(int *score, int beta, int *best, int *threat, int *donull, int depth)
{

  unsigned int ttindex;

  *donull = TRUE;

  TTProbes++;

  ttindex = hash % TTSize;
  
  if ((DP_TTable[ttindex].HashKey == hash) 
      && (DP_TTable[ttindex].Hold_hash == hold_hash) 
      && (DP_TTable[ttindex].OnMove == (char)ToMove))
    {
      TTHits++;
      
      if ((DP_TTable[ttindex].Type == UPPER) 
      	   && ((depth-2-1) <= (int) DP_TTable[ttindex].Depth) 
      	   && (DP_TTable[ttindex].Bound < beta)) 
      	  *donull = FALSE;

      if (DP_TTable[ttindex].Threat) depth++;
      
      if ((int) DP_TTable[ttindex].Depth >= depth)
	{
	  *score = DP_TTable[ttindex].Bound;
	  
	  if (*score > (INF-500))
	   *score -= ply;
	  else if (*score < (-INF+500))
	    *score += ply;

	  *best = DP_TTable[ttindex].Bestmove;
	  *threat = DP_TTable[ttindex].Threat;

	  return DP_TTable[ttindex].Type;
	}
      else
	{
	  *best = DP_TTable[ttindex].Bestmove;
	  *threat = DP_TTable[ttindex].Threat;

	  return DUMMY;
	}
    }
  else if ((AS_TTable[ttindex].HashKey == hash) 
      && (AS_TTable[ttindex].Hold_hash == hold_hash) 
      && (AS_TTable[ttindex].OnMove == (char)ToMove))
    {
      TTHits++;
      
      if ((AS_TTable[ttindex].Type == UPPER) 
      	   && ((depth-2-1) <= (int) AS_TTable[ttindex].Depth) 
      	   && (AS_TTable[ttindex].Bound < beta)) 
      	  *donull = FALSE;

      if (AS_TTable[ttindex].Threat) depth++;
      
      if ((int) AS_TTable[ttindex].Depth >= depth)
	{
	  *score = AS_TTable[ttindex].Bound;
	  
	  if (*score > (INF-500))
	   *score -= ply;
	  else if (*score < (-INF+500))
	    *score += ply;

	  *best = AS_TTable[ttindex].Bestmove;
	  *threat = AS_TTable[ttindex].Threat;

	  return AS_TTable[ttindex].Type;
	}
      else
	{
	  *best = AS_TTable[ttindex].Bestmove;
	  *threat = AS_TTable[ttindex].Threat;

	  return DUMMY;
	}
    }
  else
    return HMISS;

}

int QProbeTT(int *score, int *best)
{

  unsigned int ttindex;

  TTProbes++;

  ttindex = hash % TTSize;
  
  if ((QS_TTable[ttindex].HashKey == hash) 
      && (QS_TTable[ttindex].Hold_hash == hold_hash) 
      && (QS_TTable[ttindex].OnMove == (char)ToMove))
    {
      TTHits++;
      
      *score = QS_TTable[ttindex].Bound;
      
      *best = QS_TTable[ttindex].Bestmove;
      
      return QS_TTable[ttindex].Type;
    }
  else
    return HMISS;

}


void alloc_hash(void)
{
  AS_TTable = (TType *) malloc(sizeof(TType) * TTSize);
  DP_TTable = (TType *) malloc(sizeof(TType) * TTSize);
  QS_TTable = (QTType *) malloc(sizeof(QTType) * TTSize);

  if (AS_TTable == NULL || DP_TTable == NULL || QS_TTable == NULL)
  {
    printf("Out of memory allocating hashtables.\n");
    exit(EXIT_FAILURE);
  }
  
/*  printf("Allocated 2*%d hash entries, totalling %u bytes.\n",
          TTSize, (unsigned int)(2*sizeof(TType)*TTSize));
  printf("Allocated %d quiescenthash entries, totalling %u bytes.\n",
          TTSize, (unsigned int)(sizeof(QTType)*TTSize));
  */
  return; 
}

void free_hash(void)
{
  free(AS_TTable);
  free(DP_TTable);
  free(QS_TTable);
  return;
}

