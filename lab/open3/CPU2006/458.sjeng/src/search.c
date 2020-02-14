/*
    Sjeng - a chess variants playing program
    Copyright (C) 2000-2003 Gian-Carlo Pascutto

    File: search.c                                        
    Purpose: contains functions related to the recursive search

*/

#include "sjeng.h"
#include "extvars.h"
#include "protos.h"
#include "limits.h"

unsigned int FH, FHF;
unsigned int razor_drop, razor_material, drop_cuts, ext_recap, ext_onerep;

char true_i_depth;

int bestmovenum;

int ugly_ep_hack;

char postpv[STR_BUFF];

char searching_move[20];
int moveleft;
int movetotal;

int legals;

int failed;
int extendedtime;

int tradefreely;

int s_threat;

unsigned int rootnodecount[MOVE_BUFF];

xbool checks[PV_BUFF];
xbool recaps[PV_BUFF];
xbool singular[PV_BUFF];

#define KINGCAP 50000
#define NONE    0
#define SINGLE  1

#define MAINVAR 100000000
#define CAPTURE  50000000
#define KILLER1  25000000
#define KILLER2  20000000
#define KILLER3  15000000

void order_moves (move_s moves[], int move_ordering[], int see_values[], int num_moves, int best) {

  int promoted, captured;
  int i, from, target, seev;
  /* fill the move ordering array: */

  /* if searching the pv, give it the highest move ordering, and if not, rely
     on the other heuristics: */
  if (searching_pv) {
    searching_pv = FALSE;
    for (i = 0; i < num_moves; i++) {
      from = moves[i].from;
      target = moves[i].target;
      promoted = moves[i].promoted;
      captured = moves[i].captured;
      
      /* give captures precedence in move ordering, and order captures by
	 material gain */
      if (captured != npiece)
	{
	  /* No SEE for 'obviously not losing' captures */
	  if (abs(material[captured])+15 >= abs(material[board[from]]))
	  {
	  	see_values[i] = abs(material[captured]) - (abs(material[board[from]])>>4);
	  	move_ordering[i] = CAPTURE + see_values[i];	  		  	
	  }
	  else
	  {
		  seev = see(ToMove, target, from);
	
		  if (seev >= -50)
		    move_ordering[i] = CAPTURE + seev;
		  else
		    move_ordering[i] = seev;
		  
		  see_values[i] = seev;
	  }	  
	}      
      else
	move_ordering[i] = 0;
      
      /* make the pv have highest move ordering: */
      if (from == pv[1][ply].from 
	  && target == pv[1][ply].target
	  && promoted == pv[1][ply].promoted) {
	  	
	searching_pv = TRUE;
	move_ordering[i] = MAINVAR;

	if (captured != npiece)
	{
	  /* No SEE for 'obviously not losing' captures */
	  if (abs(material[captured])+15 >= abs(material[board[from]]))
	  {
	  	see_values[i] = abs(material[captured]) - (abs(material[board[from]])>>4);	  	
	  }
	  else
	  {
		seev = see(ToMove, target, from);		  		  
		see_values[i] = seev;
	  }	  
	}
      } 
      else if ((best != -1) && (best != -2) && (i == best))
	{
	  move_ordering[i] = MAINVAR;

	  if (captured != npiece)
	  {
	  	/* No SEE for 'obviously not losing' captures */
		  if (abs(material[captured])+15 >= abs(material[board[from]]))
		  {
		  	see_values[i] = abs(material[captured]) - (abs(material[board[from]])>>4);
		  }
		  else
		  {
			  seev = see(ToMove, target, from);		
			  see_values[i] = seev;
		  }	  
	  }
	}
      else if (best == -2)
	{
	  /* we have an iterative deepening move */
	  if (from == pv[ply+1][ply+1].from 
	      && target == pv[ply+1][ply+1].target 
	      && promoted == pv[ply+1][ply+1].promoted)
	    {
	      move_ordering[i] = MAINVAR;
	      
	      if (captured != npiece)
	  	{
	  	/* No SEE for 'obviously not losing' captures */
		  if (abs(material[captured])+15 >= abs(material[board[from]]))
		  {
		  	see_values[i] = abs(material[captured]) - (abs(material[board[from]])>>4);
		  }
		  else
		  {
			  seev = see(ToMove, target, from);		
			  see_values[i] = seev;
		  }	  
	  	}
	    }
	}
      
      /* heuristics other than pv (no need to use them on the pv move - it is
	 already ordered highest) */
      else 
	{

	  if (ply != 1 || i_depth < 2)
	    {
	      /* add the history heuristic bonus: */
	      move_ordering[i] += history_h[from][target];

	      /* add the killer move heuristic bonuses: */
	      if (from == killer1[ply].from && target == killer1[ply].target
		  && promoted == killer1[ply].promoted)
		move_ordering[i] += KILLER1;
	      else if (from == killer2[ply].from && target == killer2[ply].target
		       && promoted == killer2[ply].promoted)
		move_ordering[i] += KILLER2;
	      else if (from == killer3[ply].from && target == killer3[ply].target
		       && promoted == killer3[ply].promoted)
		move_ordering[i] += KILLER3;
	    }
	  else
	    {
	      if ((nodes / 100) > MAINVAR)
		{
		  move_ordering[i] = rootnodecount[i] / 1000;
		}
	      else
		{
		  move_ordering[i] = rootnodecount[i] / 100;
		}
	    }
	}
    }
  }

  /* if not searching the pv: */
  else {
    for (i = 0; i < num_moves; i++) {
      from = moves[i].from;
      target = moves[i].target;
      promoted = moves[i].promoted;
      captured = moves[i].captured;
      
      /* give captures precedence in move ordering, and order captures by
	 material gain */
      if ((best != -1) && (i == best))
	{
	  move_ordering[i] = MAINVAR;

	  /* make sure we have SEE data */
	  if (captured != npiece)
	    {
	    	/* No SEE for 'obviously not losing' captures */
		  if (abs(material[captured])+15 >= abs(material[board[from]]))
		  {
		  	see_values[i] = abs(material[captured]) - (abs(material[board[from]])>>4);		  	
		  }
		  else
		  {
			  seev = see(ToMove, target, from);			  
			  see_values[i] = seev;
		  }	  
	    }
	  
	}
      else if (best == -2)
	{
	  /* we have an iterative deepening move */
	  if (from == pv[ply+1][ply+1].from 
	      && target == pv[ply+1][ply+1].target 
	      && promoted == pv[ply+1][ply+1].promoted)
	    {
	      move_ordering[i] = MAINVAR;
	     
	      if (captured != npiece)
	      {
	      	/* No SEE for 'obviously not losing' captures */
		  if (abs(material[captured])+15 >= abs(material[board[from]]))
		  {
		  	see_values[i] = abs(material[captured]) - (abs(material[board[from]])>>4);		  
		  }
		  else
		  {
			  seev = see(ToMove, target, from);					  
			  see_values[i] = seev;
		  }	  
	      }
	    }
	}
      else if (captured != npiece)
	{	  
	  /* No SEE for 'obviously not losing' captures */
	  if (abs(material[captured])+15 >= abs(material[board[from]]))
	  {
	  	see_values[i] = abs(material[captured]) - (abs(material[board[from]])>>4);
	  	move_ordering[i] = CAPTURE + see_values[i];	  		  	
	  }
	  else
	  {
		  seev = see(ToMove, target, from);
	
		  if (seev >= -50)
		    move_ordering[i] = CAPTURE + seev;
		  else
		    move_ordering[i] = seev;
		  
		  see_values[i] = seev;
	  }	  	  	  
	}      
      else
	move_ordering[i] = 0;
      
      /* heuristics other than pv */
      
      /* add the history heuristic bonus: */
      move_ordering[i] += history_h[from][target];

      /* add the killer move heuristic bonuses: */
      if (from == killer1[ply].from && target == killer1[ply].target
	  && promoted == killer1[ply].promoted)
	move_ordering[i] += KILLER1;
      else if (from == killer2[ply].from && target == killer2[ply].target
	       && promoted == killer2[ply].promoted)
	move_ordering[i] += KILLER2;
      else if (from == killer3[ply].from && target == killer3[ply].target
	       && promoted == killer3[ply].promoted)
	move_ordering[i] += KILLER3;
    }
  }

}

