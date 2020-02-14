/*
    Sjeng - a chess variants playing program
    Copyright (C) 2000-2003 Gian-Carlo Pascutto
                                                          
    File: partner.c
    Purpose: handle partner communication                  

*/                                                           

#include "sjeng.h"
#include "protos.h"
#include "extvars.h"

int hand_value[] = { 0, 100, -100, 210, -210, 0, 0, 250, -250, 450, -450, 230, -230 }; 
int std_hand_value[] = { 0, 100, -100, 210, -210, 0, 0, 250, -250, 450, -450, 230, -230 };

xbool piecedead;
xbool partnerdead;

int must_go;

void ResetHandValue(void)
{
  memcpy(hand_value, std_hand_value, sizeof(hand_value));
}

void BegForPartner(void)
{
  if (xb_mode)
    {
      /* printf("tellics tell 24 partner please\n"); */
    }
  return;
}

void GreetPartner()
{
  printf("tellics ptell Hello! I am Sjeng and hope you enjoy playing with me.\n");
  printf("tellics ptell For help on some commands that I understand, ptell me \'help\'\n");

  return;
}

void HandlePartner(char *input)
{
  if (input[0] == ' ')
    {
      if (!have_partner)
	{
	  /* catch bogus xboard repartnering */
	  sscanf(input+1, "%s", my_partner);
	  have_partner = TRUE;
	  GreetPartner();
	  printf("tellics set f5 bughouse\n");
	  printf("tellics unseek\n");
	}
    }
  else
    {
      memset(my_partner, 0, sizeof(my_partner));
      have_partner = FALSE;
      BegForPartner();
      printf("tellics set f5 1=1\n");
    }
}

