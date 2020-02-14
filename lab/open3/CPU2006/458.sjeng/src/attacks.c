/*
    Sjeng - a chess variants playing program
    Copyright (C) 2000-2003 Gian-Carlo Pascutto

    File: attacks.c                                             
    Purpose: calculate attack information                      
 
*/

#include "sjeng.h"
#include "protos.h"
#include "extvars.h"

int calc_attackers (int square, int color) {

  /* this function calculates attack information for a square */

  static const int rook_o[4] = {12, -12, 1, -1};
  static const int bishop_o[4] = {11, -11, 13, -13};
  static const int knight_o[8] = {10, -10, 14, -14, 23, -23, 25, -25};
  int a_sq, i;

  int attackers = 0;

  if (board[square] == frame) return 0;

  /* white attacker: */
  if (color%2) {
    /* rook-style moves: */
    for (i = 0; i < 4; i++) {
      a_sq = square + rook_o[i];

      /* the king can attack from one square away: */
      if (board[a_sq] == wking) 
	{	  
	  attackers++;
	  break;
	}
      else
	{
	  /* otherwise, check for sliding pieces: */
	  while (board[a_sq] != frame) {
	    if (board[a_sq] == wrook || board[a_sq] == wqueen) 
	      {
		attackers++;
		break;
	      }
	    else if (board[a_sq] != npiece) break;
	    a_sq += rook_o [i];
	  }
	}
    }

    /* bishop-style moves: */
    for (i = 0; i < 4; i++) {
      a_sq = square + bishop_o[i];
      /* check for pawn attacks: */
      if (board[a_sq] == wpawn && i%2)
	{
	  attackers++;
	  break;
	}
      /* the king can attack from one square away: */
      else if (board[a_sq] == wking)
	{
	  attackers++;
	  break;
	}
      else
	{
	  while (board[a_sq] != frame) {
	    if (board[a_sq] == wbishop || board[a_sq] == wqueen) 
	      {
		attackers++;
		break;
	      }
	    else if (board[a_sq] != npiece) break;
	    a_sq += bishop_o [i];
	  }
	}
    }

    /* knight-style moves: */
    for (i = 0; i < 8; i++) {
      a_sq = square + knight_o[i];
      if (board[a_sq] == wknight)
	  attackers++;
	  
    }

    /* if we haven't hit a white attacker by now, there are none: */

  }

  /* black attacker: */
  else {
    /* rook-style moves: */
    for (i = 0; i < 4; i++) {
      a_sq = square + rook_o[i];
      /* the king can attack from one square away: */
      if (board[a_sq] == bking)
	{
	  attackers++;
	  break;
	}
      /* otherwise, check for sliding pieces: */
      else {
	while (board[a_sq] != frame) {
	  if (board[a_sq] == brook || board[a_sq] == bqueen) 
	    {
	      attackers++;
	      break;
	    };
	  if (board[a_sq] != npiece) break;
	  a_sq += rook_o [i];
	}
      }
    }

    /* bishop-style moves: */
    for (i = 0; i < 4; i++) {
      a_sq = square + bishop_o[i];
      /* check for pawn attacks: */
      if (board[a_sq] == bpawn && !(i%2))
	{
	  attackers++;
	  break;
	}
      /* the king can attack from one square away: */
      else if (board[a_sq] == bking)
	{
	  attackers++;
	  break;
	}
      else {
	while (board[a_sq] != frame) {
	  if (board[a_sq] == bbishop || board[a_sq] == bqueen) 
	    {
	      attackers++;
	      break;
	    }
	  else if (board[a_sq] != npiece) break;
	  a_sq += bishop_o [i];
	}
      }
    }

    /* knight-style moves: */
    for (i = 0; i < 8; i++) {
      a_sq = square + knight_o[i];
      if (board[a_sq] == bknight) 
	attackers++;
    }

    /* if we haven't hit a black attacker by now, there are none: */
  }

  return attackers;

}

