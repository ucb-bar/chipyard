/*
    Sjeng - a chess variants playing program
    Copyright (C) 2000-2003 Gian-Carlo Pascutto

    File: moves.c                                        
    Purpose: functions used to generate & make moves

*/

#include "sjeng.h"
#include "extvars.h"
#include "protos.h"

unsigned int total_moves;
unsigned int total_movegens;

int numb_moves;
static move_s *genfor;

xbool fcaptures;
int gfrom;

int kingcap; /* break if we capture the king */

xbool check_legal (move_s moves[], int m, int incheck) {

  /* determines if a move made was legal.  Checks to see if the player who
     just moved castled through check, or is in check.  If the move made
     was illegal, returns FALSE, otherwise, returns TRUE. */

  int castled = moves[m].castled;
  int from = moves[m].from;
  int target = moves[m].target;
  int l;
 
  if (Variant == Suicide) return TRUE;

  /* check for castling moves: */
  if (castled) 
  {
    /* white kingside castling: */
    if (castled == wck) {
      if (is_attacked (30, 0)) return FALSE;
      if (is_attacked (31, 0)) return FALSE;
      if (is_attacked (32, 0)) return FALSE;
      return TRUE;
    }
    /* white queenside castling: */
    if (castled == wcq) {
      if (is_attacked (30, 0)) return FALSE;
      if (is_attacked (29, 0)) return FALSE;
      if (is_attacked (28, 0)) return FALSE;
      return TRUE;
    }
    /* black kingside castling: */
    if (castled == bck) {
      if (is_attacked (114, 1)) return FALSE;
      if (is_attacked (115, 1)) return FALSE;
      if (is_attacked (116, 1)) return FALSE;
      return TRUE;
    }
    /* black queenside castling: */
    if (castled == bcq) {
      if (is_attacked (114, 1)) return FALSE;
      if (is_attacked (113, 1)) return FALSE;
      if (is_attacked (112, 1)) return FALSE;
      return TRUE;
    }
  }

  /* otherwise, just check on the kings: */
  /* black king: */

  /* the code in here checks whether a move could
   * have put the king in check, if he was not in
   * check before, if not, an early exit is taken */
  
  else if (white_to_move&1) 
    {
      if (!incheck)
	{
	  if (moves[m].from == 0) return TRUE;
	  
	  switch (moves[m].promoted ? bpawn : board[target])
	    {
	    case bpawn:
	      /* pawn moves, it can discover a rank or diagonal check 
	       * a capture can also discover a file check */
	      if (moves[m].captured != npiece)
		{
		  if (file(from) != file(bking_loc) 
		      && rank(from) != rank(bking_loc)
		      && diagl(from) != diagl(bking_loc)
		      && diagr(from) != diagr(bking_loc))
		    return TRUE;
		}
	      else
		{
		  if (rank(from) != rank(bking_loc)
		      && diagl(from) != diagl(bking_loc)
		      && diagr(from) != diagr(bking_loc))
		    return TRUE;
		}
	      break;
	    case bknight:
	      /* discovers all */
	      if (file(from) != file(bking_loc) 
		  && rank(from) != rank(bking_loc)
		  && diagl(from) != diagl(bking_loc)
		  && diagr(from) != diagr(bking_loc))
		return TRUE;
	      break;
	    case bbishop:
	      /* always discovers file and rank
	       * always discovers one diagonal */
	      if (file(from) != file(bking_loc) 
		  && rank(from) != rank(bking_loc))
		{
		  if (diagl(from) == diagl(target))
		    {
		      /* stays on diag, can only uncover check on
		       * other diag */
		      if (diagr(from) != diagr(bking_loc))
			return TRUE;
		    }
		  else
		    {
		      if (diagl(from) != diagl(bking_loc))
			return TRUE;
		    }
		}
	      break;
	    case brook:
	      /* discovers diagonal always */
	      /* one file or rank discovered */
	      if (diagr(from) != diagr(bking_loc)
		  && diagl(from) != diagl(bking_loc))
		{
		  /* rank move ? */
		  if(rank(from) == rank(target))
		    {
		      if (file(from) != file(bking_loc))
			return TRUE;
		    }
		  else
		    {
		      /* file move */
		      if (rank(from) != rank(bking_loc))
			return TRUE;
		    }
		}
	      break;
	    case bqueen:
	    /* find out what move it was: ldiag/rdiag/file/rank*/
	      if (file(from) == file(target))
		{
		  if (diagr(from) != diagr(bking_loc)
		      && diagl(from) != diagl(bking_loc)
		    && rank(from) != rank(bking_loc))
		    return TRUE;	
		}
	       else if (rank(from) == rank(target))
	        {
		  if (diagr(from) != diagr(bking_loc)
		      && file(from) != file(bking_loc)
		      && diagl(from) != diagl(bking_loc))
		    return TRUE;	
	        }
	      else if (diagl(from) == diagl(target))
		{
		  if (diagr(from) != diagr(bking_loc)
		      && file(from) != file(bking_loc)
		      && rank(from) != rank(bking_loc))
		    return TRUE;	
		}
	      else if (diagr(from) == diagr(target))
		{
		  if (diagl(from) != diagl(bking_loc)
		      && file(from) != file(bking_loc)
		      && rank(from) != rank(bking_loc))
		    return TRUE;	
		}
	    break;
	    default:
	      break;
	    }

	  /* we got so far, we know there can only be some
	   * kind of possible discovering */
	  /* find out what */
	  /* we do not need to check for pawn, king or knightattacks,
	   * as they cannot be discovered*/
	  
	  if (board[target] != bking)
	  {
	    if (file(from) == file(bking_loc))
	    {
	      if (bking_loc > from)
	      {
	      for (l = bking_loc-12; board[l] == npiece; l-=12);
	      if (board[l] == wrook || board[l] == wqueen) return FALSE;
	      }
	      else
	      {
	      for (l = bking_loc+12; board[l] == npiece; l+=12);
	      if (board[l] == wrook || board[l] == wqueen) return FALSE;
	      }
	    }
	    else if (rank(from) == rank(bking_loc))
	    {
	      if (bking_loc > from)
	      {
	      for (l = bking_loc-1; board[l] == npiece; l-=1);
	      if (board[l] == wrook || board[l] == wqueen) return FALSE;
	      }
	      else
	      {
	      for (l = bking_loc+1; board[l] == npiece; l+=1);
	      if (board[l] == wrook || board[l] == wqueen) return FALSE;
	      }
	    }
	    else if (diagl(from) == diagl(bking_loc))
	    {
	      if (bking_loc > from)
	      {
	      for (l = bking_loc-13; board[l] == npiece; l-=13);
	      if (board[l] == wbishop || board[l] == wqueen) return FALSE;
	      }
	      else
	      {
	      for (l = bking_loc+13; board[l] == npiece; l+=13);
	      if (board[l] == wbishop || board[l] == wqueen) return FALSE;
	      }
	    }
	    else if (diagr(from) == diagr(bking_loc))
	    {
	      if (bking_loc > from)
	      {
	      for (l = bking_loc-11; board[l] == npiece; l-=11);
	      if (board[l] == wbishop || board[l] == wqueen) return FALSE;
	      }
	      else
	      {
	      for (l = bking_loc+11; board[l] == npiece; l+=11);
	      if (board[l] == wbishop || board[l] == wqueen) return FALSE;
	      }
	    }
	    return TRUE;
	  }
	}
      
      if (is_attacked (bking_loc, 1)) return FALSE;
      else return TRUE;
    }
  
  /* white king: */
  else 
    {
      
      if (!incheck)
	{
	  if (moves[m].from == 0) return TRUE;
	  
	  switch (moves[m].promoted ? wpawn : board[target])
	    {
	    case wpawn:
	      /* pawn moves, it can discover a rank or diagonal check 
	       * a capture can also discover a file check */
	      if (moves[m].captured != npiece)
		{
		  if (file(from) != file(wking_loc) 
		      && rank(from) != rank(wking_loc)
		      && diagl(from) != diagl(wking_loc)
		      && diagr(from) != diagr(wking_loc))
		    return TRUE;
		}
	      else
		{
		  if (rank(from) != rank(wking_loc)
		      && diagl(from) != diagl(wking_loc)
		      && diagr(from) != diagr(wking_loc))
		    return TRUE;
		}
	      break;
	    case wknight:
	      /* discovers all */
	      if (file(from) != file(wking_loc) 
		  && rank(from) != rank(wking_loc)
		  && diagl(from) != diagl(wking_loc)
		  && diagr(from) != diagr(wking_loc))
		return TRUE;
	      break;
	    case wbishop:
	      /* always discovers file and rank
	       * always discovers one diagonal */
	      if (file(from) != file(wking_loc) 
		  && rank(from) != rank(wking_loc))
		{
		  if (diagl(from) == diagl(target))
		    {
		      /* stays on diag, can only uncover check on
		       * other diag */
		      if (diagr(from) != diagr(wking_loc))
			return TRUE;
		    }
		  else
		    {
		      if (diagl(from) != diagl(wking_loc))
			return TRUE;
		    }
		}
	      break;
	    case wrook:
	    /* discovers diagonal always */
	      /* one file or rank discovered */
	      if (diagr(from) != diagr(wking_loc)
		  && diagl(from) != diagl(wking_loc))
		{
		  /* rank move ? */
		  if(rank(from) == rank(target))
		    {
		      if (file(from) != file(wking_loc))
			return TRUE;
		    }
		  else
		    {
		      /* file move */
		      if (rank(from) != rank(wking_loc))
			return TRUE;
		    }
		}
	      break;
	    case wqueen:
	      /* find out what move it was: ldiag/rdiag/file/rank*/
	      if (file(from) == file(moves[m].target))
		{
		  if (diagr(from) != diagr(wking_loc)
		      && diagl(from) != diagl(wking_loc)
		      && rank(from) != rank(wking_loc))
		    return TRUE;	
		}
	      else if (rank(from) == rank(target))
		{
		  if (diagr(from) != diagr(wking_loc)
		      && file(from) != file(wking_loc)
		      && diagl(from) != diagl(wking_loc))
		    return TRUE;	
		}
	      else if (diagl(from) == diagl(target))
		{
		  if (diagr(from) != diagr(wking_loc)
		      && file(from) != file(wking_loc)
		      && rank(from) != rank(wking_loc))
		    return TRUE;	
		}
	      else if (diagr(from) == diagr(target))
		{
		  if (diagl(from) != diagl(wking_loc)
		      && file(from) != file(wking_loc)
		      && rank(from) != rank(wking_loc))
		    return TRUE;	
		}
	      break;
	    default:
	      break;
	    }
	  
	  if (board[target] != wking)
	  {
	    if (file(from) == file(wking_loc))
	    {
	      if (wking_loc > from)
	      {
	      for (l = wking_loc-12; board[l] == npiece; l-=12);
	      if (board[l] == brook || board[l] == bqueen) return FALSE;
	      }
	      else
	      {
	      for (l = wking_loc+12; board[l] == npiece; l+=12);
	      if (board[l] == brook || board[l] == bqueen) return FALSE;
	      }
	    }
	    else if (rank(from) == rank(wking_loc))
	    {
	      if (wking_loc > from)
	      {
	      for (l = wking_loc-1; board[l] == npiece; l-=1);
	      if (board[l] == brook || board[l] == bqueen) return FALSE;
	      }
	      else
	      {
	      for (l = wking_loc+1; board[l] == npiece; l+=1);
	      if (board[l] == brook || board[l] == bqueen) return FALSE;
	      }
	    }
	    else if (diagl(from) == diagl(wking_loc))
	    {
	      if (wking_loc > from)
	      {
	      for (l = wking_loc-13; board[l] == npiece; l-=13);
	      if (board[l] == bbishop || board[l] == bqueen) return FALSE;
	      }
	      else
	      {
	      for (l = wking_loc+13; board[l] == npiece; l+=13);
	      if (board[l] == bbishop || board[l] == bqueen) return FALSE;
	      }
	    }
	    else if (diagr(from) == diagr(wking_loc))
	    {
	      if (wking_loc > from)
	      {
	      for (l = wking_loc-11; board[l] == npiece; l-=11);
	      if (board[l] == bbishop || board[l] == bqueen) return FALSE;
	      }
	      else
	      {
	      for (l = wking_loc+11; board[l] == npiece; l+=11);
	      if (board[l] == bbishop || board[l] == bqueen) return FALSE;
	      }
	    }
	    return TRUE;
	  }
	}

      if (is_attacked (wking_loc, 0)) return FALSE;
      else return TRUE;
  }
  
  /* should never get here .. but just so it will compile :P */
  return FALSE;

}


