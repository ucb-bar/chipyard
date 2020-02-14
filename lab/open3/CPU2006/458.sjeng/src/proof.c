/*
   Sjeng - a chess variants playing program
   Copyright (C) 2000-2003 Gian-Carlo Pascutto

   File: proof.c                                        
   Purpose: contains functions related to the pn-search

 */

#include "sjeng.h"
#include "extvars.h"
#include "protos.h"
#include "limits.h"

#define FALSE 0
#define TRUE 1
#define UNKNOWN 2
#define STALEMATE 3		/* special case because pn-search only assumes 1/0 */

#define PN_INF 100000000

/* we can exceed PBSize before exiting the main search loop */
#define SAFETY     10000

/* define this to use the pn^2 search */
#undef PN2

int nodecount;
int nodecount2;
int pn2;
int frees;
int iters;
int forwards;
int maxply;
int pn_time;
move_s pn_move;
move_s pn_saver;

xbool kibitzed;
int forcedwin;

int rootlosers[PV_BUFF];
int alllosers;

typedef struct node
  {
    unsigned char value;
    unsigned char num_children;
    unsigned char expanded;
    unsigned char evaluated;
    int proof;
    int disproof;
    struct node **children;
    struct node *parent;
    move_s move;
  }
node_t;

void pn2_eval (node_t *node);
void suicide_pn_eval (node_t *this);
void std_pn_eval (node_t *this);
void losers_pn_eval (node_t *this);

unsigned char *membuff;
int bufftop = 0;

void* Xmalloc(int size)
{
  int oldtop = bufftop;
  
  bufftop += size;
  
  return (&membuff[oldtop]);
}

void Xfree(void)
{
  bufftop = 0;
}

void freenodes (node_t * node)
{
  int i;

  if (!node)
    return;

  if (node->children)
    {
      if (node->num_children > 0)
	{
	  for (i = 0; i < (node->num_children); i++)
	    {
	      if (node->children[i] != 0)
		{
		  freenodes (node->children[i]);
		};
	    };
	  free (node->children);
	}
    };

  free (node);
}

void pn_eval(node_t * this)
{
  if (Variant == Suicide)
    {    
      suicide_pn_eval(this);
    }
  else if (Variant == Losers)
    {
      losers_pn_eval(this);
    }
  else  
    {
      std_pn_eval(this);
    }
}

void std_pn_eval (node_t * this)
{
  int num_moves;
  move_s moves[MOVE_BUFF];
  int mate;
  int i;

  this->evaluated = TRUE;

  /*ep_temp = ep_square;*/

  if ((white_to_move && is_attacked (wking_loc, WHITE))
      || (!white_to_move && is_attacked (bking_loc, BLACK)))
    {

      num_moves = 0;
      gen (&moves[0]);
      num_moves = numb_moves;

      mate = TRUE;

      for (i = 0; i < num_moves; i++)
	{
	  make (&moves[0], i);

	  /* check to see if our move is legal: */
	  if (check_legal (&moves[0], i, TRUE))
	    {
	      mate = FALSE;
	      unmake (&moves[0], i);
	      break;
	    };

	  unmake (&moves[0], i);
	}

      if (mate == TRUE)
	{
	  /* proven or disproven */
	  if (ToMove == root_to_move)
	    {
	      /* root mover is mated-> disproven */
	      this->value = FALSE;
	    }
	  else
	    {
	      this->value = TRUE;
	    };
	}
      else
	{
	  this->value = UNKNOWN;
	};
    }
  else
    {
      this->value = UNKNOWN;
    };

  /*ep_square = ep_temp;*/

}

void suicide_pn_eval(node_t *this)
{
  int j, a, i;
  int wp = 0, bp = 0;
  
  this->evaluated = TRUE;

  for (j = 1, a = 1; (a <= piece_count); j++) 
    {
      i = pieces[j];
      
      if (!i)
	continue;
      else
	a++;
      
      switch (board[i])
	{
	case wpawn:
	case wbishop:
	case wrook:
	case wking:
	case wqueen:
	case wknight: wp++; break;
	case bpawn:
	case bbishop:
	case brook:
	case bking:
	case bqueen:
	case bknight: bp++; break;
	}

      if (wp && bp) break;
    }
  
  if (!wp)
    {
      /* white has no pieces */
      /* proven or disproven */
      if (!root_to_move)
	{
	  /* root mover is mated-> proven */
	  this->value = TRUE;
	}
      else
	{
	  this->value = FALSE;
	};
    }
  else if (!bp)
    {
      /* black has no pieces */
      if (!root_to_move)
	{
	  /* root mover is mated-> disproven */
	  this->value = FALSE;
	}
      else
	{
	  this->value = TRUE;
	};
    }
  else
    {
      this->value = UNKNOWN;
    };  
}

