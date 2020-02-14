/*
    Sjeng - a chess variants playing program
    Copyright (C) 2000-2003 Gian-Carlo Pascutto    

    File: draw.c                                       
    Purpose: Detect draws

*/

#include "sjeng.h"
#include "extvars.h"
#include "protos.h"

xbool is_draw (void)
{
  int i, repeats = 0, end, start;

  if (fifty >= 4)
    {
      if ((move_number) < (move_number + ply - 1 - fifty))
	{
	  end = move_number + ply - 1 - fifty;
	}
      else
	{
	  end = move_number;
	}
      for (i = (move_number + ply - 3); i >= 0 && i >= end; i -= 2)
	{
	  if (hash == hash_history[i])
	    {
	      return TRUE;
	    }
	}
    }
  
  if (fifty >= 6)
    {
      start = move_number - 1 - (ply % 2);
      end = move_number + ply - 1 - fifty;
      
      for (i = start; i >= 0 && i >= end; i -= 2)
	{
	  if (hash == hash_history[i])
	    {
	      repeats++;
	    }
	  if (repeats >= 2)
	    {
	      return TRUE;
	    }
	}
    }

  return FALSE;

}