#define push_slide(t) if (board[(t)] != frame) push_slidE((t))
#define push_knight(t) if (board[(t)] != frame) push_knighT((t))

void gen (move_s moves[]) {

  /* generate pseudo-legal moves, and place them in the moves array */

  int from, a, j, i;

  kingcap = FALSE;
  
  numb_moves = 0;
  genfor = &moves[0];

  if (Variant == Suicide)
  {
    captures = FALSE;
    fcaptures = FALSE;
  };
	
restart:

  /* generate white moves, if it is white to move: */
  if (white_to_move) {
    for (a = 1, j = 1;
	 (a <= piece_count) 
	   && (((Variant != Suicide) && !kingcap)
	   || ((Variant == Suicide) && (fcaptures == captures)));
	 j++) {

      i = pieces[j];
      
      if (!i)
	continue;
      else
	a++;

      from = i;
      gfrom = i;
      
       switch (board[from]) {
       case (wpawn):
	 /* pawn moves up one square: */
	 if (board[from+12] == npiece) {
	   /* only promotions when captures == TRUE */
	   if (rank (from) == 7 && ((Variant != Suicide) && (Variant != Losers))) {
	     push_pawn (from+12, FALSE);
	   }
	   else if (!captures) {
	     push_pawn (from+12, FALSE);

	     /* pawn moving up two squares on its first move: */
	     if (rank(from) == 2 && board[from+24] == npiece)
	       push_pawn_simple (from+24);
	   }
	 }
	 /* pawn capturing diagonally: */
	 if ((board[from+13]&1) == 0 && board[from+13] != frame)
	   push_pawn (from+13, FALSE);
	 /* pawn captruing diagonally: */
	 if ((board[from+11]&1) == 0 && board[from+11] != frame)
	   push_pawn (from+11, FALSE);
	 /* ep move: */
	 if (ep_square == from+13)
	   push_pawn (from+13, TRUE);
	 /* ep move: */
	 else if (ep_square == from+11)
	   push_pawn (from+11, TRUE);
	 break;
       case (wknight):
	 /* use the knight offsets: */
	 push_knight (from-25);
	 push_knight (from-23);
	 push_knight (from-14);
	 push_knight (from-10);
	 push_knight (from+10);
	 push_knight (from+14);
	 push_knight (from+23);
	 push_knight (from+25);
	 break;
       case (wbishop):
	 /* use the bishop offsets: */
	 push_slide (from-13);
	 push_slide (from-11);
	 push_slide (from+11);
	 push_slide (from+13);
	 break;
       case (wrook):
	 /* use the rook offsets: */
	 push_slide (from-12);
	 push_slide (from-1);
	 push_slide (from+1);
	 push_slide (from+12);
	 break;
       case (wqueen):
	 /* use the queen offsets: */
	 push_slide (from-13);
	 push_slide (from-12);
	 push_slide (from-11);
	 push_slide (from-1);
	 push_slide (from+1);
	 push_slide (from+11);
	 push_slide (from+12);
	 push_slide (from+13);
	 break;
       case (wking):
	 /* use the king offsets for 'normal' moves: */
	  push_king (from-13);
	  push_king (from-12);
	  push_king (from-11);
	  push_king (from-1);
	  push_king (from+1);
	  push_king (from+11);
	  push_king (from+12);
	  push_king (from+13);
	  /* castling moves: */
	  if (from == 30 && !moved[30] && !captures && (Variant != Suicide || Giveaway == TRUE)) {
	    /* kingside: */
	    if (!moved[33] && board[33] == wrook)
	      if (board[31] == npiece && board[32] == npiece)
		push_king_castle (from+2, wck);
	    /* queenside: */
	    if (!moved[26] && board[26] == wrook)
	      if (board[27] == npiece && board[28] == npiece
		  && board[29] == npiece)
		push_king_castle (from-2, wcq);
	  }
	  break;
        default:
	  break;
      }
    }
  }

  /* generate black moves, if it is black to move: */
  else {
    for (a = 1, j = 1;
	 (a <= piece_count) &&
	  (((Variant != Suicide) && !kingcap)
	  || ((Variant == Suicide) && (fcaptures == captures)))
	  ; j++) {
      i = pieces[j];
      
      if (!i)
	continue;
      else
	a++;

      from = i; 
      gfrom = i;

      switch (board[from]) {
      case (bpawn):
	/* pawn moves up one square: */
	if (board[from-12] == npiece) {
	  /* only promotions when captures == TRUE */
	  if (rank (from) == 2 && ((Variant != Suicide) && (Variant != Losers))) {
	    push_pawn (from-12, FALSE);
	  }
	  else if (!captures) {
	    push_pawn (from-12, FALSE);
	  
	  /* pawn moving up two squares on its first move: */
	  if (rank(from) == 7 && board[from-24] == npiece)
	    push_pawn_simple (from-24);
	  }
	};
	/* pawn capturing diagonally: */
	if ((board[from-13]&1) == 1 && board[from-13] != npiece)
	  push_pawn (from-13, FALSE);
	/* pawn capturing diagonally: */
	if ((board[from-11]&1) == 1 && board[from-11] != npiece)
	  push_pawn (from-11, FALSE);
	/* ep move: */
	if (ep_square == from-13)
	  push_pawn (from-13, TRUE);
	/* ep move: */
	else if (ep_square == from-11)
	  push_pawn (from-11, TRUE);
	  break;
      case (bknight):
	/* use the knight offsets: */
	push_knight (from-25);
	push_knight (from-23);
	push_knight (from-14);
	push_knight (from-10);
	push_knight (from+10);
	push_knight (from+14);
	push_knight (from+23);
	push_knight (from+25);
	  break;
      case (bbishop):
	/* use the bishop offsets: */
	push_slide (from-13);
	push_slide (from-11);
	push_slide (from+11);
	push_slide (from+13);
	break;
      case (brook):
	/* use the rook offsets: */
	push_slide (from-12);
	push_slide (from-1);
	push_slide (from+1);
	push_slide (from+12);
	break;
      case (bqueen):
	/* use the queen offsets: */
	push_slide (from-13);
	push_slide (from-12);
	push_slide (from-11);
	push_slide (from-1);
	push_slide (from+1);
	push_slide (from+11);
	push_slide (from+12);
	push_slide (from+13);
	break;
      case (bking):
	  /* use the king offsets for 'normal' moves: */
	push_king (from-13);
	push_king (from-12);
	push_king (from-11);
	push_king (from-1);
	push_king (from+1);
	push_king (from+11);
	push_king (from+12);
	push_king (from+13);
	/* castling moves: */
	if (from == 114 && !moved[114] && !captures && (Variant != Suicide || Giveaway == TRUE)) {
	  /* kingside: */
	  if (!moved[117] && board[117] == brook)
	    if (board[115] == npiece && board[116] == npiece)
	      push_king_castle (from+2, bck);
	  /* queenside: */
	  if (!moved[110] && board[110] == brook)
	    if (board[111] == npiece && board[112] == npiece
		&& board[113] == npiece)
	      push_king_castle (from-2, bcq);
	}
	break;
      default:
	break;
      }
    }
  }
  if (((Variant == Crazyhouse) || (Variant == Bughouse)) && !captures && !kingcap)
    {
      if (white_to_move && 
	  (holding[WHITE][wpawn] || holding[WHITE][wknight]
	   || holding[WHITE][wbishop] || holding[WHITE][wqueen]
	   || holding[WHITE][wrook]))
	{
	  for (from = 26; from < 118; from++)
	    {
              gfrom = from;
	      
	      switch (board[from])
		{
		case (frame):
		  from += 3;
		  continue;
		case (npiece):
		  if(holding[WHITE][wpawn])
		    {  
		      if ((rank(from) != 8) && (rank(from) != 1))	
			{
			  try_drop(wpawn);
			}
		    }   
		  if(holding[WHITE][wknight])
		    {
		      try_drop(wknight);
		    }
		  if(holding[WHITE][wbishop])
		    {
		      try_drop(wbishop);
		    }
		  if(holding[WHITE][wrook])
		    {
		      try_drop(wrook);
		    }	
		  if(holding[WHITE][wqueen])
		    {
		      try_drop(wqueen);
		    }
		};
	    }
	}      
      else if (!white_to_move && 
	       (holding[BLACK][bpawn] || holding[BLACK][bknight]
		|| holding[BLACK][bbishop] || holding[BLACK][bqueen]
		|| holding[BLACK][brook]))
	{
	  for (from = 26; from < 118; from++)
	    {
	      gfrom = from;
	      
	      switch (board[from])
		{
		case (frame):
		  from += 3;
		  continue;
		case (npiece):
		  if(holding[BLACK][bpawn])
		    {  
		      if ((rank(from) != 8) && (rank(from) != 1))	
			{
			  try_drop(bpawn);
			}
		    }   
		  if(holding[BLACK][bknight])
		    {
		      try_drop(bknight);
		    }
		  if(holding[BLACK][bbishop])
		    {
		      try_drop(bbishop);
		    }
		  if(holding[BLACK][brook])
		    {
		      try_drop(brook);
		    }	
		  if(holding[BLACK][bqueen])
		    {
		      try_drop(bqueen);
		    }
		};
	    };
	}
    }

  if ((Variant == Suicide) && fcaptures == TRUE && captures == FALSE)
    {
      captures = TRUE;
      numb_moves = 0;
      goto restart;
    }

  if (Variant == Suicide) kingcap = FALSE;
  

}


