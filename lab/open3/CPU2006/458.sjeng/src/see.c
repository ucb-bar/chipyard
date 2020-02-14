/*
    Sjeng - a chess variants playing program
    Copyright (C) 2001-2003 Gian-Carlo Pascutto

    File: see.c                                             
    Purpose: do static exchange evaluation                      
 
*/

#include "sjeng.h"
#include "extvars.h"

typedef struct
{
  int piece;
  int square;
} see_data;

see_data see_attackers[2][16];
int see_num_attackers[2];

void setup_attackers (int square) {

  /* this function calculates attack information for a square */

  static const int rook_o[4] = {12, -12, 1, -1};
  static const int bishop_o[4] = {11, -11, 13, -13};
  static const int knight_o[8] = {10, -10, 14, -14, 23, -23, 25, -25};
  register int a_sq, b_sq, i;
  int numw = see_num_attackers[WHITE], numb = see_num_attackers[BLACK];
  
  /* rook-style moves: */
  for (i = 0; i < 4; i++) 
    {
      a_sq = square + rook_o[i];
      b_sq = board[a_sq];
      
      /* the king can attack from one square away: */
      if (b_sq == wking) 
	{
	  see_attackers[WHITE][numw].piece = b_sq;
	  see_attackers[WHITE][numw].square = a_sq;
	  numw++;
	  break;
	}
      else if (b_sq == bking)
	{
	  see_attackers[BLACK][numb].piece = b_sq;
	  see_attackers[BLACK][numb].square = a_sq;
	  numb++;
	  break;
	}
      else
	{
	  /* otherwise, check for sliding pieces: */
	  while (b_sq != frame) 
	    {
	      if (b_sq == wrook || b_sq == wqueen) 
		{
		  see_attackers[WHITE][numw].piece = b_sq;
		  see_attackers[WHITE][numw].square = a_sq;
		  numw++;
		  break;
		}
	      else if (b_sq == brook || b_sq == bqueen)
		{
		  see_attackers[BLACK][numb].piece = b_sq;
		  see_attackers[BLACK][numb].square = a_sq;
		  numb++;
		  break;
		}
	      else if (b_sq != npiece) break;
	      a_sq += rook_o [i];
	      b_sq = board[a_sq];
	    }
	}
    }
  
  /* bishop-style moves: */
  for (i = 0; i < 4; i++) 
    {
      a_sq = square + bishop_o[i];
      b_sq = board[a_sq];
      /* check for pawn attacks: */
      if (b_sq == wpawn && i%2)
	{
	  see_attackers[WHITE][numw].piece = b_sq;
	  see_attackers[WHITE][numw].square = a_sq;
	  numw++;
	  break;
	}
      else if (b_sq == bpawn && !(i%2))
	{
	  see_attackers[BLACK][numb].piece = b_sq;
	  see_attackers[BLACK][numb].square = a_sq;
	  numb++;
	  break;
	}
      /* the king can attack from one square away: */
      else if (b_sq == wking)
	{
	  see_attackers[WHITE][numw].piece = b_sq;
	  see_attackers[WHITE][numw].square = a_sq;
	  numw++;
	  break;
	}
      else if (b_sq == bking)
	{
	  see_attackers[BLACK][numb].piece = b_sq;
	  see_attackers[BLACK][numb].square = a_sq;
	  numb++;
	  break;
	}
      else
	{
	  while (b_sq != frame) {
	    if (b_sq == wbishop || b_sq == wqueen) 
	      {
	        see_attackers[WHITE][numw].piece = b_sq;
	        see_attackers[WHITE][numw].square = a_sq;
		numw++;
		break;
	      }
	    else if (b_sq == bbishop || b_sq == bqueen)
	      {
	        see_attackers[BLACK][numb].piece = b_sq;
		see_attackers[BLACK][numb].square = a_sq;
		numb++;
		break;
	      }
	    else if (b_sq != npiece) break;
	    a_sq += bishop_o [i];
	    b_sq = board[a_sq];
	  }
	}
    }
  
  /* knight-style moves: */
  for (i = 0; i < 8; i++) 
    {
      a_sq = square + knight_o[i];
      b_sq = board[a_sq];
      if (b_sq == wknight)
	{
	  see_attackers[WHITE][numw].piece = b_sq;
	  see_attackers[WHITE][numw].square = a_sq;
	  numw++;
	}
      else if (b_sq == bknight)
	{
	  see_attackers[BLACK][numb].piece = b_sq;
	  see_attackers[BLACK][numb].square = a_sq;
	  numb++;
	}
    }

  see_num_attackers[WHITE] = numw;
  see_num_attackers[BLACK] = numb;
}

