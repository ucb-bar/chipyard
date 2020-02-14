/*
    Sjeng - a chess variants playing program
    Copyright (C) 2000-2003 Gian-Carlo Pascutto

    File: seval.c                                        
    Purpose: functions for evaluating positions (suicide chess)

*/

#include "sjeng.h"
#include "extvars.h"
#include "protos.h"

static int scentral[144] = {
0,0,0,0,0,0,0,0,0,0,0,0,
0,0,0,0,0,0,0,0,0,0,0,0,
0,0,-20,-10,-10,-10,-10,-10,-10,-20,0,0,
0,0,-10,0,3,5,5,3,0,-10,0,0,
0,0,-10,2,15,15,15,15,2,-10,0,0,
0,0,-10,7,15,25,25,15,7,-10,0,0,
0,0,-10,7,15,25,25,15,7,-10,0,0,
0,0,-10,2,15,15,15,15,2,-10,0,0,
0,0,-10,0,3,5,5,3,0,-10,0,0,
0,0,-20,-10,-10,-10,-10,-10,-10,-20,0,0,
0,0,0,0,0,0,0,0,0,0,0,0,
0,0,0,0,0,0,0,0,0,0,0,0};

static const int rook_o[4] = {12, -12, 1, -1};
static const int bishop_o[4] = {11, -11, 13, -13};
static const int knight_o[8] = {10, -10, 14, -14, 23, -23, 25, -25};
  static const int king_o[8] = {13, 12, 11, 1, -1, -11, -12, -13};

static int s_bishop_mobility(int square)
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
  
  return m << 2;
}

static int s_rook_mobility(int square)
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
  
  return m << 2;
}

static int s_knight_mobility(int square)
{ 
  register int d, m = 0;
  
  for (d = 0; d < 8; d++)
    {
      if (board[square + knight_o[d]] == npiece) m++;
    }
	
  return m << 2;
}

static int s_pawn_mobility(int square)
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

  return m << 3;
}

static int s_king_mobility(int square)
{
  register int d, m = 0;
  
  for (d = 0; d < 8; d++)
    {
      if (board[square + king_o[d]] == npiece) m++;
    }

  return m << 2;
}

static int black_saccers(int square)
{
  register int f = FALSE;

  /* for white pawn on square, any black
   * pieces that can sac themselves to it? */
  
  /* check for case where is_attacked fails
     because pawns dont move to their attacks */
  
  if (board[square + 24] == bpawn ||
      board[square + 22] == bpawn ||
      board[square + 26] == bpawn)
    {
      return 0;
    }

  /* ok, now check pawn moves */

  if ((rank(square) < 6) 
      && (board[square + 25] == bpawn 
	  ||
	  board[square + 23] == bpawn))
    {
      f = TRUE;
    }
  else if (rank(square) == 4 &&
	   (board[square + 35] == bpawn ||
	    board[square + 37] == bpawn))
    {
      f = TRUE;
    }
  
  if (!f)
    {
      f = (is_attacked(square + 11, 0) ? 1 : 0);
    }
  if (!f)
    {
      f = (is_attacked(square + 13, 0) ? 2 : 0);
    }
  
  if (!f)
    {
      return 0;
    }
  else
    {
      /* black can sac, but is the saccer defended ? */
      
      if (f == 1)
	{
	  if (calc_attackers(square + 11, 0) > 1)
	    {
	      /* yep */
	      return 0;
	    }
	  else
	    {
	      /* nope */
	      return 30;
	    }
	}
      else
	{
	  if (calc_attackers(square + 13, 0) > 1)
	    {
	      return 0;
	    }
	  else
	    {
	      return 30;
	    }
	  
	}
    }
  
}

static int white_saccers(int square)
{
  /* for black pawn on square, any black
   * pieces that can sac themselves to it? */
  
  register int f = FALSE;

  /* for white pawn on square, any black
   * pieces that can sac themselves to it? */
  
  /* check for case where is_attacked fails
     because pawns dont move to their attacks */
  
  if (board[square - 24] == wpawn ||
      board[square - 22] == wpawn ||
      board[square - 26] == wpawn)
    {
      return 0;
    }

  /* ok, now check pawn moves */

  if ((rank(square) > 3) 
      && (board[square - 25] == wpawn 
	  ||
	  board[square - 23] == wpawn))
    {
      f = TRUE;
    }
  else if (rank(square) == 5 &&
	   (board[square - 35] == wpawn ||
	    board[square - 37] == wpawn))
    {
      f = TRUE;
    }
  
  if (!f)
    {
      f = (is_attacked(square - 11, 1) ? 1 : 0);
    }
  if (!f)
    {
      f = (is_attacked(square - 13, 1) ? 2 : 0);
    }
  
  if (!f)
    {
      return 0;
    }
  else
    {
      /* black can sac, but is the saccer defended ? */
      
      if (f == 1)
	{
	  if (calc_attackers(square - 11, 1) > 1)
	    {
	      /* yep */
	      return 0;
	    }
	  else
	    {
	      /* nope */
	      return 30;
	    }
	}
      else
	{
	  if (calc_attackers(square - 13, 1) > 1)
	    {
	      return 0;
	    }
	  else
	    {
	      return 30;
	    }
	  
	}
    }

}