xbool in_check (void) {
  
  /* return true if the side to move is in check: */

  if (Variant == Suicide) return FALSE;
  
  if (white_to_move == 1) {
    if (is_attacked (wking_loc, 0)) {
      return TRUE;
    }
  }
  else {
    if (is_attacked (bking_loc, 1)) {
      return TRUE;
    }
  }

  return FALSE;

}

xbool f_in_check(move_s moves[], int m)
{
  int target = moves[m].target;
  int from = moves[m].from;
  int l;
  static const int knight_o[8] = {10, -10, 14, -14, 23, -23, 25, -25};
  
  if (Variant == Suicide) return FALSE;

  if (white_to_move == 1)
  {
    /* is white king attacked */
    /* we are certain the king is not in check already,
     * as we would capture him in our ply */
    /* thus, we need to check if our move could possibly
     * put the king in check */
    /* this can either be a direct check, or a discover */
    
	  switch (board[target])
    {
      case bpawn:
	if (board[target-11] == wking || board[target-13] == wking) return TRUE;
	break;
      case bbishop:
	if (diagl(target) == diagl(wking_loc))
	{
	  /* possible left diag check */
	  if (wking_loc < target)
	  {
	  	for (l = wking_loc+13; board[l] == npiece; l +=13);
	  	if (l == target) return TRUE;
	  }
	  else
	  {
	  	for (l = wking_loc-13; board[l] == npiece; l -=13);
	  	if (l == target) return TRUE;
	  }
	}
	else if (diagr(target) == diagr(wking_loc))
	{
	  if (wking_loc < target)
	  {
	  	for (l = wking_loc+11; board[l] == npiece; l +=11);
	  	if (l == target) return TRUE;
	  }
	  else
	  {
		for (l = wking_loc-11; board[l] == npiece; l -=11);
	  	if (l == target) return TRUE;
	  }
	}
	break;
      case brook:
	if (file(target) == file(wking_loc))
	{
	  if (wking_loc < target)
	  {
	  	for (l = wking_loc+12; board[l] == npiece; l +=12);
	  	if (l == target) return TRUE;
	  }
	  else
	  {
		for (l = wking_loc-12; board[l] == npiece; l -=12);
	  	if (l == target) return TRUE;
	  }
	}
	else if (rank(target) == rank(wking_loc))
	{
	  if (wking_loc < target)
	  {
	  	for (l = wking_loc+1; board[l] == npiece; l++);
	  	if (l == target) return TRUE;
	  }
	  else
	  {
	  	for (l = wking_loc-1; board[l] == npiece; l--);
	  	if (l == target) return TRUE;
	  }
	}
	break;
      case bknight:
	 for (l = 0; l < 8; l++) 
	   if ((wking_loc + knight_o[l]) == target) return TRUE;
	break;
      case bqueen:
	if (file(target) == file(wking_loc))
	{
	  if (wking_loc < target)
	  {
	  	for (l = wking_loc+12; board[l] == npiece; l +=12);
	  	if (l == target) return TRUE;
	  }
	  else
	  {
	  	for (l = wking_loc-12; board[l] == npiece; l -=12);
	  	if (l == target) return TRUE;
	  }
	}
	else if (rank(target) == rank(wking_loc))
	{
	  if (wking_loc < target)
	  {
	  	for (l = wking_loc+1; board[l] == npiece; l +=1);
	  	if (l == target) return TRUE;
	  }
	  else
	  {
	  	for (l = wking_loc-1; board[l] == npiece; l -=1);
	  	if (l == target) return TRUE;
	  }
	}
	else if (diagl(target) == diagl(wking_loc))
	{
	  if (wking_loc < target)
	  {
	  	for (l = wking_loc+13; board[l] == npiece; l +=13);
	  	if (l == target) return TRUE;
	  }
	  else
	  {
	  	for (l = wking_loc-13; board[l] == npiece; l -=13);
	  	if (l == target) return TRUE;
	  }
	}
	else if (diagr(target) == diagr(wking_loc))
	{
	  if (wking_loc < target)
	  {
	  	for (l = wking_loc+11; board[l] == npiece; l +=11);
	  	if (l == target) return TRUE;
	  }
	  else
	  {  
	  	for (l = wking_loc-11; board[l] == npiece; l -=11);
	  	if (l == target) return TRUE;
	  }
	}
	break;
      case bking:
	/* can only discover checks */
	/* castling is tricky */
	if (moves[m].castled) 
	  {
	    if (is_attacked (wking_loc, 0)) 
	      return TRUE;
	    else
	      return FALSE;
	  }
	break;
    }

    /* drop move can never discover check */
    if (from == 0) return FALSE;

    /* this checks for discovered checks */
    if (rank(from) == rank(wking_loc))
    {
      if (wking_loc > from)
      {
    	for (l = wking_loc-1; board[l] == npiece; l--);    
	if (board[l] == brook || board[l] == bqueen) return TRUE;
      }
      else
      {
	for (l = wking_loc+1; board[l] == npiece; l++);
	if (board[l] == brook || board[l] == bqueen) return TRUE;
      }
    }
    else if (file(from) == file(wking_loc))
    {
      if (wking_loc > from)
      {
        for (l = wking_loc-12; board[l] == npiece; l-=12);    
	if (board[l] == brook || board[l] == bqueen) return TRUE;
      }
      else
      {
	for (l = wking_loc+12; board[l] == npiece; l+=12);
	if (board[l] == brook || board[l] == bqueen) return TRUE;
	}
    }
    else if (diagl(from) == diagl(wking_loc))
    {
      if (wking_loc > from)
      {
       for (l = wking_loc-13; board[l] == npiece; l-=13);    
       if (board[l] == bbishop || board[l] == bqueen) return TRUE;
      }
      else
      {
       for (l = wking_loc+13; board[l] == npiece; l+=13);
       if (board[l] == bbishop || board[l] == bqueen) return TRUE;
      }
    }
    else if (diagr(from) == diagr(wking_loc))
    {
      if (wking_loc > from)
      {
       for (l = wking_loc-11; board[l] == npiece; l-=11);    
       if (board[l] == bbishop || board[l] == bqueen) return TRUE;
      }
      else
      {
       for (l = wking_loc+11; board[l] == npiece; l+=11);
       if (board[l] == bbishop || board[l] == bqueen) return TRUE;
      }
     }    
    
    return FALSE;

    /*if (is_attacked (wking_loc, 0)) 
     return TRUE;*/
  }
  else
  {
    /* is black king attacked */
    switch (board[target])
    {
      case wpawn:
	if (board[target+11] == bking || board[target+13] == bking) return TRUE;
	break;
      case wbishop:
	if (diagl(target) == diagl(bking_loc))
	{
	  /* possible left diag check */
	  if (bking_loc < target)
	  {
	  for (l = bking_loc+13; board[l] == npiece; l +=13);
	  if (l == target) return TRUE;
	  }
	  else
	  {
	  for (l = bking_loc-13; board[l] == npiece; l -=13);
	  if (l == target) return TRUE;
	  }
	}
	else if (diagr(target) == diagr(bking_loc))
	{
	  if (bking_loc < target)
	  {
	  for (l = bking_loc+11; board[l] == npiece; l +=11);
	  if (l == target) return TRUE;
	  }
	  else
	  {
	  for (l = bking_loc-11; board[l] == npiece; l -=11);
	  if (l == target) return TRUE;
	  }
	}
	break;
      case wrook:
	if (file(target) == file(bking_loc))
	{
	  if (bking_loc < target)
	  {
	  for (l = bking_loc+12; board[l] == npiece; l +=12);
	  if (l == target) return TRUE;
	  }
	  else
	  {
	  for (l = bking_loc-12; board[l] == npiece; l -=12);
	  if (l == target) return TRUE;
	  }
	}
	else if (rank(target) == rank(bking_loc))
	{
	  if (bking_loc < target)
	  {
	  for (l = bking_loc+1; board[l] == npiece; l++);
	  if (l == target) return TRUE;
	  }
	  else
	  {
	  for (l = bking_loc-1; board[l] == npiece; l--);
	  if (l == target) return TRUE;
	  }
	}
	break;
      case wknight:
	 for (l = 0; l < 8; l++) 
	   if ((bking_loc + knight_o[l]) == target) return TRUE;
	break;
      case wqueen:
	if (file(target) == file(bking_loc))
	{
	  if (bking_loc < target)
	  {
	  for (l = bking_loc+12; board[l] == npiece; l +=12);
	  if (l == target) return TRUE;
	  }
	  else
	  {
	  for (l = bking_loc-12; board[l] == npiece; l -=12);
	  if (l == target) return TRUE;
	  }
	}
	else if (rank(target) == rank(bking_loc))
	{
	  if (bking_loc < target)
	  {
	  for (l = bking_loc+1; board[l] == npiece; l +=1);
	  if (l == target) return TRUE;
	  }
	  else
	  {
	  for (l = bking_loc-1; board[l] == npiece; l -=1);
	  if (l == target) return TRUE;
	  }
	}
	else if (diagl(target) == diagl(bking_loc))
	{
	  if (bking_loc < target)
	  {
	  for (l = bking_loc+13; board[l] == npiece; l +=13);
	  if (l == target) return TRUE;
	  }
	  else
	  {
	  for (l = bking_loc-13; board[l] == npiece; l -=13);
	  if (l == target) return TRUE;
	  }
	}
	else if (diagr(target) == diagr(bking_loc))
	{
	  if (bking_loc < target)
	  {
	  for (l = bking_loc+11; board[l] == npiece; l +=11);
	  if (l == target) return TRUE;
	  }
	  else
	  {
	  for (l = bking_loc-11; board[l] == npiece; l -=11);
	  if (l == target) return TRUE;
	  }
	}
	break;
      case wking:
	/* can only discover checks */
	if (moves[m].castled)
	  { 
	    if (is_attacked (bking_loc, 1)) 
	      return TRUE;
	    else
	      return FALSE;
	  }
	break;
    }

    if (from == 0) return FALSE;

    /* this checks for discovered checks */
    if (rank(from) == rank(bking_loc))
    {
      if (bking_loc > from)
      {
    	for (l = bking_loc-1; board[l] == npiece; l--);    
	if (board[l] == wrook || board[l] == wqueen) return TRUE;
      }
      else
      {
	for (l = bking_loc+1; board[l] == npiece; l++);
	if (board[l] == wrook || board[l] == wqueen) return TRUE;
      }
    }
    else if (file(from) == file(bking_loc))
    {
      if (bking_loc > from)
      {
	for (l = bking_loc-12; board[l] == npiece; l-=12);    
	if (board[l] == wrook || board[l] == wqueen) return TRUE;
      }
      else
      {
	for (l = bking_loc+12; board[l] == npiece; l+=12);
	if (board[l] == wrook || board[l] == wqueen) return TRUE;
      }
    }
    else if (diagl(from) == diagl(bking_loc))
    {
      if (bking_loc > from)
      {
	for (l = bking_loc-13; board[l] == npiece; l-=13);    
       if (board[l] == wbishop || board[l] == wqueen) return TRUE;
      }
      else
      {
       for (l = bking_loc+13; board[l] == npiece; l+=13);
       if (board[l] == wbishop || board[l] == wqueen) return TRUE;
      }
    }
    else if (diagr(from) == diagr(bking_loc))
    {
      if (bking_loc > from)
      {
	for (l = bking_loc-11; board[l] == npiece; l-=11);    
       if (board[l] == wbishop || board[l] == wqueen) return TRUE;
      }
      else
      {
       for (l = bking_loc+11; board[l] == npiece; l+=11);
       if (board[l] == wbishop || board[l] == wqueen) return TRUE;
      }
    }
    
    return FALSE;

    /* if (is_attacked (bking_loc, 1)) 
     display_board(stdout, 1);return TRUE; */
  }
}