void perft (int depth) {

  move_s moves[MOVE_BUFF];
  int num_moves, i;
  int ic;

  num_moves = 0;

  /* return if we are at the maximum depth: */
  if (!depth) {
    raw_nodes++;
    return;
  }

  /* generate the move list: */
  gen (&moves[0]);
  num_moves = numb_moves;

  ic = in_check();

  /* loop through the moves at the current depth: */
  for (i = 0; i < num_moves; i++) {
    make (&moves[0], i);

    /* check to see if our move is legal: */
    if (check_legal (&moves[0], i, ic)) {
      /* go deeper into the tree recursively, increasing the indent to
	 create the "tree" effect: */
      perft (depth-1);
    }

    /* unmake the move to go onto the next: */
    unmake (&moves[0], i);
  }


}


int qsearch (int alpha, int beta, int depth) {

  /* perform a quiscense search on the current node using alpha-beta with
     negamax search */

  move_s moves[MOVE_BUFF];
  int num_moves, i, j;
  int score = -INF, standpat, 
    move_ordering[MOVE_BUFF],
    see_values[MOVE_BUFF];
  xbool legal_move, no_moves = TRUE;
  int sbest, best_score, best, delta, bound;
  int originalalpha;
  int oldtime;
  int seev;
  
  pv_length[ply] = ply;
   
  /* before we do anything, see if we're out of time: */
  if (!(nodes & ((1<<16)-1))) 
    {
      if (interrupt()) 
	{
	  time_exit = TRUE;
	  return 0;
	}
      else if (((rdifftime (rtime (), start_time) >= time_for_move)) && (i_depth > 1))
	{
	  if (failed == 1 && !extendedtime 
	      && !fixed_time
	      && !go_fast
	      && Variant != Bughouse
	      && (time_left > max(time_for_move*4, 1000)))
	    {
	      extendedtime = 1;
	      oldtime = time_for_move;
	      time_for_move += allocate_time();
	      printf("Extended from %d to %d, time left %d\n", oldtime,
		     time_for_move, 
		     time_left);
	    }
	  else
	    {
	      time_exit = TRUE;
	      return 0;
	    }
	}
    }
  
  /* return our score if we're at a leaf node: */
  if (depth <= 0 || ply >= PV_BUFF) 
  {
    score = eval (alpha, beta);
    return score;
  }

  qnodes++;
  nodes++;

  originalalpha = alpha;
 
  switch (QProbeTT(&bound, &best))
    {
    case EXACT:
      return bound;
      break;
    case UPPER:
      if (bound <= alpha)
	return bound;
      break;
    case LOWER:
      if (bound >= beta)
	return bound;
      break;
    case DUMMY:
      break;
    case HMISS:
      best = -1;;
      break;
    };
  
  standpat = eval (alpha, beta);
  
  if (standpat >= beta) {
    /* rem check this */
    QStoreTT(standpat, originalalpha, beta, 500);
    return standpat;
  }
  else if (standpat > alpha) {
    alpha = standpat;
  }
  
  sbest = -1;
  best_score = -INF;
  num_moves = 0;
  
  /* generate and order moves: */
  gen (&moves[0]);
  num_moves = numb_moves;

  if (kingcap) return KINGCAP;
        
  delta = alpha-150-standpat;
  
  order_moves (&moves[0], &move_ordering[0], &see_values[0], num_moves, best);

  /* loop through the moves at the current node: */
  while (remove_one (&i, &move_ordering[0], num_moves)) {

    legal_move = FALSE;
  
    if (!moves[i].promoted)
    {
    	seev = see_values[i];
 
    	if (seev < delta || seev < 0)
	  continue;  
    }

    make (&moves[0], i);
 
    score = -qsearch (-beta, -alpha, depth-1);
	
    if (score != -KINGCAP) 
      {
	legal_move = TRUE;
	no_moves = FALSE;
      };

    unmake (&moves[0], i);

    if(score > best_score && legal_move)
      {
	best_score = score;
      };

    /* check our current score vs. alpha: */
    if (score > alpha && legal_move) 
      {

	/* don't update the history heuristic scores here, since depth is messed
	   up when qsearch is called */
	best = i;
	
	/* try for an early cutoff: */
	if (score >= beta) 
	  {
	    QStoreTT(score, originalalpha, beta, i);
	    return score;
	  }
	
	alpha = score;
	
	/* update the pv: */
	pv[ply][ply] = moves[i];;
	for (j = ply+1; j < pv_length[ply+1]; j++)
	  pv[ply][j] = pv[ply+1][j];
	pv_length[ply] = pv_length[ply+1];
      }
    
  }

  /* we don't check for mate / stalemate here, because without generating all
     of the moves leading up to it, we don't know if the position could have
     been avoided by one side or not */

  QStoreTT(alpha, originalalpha, beta, best);
  return alpha;
  
}