void losers_pn_eval(node_t *this)
{
  int num_moves;
  move_s moves[MOVE_BUFF];
  int mate;
  int i;
  int j, a;
  int wp = 0, bp = 0;

  this->evaluated = TRUE;

  /*ep_temp = ep_square;*/
  
  for (j = 1, a = 1; (a <= piece_count); j++) 
    {
      i = pieces[j];
      
      if (!i)
	continue;
      else
	a++;
      
      switch (board[i])
	{
	case wpawn:
	case wbishop:
	case wrook:
	case wqueen:
	case wknight: wp++; break;
	case bpawn:
	case bbishop:
	case brook:
	case bqueen:
	case bknight: bp++; break;
	}
  
      if (wp && bp) break;
    }
  

  if (!wp)
    {
      /* proven or disproven */
      if (!root_to_move)
	{
	  /* root mover is mated-> disproven */
	  this->value = TRUE;
	}
      else
	{
	  this->value = FALSE;
	};
      return;
    }
  else if (!bp)
    {
      if (root_to_move)
	{
	  /* root mover is mated-> disproven */
	  this->value = TRUE;
	}
      else
	{
	  this->value = FALSE;
	};
      return;
    }
  
  if ((white_to_move && is_attacked(wking_loc, WHITE))
      || (!white_to_move && is_attacked(bking_loc, BLACK)))	
    {  
     
      captures = TRUE;

      num_moves = 0;
      gen (&moves[0]);
      num_moves = numb_moves;
      captures = FALSE;

      mate = TRUE;
      
      for (i = 0; i < num_moves; i++) 
	{
	  make (&moves[0], i);
	  
	  /* check to see if our move is legal: */
	  if (check_legal (&moves[0], i, TRUE)) 
	    {
	      mate = FALSE;
	      unmake(&moves[0], i);
	      break;
	    };
	  
	  unmake(&moves[0], i);
	}
      
	if (mate == TRUE)
	{
	  /* no legal capture..do non-captures */
	  captures = FALSE;
	  num_moves = 0;
	  gen (&moves[0]);
	  num_moves = numb_moves;
	  
	  for (i = 0; i < num_moves; i++) 
	    {
	      make (&moves[0], i);
	      
	      /* check to see if our move is legal: */
	      if (check_legal (&moves[0], i, TRUE)) 
		{
		  mate = FALSE;
		  unmake(&moves[0], i);
		  break;
		};
	      
	      unmake(&moves[0], i);
	    }
	}
	
	if (mate == TRUE)
	  {
	  /* proven or disproven */
	    if (ToMove == root_to_move)
	      {
		/* root mover is mated-> disproven */
		this->value = TRUE;
	      }
	    else
	      {
		this->value = FALSE;
	      };
	  }
	else
	  {
	    this->value = UNKNOWN;
	  };
    }
  else
    {
      this->value = UNKNOWN;
    };
  
  /* ep_square = ep_temp; */
  
}


node_t *select_most_proving (node_t * node)
{
  int i;
  node_t *tnode;

  tnode = node;

  while (tnode->expanded)
    {
      if (ToMove == root_to_move)
	{
	  i = 0;

	  while (tnode->children[i]->proof != tnode->proof)
	    {
	      i++;
	    };
	}
      else
	{
	  i = 0;

	  while (tnode->children[i]->disproof != tnode->disproof)
	    {
	      i++;
	    };
	};

      tnode = tnode->children[i];

      hash_history[move_number+ply-1] = hash; 	  
      
      make (&tnode->move, 0);

      if (ply > maxply)
	maxply = ply;

    };

  return tnode;

}