int extended_in_check(void)
{
  register int sq;
  static const int knight_o[8] = {10, -10, 14, -14, 23, -23, 25, -25};
  
  if (Variant == Suicide) return 0;
  
  if (white_to_move == 1) 
  {
    sq = board[wking_loc-12];
    if (sq == brook || sq == bqueen) return 2;
    sq = board[wking_loc-1];
    if (sq == brook || sq == bqueen) return 2;
    sq = board[wking_loc+1];
    if (sq == brook || sq == bqueen) return 2;
    sq = board[wking_loc+12];
    if (sq == brook || sq == bqueen) return 2;
    sq = board[wking_loc+13];
    if (sq == bbishop || sq == bqueen || sq == bpawn) return 2;
    sq = board[wking_loc+11];
    if (sq == bbishop || sq == bqueen || sq == bpawn) return 2;
    sq = board[wking_loc-11];
    if (sq == bbishop || sq == bqueen) return 2;
    sq = board[wking_loc-13];
    if (sq == bbishop || sq == bqueen) return 2;
    for (sq = 0; sq < 8; sq++) 
    {
      if (board[wking_loc + knight_o[sq]] == bknight) return 2;
    }
    if (is_attacked (wking_loc, 0)) 
    {
      if (Variant == Normal || Variant == Losers) return 2;
      else return 1;
    }
  }
  else 
  {
    sq = board[bking_loc-12];
    if (sq == wrook || sq == wqueen) return 2;
    sq = board[bking_loc-1];
    if (sq == wrook || sq == wqueen) return 2;
    sq = board[bking_loc+1];
    if (sq == wrook || sq == wqueen) return 2;
    sq = board[bking_loc+12];
    if (sq == wrook || sq == wqueen) return 2;
    sq = board[bking_loc-13];
    if (sq == wbishop || sq == wqueen || sq == wpawn) return 2;
    sq = board[bking_loc-11];
    if (sq == wbishop || sq == wqueen || sq == wpawn) return 2;
    sq = board[bking_loc+11];
    if (sq == wbishop || sq == wqueen) return 2;
    sq = board[bking_loc+13];
    if (sq == wbishop || sq == wqueen) return 2;
    for (sq = 0; sq < 8; sq++) 
    {
      if (board[bking_loc + knight_o[sq]] == wknight) return 2;
    }
    if (is_attacked (bking_loc, 1)) 
    {
      if (Variant == Normal || Variant == Losers) return 2;
      else return 1;
    }
  }

  return 0;
}