int suicide_eval (void) {

  /* select the appropriate eval() routine: */
  return (suicide_mid_eval ());
}

int suicide_mid_eval (void) {

  /* return a score for the current middlegame position: */

  int srank, pawn_file, pawns[2][11], white_back_pawn[11], black_back_pawn[11];
  int isolated, backwards, i, a, j;
  int score = 0;
  int in_cache;
  int wb = 0, bb = 0, wbc = 0, bbc = 0;
  int wk = 0, bk = 0, wr = 0, br = 0;
  int wn = 0, bn = 0, wp = 0, bp = 0;
  
  in_cache = 0;
  
  checkECache(&score, &in_cache);
  
  if(in_cache)
    {
      if (white_to_move == 1) return score;
      return -score;
    } 

  score = Material;

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

    pawn_file = file (i)+1;
    srank = rank (i);
    switch (board[i]) {
      case (wpawn):
	score += scentral[i];
	score += s_pawn_mobility(i);
        score -= black_saccers(i);
	wp++;
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
	  score -= 15*(pawns[1][pawn_file]-1);

	if (!pawns[0][pawn_file] && srank >= black_back_pawn[pawn_file-1] &&
	    srank >= black_back_pawn[pawn_file+1]) {
	  score += 30 + 3*(rank(i)-2);

	  /* outside passer ? */
	  if (file(i) == 1 || file(i) == 8)
	    score += 4 + 2*(rank(i)-2);
	    
	  /* give an extra bonus if a connected, passed pawn: */
	  if (!isolated)
	    {
	      score += 6;
	    }
	}

	/* check for pawn islands */
	if (!pawns[1][pawn_file-1])
	  score -= 20;

	break;

     case (bpawn):
	score -= scentral[i];
	score -= s_pawn_mobility(i);
	score += white_saccers(i);
	isolated = FALSE;
	backwards = FALSE;
        bp++;
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
	  score += 15*(pawns[0][pawn_file]-1);

	if (!pawns[1][pawn_file] && srank <= white_back_pawn[pawn_file-1] &&
	    srank <= white_back_pawn[pawn_file+1]) {
	  score -= 30 + 3*(7-rank(i));
	      
	  /* outside passer ? */
	  if (file(i) == 1 || file(i) == 8)
	    score -= 4 + 2*(7-rank(i));
	  
	  /* give an extra bonus if a connected, passed pawn: */
	  if (!isolated)
	    {
	      score -= 6;
	    }
	}

	if (!pawns[0][pawn_file-1])
	  score += 20;

	break;

      case (wrook):
	score += scentral[i];
	score += s_rook_mobility(i);
	wr++;
	break;

      case (brook):
	score -= scentral[i];
	score -= s_rook_mobility(i);
	br++;
	break;

      case (wbishop):
	score += scentral[i];
	score += s_bishop_mobility(i);
	if (wb)
	{
	  if (sqcolor[i] != wbc)
	    wb = 99;
	}
	wb++;
	wbc = sqcolor[i];
	break;

      case (bbishop):
	score -= scentral[i];
	score -= s_bishop_mobility(i);
	if (bb) 
	{
	  /* two bishops, check for same color */
          if (sqcolor[i] != bbc)
	    bb = 99;
	}
	bb++;
	bbc = sqcolor[i];
	break;

      case (wknight):
	score += scentral[i];
	score += s_knight_mobility(i);
	wn++;
	break;

      case (bknight):
	score -= scentral[i];
	score -= s_knight_mobility(i);
	bn++;
	break;

      case (wqueen):
	score += scentral[i];
	score += s_rook_mobility(i);
	score += s_bishop_mobility(i);
	break;

      case (bqueen):
	score -= scentral[i];
	score -= s_rook_mobility(i);
	score -= s_bishop_mobility(i);
	break;

      case (wking):
	score += scentral[i] >> 1;
	score += s_king_mobility(i);
	wk++;
	break;

      case (bking):
	score -= scentral[i] >> 1;
	score -= s_king_mobility(i);
	bk++;
	break;
    }
  }

  /* opposite color bishops */
  if ((wb < 99) && (bb < 99) && (wbc != bbc) && (piece_count < 32))
    {
	score = (int)((float)score * (float)((float)piece_count / 32.0));
    }
  
  storeECache(score);
  
  /* adjust for color: */
  if (white_to_move == 1) {
    return score;
  }
  else {
    return -score;
  }

}