void set_proof_and_disproof_numbers (node_t * node)
{
  int proof;
  int disproof;
  int i;
  move_s moves[MOVE_BUFF];
  int l, num_moves;
  int ic;

  if (node->expanded)
    {
      if (ToMove != root_to_move)
	{
	  proof = 0;
	  disproof = PN_INF;

	  for (i = 0; i < node->num_children; i++)
	    {
	      proof += node->children[i]->proof;

	      if (proof > PN_INF)
		proof = PN_INF;

	      if (node->children[i]->disproof < disproof)
		{
		  disproof = node->children[i]->disproof;
		}
	    }
	  
	  if ((proof == 0) || (disproof == PN_INF))
	    {
	      forwards++;
	      StoreTT(INF-500, INF, -INF, -1, 0, 200);
	    }
	  else if ((disproof == 0) || (proof == PN_INF))
	    {
	      forwards++;
	      StoreTT(-INF+500, INF, -INF, -1, 0, 200);
	    }
	}
      else
	{
	  disproof = 0;
	  proof = PN_INF;

	  for (i = 0; i < node->num_children; i++)
	    {

	      disproof += node->children[i]->disproof;

	      if (disproof > PN_INF)
		disproof = PN_INF;

	      if (node->children[i]->proof < proof)
		{
		  proof = node->children[i]->proof;
		}
	    }

	  if ((proof == 0) || (disproof == PN_INF))
	    {
	      forwards++;
	      StoreTT(INF-500, INF, -INF, -1, 0, 200);
	    }
	  else if ((disproof == 0) || (proof == PN_INF))
	    {
	      forwards++;
	      StoreTT(-INF+500, INF, -INF, -1, 0, 200);
	    }
	}
	  
      hash_history[move_number+ply-1] = hash; 
	  
      node->proof = proof;
      node->disproof = disproof;

    }
  else if (node->evaluated)
    {
      if (node->value == UNKNOWN)
	{
	  
	  hash_history[move_number+ply-1] = hash; 

	  if (is_draw() || ply > 200)
	    {
	      node->proof = 50000;
	      node->disproof = 50000;
	      return;
	    }
	  
	  /* ept = ep_square; */
	  
	  if (Variant != Losers)
	    {
	      num_moves = 0;
	      gen (&moves[0]);
	      num_moves = numb_moves;

	      ic = in_check();
	      
	      if (Variant != Suicide)
		{
		  l = 0;
		  
		  for (i = 0; i < num_moves; i++)
		    {
		      make (&moves[0], i);
		      /* check to see if our move is legal: */
		      if (check_legal (&moves[0], i, ic))
			{
			  l++;
			}
		      unmake (&moves[0], i);
		    };
		}
	      else
		{
		  l = numb_moves;
		};
	    }
	  else
	    {
	      /* Losers...this a bit more messy */

	      l = 0;
	      captures = TRUE;
	      num_moves = 0;
	      gen (&moves[0]);
	      num_moves = numb_moves;
	      captures = FALSE;	

	      ic = in_check();
	      
	      if (num_moves)
		{
		  for (i = 0; i < num_moves; i++)
		    {
		      make(&moves[0], i);
		      
		      if (check_legal(&moves[0], i, ic))
			{
			  l++;
			}
		      unmake(&moves[0], i);
		    }
		}
	      
	      /* ep_square = ept; */
	      
	      if (!l) 
		{
		  captures = FALSE;
		  num_moves = 0;
		  gen(&moves[0]);
		  num_moves = numb_moves;

		  for (i = 0; i < num_moves; i++)
		    {
		      make(&moves[0], i);
		      
		      if (check_legal(&moves[0], i, ic))
			{
			  l++;
			}
		      unmake(&moves[0], i);
		    }
		};
	    }
	  
	  if (l == 0)
	    {
	      /* might be stalemate too */
	      node->proof = 1;
	      node->disproof = 1;
	    }
	  else if (ToMove == root_to_move)	/* OR */
	    {
	      if ((Variant != Suicide) && (Variant != Losers))
		{
		  node->proof = 1 + ply / 50;
		  node->disproof = l + ply  / 50;
		}
	      else
		{
		  if (Variant == Losers)
		    {
		      /* this is probably a bogus line,
			 so breathen the tree */
		      if (phase == Endgame)
		      {
			node->proof = 1 + ply / 30;
			node->disproof = l + ply / 30;
		      }
		      else
		      {
			node->proof = 1 + ply / 80;
			node->disproof = l + ply / 80;
		      }
		    }
		  else
		    {
		      node->proof = 1 + ply / 150;
		      node->disproof = l + ply / 150;
		    }
		}
	    }
	  else
	    {
	      if ((Variant != Suicide) && (Variant != Losers))
		{
		  node->proof = l + ply / 50;
		  node->disproof = 1 + ply / 50;
		}
	      else
		{
		  if (Variant == Losers)
		    {
		      if (phase == Endgame)
		      {
			  node->proof = l + ply/30;
			  node->disproof = 1 + ply/30;
			
		      }
		      else
		      {
			  node->proof = l + ply/80;
			  node->disproof = 1 + ply/80;
		      }
		    }
		  else
		    {
		      node->proof = l + ply / 150;
		      node->disproof = 1 + ply / 150;
		    }
		}
	    }
	  
	  /* ep_square = ept; */
	}
      else if (node->value == FALSE)
	{
	  node->proof = PN_INF;
	  node->disproof = 0;
	}
      else if (node->value == TRUE)
	{
	  node->proof = 0;
	  node->disproof = PN_INF;
	}
      else if (node->value == STALEMATE)
	{
	  /* don't look at this node, its a dead-end */
	  node->proof = 50000;
	  node->disproof = 50000;
	};
    }
  else
    {
      node->proof = node->disproof = 1;
    }
}