void make (move_s moves[], int i) {

  /* make a move */

  /* rather than writing out from[i].from, from[i].target, etc. all over
     the place, just make a copy of them here: */
  int ep, from, target, captured, promoted, castled, find_slot;
  ep = moves[i].ep;
  from = moves[i].from;
  target = moves[i].target;
  captured = moves[i].captured;
  promoted = moves[i].promoted;
  castled = moves[i].castled;

  /*if ((moves[i].target == 0) || ((moves[i].from != 0) && ((board[moves[i].from] == npiece) || board[moves[i].from] == frame)))
      DIE;
  */
      
  /* clear the en passant rights: */
  path_x[ply].epsq = ep_square;

  ep_square = 0;

  /* update the 50 move info: */
  path_x[ply].fifty = fifty;

  /* ignore piece drops...50move draw wont happen anyway */
  if (board[from] == wpawn || board[from] == bpawn || board[target] != npiece) 
  {
    fifty = 0;
  }
  else 
  {
    fifty++;
  }
  
  if (from == 0)   
    { /* drop move */
      /* Drop moves are handled fully seperate because we exepect to encouter
	 lots of them and we try to skip as many checks as possible.
	 Note that the critical path for drop moves is very short.
	 Also, we have to handle pieces[] and squares[] specially   */
      
       /* new piece on board */
      piece_count++;

      /* find first empty slot in pieces[] */
      for(find_slot = 1; (pieces[find_slot] != 0); find_slot++)
	assert(find_slot < 63);
      
      /* add to piece array, set piece-square pointer */
      pieces[find_slot] = target;

      path_x[ply].was_promoted = is_promoted[find_slot];
      is_promoted[find_slot] = 0;
      
      /* set square->piece pointer */
      squares[target] = find_slot;
      
      assert(promoted > frame && promoted < npiece);
     
      DropremoveHolding(promoted, ToMove);

      /* piece went off holding but onto board */
      AddMaterial(promoted);

      /* put our piece on the board */
      board[target] = promoted;
      
      Hash(promoted,target);

      white_to_move ^= 1;
      ply++;
      
      return;
    }
  else
    {

      path_x[ply].was_promoted = is_promoted[squares[target]];

      /* update the "general" pieces[] / squares[] info (special moves need
	 special handling later): */
      path_x[ply].cap_num = squares[target];
      pieces[squares[target]] = 0;
      pieces[squares[from]] = target;
      squares[target] = squares[from];
      squares[from] = 0;

      /* update the piece count & add Holdings */
      if (!ep)
      {
	switch (board[target]) {
	case (npiece): break;
	default:
	  
	  if (Variant == Bughouse || Variant == Crazyhouse)
	    {
	      if (path_x[ply].was_promoted)
	    	{
		  addHolding(SwitchPromoted(board[target]), ToMove);
	    	}
	      else
	    	{ 
		  addHolding(SwitchColor(board[target]), ToMove);
	    	}
	    }
	  
	  RemoveMaterial(board[target]);
	  
	  /* remove captured piece */
	  Hash(board[target], target);
	  
	  piece_count--;
	  break;
	}
      }

      /* white pawn moves: */
      if (board[from] == wpawn) {
	/* look for a promotion move: */
	if (promoted) {
	  board[target] = promoted;
	  board[from] = npiece;
	  moved[target]++;
	  moved[from]++;
	  white_to_move ^= 1;

	  is_promoted[squares[target]] = 1;

	  /* remove pawn */
	  Hash(wpawn, from);
	  /* add new stuff */
	  Hash(promoted, target);

	  RemoveMaterial(wpawn);
	  AddMaterial(promoted);

	  ply++;
	  
	  return;
	}

	/* look for an en passant move: */
	if (ep) {
	  
	  /* remove pawn */
	  Hash(wpawn, from);
	  /* remove ep pawn */
	  Hash(bpawn, target-12);
	  /* add target pawn */
	  Hash(wpawn, target);

	  RemoveMaterial(bpawn);

	  board[target] = wpawn;
	  board[from] = npiece;

	  addHolding(wpawn, WHITE);
	  piece_count--;

	  board[target-12] = npiece;
	  moved[target]++;
	  moved[from]++;
	  moved[target-12]++;
	  white_to_move ^= 1;
	  path_x[ply].cap_num = squares[target-12];

	  pieces[squares[target-12]] = 0;
	  squares[target-12] = 0;

	  ply++;
	  
	  return;
	}
	
	/* otherwise, we have a "regular" pawn move: */
	/* first check to see if we've moved a pawn up 2 squares: */
	if (target == from+24)
	  ep_square = from+12;

	Hash(wpawn, from);
	Hash(wpawn, target);
	
	board[target] = wpawn;
	board[from] = npiece;
	moved[target]++;
	moved[from]++;
	white_to_move ^= 1;
	
	ply++;
	
	return;
	
      }
      
      /* black pawn moves: */
      if (board[from] == bpawn) {
	/* look for a promotion move: */
	if (promoted) {
	  board[target] = promoted;
	  board[from] = npiece;
	  moved[target]++;
	  moved[from]++;
	  white_to_move ^= 1;

	  is_promoted[squares[target]] = 1;

	  /* remove pawn */
	  Hash(bpawn, from);
	  /* add new stuff */
	  Hash(promoted, target);

	  RemoveMaterial(bpawn);
	  AddMaterial(promoted);

	  ply++;
	  
	  return;
	}
	
	/* look for an en passant move: */
	if (ep) {

	  /* remove pawn */
	  Hash(bpawn, from);
	  /* remove ep pawn */
	  Hash(wpawn, target+12);
	  /* add target pawn */
	  Hash(bpawn, target);

	  RemoveMaterial(wpawn);

	  board[target] = bpawn;
	  board[from] = npiece;

	  addHolding(bpawn, BLACK);
	  piece_count--;

	  board[target+12] = npiece;
	  moved[target]++;
	  moved[from]++;
	  moved[target+12]++;
	  white_to_move ^= 1;
	  path_x[ply].cap_num = squares[target+12];
	  pieces[squares[target+12]] = 0;
	  squares[target+12] = 0;
	  
	  ply++;
	  
	  return;
	}
	
	/* otherwise, we have a "regular" pawn move: */
	/* first check to see if we've moved a pawn down 2 squares: */
	if (target == from-24)
	  ep_square = from-12;
	
	board[target] = bpawn;
	board[from] = npiece;
	moved[target]++;
	moved[from]++;
	white_to_move ^= 1;

	Hash(bpawn, from);
	Hash(bpawn, target);

        ply++;
	
	return;
      }
      
      /* piece moves, other than the king: */
      if (board[from] != wking && board[from] != bking) {
	
	Hash(board[from], from);
	Hash(board[from], target);

	board[target] = board[from];
	board[from] = npiece;
	moved[target]++;
	moved[from]++;
	white_to_move ^= 1;
	
	ply++;
	
	return;
      }

      /* otherwise, we have a king move of some kind: */
      /* White king moves first: */
      if (board[from] == wking) {
	/* record the new white king location: */
	wking_loc = target;
	
	/* perform the white king's move: */
	board[target] = wking;
	board[from] = npiece;
	moved[target]++;
	moved[from]++;
	white_to_move ^= 1;

	Hash(wking, from);
	Hash(wking, target);
	
	/* check for castling: */
	/* check for white kingside castling: */
	if (castled == wck) {
	  board[33] = npiece;
	  board[31] = wrook;
	  moved[33]++;
	  moved[31]++;
	  white_castled = wck;
	  pieces[squares[33]] = 31;
	  squares[31] = squares[33];
	  squares[33] = 0;
	  
	  Hash(wrook, 33);
	  Hash(wrook, 31);

          ply++;
	  
	  return;
	}
	
	/* check for white queenside castling: */
	else if (castled == wcq) {
	  board[26] = npiece;
	  board[29] = wrook;
	  moved[26]++;
	  moved[29]++;
	  white_castled = wcq;
	  pieces[squares[26]] = 29;
	  squares[29] = squares[26];
	  squares[26] = 0;

	  Hash(wrook, 26);
	  Hash(wrook, 29);
	 
          ply++;
	  
	  return;
	}

	ply++;
	
	return;
      }
      
      /* now we have only black king moves left: */
      else {
	/* record the new black king location: */
	bking_loc = target;
	
	/* perform the black king's move: */
	board[target] = bking;
	board[from] = npiece;
	moved[target]++;
	moved[from]++;
	white_to_move ^= 1;

	Hash(bking, from);
	Hash(bking, target);
	
	/* check for castling: */
	/* check for black kingside castling: */
	if (castled == bck) {
	  board[117] = npiece;
	  board[115] = brook;
	  moved[117]++;
	  moved[115]++;
	  black_castled = bck;
	  pieces[squares[117]] = 115;
	  squares[115] = squares[117];
	  squares[117] = 0;

	  Hash(brook, 117);
	  Hash(brook, 115);

	  ply++;
	  
	  return;
	}

	/* check for black queenside castling: */
	else if (castled == bcq) {
	  board[110] = npiece;
	  board[113] = brook;
	  moved[110]++;
	  moved[113]++;
	  black_castled = bcq;
	  pieces[squares[110]] = 113;
	  squares[113] = squares[110];
	  squares[110] = 0;

	  Hash(brook, 110);
	  Hash(brook, 113);

          ply++;
	  
	  return;
	}
      }
    ply++;
      
    return;
  }
}

