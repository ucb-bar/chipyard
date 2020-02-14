/*
    Sjeng - a chess variants playing program
    Copyright (C) 2003 Gian-Carlo Pascutto and Vincent Diepeveen

    File: neval.c
    Purpose: functions for evaluating positions (standard chess)

*/

#include "sjeng.h"
#include "extvars.h"
#include "protos.h"
#include "squares.h"

int  King(int,int);
int  Queen(int,int);
int  Rook(int,int);
int  Bishop(int,int);
int  Knight(int,int);
int  Pawn(int,int);
int  ErrorIt(int,int);

typedef int (*EVALFUNC)(int sq,int c);
static EVALFUNC evalRoutines[7] = {
  ErrorIt,
  Pawn,
  Knight,
  King,
  Rook,
  Queen,
  Bishop };

int maxposdiff;

#define ENDGAME_MAT 1300
#define OPENING_MAT 2200

int distance[144][144];
int rookdistance[144][144];
int king_locs[2];
int wmat, bmat;

/* these tables will be used for positional bonuses: */

static int sbishop[144] = {
0,0,0,0,0,0,0,0,0,0,0,0,
0,0,0,0,0,0,0,0,0,0,0,0,
0,0,-2,-2,-2,-2,-2,-2,-2,-2,0,0,
0,0,-2,8,5,5,5,5,8,-2,0,0,
0,0,-2,3,3,5,5,3,3,-2,0,0,
0,0,-2,2,5,4,4,5,2,-2,0,0,
0,0,-2,2,5,4,4,5,2,-2,0,0,
0,0,-2,3,3,5,5,3,3,-2,0,0,
0,0,-2,8,5,5,5,5,8,-2,0,0,
0,0,-2,-2,-2,-2,-2,-2,-2,-2,0,0,
0,0,0,0,0,0,0,0,0,0,0,0,
0,0,0,0,0,0,0,0,0,0,0,0};

static int sknight[144] = {
0,0,0,0,0,0,0,0,0,0,0,0,
0,0,0,0,0,0,0,0,0,0,0,0,
0,0,-20,-10,-10,-10,-10,-10,-10,-20,0,0,
0,0,-10,0,0,3,3,0,0,-10,0,0,
0,0,-10,0,5,5,5,5,0,-10,0,0,
0,0,-10,0,5,10,10,5,0,-10,0,0,
0,0,-10,0,5,10,10,5,0,-10,0,0,
0,0,-10,0,5,5,5,5,0,-10,0,0,
0,0,-10,0,0,3,3,0,0,-10,0,0,
0,0,-20,-10,-10,-10,-10,-10,-10,-20,0,0,
0,0,0,0,0,0,0,0,0,0,0,0,
0,0,0,0,0,0,0,0,0,0,0,0};

static int swhite_pawn[144] = {
0,0,0,0,0,0,0,0,0,0,0,0,
0,0,0,0,0,0,0,0,0,0,0,0,
0,0,0,0,0,0,0,0,0,0,0,0,
0,0,0,0,0,0,0,0,0,0,0,0,
0,0,1,2,3,10,10,3,2,1,0,0,
0,0,2,4,6,12,12,6,4,2,0,0,
0,0,3,6,9,14,14,9,6,3,0,0,
0,0,10,12,14,16,16,14,12,10,0,0,
0,0,20,22,24,26,26,24,22,20,0,0,
0,0,0,0,0,0,0,0,0,0,0,0,
0,0,0,0,0,0,0,0,0,0,0,0,
0,0,0,0,0,0,0,0,0,0,0,0};

static int sblack_pawn[144] = {
0,0,0,0,0,0,0,0,0,0,0,0,
0,0,0,0,0,0,0,0,0,0,0,0,
0,0,0,0,0,0,0,0,0,0,0,0,
0,0,20,22,24,26,26,24,22,20,0,0,
0,0,10,12,14,16,16,14,12,10,0,0,
0,0,3,6,9,14,14,9,6,3,0,0,
0,0,2,4,6,12,12,6,4,2,0,0,
0,0,1,2,3,10,10,3,2,1,0,0,
0,0,0,0,0,0,0,0,0,0,0,0,
0,0,0,0,0,0,0,0,0,0,0,0,
0,0,0,0,0,0,0,0,0,0,0,0,
0,0,0,0,0,0,0,0,0,0,0,0};