void develop_node (node_t * node)
{
  int num_moves;
  move_s moves[MOVE_BUFF];
  int i, l;
  node_t *newnode;
#ifdef PN2
  node_t **newchildren;
#endif
  int leg;
  int ic;

  /*ept = ep_square; */

#ifdef PN2
  if (!pn2)
    pn2_eval(node);
#endif

  ic = in_check();
  
  if (Variant != Losers)
    {
      num_moves = 0;
      gen (&moves[0]);
      num_moves = numb_moves;
    }
  else
    {
      captures = TRUE;
      leg = FALSE;
      num_moves = 0;

      gen (&moves[0]);
      num_moves = numb_moves;
      captures = FALSE;

      for (i = 0; i < num_moves; i++) 
	{
	  make (&moves[0], i);
	  
	  /* check to see if our move is legal: */
	  if (check_legal (&moves[0], i, ic)) 
	    {
	      leg = TRUE;
	      unmake(&moves[0], i);
	      break;
	    };
	  
	  unmake(&moves[0], i);
	}
      
      if (leg == FALSE)
	{
	  captures = FALSE;
	  num_moves = 0;
	  gen (&moves[0]);
	  num_moves = numb_moves;
	}
    }
 
#ifdef PN2
  if (pn2)
#endif
    node->children = (node_t **) Xmalloc (num_moves * sizeof (node_t **));
#ifdef PN2
  else
    newchildren = (node_t **) malloc (num_moves * sizeof (node_t **));
#endif

  l = 0;

  for (i = 0; i < num_moves; i++)
    {
      hash_history[move_number+ply-1] = hash; 
      
      make (&moves[0], i);

      /* check to see if our move is legal: */
      if (check_legal (&moves[0], i, ic))
	{
#ifdef PN2
	  if (pn2)
#endif
	    newnode = (node_t *) Xmalloc (sizeof (node_t));
#ifdef PN2
	  else
	    newnode = (node_t *) malloc (sizeof (node_t));
#endif
	  newnode->value = 0;
#ifdef PN2
	  if (!pn2)
	    { 
	      newnode->proof = node->children[l]->proof;
	      newnode->disproof = node->children[l]->disproof;
	    }
	  else
	    {
#endif
	      newnode->proof = newnode->disproof = 1; 
#ifdef PN2
	    };
#endif

	  newnode->num_children = 0;
	  newnode->parent = node;
	  newnode->evaluated = FALSE;
	  newnode->expanded = FALSE;
	  newnode->move = moves[i];

#ifdef PN2
	  if (!pn2)
	    newchildren[l] = newnode;
	  else 
#endif
	    node->children[l] = newnode;	  	  

	  l++;
#ifdef PN2
	  if (pn2 == FALSE)
	    /*use delayed eval */;
	  else if (pn2)
#endif
	    pn_eval (newnode);
#ifdef PN2
	  if (pn2)
#endif
	    set_proof_and_disproof_numbers (newnode);

	  unmake (&moves[0], i);	 

	}
      else
	unmake (&moves[0], i);
    };

  node->expanded = TRUE;
  node->num_children = l;

#ifdef PN2
  if (!pn2)
    node->children = newchildren;
#endif
  
  /* account for stalemate ! */
  if (node->num_children == 0)
    {
      node->expanded = FALSE;
      node->evaluated = TRUE;
      if (Variant != Suicide && Variant != Losers)
      {
      	node->value = STALEMATE;
      }
      else
      {
	if (ToMove == root_to_move)
	{
	  node->value = TRUE;
	}
	else
	{
	  node->value = FALSE;
	}
      };
      
    };
#ifdef PN2
  if (pn2)
    nodecount2 += num_moves;
  else
#endif
    nodecount += num_moves;

  frees += num_moves;
  
  /*ep_square = ept;*/
#ifdef PN2
  if (!pn2) Xfree();
#endif
}