void HandlePtell(char *input)
{
  int change = 0;
  char howmuch[80] = "is...uh...what did you say?\n";

  if (!strncmp(input+6, "help", 4))
      {
	printf("tellics ptell Commands that I understand are : sit, go, fast, slow, abort, flag, +/++/+++/-/--/---{p,n,b,r,q,d,h,trades}, x, dead, formula, help.\n");
	return;  
    }
  
  if (Variant != Bughouse && (strncmp(input+6, "sorry", 5)))
  {
    printf("tellics ptell Sorry, but I'm not playing a bughouse game.\n");
    return;
  }

  if (!strncmp(input+6, "sit", 3))
    {
      printf("tellics ptell Ok, I sit next move. Tell me when to go.\n");
      must_sit = TRUE;
      must_go = 0;
    }
  else if (!strncmp(input+6, "go", 2) || (!strncmp(input+6, "move", 4)))
    {
      printf("tellics ptell Ok, I'm moving.\n");
      must_sit = FALSE;
      must_go = 4;
    }
  else if (!strncmp(input+6, "fast", 4) || (!strncmp(input+6, "time", 4)))
    {
      printf("tellics ptell Ok, I'm going FAST!\n");
      go_fast = TRUE;
      must_sit = FALSE;
    }
  else if (!strncmp(input+6, "slow", 4))
    {
      printf("tellics ptell Ok, moving normally.\n");
      go_fast = FALSE;
      must_sit = FALSE;
    }
  else if (!strncmp(input+6, "abort", 5))
    {
      printf("tellics ptell Requesting abort...\n");
      printf("tellics abort\n");
    }
  else if (!strncmp(input+6, "flag", 4))
    {
      printf("tellics ptell Flagging...\n");
      printf("tellics flag\n");
    }
  else if (!strncmp(input+6, "+", 1))
    { 
      /* trades are handled seperately */
      if ((strstr(input+6, "trade") != NULL)   /* either an explicit trade request */
	  || (                                 /* or there's just no piece in the string */
	      (strstr(input+6, "n") == NULL) &&
	      (strstr(input+6, "b") == NULL) &&
	      (strstr(input+6, "p") == NULL) &&
	      (strstr(input+6, "r") == NULL) &&
	      (strstr(input+6, "q") == NULL) &&
	      (strstr(input+6, "d") == NULL) &&
	      (strstr(input+6, "h") == NULL)))
	{
	  if (comp_color == 1)
	    {
	      hand_value[wpawn] += 25;
	      hand_value[wknight] += 50;   
	      hand_value[wbishop] += 50;   
	      hand_value[wrook] += 50;
	      hand_value[wqueen] += 100;
	    }
	  else
	    {
	      hand_value[bpawn] -= 25;
	      hand_value[bknight] -= 50;   
	      hand_value[bbishop] -= 50;   
	      hand_value[brook] -= 50;
	      hand_value[bqueen] -= 100;
	    }
	  printf("tellics ptell Ok, trading is GOOD\n");
	}
      /* first, find how much we want that piece */
      else if (strstr(input+6, "+++") != NULL)
	{
	  change = 50000;
	  strcpy(howmuch, "mates");
	}
      else if (strstr(input+6, "++") != NULL)
	{
	  change = 1000;
	  strcpy(howmuch, "is VERY good (ptell me 'x' to play normal again)");
	}
      else if (strstr(input+6, "+") != NULL)
	{
	  change = 150;
	  strcpy(howmuch, "is good (ptell me 'x' to play normal again)");
	}
      else
	DIE;

      /* now find which piece we want */
      if (strstr(input+6, "n") != NULL)
	{
	  if (comp_color == 1)
	    /* we want a black knight-> the value for holding a white knight rises */
	    hand_value[wknight] = std_hand_value[wknight] + change;
	  else
	    /* we want a white knight */
	    hand_value[bknight] = std_hand_value[bknight] - change; 
	 
	  printf("tellics ptell Ok, Knight %s\n", howmuch);
	}
      if (strstr(input+6, "b") != NULL)
	{
	  if (comp_color == 1)
	    hand_value[wbishop] = std_hand_value[wbishop] + change;
	  else
	    hand_value[bbishop] = std_hand_value[bbishop] - change;

	  /* b is good, so q is good too */
	  if (comp_color == 1)
	    hand_value[wqueen] = std_hand_value[wqueen] + change;
	  else
	    hand_value[bqueen] = std_hand_value[bqueen] - change;
	  
	  printf("tellics ptell Ok, Bishop %s\n", howmuch);
	}
     if (strstr(input+6, "r") != NULL)
	{
	  if (comp_color == 1)
	    hand_value[wrook] = std_hand_value[wrook] + change;
	  else
	    hand_value[brook] = std_hand_value[brook] - change;

	  /* r is good, so q is good too */
	  if (comp_color == 1)
	    hand_value[wqueen] = std_hand_value[wqueen] + change;
	  else
	    hand_value[bqueen] = std_hand_value[bqueen] - change;

	  printf("tellics ptell Ok, Rook %s\n", howmuch);
	}
     if (strstr(input+6, "q") != NULL)
	{
	  if (comp_color == 1)
	    hand_value[wqueen] = std_hand_value[wqueen] + change;
	  else
	    hand_value[bqueen] = std_hand_value[bqueen] - change;
	 
	  printf("tellics ptell Ok, Queen %s\n", howmuch); 
	}
     if (strstr(input+6, "p") != NULL)
	{
	  if (comp_color == 1)
	    hand_value[wpawn] = std_hand_value[wpawn] + change;
	  else
	    hand_value[bpawn] = std_hand_value[bpawn] - change;
	
	  /* p is good, so b and q are good too */
	  if (comp_color == 1)
	    {
	      hand_value[wqueen] = std_hand_value[wqueen] + change;
	      hand_value[wbishop] = std_hand_value[wbishop] + change;
	    }	  
	  else
	    {
	      hand_value[bqueen] = std_hand_value[bqueen] - change;
	      hand_value[bbishop] = std_hand_value[bbishop] - change;
	    }
	
	  printf("tellics ptell Ok, Pawn %s\n", howmuch); 
	}
     if (strstr(input+6, "d") != NULL)
	{
	  if (comp_color == 1)
	    {
	      hand_value[wpawn] = std_hand_value[wpawn] + change;
	      hand_value[wbishop] = std_hand_value[wbishop] + change;
	      hand_value[wqueen] = std_hand_value[wqueen] + change;
	    }	  
	  else
	    {
	      hand_value[bpawn] = std_hand_value[bpawn] - change;
	      hand_value[bbishop] = std_hand_value[bbishop] - change;
	      hand_value[bqueen] = std_hand_value[bqueen] - change;
	    }

	  printf("tellics ptell Ok, Diagonal %s\n", howmuch); 
	}
     if (strstr(input+6, "h") != NULL)
	{
	  if (comp_color == 1)
	    {
	      hand_value[wrook] = std_hand_value[wrook] + change;
	      hand_value[wqueen] = std_hand_value[wqueen] + change;
	    }	  
	  else
	    {
	      hand_value[brook] = std_hand_value[brook] - change;
	      hand_value[bqueen] = std_hand_value[bqueen] - change;
	    }

	  printf("tellics ptell Ok, Heavy %s\n", howmuch); 
	}
    }
  else if (!strncmp(input+6, "-", 1))
    {
      /* trades are handled seperately */
      if ((strstr(input+6, "trade") != NULL)   /* either an explicit trade request */
	  || (                                 /* or there's just no piece in the string */
	      (strstr(input+6, "n") == NULL) &&
	      (strstr(input+6, "b") == NULL) &&
	      (strstr(input+6, "p") == NULL) &&
	      (strstr(input+6, "r") == NULL) &&
	      (strstr(input+6, "q") == NULL) &&
	      (strstr(input+6, "d") == NULL) &&
	      (strstr(input+6, "h") == NULL)))
	{
      	  if (comp_color == 1)
	    {
	      hand_value[bpawn] -= 20;
	      hand_value[bknight] -= 50;   
	      hand_value[bbishop] -= 50;   
	      hand_value[brook] -= 50;
	      hand_value[bqueen] -= 100;
	    }
	  else
	    {
	      hand_value[wpawn] += 20;
	      hand_value[wknight] += 50;   
	      hand_value[wbishop] += 50;   
	      hand_value[wrook] += 50;
	      hand_value[wqueen] += 100;
	    }
	  printf("tellics ptell Ok, trading is BAD\n");
	}
      /* first, find how bad we want to avoid losing that piece */
      else if (strstr(input+6, "---") != NULL)
	{
	  change = 50000;
	  strcpy(howmuch, "mates you (ptell me 'x' when it no longer mates you)");
	}
      else if (strstr(input+6, "--") != NULL)
	{
	  change = 1000;
	  strcpy(howmuch, "is VERY bad (ptell me 'x' when it is no longer bad)");
	}
      else if (strstr(input+6, "-") != NULL)
	{
	  change = 150;
	  strcpy(howmuch, "is bad (ptell me 'x' when it is no longer bad)");
	}
      else
	DIE;

      if (strstr(input+6, "n") != NULL)
	{
	  if (comp_color == 1)
	    hand_value[bknight] = std_hand_value[bknight] - change;
	  else
	    hand_value[wknight] = std_hand_value[wknight] + change; 
	 
	  printf("tellics ptell Ok, Knight %s\n", howmuch);
	}
      if (strstr(input+6, "b") != NULL)
	{
	  if (comp_color == 1)
	    hand_value[bbishop] = std_hand_value[bbishop] - change;
	  else
	    hand_value[wbishop] = std_hand_value[wbishop] + change;

	  /* b is bad, so q is bad too */
	  if (comp_color == 1)
	    hand_value[bqueen] = std_hand_value[bqueen] - change;
	  else
	    hand_value[wqueen] = std_hand_value[wqueen] + change;

	  printf("tellics ptell Ok, Bishop %s\n", howmuch);
	}
     if (strstr(input+6, "r") != NULL)
	{
	  if (comp_color == 1)
	    hand_value[brook] = std_hand_value[brook] - change;
	  else
	    hand_value[wrook] = std_hand_value[wrook] + change;

	  /* r is bad, so q is bad too */
	  if (comp_color == 1)
	    hand_value[bqueen] = std_hand_value[bqueen] - change;
	  else
	    hand_value[wqueen] = std_hand_value[wqueen] + change;
	      
	  printf("tellics ptell Ok, Rook %s\n", howmuch);
	}
     if (strstr(input+6, "q") != NULL)
	{
	  if (comp_color == 1)
	    hand_value[bqueen] = std_hand_value[bqueen] - change;
	  else
	    hand_value[wqueen] = std_hand_value[wqueen] + change;
	 
	  printf("tellics ptell Ok, Queen %s\n", howmuch); 
	}
     if (strstr(input+6, "p") != NULL)
	{
	  if (comp_color == 1)
	    hand_value[bpawn] = std_hand_value[bpawn] - change;
	  else
	    hand_value[wpawn] = std_hand_value[wpawn] + change;
	
	  /* p is bad, so b and q are bad too */
	  if (comp_color == 1)
	    {
	      hand_value[bqueen] = std_hand_value[bqueen] - change;
	      hand_value[bbishop] = std_hand_value[bbishop] - change;
	    }	  
	  else
	    {
	      hand_value[wqueen] = std_hand_value[wqueen] + change;
	      hand_value[wbishop] = std_hand_value[wbishop] + change;
	    }	

	  printf("tellics ptell Ok, Pawn %s\n", howmuch); 
	}
     if (strstr(input+6, "d") != NULL)
	{
	  if (comp_color == 1)
	    {
	      hand_value[bpawn] = std_hand_value[bpawn] - change;
	      hand_value[bbishop] = std_hand_value[bbishop] - change;
	      hand_value[bqueen] = std_hand_value[bqueen] - change;
	    }	  
	  else
	    {
	      hand_value[wpawn] = std_hand_value[wpawn] + change;
	      hand_value[wbishop] = std_hand_value[wbishop] + change;
	      hand_value[wqueen] = std_hand_value[wqueen] + change;
	    }

	  printf("tellics ptell Ok, Diagonal %s\n", howmuch); 
	}
     if (strstr(input+6, "h") != NULL)
	{
	  if (comp_color == 1)
	    {
	      hand_value[brook] = std_hand_value[brook] - change;
	      hand_value[bqueen] = std_hand_value[bqueen] - change;
	    }	  
	  else
	    {
	      hand_value[wrook] = std_hand_value[wrook] + change;
	      hand_value[wqueen] = std_hand_value[wqueen] + change;
	    }

	  printf("tellics ptell Ok, Heavy %s\n", howmuch); 
	}
    }
  else if (((!strncmp(input+6, "x", 1) 
       || (strstr(input+6, "mate me anymore") != NULL) 
       || ((strstr(input+6, "never") != NULL) && (strstr(input+6, "mind") != NULL)))
       || (!strncmp(input+6, "=", 1))) && (strstr(input+6, "ptell me") == NULL))
    {
      printf("tellics ptell Ok, reverting to STANDARD piece values!\n");
      ResetHandValue();
      must_sit = FALSE;
      partnerdead = FALSE;
      piecedead = FALSE;
    }
  else if (!strncmp(input+6, "i'll have to sit...(dead)", 25) ||
	   !strncmp(input+6, "dead", 4))
    {
      /* fellow sjeng is dead -> give it all we've got */
      go_fast = TRUE;
      must_sit = FALSE;
      partnerdead = TRUE;

      /* maybe also here tell go if partner is sjeng ? */
    }
  else if (!strncmp(input+6, "i'll have to sit...(piece)", 26))
    {
      /* fellow sjeng is dead -> get safe fast */
      go_fast = TRUE;
      must_sit = FALSE;
      piecedead = TRUE;
    }
  else if (!strncmp(input+6, "sorry", 5))
    {
      return;
    }
  else if (!strncmp(input+6, "ok", 2))
    {
      return;
    }
  else if (!strncmp(input+6, "hi", 2) || (!strncmp(input+6, "hello", 5)))
    {
      printf("tellics ptell Greetings.\n");
    }
  else if (strstr(input+6, "formula") != NULL)
  {
     printf("tellics ptell Setting formula, if you are still interrupted, complain to my operator.\n");
     printf("tellics set f5 bughouse\n");
  }
  else   
    {
      printf("tellics ptell Sorry, but I don't understand that command.\n");
    }
  return;
}