xbool remove_one (int *marker, int move_ordering[], int num_moves) {

  /* a function to give pick the top move order, one at a time on each call.
     Will return TRUE while there are still moves left, FALSE after all moves
     have been used */

  int i, best = -INF;

  *marker = -INF;

  for (i = 0; i < num_moves; i++) {
    if (move_ordering[i] > best) {
      *marker = i;
      best = move_ordering[i];
    }
  }

  if (*marker > -INF) {
    move_ordering[*marker] = -INF;
    return TRUE;
  }
  else {
    return FALSE;
  }

}

int search (int alpha, int beta, int depth, int is_null) {

  /* search the current node using alpha-beta with negamax search */

  move_s moves[MOVE_BUFF];
  int num_moves, i, j;
  int score = -INF, move_ordering[MOVE_BUFF], see_values[MOVE_BUFF];
  xbool no_moves, legal_move;
  int bound, threat, donull, best, sbest, best_score, old_ep;
  xbool incheck, first;
  int extend, fscore, fmax, selective;
  move_s kswap;
  int ksswap;
  int originalalpha;
  int afterincheck;
  int legalmoves;
  int dropcut;
  int oldtime;
  static const int rc_index[14] = {0,1,1,2,2,5,5,3,3,4,4,2,2,0};

  nodes++;
 
  /* before we do anything, see if we're out of time: */
  if (!(nodes & ((1<<16)-1))) {
    if (interrupt()) 
      {
	time_exit = TRUE;
	return 0;
      }
    else if (((rdifftime (rtime (), start_time) >= time_for_move)) && (i_depth > 1))
      {
	if (failed == 1 && !extendedtime 
	    && !fixed_time
	    && !go_fast
	    && Variant != Bughouse
	    && (time_left > max(time_for_move*4, 1000)))
	  {
	    extendedtime = 1;
	    oldtime = time_for_move;
	    time_for_move += allocate_time();
	    printf("Extended from %d to %d, time left %d\n", oldtime,
		   time_for_move, 
		   time_left);
	  }
	else
	  {
	    time_exit = TRUE;
	    return 0;
	  }
      }
  }
  
  originalalpha = alpha;
  fmax = -INF;
  
  threat = 0;
  extend = 0;
  
  pv_length[ply] = ply;
  
  if (is_draw ()) 
    {
      return 0;
    }
  
  incheck = checks[ply];
  singular[ply] = 0;
  recaps[ply] = 0;
  
  /* perform check extensions if we haven't gone past maxdepth: */
  if (ply < PV_BUFF && incheck && ((ply <= i_depth*2) || (depth == 0))) 
    {
      depth++;
      ext_check++;
      extend++;
    } 
  else if ((ply < PV_BUFF) && (ply > 2) && (ply <= (i_depth*2))	   
           && !recaps[ply-2]
           && cfg_recap
	   && (path[ply-1].captured != npiece)
	   && (rc_index[path[ply-1].captured] == rc_index[path[ply-2].captured]))
    {
      depth++;
      ext_recap++;
      extend++;
      recaps[ply] = TRUE;
    }

  /* try to find a stable position before passing the position to eval (): */
  if (depth <= 0 || ply >= PV_BUFF)
    {
      if (Variant != Suicide && Variant != Losers)
	{
	      captures = TRUE;
	      score = qsearch (alpha, beta, PV_BUFF-ply);   
	      captures = FALSE;
	      return score; 
	}
      else
	{
	  if (Variant == Suicide)
	    {
	      return suicide_eval();

	    }
	  else if (Variant == Losers)
	    {		 
	      i = losers_eval();
	      
	      if (abs(i) == INF)
		{
		  return ((i > 0) ? INF-ply : -INF+ply);
		}
	      else
		{		
		  return i;		  
		}
	    }
	}
    }
  
  num_moves = 0;
  no_moves = TRUE;
 
  switch (ProbeTT(&bound, beta, &best, &threat, &donull, depth))
    {
    case EXACT:
      return bound;
      break;
    case UPPER:
      if (bound <= alpha)
	return bound;
      break;
    case LOWER:
      if (bound >= beta)
	return bound;
      break;
    case DUMMY:
      break;
    case HMISS:
      best = -1;
      threat = FALSE;
      break;
    };
  
  if (best == 500) best = -1;
 
  sbest = -1;
  best_score = -INF;

  old_ep = ep_square;
  
  legalmoves = 0;
  
  if (Variant == Losers)
    {
      i = losers_eval();
      
      if (abs(i) == INF)
      {
	return (i > 0) ? i-ply : i+ply;
      }
      
      captures = TRUE;
      gen (&moves[0]);
      num_moves = numb_moves;
      captures = FALSE; 
									               
      if (num_moves)
	{
	  for (i = 0; i < num_moves; i++)
	    {
	      make(&moves[0], i);
	      if (check_legal(&moves[0], i, incheck))
		{
		  legalmoves++;
		}
	      unmake(&moves[0], i);
	    }
	}       
      if (!legalmoves) 
	{
	  captures = FALSE;
	  gen(&moves[0]);
	  num_moves = numb_moves;
	};          

      legalmoves = 0;
    } 

  if ((is_null == NONE)
      && ((phase != Endgame) || ((phase == Endgame) && (depth <= 6)))
      && !incheck && donull && !searching_pv
      && (threat == FALSE)
      && (((Variant != Suicide) && (Variant != Losers)) 
	  || (Variant == Losers && moves[0].captured == npiece)))
    {

        ep_square = 0;      
        white_to_move ^= 1;
        ply++;
        fifty++;
        hash ^= 0xDEADBEEF;
	
      	/* use R=1 cos R=2 is too dangerous for our ply depths */
      	if (Variant != Normal && Variant != Losers)
            score = -search(-beta, -beta+1, ((depth > 3) ? depth-2-1 : depth-1-1), SINGLE);
     	else
	    score = -search(-beta, -beta+1, depth-3-1, SINGLE);
      
      hash ^= 0xDEADBEEF;
      fifty--;
      ply--;
      white_to_move ^= 1;
      ep_square = old_ep;

      if (time_exit) return 0;

      NTries++;

      if (score >= beta)
	{
	  
	  NCuts++;
	  
          StoreTT(score, alpha, beta, 500, 0, depth);
	  
	  return score;
	}
      else if (score < -INF+100)
	{
	  threat = TRUE;
	  TExt++;
	  depth++;
	  extend++;
	  ext_onerep++;
	}
    }
  else if (threat == TRUE)
    {
      TExt++;
      depth++;
      extend++;
      ext_onerep++;
    }
  
  score = -INF;
  
   if (Variant != Losers)
    {
      /* generate and order moves: */
      gen (&moves[0]);
      num_moves = numb_moves;
    }
  
  /* one reply extension */  
  if (cfg_onerep && incheck)
  {
      if (num_moves)
      {      	
	for (i = 0;(i < num_moves) && (legalmoves < 2); i++)
	  {
	    make(&moves[0], i);
	    if (check_legal(&moves[0], i, incheck))
	      {
		legalmoves++;
	      }
	    unmake(&moves[0], i);
	  }
      }
  }
    
  if (ply < PV_BUFF)
  {
  	if ((Variant == Suicide) && num_moves == 1) {
		depth++;
  		ext_onerep++; 
		singular[ply] = TRUE;
        } 
  	else if (legalmoves == 1 && !singular[ply-2]) {
		depth++;
  		ext_onerep++;  	
		singular[ply] = TRUE;
	}
   }
  
  first = TRUE;
  selective = 0;

  if (phase != Endgame && (Variant != Suicide) && cfg_futprune)
    {

      fscore = (white_to_move ? Material : -Material) + 900;
    
      if (!extend && depth == 3 && fscore <= alpha)
	depth = 2;
    
      fscore = (white_to_move ? Material : -Material) + 500;
    
      if (!extend && depth == 2 && fscore <= alpha)
	{
	  selective = 1;
	  best_score = fmax = fscore;
	}
    
      fscore = (white_to_move ? Material : -Material) + ((Variant == Normal) ? 150 : 200);
    
      if (!extend && depth == 1 && fscore <= alpha)
	{
	  selective = 1;
	  best_score = fmax = fscore;
	}
    }   
  
  if (num_moves > 0)
    {
      order_moves (&moves[0], &move_ordering[0], &see_values[0], num_moves, best);
      
      /* loop through the moves at the current node: */
      while (remove_one (&i, &move_ordering[0], num_moves)) {
      
	make (&moves[0], i);
   
	legal_move = FALSE;
      
	hash_history[move_number+ply-1] = hash;
	path[ply-1] = moves[i];
      
	extend = 0; /* dont extend twice */
      
	/* go deeper if it's a legal move: */
      
	if (check_legal (&moves[0], i, incheck)) {
      
	  afterincheck = f_in_check(&moves[0], i);
	  checks[ply] = afterincheck;
	
	  if (!afterincheck && ((Variant == Normal) 
		             || (Variant == Suicide) 
			     || (Variant == Losers)) && (depth < 3) &&
	      ((((board[moves[i].target] == wpawn) && (rank(moves[i].target) >= 6))
		|| ((board[moves[i].target] == bpawn) && (rank(moves[i].target) <= 3)))))
	    {
	      extend++;
	    };
	
	  dropcut = 0;
	
	  /* Razoring of uninteresting drops */
	  if ((moves[i].from == 0)
	      && (depth > 1)           /* more than pre-frontier nodes */
	      && (afterincheck == 0)   /* not a contact checking move */
	      && (incheck == 0)        /* not a check evasion */
	      && !searching_pv
	      && cfg_razordrop
	      )
	    { razor_drop++; extend--;}
	  else
	    {
	      if ((moves[i].from == 0) && (depth == 1) && (incheck == 0) && cfg_cutdrop) 
		{
		  if (white_to_move)
		    {
		      dropcut = (calc_attackers(moves[i].target, 1) 
				 - calc_attackers(moves[i].target, 0)) > 0;
		      if (dropcut) drop_cuts++;
		    }
		  else
		    {
		      dropcut = (calc_attackers(moves[i].target, 0)
				 - calc_attackers(moves[i].target, 1)) > 0;
		      if (dropcut) drop_cuts++;
		    }
		}

	    }
	
	  if (!dropcut && (!selective || (afterincheck != 0) 
			   || (fmax + ((abs(material[moves[i].captured]) * 
				 ((Variant == Normal || Variant == Losers)?1:2)
				 )) > alpha) 
			   || (moves[i].promoted))) 
	    {
	      if (first == TRUE)
		{ 
		  score = -search (-beta, -alpha, depth+extend-1, NONE);
		  FULL++;
		}
	      else
		{
		  score = -search (-alpha-1, -alpha, depth+extend-1, NONE);
		  PVS++;
		    
		  if (score > best_score && !time_exit && score != -KINGCAP)
		    {
		      if ((score > alpha) && (score < beta))
			{
			  score = -search(-beta, -alpha, depth+extend-1, NONE);
			  PVSF++;
			    
			  if (score > best_score) best_score = score;
			}
		      else	
			best_score = score;
		    }
		}

	      legal_move = TRUE;
	    
	    }
	  else
	    razor_material++;
	
	
	  legalmoves++;
	  no_moves = FALSE;
	}

	if (score > best_score && legal_move)
	  {
	    best_score = score;
	  };
      
	unmake (&moves[0], i);
      
	/* return if we've run out of time: */
	if (time_exit) return 0;
      
	/* check our current score vs. alpha: */
	if (score > alpha && legal_move) {
	
	  /* try for an early cutoff: */
	  if (score >= beta) {
	  
	    /* update the history heuristic since we have a cutoff: */
	    history_h[moves[i].from][moves[i].target]++;
	  
	    if (moves[i].captured == npiece)
	      {
		/* we have a cutoff, so update our killers: */
		/* first, check whether it matches one of the known killers */
		if (moves[i].from == killer1[ply].from && moves[i].target ==
		    killer1[ply].target && moves[i].promoted == killer1[ply].promoted)
		  {
		    killer_scores[ply]++;
		  }
		else if (moves[i].from == killer2[ply].from && moves[i].target ==
			 killer2[ply].target && moves[i].promoted == killer2[ply].promoted)
		  {
		    killer_scores2[ply]++;
		    
		    if (killer_scores2[ply] > killer_scores[ply])
		      {
			kswap = killer1[ply];
			killer1[ply] = killer2[ply];
			killer2[ply] = kswap;		
			ksswap = killer_scores[ply];
			killer_scores[ply] = killer_scores2[ply];
			killer_scores2[ply] = ksswap;
		      }
		  }
		
		else if (moves[i].from == killer3[ply].from && moves[i].target ==
			 killer3[ply].target && moves[i].promoted == killer3[ply].promoted)
		  {
		    
		    killer_scores3[ply]++;
		    
		    if (killer_scores3[ply] > killer_scores2[ply])
		      {
			kswap = killer2[ply];
			killer2[ply] = killer3[ply];
			killer3[ply] = kswap;		
			ksswap = killer_scores2[ply];
			killer_scores2[ply] = killer_scores3[ply];
			killer_scores3[ply] = ksswap;
		      }
		  }
		/* if not, replace killer3 */
		else
		  {
		    killer_scores3[ply] = 1;
		    killer3[ply] = moves[i];
		  }
	      }
	  
	    if (first == TRUE) FHF++;
	  
	    FH++;
	  
	    StoreTT(score, originalalpha, beta, i, threat, depth);
	  
	    return score;
	  }
	
	  alpha = score;
	
	  sbest = i;

	  /* update the pv: */
	  pv[ply][ply] = moves[i];
	  for (j = ply+1; j < pv_length[ply+1]; j++)
	    pv[ply][j] = pv[ply+1][j];
	  pv_length[ply] = pv_length[ply+1];
	}
      
	if (legal_move)
	  first = FALSE;
      
      }
    }
  else
    {
      /* no generated moves..only happens in suicide */
      StoreTT(INF-ply, originalalpha, beta, 0, threat, depth);
      return INF-ply;
    }

  /* check for mate / stalemate: */
  if (no_moves) 
    {
      if (Variant != Losers && Variant != Suicide)
      {
      	if (in_check ()) 
	{
	  StoreTT(-INF+ply, originalalpha, beta, 0, threat, depth);
	  return (-INF+ply);
	}
      else 
	{
	  StoreTT(0, originalalpha, beta, 0, threat, depth);
	  return 0;
	}
      }
      else
      {
	  StoreTT(INF-ply, originalalpha, beta, 0, threat, depth);
	  return (INF-ply);
      }
    }
  else
    {
      if (fifty > 100) 
	{
	  return 0;
	}	
    };
  
  if (sbest == -1) sbest = 500;

  if (best_score <= originalalpha)
    {
      if (!selective)
	StoreTT(best_score, originalalpha, beta, sbest, threat, depth);
    }
  else 
    {
      if (!selective)
	StoreTT(best_score, originalalpha, beta, sbest, threat, depth);
      else
	StoreTT(best_score, -INF, -INF, sbest, threat, depth);/*store lowbound*/
    }
 
  return best_score;

}