void update_ancestors (node_t * node)
{
  node_t *tnode, *prevnode;
  
  tnode = node;
  prevnode = node;

  while (tnode != 0)
    {
      set_proof_and_disproof_numbers (tnode);

      prevnode = tnode;

      if (tnode->move.target != 0)
	{			/* traverse */
	  unmake (&tnode->move, 0);
	}

      tnode = tnode->parent;
    };

  if (prevnode->move.target != 0)
    {
      make (&prevnode->move, 0);
    }

  return;

}

void 
pn2_eval (node_t * root)
{
  node_t *mostproving;
#ifdef PN2
  node_t *newroot;
#endif
  node_t *currentnode;
  node_t *oldparent;

  nodecount2 = 0;
  pn2 = TRUE;

  oldparent = root->parent;
  root->parent = 0;

  pn_eval (root);
  
  set_proof_and_disproof_numbers (root);

  currentnode = root;

  while (root->proof != 0 && root->disproof != 0 && nodecount2 < nodecount )
    {
      mostproving = select_most_proving (root);
      develop_node (mostproving);
      update_ancestors (mostproving);
    };

  root->expanded = FALSE;
  root->num_children = 0;

  root->parent = oldparent;

  pn2 = FALSE;
  
}

void proofnumberscan (void)
{
 move_s moves[MOVE_BUFF];
 int islegal[MOVE_BUFF];
 int nodesspent[MOVE_BUFF];
 int i, l, legal;
 int num_moves;
 rtime_t xstart_time;
 node_t *root;
 node_t *mostproving;
 node_t *currentnode;
 int leastlooked, leastlooked_l = 0, leastlooked_i = 0;
 int losers;
 int xnodecount;
 int firsts, alternates;
 char output[8];
 int ic;
 float bdp;
 int altlosers;
 
 xstart_time = rtime ();
 
 membuff = (unsigned char *) calloc(PBSize, sizeof(node_t));
 
 root = (node_t *) calloc (1, sizeof (node_t));

 gen (&moves[0]);
 num_moves = numb_moves;

 alllosers = FALSE;
 memset(rootlosers, 0, sizeof(rootlosers));
 memset(nodesspent, 0, sizeof(nodesspent));
 
 pn_move = dummy;
 
 legal = 0;

 ic = in_check();
 
 for (i = 0; i < num_moves; i++)
   {
     make (&moves[0], i);
     
     /* check to see if our move is legal: */
     if (check_legal (&moves[0], i, ic))
       {
	 legal++;
	 islegal[i] = 1;
       }
     else
       {
	 islegal[i] = 0;
       };
     
     unmake(&moves[0], i);
   }

 if (legal == 0)
 {
   Xfree();
   free(membuff);
   free(root);
   return;
 }
 
 losers = 0;
 
 nodecount = 1;
 iters = 0;
 maxply = 0;
 forwards = 0;
 firsts = 0;
 alternates = 0;
 hash_history[move_number+ply-1] = hash; 
 root_to_move = ToMove;

 pn_eval (root);

 if (root->value == TRUE || root->value == FALSE)
 {
   Xfree();
   free(membuff);
   free(root);
   pn_move = dummy;
   return;
  }

 set_proof_and_disproof_numbers (root);
     
 while ((rdifftime (rtime (), xstart_time) < pn_time) && !interrupt()
	&& ((unsigned)bufftop < ((PBSize-SAFETY) * sizeof(node_t))) 
	&& root->proof != 0 && root->disproof != 0)
   {
     
     iters++; 
     xnodecount = nodecount;

     if ((nodecount % 100) < 66)
       {
	 firsts++;
	 
	 /* pick normal pn move */
	 currentnode = root;
	 
	 mostproving = select_most_proving (currentnode);
	 develop_node (mostproving);
	 update_ancestors (mostproving);

	 /* what was the mostproving node ? */
	 i = 0;
	 while (root->children[i]->proof != root->proof) i++;

	 nodesspent[i] += nodecount - xnodecount;

	 if (root->proof == 0 && root->disproof == PN_INF)
	   {	 
	     forcedwin = TRUE;
	     
	     if (!kibitzed)
	       {
		 kibitzed = TRUE;
		 printf("tellics kibitz Forced win!\n");
	       }
              
	     pn_move = root->children[i]->move;

	   }
	 else if (root->disproof == 0 && root->proof == PN_INF)
	   {
	     pn_move = dummy;
	     losers++;
	   }
       } 
     else
       {
	 /* pick alternate move */
	 alternates++;

	 leastlooked = PN_INF;
         l = 0;
	 
	 for (i = 0; i < num_moves; i++)
	   {
	     if ((nodesspent[i] < leastlooked) && islegal[i] && !rootlosers[i])
	       {
		 leastlooked = nodesspent[i];
		 leastlooked_i = i;
		 leastlooked_l = l;
	       }
	     if (islegal[i]) l++;
	   }

	 if (leastlooked == PN_INF)
	 {
	   /*  could not find a nonlosing legal move */
	   nodecount += 30;
	   continue;
	 }
	  
	 make(&moves[0], leastlooked_i);

	 currentnode = root->children[leastlooked_l];
	 
	 mostproving = select_most_proving (currentnode);
	 develop_node (mostproving);
	 update_ancestors (mostproving);

	 nodesspent[leastlooked_i] += nodecount - xnodecount;
	 
	 /* should be back at root now */

	 if (root->children[leastlooked_l]->proof == 0 &&
	     root->children[leastlooked_l]->disproof == PN_INF)
	   {
	     /* alternate move was forced win */
	     forcedwin = TRUE;
	     
	     if (!kibitzed)
	       {
		 kibitzed = TRUE;
		 printf("tellics kibitz Forced win! (alt)\n");
	       }	     

	     pn_move = root->children[leastlooked_l]->move;
	   }
	 else if (root->children[leastlooked_l]->disproof == 0
	     &&   root->children[leastlooked_l]->proof == PN_INF)
	   {
	     /* alternate move loses */
	     rootlosers[leastlooked_i] = 1;
	     losers++;
	   }
       }             	
   };

 l = 0;
 bdp = -1;
 altlosers = 0;
 
 if (root->expanded)
 {
 for (i = 0; i < num_moves; i++)
 {
   if (islegal[i])
   {
     comp_to_san(moves[i], output);
     /*printf("checked %s, nodes: %d, pn: %d, dp: %d\n", 
         output, nodesspent[i], root->children[l]->proof, root->children[l]->disproof);
     */
	 
     if (root->children[l]->proof != 0)
     {
       if (((float)root->children[l]->disproof / (float)root->children[l]->proof) > bdp)
       {
         bdp = ((float)root->children[l]->disproof / (float)root->children[l]->proof);
	 pn_move = root->children[l]->move;
       }
       if ((root->children[l]->disproof == 0) && (root->children[l]->proof == PN_INF))
       {
	 altlosers++;
       }
     }
     else
     {
       forcedwin = TRUE;
       pn_move = root->children[l]->move;
       bdp = PN_INF;
     }
   l++;
   }
 }
 }

 comp_to_san(pn_move, output);

 if (xb_mode && post)
    printf ("tellics whisper proof %d, disproof %d, %d losers, highest depth %d, primary %d, secondary %d\n", root->proof, root->disproof, altlosers, maxply, firsts, alternates);

#if 0
 if (forcedwin && maxply == 0)
 {
	if (root_to_move == WHITE)
	{
	  result = black_is_mated;
	}
	else
	{
	  result = white_is_mated;
	}
 }
#endif
 
 if (altlosers == (legal - 1))
 {
   printf("tellics whisper Forced reply\n");
   
   for (i = 0; i < num_moves; i++)
       {
	 if (!rootlosers[i] && islegal[i])
	 {
	   /* not really forced win but setting this flag
	    * just means 'blindy trust pnsearch' */
	   forcedwin = TRUE;
	   pn_move = moves[i];
	   break;
	 }
       }
 }
 
 if (altlosers == legal)
 {
   alllosers = TRUE;
 }
 
 Xfree();
 free(membuff);
 free(root);

 return;
 
}
			     

