/* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */
/*                                                                           */
/*                  This file is part of the class library                   */
/*       SoPlex --- the Sequential object-oriented simPlex.                  */
/*                                                                           */
/*    Copyright (C) 2001-2002 Thorsten Koch                                  */
/*                  2001-2002 Konrad-Zuse-Zentrum                            */
/*                            fuer Informationstechnik Berlin                */
/*                                                                           */
/*  SoPlex is distributed under the terms of the ZIB Academic Licence.       */
/*                                                                           */
/*  You should have received a copy of the ZIB Academic License              */
/*  along with SoPlex; see the file COPYING. If not email to soplex@zib.de.  */
/*                                                                           */
/* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */
#ifndef SPEC_CPU
#pragma ident "@(#) $Id: mpsinput.cpp,v 1.6 2002/04/09 19:00:15 bzfkocht Exp $"
#endif

/**@file  mpsinput.cpp
 * @brief Read MPS format files.
 */
//#define DEBUGGING 1             // Setting this generates a lot of output

#include <assert.h>
#include <string.h>

#include "spxdefines.h"
#include "mpsinput.h"

#define PATCH_CHAR    '_'
#define BLANK         ' '

namespace soplex
{ 

/// fill the line from \p pos up to column 80 with blanks.
static void clear_from(char* buf, int pos)
{
   for(int i = pos; i < 80; i++)
      buf[i] = BLANK;
   buf[80] = '\0';
}

/// change all blanks inside a field to #PATCH_CHAR.
static void patch_field(char* buf, int beg, int end)
{
   while((beg <= end) && (buf[end] == BLANK))
      end--;

   while((beg <= end) && (buf[beg] == BLANK))
      beg++;

   for(int i = beg; i <= end; i++)
      if (buf[i] == BLANK)
         buf[i] = PATCH_CHAR;
}

/// read a MPS format data line and parse the fields.
bool MPSInput::readLine()
{
   int   len;
   int   space;
   char* s;
   bool  is_marker;

   do
   {
      m_f0 = m_f1 = m_f2 = m_f3 = m_f4 = m_f5 = 0;
      is_marker = false;
   
      // Read until we have a not comment line.
      do
      {
         if (!m_input.getline(m_buf, sizeof(m_buf)))
            return false;
        m_lineno++;

        DEBUG({ std::cerr << "Line " << m_lineno
                          << " " << m_buf << std::endl; });        
      } 
      while(*m_buf == '*');

      /* Normalize line
       */
      len = int(strlen(m_buf));

      for(int i = 0; i < len; i++)
         if ((m_buf[i] == '\t') || (m_buf[i] == '\n') || (m_buf[i] == '\r'))
            m_buf[i] = BLANK;
      
      if (len < 80)
         clear_from(m_buf, len);

      assert(strlen(m_buf) >= 80);

      /* Look for new section
       */
      if (*m_buf != BLANK)
      {
         m_f0 = strtok(&m_buf[0], " ");

         assert(m_f0 != 0);

         m_f1 = strtok(0, " ");

         return true;
      }

      /* Test for fixed format comments
       */
      if ((m_buf[14] == '$') && (m_buf[13] == ' '))
         clear_from(m_buf, 14);
      else if ((m_buf[39] == '$') && (m_buf[38] == ' '))
         clear_from(m_buf, 39);

      /* Test for fixed format
       */
      space = m_buf[12] | m_buf[13] 
         | m_buf[22] | m_buf[23] 
         | m_buf[36] | m_buf[37] | m_buf[38]
         | m_buf[47] | m_buf[48] 
         | m_buf[61] | m_buf[62] | m_buf[63];
      
      if (space == BLANK)
      {
         /* We assume fixed format, so we patch possible embedded spaces.
          */
         patch_field(m_buf,  4, 12);
         patch_field(m_buf, 14, 22);
         patch_field(m_buf, 39, 47);
      }
      s = &m_buf[1];
      
      /* At this point it is not clear if we have a indicator field.
       * If there is none (e.g. empty) f1 will be the first name field.
       * If there is one, f2 will be the first name field.
       * 
       * Initially comment marks '$' ar only allowed in the beginning
       * of the 2nd and 3rd name field. We test all fields but the first.
       * This makes no difference, since if the $ is at the start of a value
       * field, the line will be errornous anyway.
       */
      do
      {
         if (0 == (m_f1 = strtok(s, " ")))
            break;
         
         if ((0 == (m_f2 = strtok(0, " "))) || (*m_f2 == '$'))
         {
            m_f2 = 0;
            break;      
         }
         if (!strcmp(m_f2, "'MARKER'"))
            is_marker = true;
            
         if ((0 == (m_f3 = strtok(0, " "))) || (*m_f3 == '$'))
         {
            m_f3 = 0;
            break;      
         }
         if (is_marker)
            if (!strcmp(m_f3, "'INTORG'"))
               m_is_integer = true;
            else if (!strcmp(m_f3, "'INTEND'"))
               m_is_integer = false;
            else
               break; // unknown marker

         if (!strcmp(m_f3, "'MARKER'"))
            is_marker = true;

         if ((0 == (m_f4 = strtok(0, " "))) || (*m_f4 == '$'))
         {
            m_f4 = 0;
            break;      
         }
         if (is_marker)
            if (!strcmp(m_f4, "'INTORG'"))
               m_is_integer = true;
            else if (!strcmp(m_f4, "'INTEND'"))
               m_is_integer = false;
            else
               break; // unknown marker

         if ((0 == (m_f5 = strtok(0, " "))) || (*m_f5 == '$'))
            m_f5 = 0;
      }
      while(false);
   }
   while(is_marker);

   DEBUG({
      std::cerr << "-----------------------------------------------" 
                << std::endl
                << "f0=" << ((m_f0 == 0) ? "nil" : m_f0) << std::endl
                << "f1=" << ((m_f1 == 0) ? "nil" : m_f1) << std::endl
                << "f2=" << ((m_f2 == 0) ? "nil" : m_f2) << std::endl
                << "f3=" << ((m_f3 == 0) ? "nil" : m_f3) << std::endl
                << "f4=" << ((m_f4 == 0) ? "nil" : m_f4) << std::endl
                << "f5=" << ((m_f5 == 0) ? "nil" : m_f5) << std::endl
                << "-----------------------------------------------" 
                << std::endl;
   });

   return true;
}

/// Insert \p name as field 1 and shift all other fields up.
void MPSInput::insertName(const char* name, bool second)
{
   m_f5 = m_f4;
   m_f4 = m_f3;
   m_f3 = m_f2;

   if (second)
      m_f2 = name;
   else
   {
      m_f2 = m_f1;
      m_f1 = name;
   }
}

} // namespace soplex

//-----------------------------------------------------------------------------
//Emacs Local Variables:
//Emacs mode:c++
//Emacs c-basic-offset:3
//Emacs tab-width:8
//Emacs indent-tabs-mode:nil
//Emacs End:
//-----------------------------------------------------------------------------