move_s search_root (int originalalpha, int originalbeta, int depth) {

  /* search the root node using alpha-beta with negamax search */

  move_s moves[MOVE_BUFF], best_move = dummy;
  int num_moves, i, j;
  int root_score = -INF, move_ordering[MOVE_BUFF], see_values[MOVE_BUFF];
  xbool no_moves, legal_move, first;
  int alpha, beta;
  move_s kswap;
  int ksswap;
  int incheck;
  int mc = 0;
  int oldnodecount;
  
  alpha = originalalpha;
  beta = originalbeta;

  num_moves = 0;
  no_moves = TRUE;
  ply = 1;
  searching_pv = TRUE;
  time_exit = FALSE;
  time_failure = FALSE;
  first = TRUE;
  cur_score = -INF;
  nodes++;
  
  /* check for a draw by 3 fold repetition: */
  if (is_draw ()) 
    {
      result = draw_by_rep;
      cur_score = 0;
      pv_length[ply] = 0;
      return (dummy);
    };
  
  pv_length[ply] = ply;
  hash_history[move_number+ply-1] = hash; 
  
  /*check extensions: */
 
  incheck = in_check ();
  
  if (incheck) 
  {
    ext_check++;
    depth++;
  };

  checks[ply] = incheck;
  recaps[ply] = 0;
  singular[ply] = 0;
  
  if (Variant == Losers)
    { 
      legals = 0;
      captures = TRUE;
      gen (&moves[0]);
      num_moves = numb_moves;
      captures = FALSE; 
									               
      if (num_moves)
	{
	  for (i = 0; i < num_moves; i++)
	    {
	      make(&moves[0], i);
	      if (check_legal(&moves[0], i, incheck))
		{
		  legals++;
		}
	      unmake(&moves[0], i);
	    }
	}  
      
      if (!legals) 
	{
	  captures = FALSE;
	  gen(&moves[0]);
	  num_moves = numb_moves;

	  for (i = 0; i < num_moves; i++)
	    {
	      make(&moves[0], i);
	      if (check_legal(&moves[0], i, incheck))
		{
		  legals++;
		}
	      unmake(&moves[0], i);					               
	    }
	};                 
    }   
  else
    {
      /* generate and order moves: */
      
      gen (&moves[0]);
      num_moves = numb_moves;
    }
  
  movetotal = legals;

  order_moves (&moves[0], &move_ordering[0], &see_values[0], num_moves, -1);
  
  /* loop through the moves at the root: */
  while (remove_one (&i, &move_ordering[0], num_moves)) {
  
    if (!alllosers && rootlosers[i] && ((Variant == Losers) || (Variant == Suicide))) continue;
    
    make (&moves[0], i);
    legal_move = FALSE;

    hash_history[move_number+ply-1] = hash;
    path[ply-1] = moves[i];
    
    oldnodecount = nodes;
    
    /* go deeper if it's a legal move: */
    if (check_legal (&moves[0], i, incheck)) {
  
      unmake(&moves[0], i);
      mc++;
      moveleft = movetotal - mc;
      comp_to_san(moves[i], searching_move);
      make(&moves[0], i);
  
      checks[ply] = f_in_check(&moves[0], i);
      recaps[ply] = 0;
      singular[ply] = 0;
      
      if ((first == TRUE) || (i_depth < 2))
	{
	  root_score = -search (-beta, -alpha, depth-1, NONE);

	  if (!time_exit && (post || !xb_mode) && i_depth >= mindepth) 
	    {
	      if (root_score >= beta)
		{		  
		  post_fh_thinking(root_score, &moves[i]);
		}
	      else if (root_score <= alpha)
		{
		  failed = 1;

		  post_fl_thinking(root_score, &moves[i]);
		}
	      else
		{
		  /* update the pv: */
		  pv[ply-1][ply-1] = moves[i];
		  for (j = ply; j < pv_length[ply]; j++)
		    pv[ply-1][j] = pv[ply][j];
		  pv_length[ply-1] = pv_length[ply];

		  post_thinking(root_score);
		}
	      
	       if (root_score > cur_score && !time_exit)
	       {
	          cur_score = root_score;
		  bestmovenum = i;
		  best_move = moves[i];
	       }
			    
	    }
	}
      else
	{
	  root_score = -search (-alpha-1, -alpha, depth-1, NONE);

	  if ((root_score > alpha) && (root_score < beta) && !time_exit)
	    {
              post_fail_thinking(root_score, &moves[i]); 	      
	
	      root_score = -search(-beta, -alpha, depth-1, NONE);
	      
	      if (root_score > alpha && !time_exit)
	      {
		  failed = 0;
		
		  cur_score = root_score;
		  bestmovenum = i;
		  best_move = moves[i];
	          
		  if (root_score < beta && i_depth >= mindepth)
	      	  {
		      /* update the pv: */
		      pv[ply-1][ply-1] = moves[i];
		      for (j = ply; j < pv_length[ply]; j++)
		        pv[ply-1][j] = pv[ply][j];
		      pv_length[ply-1] = pv_length[ply];
		  }
	      }
	   }

	  if (root_score >= beta && !time_exit) 
	    post_fh_thinking(root_score, &moves[i]);
	}

    if (root_score > cur_score && !time_exit) 
	{
	  cur_score = root_score;
	  bestmovenum = i;
	  best_move = moves[i];
	}
      
      /* check to see if we've aborted this search before we found a move: 
       * or a failed search <- removed 2000-5-28
       * we should use the fail-highs
       * and the fail-lows are handled in think */   
    if (time_exit && (cur_score == -INF))
      {
	if (no_moves)
	  time_failure = TRUE;
      }
    
    no_moves = FALSE;
    legal_move = TRUE;
    
    }
    
    unmake (&moves[0], i);

    /* if we've run out of time, return the best we have so far: */
    if (time_exit)
      return best_move;

    /* check our current score vs. alpha: */
    if (root_score > alpha && legal_move) {

       /* we have a cutoff, so update our killers: */
      /* first, check whether it matches one of the known killers */
      if (moves[i].from == killer1[ply].from && moves[i].target ==
	 killer1[ply].target && moves[i].promoted == killer1[ply].promoted)
	{
	  killer_scores[ply]++;
	}
      else if (moves[i].from == killer2[ply].from && moves[i].target ==
	killer2[ply].target && moves[i].promoted == killer2[ply].promoted)
	{
	  killer_scores2[ply]++;
		
	  if (killer_scores2[ply] > killer_scores[ply])
	    {
	      kswap = killer1[ply];
	      killer1[ply] = killer2[ply];
	      killer2[ply] = kswap;		
	      ksswap = killer_scores[ply];
	      killer_scores[ply] = killer_scores2[ply];
	      killer_scores2[ply] = ksswap;
	    }
	}
      else if (moves[i].from == killer3[ply].from && moves[i].target ==
	       killer3[ply].target && moves[i].promoted == killer3[ply].promoted)
	{
	  killer_scores3[ply]++;
	  
	  if (killer_scores3[ply] > killer_scores2[ply])
	    {
	      kswap = killer2[ply];
	      killer2[ply] = killer3[ply];
	      killer3[ply] = kswap;		
	      ksswap = killer_scores2[ply];
	      killer_scores2[ply] = killer_scores3[ply];
	      killer_scores3[ply] = ksswap;
	    }
	}
	/* if not, replace killer3 */
	else
	{
	  killer_scores3[ply] = 1;
	  killer3[ply] = moves[i];
	}

      /* update the history heuristic since we have a cutoff: */
      history_h[moves[i].from][moves[i].target]++;

      alpha = root_score;
      best_move = moves[i];
      bestmovenum = i;
      cur_score = alpha;
      
      /* update the pv: */
      pv[ply][ply] = moves[i];
      for (j = ply+1; j < pv_length[ply+1]; j++)
	pv[ply][j] = pv[ply+1][j];
      pv_length[ply] = pv_length[ply+1];
      
      if (cur_score >= beta) return best_move;

      /* print out thinking information: */
      if (post && i_depth >= mindepth) {
	post_thinking (alpha);
      }
    }
    if (legal_move)
      first = FALSE;

    rootnodecount[i] = nodes - oldnodecount;
  }

  /* check to see if we are mated / stalemated: */
  if (no_moves && !is_pondering) 
  {
    if (Variant != Suicide && Variant != Losers)
      {
	if (in_check ()) {
	  if (white_to_move == 1) {
	    result = white_is_mated;
	  }
	  else {
	    result = black_is_mated;
	  }
	}
	else {
	  result = stalemate;
	}
      }
    else
      {
	if (white_to_move == 1) {
	  result = black_is_mated;
	}
	else {
	  result = white_is_mated;
	}
      }
  }
  else if (!is_pondering)
  {
    /* check for draw by the 50 move rule: */
    if (fifty > 100) 
    {
    	result = draw_by_fifty;
	cur_score = 0;
	pv_length[ply] = 0;
	return dummy;
    }
  }

  return best_move;

}