void 
proofnumbersearch (void)
{
  node_t *root;
  node_t *mostproving;
  node_t *currentnode;
  rtime_t xstart_time;
  char output[8192];
  char PV[8192];
  int i;
  float bdp;
  int oldply;
  
  nodecount = 1;
  iters = 0;
  frees = 0;
  ply = 1;
  maxply = 0;
  forwards = 0;
  hash_history[move_number+ply-1] = hash; 
  root_to_move = ToMove;
  
  /*eps = ep_square;*/

  xstart_time = rtime ();

  root = (node_t *) calloc (1, sizeof (node_t));

  membuff = (unsigned char *) calloc(PBSize, sizeof(node_t));

  pn_eval (root);
  
  if (root->value == FALSE)
  {
    pn_move = dummy;
    Xfree();
    free(root);
    free(membuff);
    return;
  }

  set_proof_and_disproof_numbers (root);

  currentnode = root;

  while (root->proof != 0 && root->disproof != 0 
      && ((unsigned)bufftop < ((PBSize-SAFETY) * sizeof(node_t))))
    {
      mostproving = select_most_proving (currentnode);
      develop_node (mostproving);
      update_ancestors (mostproving);

      iters++;

#ifdef PN2
      if (iters)
#else
      if ((iters % 32) == 0)
#endif
      {
#ifdef PN2
	  printf("P: %d D: %d N: %d S: %d Mem: %2.2fM Iters: %d ", root->proof, root->disproof, nodecount, frees, (((nodecount) * sizeof(node_t) / (float)(1024*1024))), iters);
	  
	  printf ("PV: ");
	  
	  memset (output, 0, sizeof (output));
	  memset (PV, 0, sizeof (PV));
	  /*currentnode = root;*/
	  ply = 1;
	  
	  while (currentnode->expanded)
	    {
	      if (ToMove == root_to_move)
		{
		  i = 0;
		  while (currentnode->children[i]->proof != currentnode->proof)
		    {
		      i++;
		    };
		}
	      else
		{
		  i = 0;
		  while (currentnode->children[i]->disproof != currentnode->disproof)
		    {
		      i++;
		    }
		};
	      
	      currentnode = currentnode->children[i];
	      
	      comp_to_coord (currentnode->move, output);
	      printf ("%s ", output);
	      strcat (PV, output);
	      strcat (PV, " ");
	      
	      make (&currentnode->move, 0);
	    };
	  
	  while (currentnode != root)
	    {
	      unmake (&currentnode->move, 0);
	      currentnode = currentnode->parent;
	    };

	  printf("\n");
#endif
      	  if ((rdifftime (rtime (), xstart_time) > pn_time) && !interrupt())
       	    break;
	}
    };
  
  printf ("P: %d D: %d N: %d S: %d Mem: %2.2fM Iters: %d MaxDepth: %d\n", root->proof, root->disproof, nodecount, frees, (((nodecount) * sizeof (node_t) / (float) (1024 * 1024))), iters,maxply);

  if (xb_mode && post)
    printf ("tellics whisper proof %d, disproof %d, %d nodes, %d forwards, %d iters, highest depth %d\n", root->proof, root->disproof, nodecount, forwards, iters, maxply);
  
  if (!xb_mode)
    printf("Time : %f\n", (float)rdifftime(rtime(), xstart_time)/100.);
  
  while (currentnode != root)
    {
      unmake (&currentnode->move, 0);
      currentnode = currentnode->parent;
    };

  if (root->proof == 0)
    {
      root->value = TRUE;

      printf ("This position is WON.\n");
      printf ("PV: ");

      memset (output, 0, sizeof (output));
      memset (PV, 0, sizeof (PV));
      /*currentnode = root;*/
      ply = 1;
      
      while (currentnode->expanded)
	{
	  if (ToMove == root_to_move)
	    {
	      i = 0;
	      while (currentnode->children[i]->proof != currentnode->proof)
		{
		  i++;
		};
	    }
	  else
	    {
	      i = 0;
	      while (currentnode->children[i]->disproof != currentnode->disproof)
		{
		  i++;
		}
	    };

	  currentnode = currentnode->children[i];

	  comp_to_coord (currentnode->move, output);
	  printf ("%s ", output);
	  strcat (PV, output);
	  strcat (PV, " ");

	  make (&currentnode->move, 0);

	  if (ply == 1)
	    pn_move = currentnode->move;

	  forcedwin = TRUE;
	};

      oldply = ply;

      while (currentnode != root)
	{
	  unmake (&currentnode->move, 0);
	  currentnode = currentnode->parent;
	};

      if (!kibitzed && xb_mode && post)
	{
	  kibitzed = TRUE;
	  printf ("\ntellics kibitz Forced win in %d moves.\n", oldply/2);
	}

      if (oldply == 1 && (root->proof == 0 || root->disproof == 0)) 
      {
	if (root_to_move == WHITE)
	{
	  printf("\n1-0 {White mates}\n");
	  result = black_is_mated;
	}
	else
	{
	  printf("\n0-1 {Black mates}\n");
	  result = white_is_mated;
	}
      }

      printf ("\n");
    }
  else if (root->disproof == 0)
    {
      root->value = FALSE;
      printf ("This position is LOST.\n");

      pn_move = dummy;
    }
  else
    {
      root->value = UNKNOWN;
      printf ("This position is UNKNOWN.\n");

      pn_move = dummy;
    };

  /* find the move which is least likely to lose */
  bdp = -1;
  
  for (i = 0; i < root->num_children; i++)
    {
      if (root->children[i]->proof != 0)
      {
      	if (((float)(root->children[i]->disproof) / (float)(root->children[i]->proof)) > bdp)
	{
	  bdp = (float)root->children[i]->disproof / (float)(root->children[i]->proof);
	  pn_move = root->children[i]->move;
	}
      }
      else
      {
	pn_move = root->children[i]->move;
	break;
      }
    };

  pn_saver = pn_move;

  free(root);
  Xfree();
  free(membuff);
  
  /*ep_square = eps;*/

  return;
}