/* to be used during opening and middlegame for white king positioning: */
static int swhite_king[144] = {
0,0,0,0,0,0,0,0,0,0,0,0,
0,0,0,0,0,0,0,0,0,0,0,0,
0,0,2,14,0,0,0,9,14,2,0,0,
0,0,-3,-5,-6,-6,-6,-6,-5,-3,0,0,
0,0,-5,-5,-8,-8,-8,-8,-5,-5,0,0,
0,0,-8,-8,-13,-13,-13,-13,-8,-8,0,0,
0,0,-13,-13,-21,-21,-21,-21,-13,-13,0,0,
0,0,-21,-21,-34,-34,-34,-34,-21,-21,0,0,
0,0,-34,-34,-55,-55,-55,-55,-34,-34,0,0,
0,0,-55,-55,-89,-89,-89,-89,-55,-55,0,0,
0,0,0,0,0,0,0,0,0,0,0,0,
0,0,0,0,0,0,0,0,0,0,0,0};

/* to be used during opening and middlegame for black king positioning: */
static int sblack_king[144] = {
0,0,0,0,0,0,0,0,0,0,0,0,
0,0,0,0,0,0,0,0,0,0,0,0,
0,0,-55,-55,-89,-89,-89,-89,-55,-55,0,0,
0,0,-34,-34,-55,-55,-55,-55,-34,-34,0,0,
0,0,-21,-21,-34,-34,-34,-34,-21,-21,0,0,
0,0,-13,-13,-21,-21,-21,-21,-13,-13,0,0,
0,0,-8,-8,-13,-13,-13,-13,-8,-8,0,0,
0,0,-5,-5,-8,-8,-8,-8,-5,-5,0,0,
0,0,-3,-5,-6,-6,-6,-6,-5,-3,0,0,
0,0,2,14,0,0,0,9,14,2,0,0,
0,0,0,0,0,0,0,0,0,0,0,0,
0,0,0,0,0,0,0,0,0,0,0,0};

/* to be used for positioning of both kings during the endgame: */
static int send_king[144] = {
0,0,0,0,0,0,0,0,0,0,0,0,
0,0,0,0,0,0,0,0,0,0,0,0,
0,0,-5,-3,-1,0,0,-1,-3,-5,0,0,
0,0,-3,10,10,10,10,10,10,-3,0,0,
0,0,-1,10,25,25,25,25,10,-1,0,0,
0,0,0,10,25,50,50,25,10,0,0,0,
0,0,0,10,25,50,50,25,10,0,0,0,
0,0,-1,10,25,25,25,25,10,-1,0,0,
0,0,-3,10,10,10,10,10,10,-3,0,0,
0,0,-5,-3,-1,0,0,-1,-3,-5,0,0,
0,0,0,0,0,0,0,0,0,0,0,0,
0,0,0,0,0,0,0,0,0,0,0,0};

/* utility array to reverse rank: */
static const int srev_rank[9] = {
0,8,7,6,5,4,3,2,1};

const int std_p_tropism[8] =
{ 9999, 15, 10, 7, 2, 0, 0, 0};

const int std_own_p_tropism[8] =
{ 9999, 30, 10, 2, 0, 0, 0, 0};

