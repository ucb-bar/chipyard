/*
    Sjeng - a chess variants playing program
    Copyright (C) 2000-2003 Gian-Carlo Pascutto

    File: leval.c                                        
    Purpose: functions for evaluating positions in losers chess

*/

#include "sjeng.h"
#include "extvars.h"
#include "protos.h"

static int lcentral[144] = {
0,0,0,0,0,0,0,0,0,0,0,0,
0,0,0,0,0,0,0,0,0,0,0,0,
0,0,-20,-15,-15,-15,-15,-15,-15,-20,0,0,
0,0,-15,0,3,5,5,3,0,-15,0,0,
0,0,-15,0,15,15,15,15,0,-15,0,0,
0,0,-15,0,15,30,30,15,0,-15,0,0,
0,0,-15,0,15,30,30,15,0,-15,0,0,
0,0,-15,0,15,15,15,15,0,-15,0,0,
0,0,-15,0,3,5,5,3,0,-15,0,0,
0,0,-20,-15,-15,-15,-15,-15,-15,-20,0,0,
0,0,0,0,0,0,0,0,0,0,0,0,
0,0,0,0,0,0,0,0,0,0,0,0};

static int l_bishop_mobility(int square)
{
  register int l;
  register int m = 0;

  for (l = square-13; board[l] == npiece; l-=13)
    m++;
  for (l = square-11; board[l] == npiece; l-=11)
    m++;
  for (l = square+11; board[l] == npiece; l+=11)
    m++;
  for (l = square+13; board[l] == npiece; l+=13)
    m++;

  return m;
}

static int l_rook_mobility(int square)
{
  register int l;
  register int m = 0;

  for (l = square-12; board[l] == npiece; l-=12)
    m++;
  for (l = square-1; board[l] == npiece; l-=1)
    m++;
  for (l = square+1; board[l] == npiece; l+=1)
    m++;
  for (l = square+12; board[l] == npiece; l+=12)
    m++;

  return m;
}


static int l_knight_mobility(int square)
{ 
  static const int knight_o[8] = {10, -10, 14, -14, 23, -23, 25, -25};
  register int d, m = 0;
  
  for (d = 0; d < 8; d++)
    {
      if (board[square + knight_o[d]] == npiece) m++;
    }
	
  return m;
}

static int l_pawn_mobility(int square)
{
  register int m = 0;

  if (board[square] == wpawn)
    {
      if (board[square + 12] == npiece) m++;
    }
  else
    {
      if (board[square - 12] == npiece) m++;
    } 

  return m;
}

static int l_king_mobility(int square)
{
  static const int king_o[8] = {13, 12, 11, 1, -1, -11, -12, -13};
  register int d, m = 0;
  
  for (d = 0; d < 8; d++)
    {
      if (board[square + king_o[d]] == npiece) m++;
    }

  return m;
}