xbool is_attacked (int square, int color) {

  /* this function will return TRUE if square "square" is attacked by a piece
     of color "color", and return FALSE otherwise */

  static const int rook_o[4] = {12, -12, 1, -1};
  static const int bishop_o[4] = {11, -11, 13, -13};
  static const int knight_o[8] = {10, -10, 14, -14, 23, -23, 25, -25};
  register int ndir, a_sq;
  register int basq, i;

  /* white attacker: */
  if (color&1) {
    
    /* bishop-style moves: */
    for (i = 0; i < 4; i++) {
      ndir = bishop_o[i];
      a_sq = square+ndir;
      basq = board[a_sq];
      /* check for pawn attacks: */
      if (basq == wpawn && (i&1)) return TRUE;
      /* the king can attack from one square away: */
      if (basq == wking) return TRUE;
      while (basq != frame) {
	if (basq == wbishop || basq == wqueen) return TRUE;
	if (basq != npiece) break;
	a_sq += ndir;
	basq = board[a_sq];
      }
    }
    /* knight-style moves: */
    for (i = 0; i < 8; i++) {
      if (board[square + knight_o[i]] == wknight) return TRUE;
    }
    /* rook-style moves: */
    for (i = 0; i < 4; i++) {
      ndir = rook_o[i];
      a_sq = square + ndir;
      basq = board[a_sq];
      /* the king can attack from one square away: */
      if (basq == wking) return TRUE;
      /* otherwise, check for sliding pieces: */
      while (basq != frame) {
	if (basq == wrook || basq == wqueen) return TRUE;
	if (basq != npiece) break;
	a_sq += ndir;
	basq = board[a_sq];
      }
    }

    /* if we haven't hit a white attacker by now, there are none: */
    return FALSE;

  }

  /* black attacker: */
  else {
      /* bishop-style moves: */
    for (i = 0; i < 4; i++) {
      ndir = bishop_o[i];
      a_sq = square + ndir;
      basq = board[a_sq];
      /* check for pawn attacks: */
      if (basq == bpawn && !(i&1)) return TRUE;
      /* the king can attack from one square away: */
      if (basq == bking) return TRUE;
      while (basq != frame) {
	if (basq == bbishop || basq == bqueen) return TRUE;
	if (basq != npiece) break;
	a_sq += ndir;
	basq = board[a_sq];
      }
    }

    /* knight-style moves: */
    for (i = 0; i < 8; i++) {
      if (board[square + knight_o[i]] == bknight) return TRUE;
    }

    /* rook-style moves: */
    for (i = 0; i < 4; i++) {
      ndir = rook_o[i];
      a_sq = square + rook_o[i];
      basq = board[a_sq];
      /* the king can attack from one square away: */
      if (basq == bking) return TRUE;
      /* otherwise, check for sliding pieces: */
      while (basq != frame) {
	if (basq == brook || basq == bqueen) return TRUE;
	if (basq != npiece) break;
	a_sq += ndir;
	basq = board[a_sq];
      }
    }

    /* if we haven't hit a black attacker by now, there are none: */
    return FALSE;

  }

}

xbool nk_attacked (int square, int color) {

  /* this function will return TRUE if square "square" is attacked by a piece
     of color "color", and return FALSE otherwise */

  static const int rook_o[4] = {12, -12, 1, -1};
  static const int bishop_o[4] = {11, -11, 13, -13};
  static const int knight_o[8] = {10, -10, 14, -14, 23, -23, 25, -25};
  register int ndir, a_sq;
  register int basq, i;

  /* white attacker: */
  if (color&1) {
    
    /* bishop-style moves: */
    for (i = 0; i < 4; i++) {
      ndir = bishop_o[i];
      a_sq = square+ndir;
      basq = board[a_sq];
      /* check for pawn attacks: */
      if (basq == wpawn && (i&1)) return TRUE;
      /* the king can attack from one square away: */
      while (basq != frame) {
	if (basq == wbishop || basq == wqueen) return TRUE;
	if (basq != npiece) break;
	a_sq += ndir;
	basq = board[a_sq];
      }
    }
    /* knight-style moves: */
    for (i = 0; i < 8; i++) {
      if (board[square + knight_o[i]] == wknight) return TRUE;
    }
    /* rook-style moves: */
    for (i = 0; i < 4; i++) {
      ndir = rook_o[i];
      a_sq = square + ndir;
      basq = board[a_sq];
      /* otherwise, check for sliding pieces: */
      while (basq != frame) {
	if (basq == wrook || basq == wqueen) return TRUE;
	if (basq != npiece) break;
	a_sq += ndir;
	basq = board[a_sq];
      }
    }

    /* if we haven't hit a white attacker by now, there are none: */
    return FALSE;

  }

  /* black attacker: */
  else {
      /* bishop-style moves: */
    for (i = 0; i < 4; i++) {
      ndir = bishop_o[i];
      a_sq = square + ndir;
      basq = board[a_sq];
      /* check for pawn attacks: */
      if (basq == bpawn && !(i&1)) return TRUE;
      /* the king can attack from one square away: */
      while (basq != frame) {
	if (basq == bbishop || basq == bqueen) return TRUE;
	if (basq != npiece) break;
	a_sq += ndir;
	basq = board[a_sq];
      }
    }

    /* knight-style moves: */
    for (i = 0; i < 8; i++) {
      if (board[square + knight_o[i]] == bknight) return TRUE;
    }

    /* rook-style moves: */
    for (i = 0; i < 4; i++) {
      ndir = rook_o[i];
      a_sq = square + rook_o[i];
      basq = board[a_sq];
      /* otherwise, check for sliding pieces: */
      while (basq != frame) {
	if (basq == brook || basq == bqueen) return TRUE;
	if (basq != npiece) break;
	a_sq += ndir;
	basq = board[a_sq];
      }
    }

    /* if we haven't hit a black attacker by now, there are none: */
    return FALSE;

  }

}