move_s proofnumbercheck(move_s compmove)
{
  node_t* root;
  node_t *mostproving;
  node_t *currentnode;
  rtime_t xstart_time;
  move_s resmove;
  
  if (piece_count <= 3 && (Variant == Suicide))
  {
    return compmove;
  }
  
  nodecount = 0;
  iters = 0;
  frees = 0;
  ply = 1;
  maxply = 0;
  
  /* make our move to check */
  make(&compmove, 0);

  hash_history[move_number+ply-1] = hash; 

  root_to_move = ToMove;
  
  /*eps = ep_square;*/
  
  xstart_time = rtime();
  
  root = (node_t *) calloc(1, sizeof(node_t));

  membuff = (unsigned char *) calloc(PBSize, sizeof(node_t));
  
  pn_eval(root);

  set_proof_and_disproof_numbers(root);

  currentnode = root;

  while (root->proof != 0 && root->disproof != 0
      && ((unsigned)bufftop < ((PBSize-SAFETY) * sizeof(node_t))))
    {
      mostproving = select_most_proving(currentnode);
      develop_node(mostproving);
      update_ancestors(mostproving);
	
      iters++;
      
      if ((iters % 32) == 0)
	{
	  /*	 printf("P: %d D: %d N: %d S: %d Mem: %2.2fM Iters: %d\n", root->proof, root->disproof, nodecount, frees, (((nodecount) * sizeof(node_t) / (float)(1024*1024))), iters); */
	  if ((rdifftime (rtime (), xstart_time) > pn_time))
	    break;
	}
    };

  printf("P: %d D: %d N: %d S: %d Mem: %2.2fM Iters: %d\n", root->proof, root->disproof, nodecount, frees, (((nodecount) * sizeof(node_t) / (float)(1024*1024))), iters);

  while(currentnode != root)
  {
    unmake(&currentnode->move, 0);
    currentnode = currentnode->parent;
  };  

  unmake(&compmove, 0);
  
  if (root->proof == 0)
    {
      /* ok big problem our ab move loses */
      root->value = TRUE;

      /* use best disprover instead */
      resmove = pn_move;

      s_threat = TRUE;
    }
  else if (root->disproof == 0)
    {
      /* ab move wins...unlikely due to earlier pnsearch */

      root->value = FALSE;
      resmove = compmove;

    }
  else
    {
      root->value = UNKNOWN;
      resmove = compmove;

    };

  Xfree();
  free(root);
  free(membuff);

  /*ep_square = eps;*/
  
  return resmove;
}