int losers_eval (void) {

  /* return a score for the current middlegame position: */
  int srank = 0, pawn_file = 0, pawns[2][11], white_back_pawn[11], black_back_pawn[11];
  int isolated, backwards;
  int i, a, j;
  int score = 0;
  int in_cache;
  int wp = 0, bp = 0;
  int wks = 0, bks = 0;
  int wpassp = 0, bpassp = 0;
  int wpawns = 0, bpawns = 0;
  
  in_cache = 0;
  
  checkECache(&score, &in_cache);
  
  if(in_cache)
    {
      if (white_to_move == 1) return score;
      return -score;
    } 

  /* initialize the pawns array, (files offset by one to use dummy files in
     order to easier determine isolated status) and also initialize the
     arrays keeping track of the rank of the most backward pawn: */
  memset (pawns, 0, sizeof (pawns));
  for (i = 0; i < 11; i++) {
    white_back_pawn[i] = 7;
    black_back_pawn[i] = 2;
  }
  for (j = 1, a = 1; (a <= piece_count); j++) {
     i = pieces[j];
    
    if (!i)
      continue;
    else
      a++;

    assert((i > 0) && (i < 145));
 
    pawn_file = file (i)+1;
    srank = rank (i);
    if (board[i] == wpawn) {
      pawns[1][pawn_file]++;
      if (srank < white_back_pawn[pawn_file]) {
	white_back_pawn[pawn_file] = srank;
      }
    }
    else if (board[i] == bpawn) {
      pawns[0][pawn_file]++;
      if (srank > black_back_pawn[pawn_file]) {
	black_back_pawn[pawn_file] = srank;
      }
    }
  }

  /* loop through the board, adding material value, as well as positional
     bonuses for all pieces encountered: */
  for (j = 1, a = 1; (a <= piece_count); j++) {
    i = pieces[j];
    
    if (!i)
      continue;
    else
      a++;

    switch (board[i]) {
      case (wpawn):
	wp++;
	wpawns++;
	score += lcentral[i];
	score += l_pawn_mobility(i) << 2;
	score += (rank(i) - 2) * 8; 
	isolated = FALSE;
	backwards = FALSE;
	
	/* check for backwards pawns: */
	if (white_back_pawn[pawn_file+1] > srank
	    && white_back_pawn[pawn_file-1] > srank) {
	  score -= 8;
	  backwards = TRUE;
	  /* check to see if it is furthermore isolated: */
	  if (!pawns[1][pawn_file+1] && !pawns[1][pawn_file-1]) {
	    score -= 12;
	    isolated = TRUE;
	  }
	}

	if (!pawns[0][pawn_file]) {
	  if (backwards) score -= 5;
	  if (isolated) score -= 8;
	}

	if (pawns[1][pawn_file] > 1)
	  score -= 8*(pawns[1][pawn_file]-1);

	if (!pawns[0][pawn_file] && srank >= black_back_pawn[pawn_file-1] &&
	    srank >= black_back_pawn[pawn_file+1]) {
	  score += 25 + 10*(rank(i)-2);

	  if (rank(i) == 7) score += 50;
	  
	  wpassp++;

	  /* outside passer ? */
	  if (file(i) == 1 || file(i) == 8)
	    score += 4 + 2*(rank(i)-2);
	    
	  /* give an extra bonus if a connected, passed pawn: */
	  if (!isolated)
	    {
	      score += 24;
	    }
	}

	/* check for pawn islands */
	if (!pawns[1][pawn_file-1])
	  score -= 5;

	break;

      case (bpawn):
	bp++;
	bpawns++;
	score -= lcentral[i];
	score -= l_pawn_mobility(i) << 2;
	score -= (7 - rank(i)) * 8;
	isolated = FALSE;
	backwards = FALSE;	

	/* in general, bonuses/penalties in the endgame evaluation will be
	   higher, since pawn structure becomes more important for the
	   creation of passed pawns */

	/* check for backwards pawns: */
	if (black_back_pawn[pawn_file+1] < srank
	    && black_back_pawn[pawn_file-1] < srank) {
	  score += 8;
	  backwards = TRUE;
	  /* check to see if it is furthermore isolated: */
	  if (!pawns[0][pawn_file+1] && !pawns[0][pawn_file-1]) {
	    score += 12;
	    isolated = TRUE;
	  }
	}

	if (!pawns[1][pawn_file]) {
	  if (backwards) score += 5;
	  if (isolated) score += 8;
	}

	if (pawns[0][pawn_file] > 1)
	  score += 8*(pawns[0][pawn_file]-1);

	if (!pawns[1][pawn_file] && srank <= white_back_pawn[pawn_file-1] &&
	    srank <= white_back_pawn[pawn_file+1]) {
	  score -= 25 + 10*(7-rank(i));

	  if (rank(i) == 2) score -= 50;

	  bpassp++;
	      
	  /* outside passer ? */
	  if (file(i) == 1 || file(i) == 8)
	    score -= 4 + 2*(7-rank(i));
	  
	  /* give an extra bonus if a connected, passed pawn: */
	  if (!isolated)
	    {
	      score -= 24;
	    }
	}

	if (!pawns[0][pawn_file-1])
	  score += 5;

	break;

      case (wrook):
	wp++;
	score += l_rook_mobility(i) << 2;
	score += lcentral[i];
	break;

      case (brook):
	bp++;
	score -= l_rook_mobility(i) << 2;
	score -= lcentral[i];
	break;

      case (wbishop):
	wp++;
	score += l_bishop_mobility(i) << 2;
	score += lcentral[i];
	break;

      case (bbishop):
	bp++;
	score -= l_bishop_mobility(i) << 2;
	score -= lcentral[i];
	break;

      case (wknight):
	wp++;
	score += lcentral[i] << 1;
	score += l_knight_mobility(i) << 2;
	break;

      case (bknight):
	bp++;
	score -= lcentral[i] << 1;
	score -= l_knight_mobility(i) << 2;
	break;

      case (wqueen):
	wp++;
	score += l_bishop_mobility(i) << 1;
	score += l_rook_mobility(i) << 1;
	score += lcentral[i];
	break;

      case (bqueen):
	bp++;
	score -= l_bishop_mobility(i) << 1;
	score -= l_rook_mobility(i) << 1;
	score -= lcentral[i];
	break;

      case (wking):
	/* being in center is BAD */
	wks = lcentral[i] << 1;
	score += l_king_mobility(i);
	break;

      case (bking):
	/* being in center is BAD */
	bks = lcentral[i] << 1;
	score -= l_king_mobility(i);
	break;
    }
  }

  if (wp + bp > 10)
  {
     score -= wks - bks;
  }
  
  if (abs(Material) <= 900)
  {
    score += Material;
  }
  else
  {
    /* one side has a huge advantage, which could
     * become problematic */
    /* only apply this to self, we assume somebody
     * else can handle this just fine */
  
    /* I would swear the colors are reversed here,
     * but it works this way and not otherwise :) */

    /* disable this if we have passers...else they'll
       just sit on last rank */
    
    if (Material > 0 && comp_color == !WHITE && !wpassp)
    {
       score += 1800 - Material;
    }
    else if (Material < 0 && comp_color == !BLACK && !bpassp)
    {
       score += -(1800 + Material);
    }
    else
    {
    	score += Material;
    }
  }

  if (!wpawns) score += 200;
  else if (!bpawns) score -= 200;
  
  if (!wp) score = INF;
  else if (!bp) score = -INF;
  
  storeECache(score);
  
  /* adjust for color: */
  if (white_to_move == 1) {
    return score;
  }
  else {
    return -score;
  }

}