void add_move(int Ptarget,
	      int Ppromoted)
{
  genfor[numb_moves].from = gfrom;
  genfor[numb_moves].target = Ptarget;
  genfor[numb_moves].captured = npiece;
  genfor[numb_moves].castled = no_castle;
  genfor[numb_moves].promoted = Ppromoted;
  genfor[numb_moves].ep = FALSE;
  numb_moves++;

  return;	
}

void add_capture(int Ptarget,
		 int Pcaptured,
		 int Ppromoted,
		 int Pep)
{
  if ((Variant != Suicide) && (Pcaptured == wking || Pcaptured == bking))
    {
      kingcap = TRUE;
      return;
    }
  else
    if (Pcaptured != npiece) fcaptures = TRUE; 
  
  genfor[numb_moves].from = gfrom;
  genfor[numb_moves].target = Ptarget;
  genfor[numb_moves].captured = Pcaptured;
  genfor[numb_moves].castled = no_castle;
  genfor[numb_moves].promoted = Ppromoted;
  genfor[numb_moves].ep = Pep;
  numb_moves++;
  
  return;
}

void try_drop (int ptype)
{
  genfor[numb_moves].from = 0;
  genfor[numb_moves].target = gfrom;
  genfor[numb_moves].captured = npiece;
  genfor[numb_moves].castled = no_castle;
  genfor[numb_moves].promoted = ptype;
  genfor[numb_moves].ep = FALSE;
  numb_moves++;
  
  return;  
}

