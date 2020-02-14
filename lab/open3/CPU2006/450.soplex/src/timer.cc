/* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */
/*                                                                           */
/*                  This file is part of the class library                   */
/*       SoPlex --- the Sequential object-oriented simPlex.                  */
/*                                                                           */
/*    Copyright (C) 1997-1999 Roland Wunderling                              */
/*                  1997-2002 Konrad-Zuse-Zentrum                            */
/*                            fuer Informationstechnik Berlin                */
/*                                                                           */
/*  SoPlex is distributed under the terms of the ZIB Academic Licence.       */
/*                                                                           */
/*  You should have received a copy of the ZIB Academic License              */
/*  along with SoPlex; see the file COPYING. If not email to soplex@zib.de.  */
/*                                                                           */
/* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */
#ifndef SPEC_CPU
#pragma ident "@(#) $Id: timer.cpp,v 1.9 2002/03/03 13:50:36 bzfkocht Exp $"
#endif

#include <assert.h>

#ifdef _WIN32

#include <time.h>

#else   // !_WIN32 

#include <sys/types.h>
#include <sys/times.h>
//#include <sys/param.h>
#include <limits.h>
#include <unistd.h>

#endif  // !_WIN32 

#include "spxdefines.h"
#include "timer.h"

namespace soplex
{
/* determine TIMES_TICKS_PER_SEC for clock ticks delivered by times().
 * (don't use CLOCKS_PER_SEC since this is related to clock() only).
 */
#if defined(CLK_TCK)
#define TIMES_TICKS_PER_SEC CLK_TCK
#elif defined(_SC_CLK_TCK)
#if defined(__riscv) && !defined(__linux)
#define TIMES_TICKS_PER_SEC 1000000U
#else
#define TIMES_TICKS_PER_SEC sysconf(_SC_CLK_TCK)
#endif
#elif defined(HZ)
#define TIMES_TICKS_PER_SEC HZ
#else // !CLK_TCK && !_SC_CLK_TCK && !HZ 
#define TIMES_TICKS_PER_SEC 60
#endif // !CLK_TCK && !_SC_CLK_TCK && !HZ 

const long Timer::ticks_per_sec = long(TIMES_TICKS_PER_SEC);

// get actual user, system and real time from system 
void Timer::updateTicks() const
{
#ifdef  _WIN32

   sTicks = 0;
   uTicks = rTicks = clock();

#else   /* !_WIN32 */

   struct tms now;
   clock_t    ret = times(&now);

   if (ret == -1)
      now.tms_utime = now.tms_stime = ret = 0;

   uTicks = long(now.tms_utime);
   sTicks = long(now.tms_stime);
   rTicks = long(ret);

#endif  /* !_WIN32 */
}

// start timer, resume accounting user, system and real time.
void Timer::start()
{
   // ignore start request if timer is runnning 
   if (status != RUNNING)
   {
      updateTicks();
      
      uAccount -= uTicks;
      sAccount -= sTicks;
      rAccount -= rTicks;
      status    = RUNNING;
   }
}

// stop timer, return accounted user time.
Real Timer::stop()
{
   // status remains unchanged if timer is not running 
   if (status == RUNNING)
   {
      updateTicks();
      
      uAccount += uTicks;
      sAccount += sTicks;
      rAccount += rTicks;
      status    = STOPPED;
   }
   return ticks2sec(uAccount);
}

// get accounted user, system or real time.
void Timer::getTimes(
   Real* uTime,
   Real* sTime,
   Real* rTime) const
{
   if (status != RUNNING)
   {
      if (uTime)
         *uTime = ticks2sec(uAccount);
      if (sTime)
         *sTime = ticks2sec(sAccount);
      if (rTime)
         *rTime = ticks2sec(rAccount);
   }
   else
   {
      updateTicks();

      if (uTime)
         *uTime = ticks2sec(uTicks + uAccount);
      if (sTime)
         *sTime = ticks2sec(sTicks + sAccount);
      if (rTime)
         *rTime = ticks2sec(rTicks + rAccount);
   }
   assert((uTime == 0) || (*uTime >= 0.0));
   assert((sTime == 0) || (*sTime >= 0.0));
   assert((rTime == 0) || (*rTime >= 0.0));
}

// return user time accounted by timer
Real Timer::userTime() const
{
   Real uTime;

   getTimes(&uTime, 0, 0);

   return uTime;
}

// return system time accounted by timer
Real Timer::systemTime() const
{
   Real sTime;

   getTimes(0, &sTime, 0);

   return sTime;
}

// return real time accounted by timer
Real Timer::realTime() const
{
   Real rTime;

   getTimes(0, 0, &rTime);
   
   return rTime;
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