#define CANCEL_THRESH 3

void CheckBadFlow(xbool reset)
{
  move_s hismoves[MOVE_BUFF];
  move_s ourmoves[MOVE_BUFF];
  int his_num_moves, our_num_moves, j, i, ic, icc;

  xbool othermove = FALSE;

  int 
    pawnmates = FALSE, 
    knightmates = FALSE, 
    bishopmates = FALSE, 
    rookmates = FALSE, 
    queenmates = FALSE;
  
  static int 
    pawnmated = FALSE, 
    knightmated = FALSE, 
    bishopmated = FALSE, 
    rookmated = FALSE, 
    queenmated = FALSE;

  xbool
    pawnwarn = FALSE,
    knightwarn = FALSE,
    bishopwarn = FALSE,
    rookwarn = FALSE,
    queenwarn = FALSE;

  if (reset)
    {
      pawnmated = FALSE; 
      knightmated = FALSE; 
      bishopmated = FALSE; 
      rookmated = FALSE; 
      queenmated = FALSE;
      return;
    }

  ic = in_check();

  if (!holding[!white_to_move][(white_to_move ? wpawn : bpawn)])
    {
  
      DropaddHolding((white_to_move ? wpawn : bpawn) , !white_to_move);
      
      gen(&hismoves[0]);
      his_num_moves = numb_moves;
      
      for(i = 0; (i < his_num_moves) && (pawnmates == FALSE); i++)
	{
	  make(&hismoves[0], i);
	  
	  if (check_legal(&hismoves[0], i, ic))
	    {
	      pawnmates = CANCEL_THRESH;

	      icc = in_check();
	      
	      gen(&ourmoves[0]); 
	      our_num_moves = numb_moves;
	      
	      for (j = 0; (j < our_num_moves) && (pawnmates != FALSE); j++)
		{
		  
		  make(&ourmoves[0], j);
		  
		  if (check_legal(&ourmoves[0], j, icc))
		    pawnmates = FALSE;
		  
		  unmake(&ourmoves[0], j);
		  
		}
	    }
	  unmake(&hismoves[0], i);	  
	}
      DropremoveHolding((white_to_move ? wpawn : bpawn), !white_to_move);
    }
  
  if (!holding[!white_to_move][(white_to_move ? wknight : bknight)])
    {
      
      DropaddHolding((white_to_move ? wknight : bknight) , !white_to_move);
      
      gen(&hismoves[0]); 
      his_num_moves = numb_moves;
      
      for(i = 0; (i < his_num_moves) && (knightmates == FALSE); i++)
	{
	  
	  make(&hismoves[0], i);
	  
	  if (check_legal(&hismoves[0], i, ic))
	    {
	      knightmates = CANCEL_THRESH;

	      icc = in_check();
	      
	      gen(&ourmoves[0]); 
	      our_num_moves = numb_moves;
	      
	      for (j = 0; (j < our_num_moves) && (knightmates != FALSE); j++)
		{
		  make(&ourmoves[0], j);
		  
		  if (check_legal(&ourmoves[0], j, icc))
		    knightmates = FALSE;
		  
		  unmake(&ourmoves[0], j);
		}
	    }
	  unmake(&hismoves[0], i);
	}
      DropremoveHolding((white_to_move ? wknight : bknight), !white_to_move);
    }
  
  if (!holding[!white_to_move][(white_to_move ? wbishop : bbishop)])
    {      
      
      DropaddHolding((white_to_move ? wbishop : bbishop) , !white_to_move);
      
      gen(&hismoves[0]); 
      his_num_moves = numb_moves;
      
      for(i = 0; (i < his_num_moves) && (bishopmates == FALSE); i++)
	{
	  make(&hismoves[0], i);
	  
	  if (check_legal(&hismoves[0], i, ic))
	    {
	      bishopmates = CANCEL_THRESH;
	      
	      icc = in_check();

	      gen(&ourmoves[0]);
	      our_num_moves = numb_moves;
	      
	      for (j = 0; (j < our_num_moves) && (bishopmates != FALSE); j++)
		{
		  make(&ourmoves[0], j);
		  
		  if (check_legal(&ourmoves[0], j, icc))
		    bishopmates = FALSE;
		  
		  unmake(&ourmoves[0], j);
		}
	    }
	  unmake(&hismoves[0], i);
	}
      DropremoveHolding((white_to_move ? wbishop : bbishop), !white_to_move);  
    }
  
  if (!holding[!white_to_move][(white_to_move ? wrook : brook)])
    {
      
      DropaddHolding((white_to_move ? wrook : brook) , !white_to_move);
      
      gen(&hismoves[0]);
      his_num_moves= numb_moves;
      
      for(i = 0; (i < his_num_moves) && (rookmates == FALSE); i++)
	{
	  make(&hismoves[0], i);
	  
	  if (check_legal(&hismoves[0], i, ic))
	    {
	      rookmates = CANCEL_THRESH;

	      icc = in_check();
	      
	      gen(&ourmoves[0]);
	      our_num_moves = numb_moves;
	      
	      for (j = 0; (j < our_num_moves) && (rookmates != FALSE); j++)
		{
		  make(&ourmoves[0], j);
		  
		  if (check_legal(&ourmoves[0], j, icc))
		    rookmates = FALSE;
		  
		  unmake(&ourmoves[0], j); 
		}
	    }
	  unmake(&hismoves[0], i);
	}
      DropremoveHolding((white_to_move ? wrook : brook), !white_to_move); 
    }
  
  if (!holding[!white_to_move][(white_to_move ? wqueen : bqueen)])
    {
      
      DropaddHolding((white_to_move ? wqueen : bqueen) , !white_to_move);

      gen(&hismoves[0]);
      his_num_moves= numb_moves;
      
      for(i = 0; (i < his_num_moves) && (queenmates == FALSE); i++)
	{
	  make(&hismoves[0], i);
	  
	  if (check_legal(&hismoves[0], i, ic))
	    {
	      queenmates = CANCEL_THRESH;

	      icc = in_check();
	      
	      gen(&ourmoves[0]);
	      our_num_moves = numb_moves;
	      
	      for (j = 0; (j < our_num_moves) && (queenmates != FALSE); j++)
		{
		  make(&ourmoves[0], j);
		  
		  if (check_legal(&ourmoves[0], j, icc))
		    queenmates = FALSE;
		  
		  unmake(&ourmoves[0], j); 
		}
	    }
	  unmake(&hismoves[0], i);
	}
      DropremoveHolding((white_to_move ? wqueen : bqueen), !white_to_move);
    }

  /* order in which we tell things is important if we partner ourselves */
  
  /* only update if changed */
  if (pawnmates != pawnmated)
    {
      if (pawnmates == CANCEL_THRESH)
	  pawnwarn = TRUE;
      else if (pawnmates == 0 && pawnmated == 0)
	{
	  printf("tellics ptell p doesn't mate me anymore\n");
	  othermove = TRUE;
	}
    }
  
  if (knightmates != knightmated)
    {
      if (knightmates == CANCEL_THRESH)
	  knightwarn = TRUE;
      else if (knightmates == 0 && knightmated == 0)
	{
	  printf("tellics ptell n doesn't mate me anymore\n");
	  othermove = TRUE;
	}
    }  

  if (bishopmates != bishopmated)
    {
      if (bishopmates == CANCEL_THRESH)
	  bishopwarn = TRUE;
      else if (bishopmates == 0 && bishopmated == 0)
	{
	  printf("tellics ptell b doesn't mate me anymore\n");
	  othermove = TRUE;
	}
    }  
  if (rookmates != rookmated)
    {
      if (rookmates == CANCEL_THRESH)
	  rookwarn = TRUE;
      else if (rookmates == 0 && rookmated == 0)
	{
	  printf("tellics ptell r doesn't mate me anymore\n");
	  othermove = TRUE;
	}
    }
  if (queenmates != queenmated)
    {
      if (queenmates == CANCEL_THRESH)
	  queenwarn = TRUE;
      else if (queenmates == 0 && queenmated == 0)
	{
	  printf("tellics ptell q doesn't mate me anymore\n");
	  othermove = TRUE;
	} 
    }

  if (pawnwarn)
	printf("tellics ptell ---p\n");
  if (knightwarn)
	printf("tellics ptell ---n\n");
  if (bishopwarn)
	printf("tellics ptell ---b\n");
  if (rookwarn)
	printf("tellics ptell ---r\n");
  if (queenwarn)
	printf("tellics ptell ---q\n");

  /* if other sjeng had to sit because of piece-loss, he
     may be able to go now */
  
  if (piecedead && othermove)
    {
      piecedead = FALSE;
      printf("tellics ptell x\n");
      printf("tellics ptell go\n");
      go_fast = FALSE;
    }

  (pawnmates) ? (pawnmated = pawnmates) : (pawnmated--);
  (bishopmates) ? (bishopmated = bishopmates) : (bishopmated--);
  (rookmates) ? (rookmated = rookmates) : (rookmated--);
  (queenmates) ? (queenmated = queenmates) : (queenmated--);
  (knightmates) ? (knightmated = knightmates) : (knightmated--);

  return;
}