move_s think (void) {

  /* Perform iterative deepening to go further in the search */
  
  move_s comp_move, temp_move, old_move;
  int i, j, k = 0;
  int elapsed, temp_score = 0, true_score;
  int alpha, beta;
  int tmptmp;
  int rs;
  move_s moves[MOVE_BUFF];
  int l, lastlegal = 0, ic;
  int pn_restart;
  int num_moves;
  char output[8];
  
  userealholdings = 0;
  pn_restart = 0;
restart:
  nodes = 0;
  qnodes = 0;
  ply = 1;

  ECacheProbes = 0;
  ECacheHits = 0;
  TTProbes = 0;
  TTHits = 0;
  TTStores = 0;  
  NCuts = 0;
  NTries = 0;
  TExt = 0;
  FH = 0;
  FHF = 0;
  PVS = 0;
  FULL = 0;
  PVSF = 0;
  ext_check = 0;
  ext_recap = 0;
  ext_onerep = 0;
  razor_drop = 0;
  razor_material = 0;
  drop_cuts = 0;
  rs = 0;
  extendedtime = 0;
  forcedwin = 0;
  maxposdiff = 200;
  
  true_i_depth = 0;
  bestmovenum = -1;

  /* Don't do anything if the queue isn't clean */
  /* PGC: only safe if we're not playing...else partner tells screw us up */
  if (interrupt() && (is_analyzing || is_pondering)) return dummy;
  
 
  start_time = rtime ();

  /* we need to know if we must sit or not in bug */ 
  legals = 0;

  if (Variant == Losers) captures = TRUE;
  else captures = FALSE;
  gen(&moves[0]);
  num_moves = numb_moves;
  
  ic = in_check();
  
  for (l = 0; l < numb_moves; l++)
    {
      make(&moves[0],l);
      if (check_legal(&moves[0], l, ic))
	{
	  legals++;
	  lastlegal = l;
	}
      unmake(&moves[0],l);
    }

  if (Variant == Losers && legals == 0)
    {
      captures = FALSE;
      num_moves = 0;
      gen(&moves[0]);
      num_moves = numb_moves;

      for (l = 0; l < numb_moves; l++)
	{
	  make(&moves[0],l);
	  if (check_legal(&moves[0], l, ic))
	    {
	      legals++;
	      lastlegal = l;
	    }
	  unmake(&moves[0],l);
	}
    };                       
  
  if (Variant != Bughouse && !is_pondering)
    {
      if (legals == 1)
      {
	time_cushion += (inc*100);
	return moves[lastlegal];
      }
    }

  
   check_phase();

   switch(phase)
     {
     case Opening :
       printf("Opening phase.\n");
       break;
     case Middlegame :
       printf("Middlegame phase.\n");
       break;
     case Endgame :
       printf("Endgame phase.\n");
       break;
     }
   
   /* allocate our time for this move: */

   if (!is_pondering)
     {
       if (!fixed_time)
	 {
	   if (go_fast)
	     {
	       tmptmp = allocate_time();
	       
	       if (tmptmp > 40)
	       {
		 time_for_move = 40;
	       }
	       else
	       {
		 time_for_move = tmptmp;
	       }
	     }
	   else
	     {
	       time_for_move = allocate_time ();
	     }	
	 }
       else
	 {
	   time_for_move = fixed_time;
	 }
     }
   else
     {
       time_for_move = 999999;
     };

   if (pn_restart) time_for_move = (int)((float)time_for_move 
		   * (float)2/((float)pn_restart+1.0));
   
   printf("Time for move : %d\n", time_for_move);
   
   if (!pn_restart)
   {  
     /* for SPEC, force reproducible searches */
     clear_tt();
     reset_ecache();
     memset(rootlosers, 0, sizeof(rootlosers));
   }
   
   if (!pn_restart && !is_pondering && ((Variant == Suicide) || (Variant == Losers)) 
       && (piece_count > 3 || (Variant != Suicide)))
     { 
       pn_time = (int)((float)(time_for_move) * 1.0/3.0);
       proofnumberscan();
     }
   else if (!pn_restart)
     pn_move = dummy;
   
  if (result && pn_move.target == dummy.target)
    return pn_move;
  
  if ((forcedwin || result) && (pn_move.target != dummy.target) 
      && !is_analyzing)
    {
      comp_move = pn_move;
    }
  else 
     {
       /* clear the pv before a new search: */
       for (i = 0; i < PV_BUFF; i++)
	 for (j = 0; j < PV_BUFF; j++)
	   pv[i][j] = dummy;
       
       /* clear the history heuristic: */
       memset (history_h, 0, sizeof (history_h));
       
       /* clear the killer moves: */
       for (i = 0; i < PV_BUFF; i++) {
	 killer_scores[i] = 0;
	 killer_scores2[i] = 0;
	 killer_scores3[i] = 0;
	 killer1[i] = dummy;
	 killer2[i] = dummy;
	 killer3[i] = dummy;
       }
       
       memset(rootnodecount, 0, sizeof(rootnodecount));
       
       temp_score = 0;
       cur_score = 0;
       true_score = 0;
       
       for (i_depth = 1; i_depth <= maxdepth; i_depth++) {
	 
	 /* don't bother going deeper if we've already used 2/3 of our time, and we
	    haven't finished our mindepth search, since we likely won't finsish */
	 elapsed = rdifftime (rtime (), start_time);
	 if (elapsed > time_for_move*2.1/3.0 && i_depth > mindepth)
	   break;
	 
	 failed = 0;
	 
	 alpha = temp_score - (Variant == Normal ? 35 : 100);
	 beta = temp_score + (Variant == Normal ? 35 : 100);

	 temp_move = search_root (alpha, beta, i_depth);

         if (result) break;
	 
	 if (cur_score <= alpha) failed = 1;
	 else failed = 0;
	 
	 if (cur_score <= alpha && !time_exit) /* fail low */
	   {	     	     	     
	     rs++;
	     
	     temp_move = search_root (-INF, INF, i_depth);
	     if (!time_exit) failed = 0;
	       
	   }
	 else if (cur_score >= beta && !time_exit) /* fail high */
	   {
	     comp_move = temp_move;
	     temp_score = cur_score;	     	     
	     
	     rs++;
	     
	     temp_move = search_root (-INF, INF, i_depth);
	     if (!time_exit) failed = 0;
	     
	   };
	 
	 
	 if (interrupt() && (i_depth > 1)) 
	   {
	     if (is_pondering)
	       return dummy;
	     else if (!go_fast)
	       break;
	   }
	 
	 /* if we haven't aborted our search on time, set the computer's move
	    and post our thinking: */
	 if (!time_failure && !failed) {
	   /* if our search score suddenly drops, and we ran out of time on the
	      search, just use previous results */

	   /* accidentally pondering if mated */
	   if (cur_score == -INF) return dummy;
	   
	   comp_move = temp_move;
	   temp_score = cur_score;
		  
	   stringize_pv(postpv);
	   
	   if (!time_exit)
	     {
	       true_i_depth = i_depth;
	     }      
	   
	   if (i_depth >= mindepth)
	     post_thinking (cur_score);
	   
	   if (temp_score > 900000 && ((int)(1000000-cur_score) < i_depth))
	     {
	       break;
	     };
	 }

	 if (time_exit) break;

	 /* reset the killer scores (we can keep the moves for move ordering for
	    now, but the scores may not be accurate at higher depths, so we need
	    to reset them): */
	 for (j = 0; j < PV_BUFF; j++) {
	   killer_scores[j] = 0;
	   killer_scores2[j] = 0;
	   killer_scores3[j] = 0;
	 }
	 
       }
     }
       

  if (!forcedwin)
  {

    old_move = comp_move;
    
    if ((Variant == Losers || Variant == Suicide) && !result && !alllosers && !is_pondering)
    {
      s_threat = FALSE;
      
      comp_move = proofnumbercheck(comp_move);

      if ((pn_restart < 10) && (s_threat))
      {
	/* a/b loser */
	pn_restart++;

	/* mark loser */
	for (i = 0; i < num_moves; i++)
	{
	  if (moves[i].from == old_move.from && moves[i].target == old_move.target
	      && moves[i].promoted == old_move.promoted)
	  {
	    rootlosers[i] = TRUE;
	    break;
	  }
	}
	for (j = 0; j < num_moves; j++)
	{
	    if (rootlosers[j]) k++;  
	}

	if (k == legals) alllosers = TRUE;
	
	goto restart;
      }
    }
  };

  if (alllosers) comp_move = old_move;

  if (pn_restart != 0 && xb_mode)
  {
    comp_to_san(comp_move, output);
    printf("tellics whisper %d restart(s), ended up with %s\n", pn_restart, output);
    result = 0;
  }
  elapsed = rdifftime (rtime (), start_time);
  
  /*printf("Used time : %.2f\n", elapsed/100.0);*/
  time_left -= elapsed;
  
  /* update our elapsed time_cushion: */
  if (moves_to_tc && !is_pondering) {
    time_cushion += time_for_move-elapsed+inc;
  }
  
  
  if (temp_score == INF-2 && !is_pondering/* && pn_move.target == dummy.target*/) 
    {
      if (white_to_move == 1) 
	{
	  result = black_is_mated;
	}
      else 
	{
	  result = white_is_mated;
	}
  }
  else if (temp_score == -(INF-2) && !is_pondering/* && pn_move.target == dummy.target*/)
    {
      if (white_to_move == 1)
	{
	  result = white_is_mated;
	}
      else
	{
	  result = black_is_mated;
	}
    }
  
  
  if (post && xb_mode && !is_pondering && 
	result != black_is_mated &&
	result != white_is_mated &&
        result != draw_by_fifty && result != draw_by_rep &&
	result != stalemate && !forcedwin)
    {
      if (temp_score > INF-400) 
	{
	  if (Variant != Bughouse)
	  {
	    printf("tellics kibitz Mate in %d\n", (int)((1000000-temp_score)/2));
	  }
	  else
	  {
	    printf("tellics ptell Mate in %d, give him no more pieces.\n", (int)((1000000-temp_score)/2));
	  }
	 }
    }

  
  if ((result != white_is_mated) 
      && (result != black_is_mated)
      && (result != stalemate)
      && (result != draw_by_fifty) && (result != draw_by_rep)
      && (true_i_depth >= 3) 
      && pn_move.target == dummy.target 
      && !is_pondering
      && (Variant != Bughouse))
    {
      if (bestmovenum == -1) DIE;
    }

  if ((Variant == Bughouse) && temp_score > -999900)
  {
    if (tradefreely == 0 && !userealholdings)
    {
      tradefreely = 1;
      printf("tellics ptell You can trade freely.\n");
    }
  }
  else if ((temp_score < -999900) && (Variant == Bughouse) && pn_move.target == dummy.target)
    {
	if (userealholdings)
	{
            must_sit = TRUE;
	}
	else
	{
	    userealholdings = 1;
	    ProcessHoldings(realholdings);
	    tradefreely = 0;
	    printf("tellics ptell ---trades\n");
	    goto restart;
	}

      
      /* shut up if the mate is already played */
      if (temp_score > -1000000)
	{
	  if (partnerdead)
	    {
	      printf("tellics kibitz Both players dead...resigning...\n");
	      printf("tellics resign\n");
	    }
	  else
	    {
	      printf("tellics ptell I am forcedly mated (dead). Tell me 'go' to start moving into it.\n");
	    }
	}
    }
  else if ((temp_score > -60000) && (temp_score < -40000) && (Variant == Bughouse) && !partnerdead && pn_move.target == dummy.target)
    {
      must_sit = TRUE;
      printf("tellics ptell I'll have to sit...(lose piece that mates you)\n");
    }

  return comp_move;

}


void tree (int depth, int indent, FILE *output, char *disp_b) {

  move_s moves[MOVE_BUFF];
  int num_moves, i, j;
  int ic;

  num_moves = 0;

  /* return if we are at the maximum depth: */
  if (!depth) {
    return;
  }

  /* generate the move list: */
  gen (&moves[0]);
  num_moves = numb_moves;

  ic = in_check();

  /* loop through the moves at the current depth: */
  for (i = 0; i < num_moves; i++) {
    make (&moves[0], i);

    /* check to see if our move is legal: */
    if (check_legal (&moves[0], i, ic)) {
      /* indent and print out our line: */
      for (j = 0; j < indent; j++) {
	fputc (' ', output);
      }
      print_move (&moves[0], i, output);
      fprintf (output, "\n");

      /* display board if desired: */
      if (disp_b[0] == 'y')
	display_board (output, 1);

      /* go deeper into the tree recursively, increasing the indent to
	 create the "tree" effect: */
      tree (depth-1, indent+2, output, disp_b);
    }

    /* unmake the move to go onto the next: */
    unmake(&moves[0], i);
  }
}