const int std_r_tropism[16] =
{ 9999, 0, 15, 5, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

const int std_n_tropism[8] =
{ 9999, 14, 9, 6, 1, 0, 0, 0};

const int std_q_tropism[8] =
{ 9999, 200, 50, 15, 3, 2, 1, 0};

const int std_b_tropism[8] =
{ 9999, 12, 7, 5, 0, 0, 0, 0};
  
void check_phase(void)
{
  int xnum_pieces = 0;
  int j, a, i;

  for (j = 1, a = 1; (a <= piece_count); j++)
    {
      i = pieces[j];

      if (!i)
	continue;
      else
	a++;

      if (board[i] != wpawn && board[i] != bpawn &&
	  board[i] != npiece && board[i] != frame)
	{
	  xnum_pieces++;
	}
    };
  if ((xnum_pieces >= 12)
      /* not both have castled */
      && (!white_castled || !black_castled)
      /* no both lost castling priveledges */
      && (board[30] == wking || board[114] == bking))
    {
      phase = Opening;
    }
  else if (xnum_pieces <= 6)
    {
      phase = Endgame;
    }
  else
    phase = Middlegame;

}

static int bishop_mobility(int square)
{
  register int l;
  register int m = 0;
  register int diridx;
  static const int dir[4] = { -13, -11, 11, 13 };

  for (diridx = 0; diridx < 4; diridx++)
  {
     for (l = square+dir[diridx]; board[l] == npiece; l+=dir[diridx])
        m++;
  }
  return m;
}

static int rook_mobility(int square)
{
  register int l;
  register int m = 0;
  register int diridx;
  static const int dir[4] = { -1, 1, 12, -12 };

  for (diridx = 0; diridx < 4; diridx++)
  {
     for (l = square+dir[diridx]; board[l] == npiece; l+=dir[diridx])
        m++;
  }
  return m;
}

int  King(int sq,int c) {
  int s = 0;

  if( file(sq) >= 6
   && piecet(sq-1) == rook
   && pieceside(sq-1) == c ) {
    s += 2;
  }

  if( file(sq) >= 6
   && piecet(sq+1) == rook
   && pieceside(sq+1) == c ) {
    s += 2;
  }

  if( c == BLACK )
    s = -s;
  return s;
}

int  Queen(int sq,int c) {
  int s = 0;
  int mob;
  int xside = c^1;

  s += 900;

  s += std_q_tropism[distance[sq][king_locs[xside]]];

  mob = rook_mobility(sq) << 1;
  mob += bishop_mobility(sq) << 1;

  if (mob <= 4) {
     if (mob == 0)
	mob -= 15;
     else if (mob == 2)
	mob -= 10;
     else
	mob -= 5;
  }

  s += mob;

  if( c == BLACK )
    s = -s;
  return s;
}

int Rook(int sq,int c) {
  static const int square_d1[2] = {D1,D8};
  int s = 0;
  int mob;
  int xside = c^1;

  s += 500;

  s += std_r_tropism[rookdistance[sq][king_locs[xside]]];

  mob = rook_mobility(sq) << 1;
  if (mob <= 2) mob -= 5;
  s += mob;

  /* horizontal blocked */
  if (board[sq-1] != npiece && board[sq+1] != npiece) {
      s -= 5;
  }

  /* Maximize center control */
  if( wmat != ENDGAME_MAT || bmat != ENDGAME_MAT ) {
    if( sq == square_d1[c] ) {
      s += 10;
       if ( piecet(square_d1[c]+1) == rook
          && pieceside(square_d1[c]+1) == c)
       {
         s += 5;
       }
      }
    if( sq == square_d1[c]+1 ) {
      s += 10;
    }
  }
  
  if( c == BLACK )
    s = -s;
  return s;
}

int  Bishop(int sq,int c) {
  int s = 0;
  int mob;
  int xside = c^1;

  s += 325;
  s += sbishop[sq];

  s += std_b_tropism[distance[sq][king_locs[xside]]];

  mob = bishop_mobility(sq) << 1;
  if (mob <= 2) mob -= 5;
  s += mob;

  if ((c == WHITE && sq == B2 && board[C3] == wpawn)
	||
      (c == BLACK && sq == B7 && board[C6] == bpawn))
      s -= 5;
  
  if( c == BLACK )
    s = -s;
  return s;
}

int  Knight(int sq,int c) {
  int s = 0;
  int xside = c^1;

  s += 310;
  s += sknight[sq];

  s += std_n_tropism[distance[sq][king_locs[xside]]];
  
  if( c == BLACK )
    s = -s;
  return s;
}

int  Pawn(int sq,int c) {
  int s = 0;
  int xside = c^1;
  
  s += 100;

  s += std_p_tropism[distance[sq][king_locs[xside]]];
  s += std_own_p_tropism[distance[sq][king_locs[c]]];

  /* pawn duo */
  if (piecet(sq+1) == pawn
      && pieceside(sq+1) == c)
      s += 5;

  /* pawn chain */
  if(piecet(sq+11) == pawn
     && pieceside(sq+11) == c)
     s += 1;
  else if (piecet(sq+13) == pawn
           && pieceside(sq+13) == c)
     s += 1;
  
  if( c == BLACK )
    s = -s;
  return s;
}

int  ErrorIt(int sq,int c) {
  printf("Illegal piece detected sq=%i c=%i\n",sq,c);
  return 0;
}

int std_eval (int alpha, int beta) {

  int i, a, pawn_file, pawns[2][11], white_back_pawn[11], black_back_pawn[11],
      srank, wking_pawn_file, bking_pawn_file, j;
  int score = 0;
  xbool isolated, backwards;
  int in_cache;
  int wp, bp, wn, bn, wb, bb, wq, bq, wr, br;
  int rbrook, fbrook, rwrook, fwrook;
  int wpotential, bpotential, tmp;
  int wksafety, bksafety;

  if ((white_to_move?Material:-Material) - maxposdiff >= beta)
   return (white_to_move?Material:-Material) - maxposdiff;
  if ((white_to_move?Material:-Material) + maxposdiff <= alpha)
   return (white_to_move?Material:-Material) + maxposdiff;

  in_cache = 0;

  checkECache(&score, &in_cache);

  if(in_cache)
    {
      if (white_to_move == 1) return score;
      return -score;
    }

  memset (pawns, 0, sizeof (pawns));
  for (i = 0; i < 11; i++) {
    white_back_pawn[i] = 7;
    black_back_pawn[i] = 2;
  }

  wmat = 0;
  bmat = 0;

  king_locs[WHITE] = wking_loc;
  king_locs[BLACK] = bking_loc;

  /* Evaluation Pass 1 : rude information round */
  for (j = 1, a = 1; (a <= piece_count); j++) {
    i = pieces[j];

    if (!i)
      continue;
    else
      a++;

    assert((i > 0) && (i < 145));

    if (piecet(i) != pawn && piecet(i) != king)
    {
        if (pieceside(i) == WHITE)
    	    wmat += material[board[i]];
        else
	    bmat += abs(material[board[i]]);
    }

    if (piecet(i) == pawn)
    {
      pawn_file = file (i)+1;
      srank = rank (i);

      if (pieceside(i) == WHITE)
      {
      	pawns[1][pawn_file]++;
      	if (srank < white_back_pawn[pawn_file]) {
		white_back_pawn[pawn_file] = srank;
      	}
      }
      else
      {
      	pawns[0][pawn_file]++;
      	if (srank > black_back_pawn[pawn_file]) {
		black_back_pawn[pawn_file] = srank;
      	}
      }
    }
  }

  /* ready, set, ... */
  wpotential = 0;
  bpotential = 0;
  wksafety = 0;
  bksafety = 0;
  rbrook = 0;
  fbrook = 0;
  rwrook = 0;
  fwrook = 0;
  wp = 0;
  bp = 0;
  wb = 0;
  bb = 0;
  wn = 0;
  bn = 0;
  wr = 0;
  br = 0;
  wq = 0;
  bq = 0;

  /* Pass 2 : using pass 1 information to allow more complex knowledge patterns */
  for (j = 1, a = 1; (a <= piece_count); j++) {
    i = pieces[j];
    if (!i)
      continue;
    else
      a++;

    score += (*(evalRoutines[piecet(i)]))(i,pieceside(i));

    pawn_file = file (i)+1;
    srank = rank (i);
    switch (board[i]) {
      case (wpawn):
	isolated = FALSE;
	backwards = FALSE;
	score += swhite_pawn[i];
	wp++;

	/* check for backwards pawns: */
	if (white_back_pawn[pawn_file+1] > srank
	    && white_back_pawn[pawn_file-1] > srank) {
	  score -= 8;
	  backwards = TRUE;
	  /* check to see if it is furthermore isolated: */
	  if (!pawns[1][pawn_file+1] && !pawns[1][pawn_file-1]) {
	    score -= 5;
	    isolated = TRUE;
	  }
	}

	/* give weak, exposed pawns a penalty */
	if (!pawns[0][pawn_file]) {
	  if (backwards) score -= 3;
	  if (isolated) score -= 5;
	}

	/* give doubled, trippled, etc.. pawns a penalty */
	if (pawns[1][pawn_file] > 1)
	  score -= 3*(pawns[1][pawn_file]-1);

	/* give bonuses for passed pawns */
	if (!pawns[0][pawn_file] && srank >= black_back_pawn[pawn_file-1] &&
	    srank >= black_back_pawn[pawn_file+1]) {
	  score += 30 + 3*swhite_pawn[i];

	  if (white_to_move)
	  {
	    /* check queening race */
	    /* tmp = queening square */
	    tmp = A8 + file(i) - 1;
	    /* king is away how much ?*/
	    if (max(abs(file(bking_loc)-file(tmp)), abs(rank(bking_loc)-rank(tmp)))
		> (abs(rank(tmp) - rank(i))))
	    {
	      wpotential += 800;
	    }
	  }
	  else
	  {
	    /* check queening race */
	    /* tmp = queening square */
	    tmp = A8 + file(i) - 1;
	    /* king is away how much ?*/
	    if ((max(abs(file(bking_loc)-file(tmp)), abs(rank(bking_loc)-rank(tmp)))-1)
		> (abs(rank(tmp) - rank(i))))
	    {
	      wpotential += 800;
	    }
	  }

	  /* outside passer ? */
	  if (file(i) == 1 || file(i) == 8)
	    score += 12 + 2*swhite_pawn[i];

	  /* give an extra bonus if a connected, passed pawn: */
	  if (!isolated)
	    {
	      score += 12;

	      /* check whether supporter is passed */
	      if (pawns[1][pawn_file+1])
		{
		  if (!pawns[0][pawn_file+1]
		      && white_back_pawn[pawn_file+1] >= black_back_pawn[pawn_file+2])
		    {
		      score += 7*rank(i);

		      /* connected on seventh ? */
		      if (rank(i) == 7 && white_back_pawn[pawn_file+1] >= 6)
			{
			  score += 50;
			}
		    }
		}
	      if (pawns[1][pawn_file-1])
		{
		   if (!pawns[0][pawn_file-1]
		      && white_back_pawn[pawn_file+1] >= black_back_pawn[pawn_file-2])
		    {
		      score += 7*rank(i);

		      /* connected on seventh ? */
		      if (rank(i) == 7 && white_back_pawn[pawn_file-1] >= 6)
			{
			  score += 50;
			}
		    }
		}
	    }
	}

	if (!pawns[1][pawn_file-1])
	  score -= 7;

	break;

      case (bpawn):
	isolated = FALSE;
	backwards = FALSE;
	score -= sblack_pawn[i];
	bp++;


	/* check for backwards pawns: */
	if (black_back_pawn[pawn_file+1] < srank
	    && black_back_pawn[pawn_file-1] < srank) {
	  score += 8;
	  backwards = TRUE;
	  /* check to see if it is furthermore isolated: */
	  if (!pawns[0][pawn_file+1] && !pawns[0][pawn_file-1]) {
	    score += 5;
	    isolated = TRUE;
	  }
	}

	/* give weak, exposed pawns a penalty  */
	if (!pawns[1][pawn_file]) {
	  if (backwards) score += 3;
	  if (isolated) score += 5;
	}

	/* give doubled, trippled, etc.. pawns a penalty */
	if (pawns[0][pawn_file] > 1)
	  score += 3*(pawns[0][pawn_file]-1);

	/* give bonuses for passed pawns  */
	if (!pawns[1][pawn_file] && srank <= white_back_pawn[pawn_file-1] &&
	    srank <= white_back_pawn[pawn_file+1]) {
	  score -= 30 + 3*sblack_pawn[i];

	  if (!white_to_move)
	  {
	    /* check queening race */
	    /* tmp = queening square */
	    tmp = A1 + file(i) - 1;
	    /* king is away how much ?*/
	    if (max(abs(file(wking_loc)-file(tmp)), abs(rank(wking_loc)-rank(tmp)))
		> (abs(rank(tmp) - (rank(i)))))
	    {
	      bpotential -= 800;
	    }
	  }
	  else
	  {
	    /* check queening race */
	    /* tmp = queening square */
	    tmp = A1 + file(i) - 1;
	    /* king is away how much ?*/
	    if ((max(abs(file(wking_loc)-file(tmp)), abs(rank(wking_loc)-rank(tmp)))-1)
		> abs((rank(tmp) - rank(i))))
	    {
	      bpotential -= 800;
	    }
	  }

	  /* outside passer ? */
	  if (file(i) == 1 || file(i) == 8)
	    score -= 12 + 2*sblack_pawn[i];

	  /* give an extra bonus if a connected, passed pawn: */
	  if (!isolated)
	    {
	      score -= 12;

	      /* check whether supporter is passed */
	      if (pawns[0][pawn_file+1])
		{
		  if (!pawns[1][pawn_file+1]
		      && black_back_pawn[pawn_file+1] <= white_back_pawn[pawn_file+2])
		    {
		      score -= 7*(9-rank(i));

		      /* on seventh and supported ? */
		      if (rank(i) == 2 && black_back_pawn[pawn_file+1] <= 3)
			{
			  score -= 50;
			}
		    }
		}
	      if (pawns[0][pawn_file-1])
		{
		   if (!pawns[1][pawn_file-1]
		      && black_back_pawn[pawn_file-1] <= white_back_pawn[pawn_file-2])
		    {
		      score -= 7*(9-rank(i));

		      /* connected on seventh ? */
		      if (rank(i) == 2 && black_back_pawn[pawn_file-1] <= 3)
			{
			  score -= 50;
			}

		    }
		}
	    }
	}

	if (!pawns[0][pawn_file-1])
	  score += 7;

	break;

      case (wrook):
	wr++;

	if (wr == 1)
	    {
		fwrook = file(i);
		rwrook = rank(i);
	    }


	/* bonus for being on the 7th: */
	if (srank == 7)
	    {
	      score += 25;
              if (wr == 2 && rwrook == 7)
	      {
		score += 10;
	      }

	    }

	/* give bonuses depending on how open the rook's file is: */
	if (!pawns[1][pawn_file]) {
	  /* half open file */
	  score += 5;

	  if (wr == 2 && file(i) == fwrook)
	      {
		score += 12;
	      }

	  if (!pawns[0][pawn_file]) {
	    /* open file */
	    score += 3;
	  }
	}

	break;

      case (brook):
	br++;
	if (br == 1)
	    {
		fbrook = file(i);
		rbrook = rank(i);
	    }

	/* bonus for being on the 7th: */
	if (srank == 2)
	    {
		score -= 25;
              if (wr == 2 && rbrook == 2)
	      {
		score -= 10;
	      }
	    }

	/* give bonuses depending on how open the rook's file is: */
	if (!pawns[0][pawn_file]) {
	  /* half open file */
	  score -= 5;

	  if (br == 2 && file(i) == fbrook)
	      {
		score -= 12;
	      }

	  if (!pawns[1][pawn_file]) {
	    /* open file */
	    score -= 3;
	  }
	}

	break;

      case (wbishop):
	wb++;
	break;

      case (bbishop):
	bb++;
	break;

      case (wknight):
	wn++;
	break;

      case (bknight):
	bn++;
	break;

      case (wqueen):
	wq++;
	break;

      case (bqueen):
	bq++;
	break;

      case (wking):
	if (wmat > ENDGAME_MAT)
	{
		score += swhite_king[i];
	
		/* encourage castling, and give a penalty for moving the king without
		   castling */
		if (white_castled == wcq)
		  score += 15;
		else if (white_castled == wck)
		  score += 25;
		else if (moved[30]) {
		  score -= 10;
		  /* make the penalty bigger if the king is open, leaving the other
		     side a chance to gain tempo with files along the file, as well
		     as building an attack: */
		  if (!pawns[1][pawn_file])
		    score -= 15;
		}
	
		/* if the king is behind some pawn cover, give penalties for the pawn
		   cover being far from the king, else give a penalty for the king
		   not having any pawn cover: */
		if (file(wking_loc) != 4 && file(wking_loc) != 5)
		{
		if (srank < white_back_pawn[pawn_file] && pawns[1][pawn_file])
		  score -= 9*(white_back_pawn[pawn_file]-srank-1);
		else
		  score -= 22;
		if (srank < white_back_pawn[pawn_file+1] && pawns[1][pawn_file+1])
		  score -= 8*(white_back_pawn[pawn_file+1]-srank-1);
		else
		  score -= 16;
		if (srank < white_back_pawn[pawn_file-1] && pawns[1][pawn_file-1])
		  score -= 8*(white_back_pawn[pawn_file-1]-srank-1);
		else
		  score -= 16;
		}
		else
		{
			/* being in center isnt great either, so correct */
			score -= 10;
		}
	}
	else
	{
		score += send_king[i];
	}
	break;

      case (bking):
	if (bmat > ENDGAME_MAT)
	{
		score -= sblack_king[i];
	
		/* encourage castling, and give a penalty for moving the king without
		   castling */
		if (black_castled == bcq)
		  score -= 15;
		else if (black_castled == bck)
		  score -= 25;
		else if (moved[114]) {
		  score += 10;
		  /* make the penalty bigger if the king is open, leaving the other
		     side a chance to gain tempo with files along the file, as well
		     as building an attack: */
		  if (!pawns[0][pawn_file])
		    score += 15;
		}
	
		/* if the king is behind some pawn cover, give penalties for the pawn
		   cover being far from the king, else give a penalty for the king
		   not having any pawn cover: */
		if (file(bking_loc) != 4 && file(bking_loc) != 5)
		{
		if (srank > black_back_pawn[pawn_file] && pawns[0][pawn_file])
		  score += 9*(srev_rank[srank-black_back_pawn[pawn_file]-1]);
		else
		  score += 22;
		if (srank > black_back_pawn[pawn_file+1] && pawns[0][pawn_file+1])
		  score += 8*(srev_rank[srank-black_back_pawn[pawn_file+1]-1]);
		else
		  score += 16;
		if (srank > black_back_pawn[pawn_file-1] && pawns[0][pawn_file-1])
		  score += 8*(srev_rank[srank-black_back_pawn[pawn_file-1]-1]);
		else
		  score += 16;
		}
		else
		{
		  score += 10;
		}
	}
	else
	{
		score -= send_king[i];
	}
	break;
    }
  }


  if (wmat > OPENING_MAT || bmat > OPENING_MAT)
  {
    /* give penalties for blocking the e/d pawns: */
    if (!moved[41] && board[53] != npiece)
      score -= 5;
    if (!moved[42] && board[54] != npiece)
      score -= 5;
    if (!moved[101] && board[89] != npiece)
      score += 5;
    if (!moved[102] && board[90] != npiece)
      score += 5;
  }
	
  if (wmat > ENDGAME_MAT || bmat > ENDGAME_MAT)
  {
    /* to be used for pawn storm code: */
    wking_pawn_file = file (wking_loc)+1;
    bking_pawn_file = file (bking_loc)+1;

    /* if the kings are on opposite wings, or far apart, check for pawn
       storms, and open lines for heavy pieces: */
    if (abs(wking_pawn_file-bking_pawn_file) > 2) {
      /* black pawn storms: */
      wksafety += 3*(srev_rank[black_back_pawn[wking_pawn_file]]-2);
      wksafety += 3*(srev_rank[black_back_pawn[wking_pawn_file+1]]-2);
      wksafety += 3*(srev_rank[black_back_pawn[wking_pawn_file-1]]-2);

      /* white pawn storms: */      
      bksafety += 3*(white_back_pawn[bking_pawn_file]-2);
      bksafety += 3*(white_back_pawn[bking_pawn_file+1]-2);
      bksafety += 3*(white_back_pawn[bking_pawn_file-1]-2);

      /* black opening up lines: */
      if (!pawns[0][wking_pawn_file])
        wksafety += 8;
      if (!pawns[0][wking_pawn_file+1])
        wksafety += 6;
      if (!pawns[0][wking_pawn_file-1])
        wksafety += 6;

      /* white opening up lines: */
      if (!pawns[1][bking_pawn_file])
        bksafety += 8;
      if (!pawns[1][bking_pawn_file+1])
        bksafety += 6;
      if (!pawns[1][bking_pawn_file-1])
        bksafety += 6;
    }
  }

  if (bmat > ENDGAME_MAT && bq)
  {
	  score -= wksafety;
  }
  if (wmat > ENDGAME_MAT && wq)
  {
	  score += bksafety;
  }

  /* some static knowledge about drawn endgames */

 /* pawn ending detection */
 if (!wr && !wq && !wb && !wn)
 {
   score += bpotential;
 }

 if (!br && !bq && !bb && !bn)
 {
   score += wpotential;
 }

 /* no more pawns */
 if (!wp && !bp)
    {
      /* nor heavies */
      if (!wr && !br && !wq && !bq)
	{
	  if (!bb && !wb)
	    {
	      /* only knights */
	      /* it pretty safe to say this is a draw */
	      if (wn < 3 && bn < 3)
		{
		  score = 0;
		}
	    }
	  else if (!wn && !bn)
	    {
	      /* only bishops */
	      /* not a draw if one side two other side zero
		 else its always a draw                     */
	      if (abs(wb - bb) < 2)
		{
		  score = 0;
		}
	    }
	  else if ((wn < 3 && !wb) || (wb == 1 && !wn))
	    {
	      /* we cant win, but can black? */
	      if ((bn < 3 && !bb) || (bb == 1 && !bn))
		{
		  /* guess not */
		  score = 0;
		}
	    }
	}
      else if (!wq && !bq)
	{
	  if (wr == 1 && br == 1)
	    {
	      /* rooks equal */
	      if ((wn + wb) < 2 && (bn + bb) < 2)
		{
		  /* one minor difference max */
		  /* a draw too usually */
		  score = 0;
		}
	    }
	  else if (wr == 1 && !br)
	    {
	      /* one rook */
	      /* draw if no minors to support AND
		 minors to defend  */
	      if ((wn + wb == 0) && (((bn + bb) == 1) || ((bn + bb) == 2)))
		{
		  score = 0;
		}
	    }
	  else if (br == 1 && !wr)
	    {
	      /* one rook */
	      /* draw if no minors to support AND
		 minors to defend  */
	      if ((bn + bb == 0) && (((wn + wb) == 1) || ((wn + wb) == 2)))
		{
		  score = 0;
		}
	    }
	}
    }
  else
    {
      /* minors are not equal */
      if ((wn + wb) != (bn + bb))
	{
	  /* majors are equal */
	  if ((wq + wr) == (bq + br))
	    {
	      if ((wn + wb) > (bn + bb))
		{
		  /* white is a piece up */
		  score += 120;
		}
	      else
		{
		  /* black is a piece up */
		  score -= 120;
		}
	    }
	  else if (abs((wr + wq) - (br + bq)) == 1)
	    {
	      /* one major difference */

	      if ((wb + wn) > (bb + bn + 1))
		{
		  /* two minors for one major */
		  score += 120;
		}
	      else if ((bb + bn) > (wb + wn + 1))
		{
		  score -= 120;
		}
	    }
	  else if (abs((wr + wq) - (br + bq)) == 2)
	    {
	      /* two majors difference */

	      if ((wb + wn) > (bb + bn + 2))
		{
		  /* three minors for two majors */
		  score += 120;
		}
	      else if ((bb + bn) > (wb + wn + 2))
		{
		  score -= 120;
		}

	    }
	}
      else if ((wq + wr) == (bq + br))
	{
	  if (wq && !bq)
	    {
	      score += 120;
	    }
	  else if (!wq && bq)
	    {
	      score -= 120;
	    }
	}
    }

   storeECache(score);

   if (abs(Material - score) > maxposdiff)
      maxposdiff = min(1000, abs(Material - score));

  /* adjust for color: */
  if (white_to_move == 1) {
    return score;
  }
  else {
    return -score;
  }
}