void findlowest(int color, int next)
{
  int lowestp;
  int lowestv;
  see_data swap;
  int i;

  lowestp = next;
  lowestv = abs(material[see_attackers[color][next].piece]);

  for (i = next; i < see_num_attackers[color]; i++)
    {
      if (abs(material[see_attackers[color][i].piece]) < lowestv)
	{
	  lowestp = i;
	  lowestv = abs(material[see_attackers[color][i].piece]);
	}
    } 

  /* lowestp now points to the lowest attacker, which we swap with next */
  swap = see_attackers[color][next];
  see_attackers[color][next] = see_attackers[color][lowestp];
  see_attackers[color][lowestp] = swap;
}


int see(int color, int square, int from)
{
  int sside;
  int caps[2];
  int value;
  int origpiece;
  int ourbestvalue;
  int hisbestvalue;

  /* reset data */
  see_num_attackers[WHITE] = 0;
  see_num_attackers[BLACK] = 0;

  /* remove original capturer from board, exposing his first xray-er */
  origpiece = board[from];
  board[from] = npiece;

  see_num_attackers[color]++;
  see_attackers[color][0].piece = origpiece;
  see_attackers[color][0].square = from;

  /* calculate all attackers to square */
  setup_attackers(square);

  /* initially we gain the piece we are capturing */
  value = abs(material[board[square]]);

  /* free capture ? */
  if (!see_num_attackers[!color])
    {
      board[from] = origpiece;
      return value;
    }
  else
    {
      /* we can never get a higher SEE score than the piece we just captured */
      /* so that is the current best value for our opponent */
      /* we arent sure of anything yet, so -INF */
      hisbestvalue = value;
      ourbestvalue = -INF;
    }

  caps[color] = 1;
  caps[!color] = 0;

  /* start with recapture */
  sside = !color;

  /* continue as int as there are attackers */
  while (caps[sside] < see_num_attackers[sside])
    {
      /* resort capturelist of sside to put lowest attacker in next position */
      findlowest(sside, caps[sside]);

      if (sside == color)
	{
	  /* capturing more */
	  /* we capture the opponents recapturer */
	  value += abs(material[see_attackers[!sside][caps[!sside]-1].piece]);

	  /* if the opp ran out of attackers we can stand pat now! */
	   if (see_num_attackers[!sside] <= caps[!sside] && value > ourbestvalue)
	    ourbestvalue = value;

	  /* our opponent can always stand pat now */
	  if (value < hisbestvalue) hisbestvalue = value;
	}
      else 
	{
	  /* recapture by opp */
	  /* we lose whatever we captured with in last iteration */
	  value -= abs(material[see_attackers[!sside][caps[!sside]-1].piece]);

	  /* we can stand pat if we want to now */
	  /* our best score goes up, opponent is unaffected */

	  if (value > ourbestvalue)
	    { 
	      ourbestvalue = value;
	    }

	  if (see_num_attackers[!sside] <= caps[!sside] && value < hisbestvalue)
	    hisbestvalue = value;
	}

      /* keep track of capture count */
      caps[sside]++;

      /* switch sides */
      sside ^= 1;

    }

  /* restore capturer */
  board[from] = origpiece;

  /* we return our best score now, keeping in mind that
     it can never we better than the best for our opponent */
  return (ourbestvalue > hisbestvalue) ? hisbestvalue : ourbestvalue;
}