void push_king_castle (int Ptarget, int Pcastle_type)
{
  genfor[numb_moves].from = gfrom;
  genfor[numb_moves].target = Ptarget;
  genfor[numb_moves].captured = npiece;
  genfor[numb_moves].castled = Pcastle_type;
  genfor[numb_moves].promoted = 0;
  genfor[numb_moves].ep = FALSE;
  numb_moves++;
  
  return;
}

void push_king (int target) {

  /* add king moves to the moves array */

  /* first see if the move will take the king off the board: */
  if (board[target] == frame)
    return;

  /* check to see if we have a non capture when in qsearch: */
  if (board[target] == npiece && captures)
    return;

  /* non-capture, 'normal' king moves: */
  if (board[target] == npiece) {
    add_move(target, 0);
    return;
  }

  /* 'normal' capture moves by the king: */
  else if ((board[target]&1) != (board[gfrom]&1)) {
    add_capture(target, board[target], 0, FALSE);
    return;
  }

  /* no more possible moves for the king, so return: */
  return;
}


void push_knighT (int target) {

  /* add knight moves to the moves array */

  /* check to see if we have a non capture when in qsearch: */
  if (board[target] == npiece && captures)
    return;

  /* check for a non-capture knight move: */
  if (board[target] == npiece) {
    add_move(target, 0);
    return;
  }

  /* check for a capture knight move: */
  else if ((board[target]&1) != (board[gfrom]&1)) {
    add_capture(target, board[target], 0, FALSE);
    return;
  }

  /* no more possible moves left for the knight, so return: */
  return;
}


void push_pawn (int target, xbool is_ep) {

  /* add pawn moves to the moves array */

  int captured_piece;

  /* check to see if it's an ep move: */
  if (is_ep) {
    if (board[gfrom] == wpawn) {
      add_capture(target, bpawn, 0, TRUE);
	  return;
    }
    else {
      add_capture(target, wpawn, 0, TRUE);
      return;
    }
  }

  /* record which piece we are taking, so we don't have to compute it over
     and over again: */
  captured_piece = board[target];
  
  /* look for a white promotion move: */
  if (board[gfrom] == wpawn && rank(gfrom) == 7) {
    add_capture(target, captured_piece, wqueen, FALSE);
    add_capture(target, captured_piece, wrook, FALSE);
    add_capture(target, captured_piece, wbishop, FALSE);
    add_capture(target, captured_piece, wknight, FALSE);
    if (Variant == Suicide)
      add_capture(target, captured_piece, wking, FALSE);
    /* we've finished generating all the promotions: */
    return;
  }

  /* look for a black promotion move: */
  else if (board[gfrom] == bpawn && rank(gfrom) == 2) {
    add_capture(target, captured_piece, bqueen, FALSE);
    add_capture(target, captured_piece, brook, FALSE);
    add_capture(target, captured_piece, bbishop, FALSE);
    add_capture(target, captured_piece, bknight, FALSE);
    if (Variant == Suicide)
      add_capture(target, captured_piece, bking, FALSE);
    /* we've finished generating all the promotions: */
    return;
  }

  /* otherwise, we have a normal pawn move: */
  else {
    add_capture(target, captured_piece, 0, FALSE);
    return;
  }  
}

void push_pawn_simple (int target) {

  /* add pawn moves to the moves array */
  
  add_move(target, 0);
  return;
}

void push_slidE (int target) {

  /* add moves for sliding pieces to the moves array */

  int offset;
  int mycolor;
  
  /* check to see if we have gone off the board first: */
  /* init variables: */
  offset = target - gfrom;
  mycolor = board[gfrom]&1;
  
  /* loop until we hit the edge of the board, or another piece: */
  do {
    /* case when the target is an empty square: */
    if (board[target] == npiece) {
      if (!captures) {
      add_move(target, 0);
      }
      target += offset;
    }

    /* case when an enemy piece is hit: */
    else if ((board[target]&1) != mycolor) {
      add_capture(target, board[target], 0, FALSE);
      break;
    }

    /* otherwise, we have hit a friendly piece (or edge of board): */
    else
      break;
  } while (board[target] != frame);

  /* we have finished generating all of the sliding moves, so return: */
  return;

}

void unmake (move_s moves[], int i) {

  /* un-make a move */

  /* rather than writing out from[i].from, from[i].target, etc. all over
     the place, just make a copy of them here: */
  int ep, from, target, captured, promoted, castled;
  ep = moves[i].ep;
  from = moves[i].from;
  target = moves[i].target;
  captured = moves[i].captured;
  promoted = moves[i].promoted;
  castled = moves[i].castled;

  /* if ((moves[i].target == 0) || ((moves[i].target != 0) && (board[moves[i].target] == npiece)))
      DIE;
  */
      
  ply--;
 
  ep_square = path_x[ply].epsq;
  
  /* update the 50 move info: */
  fifty = path_x[ply].fifty;
  
  if (from == 0)   /* drop move */
    {
      /* Drop moves are hanled fully seperate because we exepect to encouter
	 lots of them and we try to skip as many checks as possible.
	 Note that the critical path for drop moves is very short.
	 Also, we have to handle pieces[] and squares[] specially   */
      
       /* remove from piece array, unset piece-square pointer */

       pieces[squares[target]] = 0;
       is_promoted[squares[target]] = path_x[ply].was_promoted;
       
       /* unset square->piece pointer */
       squares[target] = 0;
       
       piece_count--;

       assert(promoted < npiece && promoted > frame);
       
       DropaddHolding(promoted, NotToMove);

       RemoveMaterial(promoted);

       /* restore board, either no piece or ep square */
       board[target] = captured;

       Hash(promoted,target);

       white_to_move ^= 1;

       return;
    }
  else
    {
      
      /* update the "general" pieces[] / squares[] info (special moves need
	 special handling later): */
      
      squares[from] = squares[target];
      squares[target] = path_x[ply].cap_num;
      pieces[squares[target]] = target;
      pieces[squares[from]] = from;

      is_promoted[squares[target]] = path_x[ply].was_promoted;
      
      /* update the piece count for determining opening/middlegame/endgame stage */
      if (!ep)
	{
	  switch (captured) {
	  case (npiece): break;
	  default:
	    
	    if (Variant == Bughouse || Variant == Crazyhouse)
	      {
		if (is_promoted[squares[target]])
		  {
		    removeHolding(SwitchPromoted(captured), NotToMove); 
		  }
		else
		  { 
		removeHolding(SwitchColor(captured), NotToMove);
		  } 
	      }
	
	    Hash(captured, target);
	    
	    AddMaterial(captured);
	    
	    piece_count++; 
	    break;
	  }
	}
      
      /* white pawn moves: */
      if (board[target] == wpawn) {
	/* look for an en passant move: */
	if (ep) {
	  
	  Hash(wpawn, target);
	  Hash(wpawn, from);
	  Hash(bpawn, target-12);

	  board[target] = npiece;
	  board[from] = wpawn;

	  AddMaterial(bpawn);

	  removeHolding(wpawn, WHITE);
	  piece_count++;

	  board[target-12] = bpawn;
	  moved[target]--;
	  moved[from]--;
	  moved[target-12]--;
	  white_to_move ^= 1;
	  squares[target-12] = path_x[ply].cap_num;
	  pieces[path_x[ply].cap_num] = target-12;
	  squares[target] = 0;
	  return;
	}
	
	/* otherwise, we have a "regular" pawn move: */
	Hash(wpawn, from);
	Hash(wpawn, target);

	board[target] = captured;
	board[from] = wpawn;
	moved[target]--;
	moved[from]--;
	white_to_move ^= 1;
	return;
	
      }
      
      /* black pawn moves: */
      if (board[target] == bpawn) {
	/* look for an en passant move: */
	if (ep) {

	  Hash(bpawn, target);
	  Hash(bpawn, from);
	  Hash(wpawn, target+12);

	  board[target] = npiece;
	  board[from] = bpawn;

	  AddMaterial(wpawn);

	  removeHolding(bpawn, BLACK);
	  piece_count++;

	  board[target+12] = wpawn;
	  moved[target]--;
	  moved[from]--;
	  moved[target+12]--;
	  white_to_move ^= 1;
	  squares[target+12] = path_x[ply].cap_num;
	  pieces[path_x[ply].cap_num] = target+12;
	  squares[target] = 0;
	  return;
	}
	
	Hash(bpawn, from);
	Hash(bpawn, target);

	/* otherwise, we have a "regular" pawn move: */
	board[target] = captured;
	board[from] = bpawn;
	moved[target]--;
	moved[from]--;
	white_to_move ^= 1;
	return;
	
      }
      
      /* piece moves, other than the king: */
      if (board[target] != wking && board[target] != bking && !promoted) {
	board[from] = board[target];
	board[target] = captured;
	moved[target]--;
	moved[from]--;
	white_to_move ^= 1;
	
	Hash(board[from], target);
	Hash(board[from], from);

	return;
      }
      
      /* look for a promotion move: */
      if (promoted) {
	/* white promotions: */
	if (board[target]%2) {
	  board[target] = captured;
	  board[from] = wpawn;
	  moved[target]--;
	  moved[from]--;
	  white_to_move ^= 1;

	  Hash(wpawn, from);
	  Hash(promoted, target);

	  RemoveMaterial(promoted);
	  AddMaterial(wpawn);

	  return;
	}
	
	/* black promotions: */
	board[target] = captured;
	board[from] = bpawn;
	moved[target]--;
	moved[from]--;
	white_to_move ^= 1;

	Hash(bpawn, from);
	Hash(promoted, target);

	RemoveMaterial(promoted);
	AddMaterial(bpawn);

	return;
      }
      
      /* otherwise, we have a king move of some kind: */
      /* White king moves first: */
      if (board[target] == wking) {
	/* record the new white king location: */
	wking_loc = from;

	/* perform the white king's move: */
	board[target] = captured;
	board[from] = wking;
	moved[target]--;
	moved[from]--;
	white_to_move ^= 1;

	Hash(wking, from);
	Hash(wking, target);
	
	/* check for castling: */
	/* check for white kingside castling: */
	if (castled == wck) {
	  board[33] = wrook;
	  board[31] = npiece;
	  moved[33]--;
	  moved[31]--;
	  white_castled = no_castle;
	  squares[33] = squares[31];
	  squares[31] = 0;
	  pieces[squares[33]] = 33;

	  Hash(wrook, 33);
	  Hash(wrook, 31);

	  return;
	}
	
	/* check for white queenside castling: */
	else if (castled == wcq) {
	  board[26] = wrook;
	  board[29] = npiece;
	  moved[26]--;
	  moved[29]--;
	  white_castled = no_castle;
	  squares[26] = squares[29];
	  squares[29] = 0;
	  pieces[squares[26]] = 26;

	  Hash(wrook, 29);
	  Hash(wrook, 26);

	  return;
	}
	
	return;
      }
      
      /* now we have only black king moves left: */
      else {
	/* record the new black king location: */
	bking_loc = from;
	
	/* perform the black king's move: */
	board[target] = captured;
	board[from] = bking;
	moved[target]--;
	moved[from]--;
	white_to_move ^= 1;

	Hash(bking, from);
	Hash(bking, target);
	
	/* check for castling: */
	/* check for black kingside castling: */
	if (castled == bck) {
	  board[117] = brook;
	  board[115] = npiece;
	  moved[117]--;
	  moved[115]--;
	  black_castled = no_castle;
	  squares[117] = squares[115];
	  squares[115] = 0;
	  pieces[squares[117]] = 117;

	  Hash(brook, 117);
	  Hash(brook, 115);

	  return;
	}
	
	/* check for black queenside castling: */
	else if (castled == bcq) {
	  board[110] = brook;
	  board[113] = npiece;
	  moved[110]--;
	  moved[113]--;
	  black_castled = no_castle;
	  squares[110] = squares[113];
	  squares[113] = 0;
	  pieces[squares[110]] = 110;

	  Hash(brook, 110);
	  Hash(brook, 113);

	  return;
	}
      }
    }
  return;
}
