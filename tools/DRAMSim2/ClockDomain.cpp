#include "ClockDomain.h"

using namespace std;



namespace ClockDomain
{
	// "Default" crosser with a 1:1 ratio
	ClockDomainCrosser::ClockDomainCrosser(ClockUpdateCB *_callback)
		: callback(_callback), clock1(1UL), clock2(1UL), counter1(0UL), counter2(0UL)
	{
	}
	ClockDomainCrosser::ClockDomainCrosser(uint64_t _clock1, uint64_t _clock2, ClockUpdateCB *_callback) 
		: callback(_callback), clock1(_clock1), clock2(_clock2), counter1(0), counter2(0)
	{
		//cout << "CTOR: callback address: " << (uint64_t)(this->callback) << "\t ratio="<<clock1<<"/"<<clock2<< endl;
	}

	ClockDomainCrosser::ClockDomainCrosser(double ratio, ClockUpdateCB *_callback)
		: callback(_callback), counter1(0), counter2(0)
	{
		// Compute numerator and denominator for ratio, then pass that to other constructor.
		double x = ratio;

		const int MAX_ITER = 15;
		size_t i;
		unsigned ns[MAX_ITER], ds[MAX_ITER];
		double zs[MAX_ITER];
		ds[0] = 0;
		ds[1] = 1;
		zs[1] = x;
		ns[1] = (int)x; 

		for (i = 1; i<MAX_ITER-1; i++)
		{
			if (fabs(x - (double)ns[i]/(double)ds[i]) < 0.00005)
			{
				//printf("ANSWER= %u/%d\n",ns[i],ds[i]);
				break;
			}
			//TODO: or, if the answers are the same as the last iteration, stop 

			zs[i+1] = 1.0f/(zs[i]-(int)floor(zs[i])); // 1/(fractional part of z_i)
			ds[i+1] = ds[i]*(int)floor(zs[i+1])+ds[i-1];
			double tmp = x*ds[i+1];
			double tmp2 = tmp - (int)tmp;
			ns[i+1] = tmp2 >= 0.5 ? ceil(tmp) : floor(tmp); // ghetto implementation of a rounding function
			//printf("i=%lu, z=%20f n=%5u d=%5u\n",i,zs[i],ns[i],ds[i]);
		}

		//printf("APPROXIMATION= %u/%d\n",ns[i],ds[i]);
		this->clock1=ns[i];
		this->clock2=ds[i];

		//cout << "CTOR: callback address: " << (uint64_t)(this->callback) << "\t ratio="<<clock1<<"/"<<clock2<< endl;
	}

	void ClockDomainCrosser::update()
	{
		//short circuit case for 1:1 ratios
		if (clock1 == clock2 && callback)
		{
			(*callback)();
			return; 
		}

		// Update counter 1.
		counter1 += clock1;

		while (counter2 < counter1)
		{
			counter2 += clock2;
			//cout << "CALLBACK: counter1= " << counter1 << "; counter2= " << counter2 << "; " << endl;
			//cout << "callback address: " << (uint64_t)callback << endl;
			if (callback)
			{
				//cout << "Callback() " << (uint64_t)callback<< "Counters: 1="<<counter1<<", 2="<<counter2 <<endl;
				(*callback)();
			}
		}

		if (counter1 == counter2)
		{
			counter1 = 0;
			counter2 = 0;
		}
	}



	void TestObj::cb()
	{
			cout << "In Callback\n";
	}

	int TestObj::test()
	{
		ClockUpdateCB *callback = new Callback<TestObj, void>(this, &TestObj::cb);

		//ClockDomainCrosser x(5,2,&cb);
		//ClockDomainCrosser x(2,5,NULL);
		//ClockDomainCrosser x(37,41,NULL);
		//ClockDomainCrosser x(41,37,NULL);
		//cout << "(main) callback address: " << (uint64_t)&cb << endl;
		ClockDomainCrosser x(0.5, callback);
		cout <<"------------------------------------------\n";
		ClockDomainCrosser y(0.3333, callback);
		cout <<"------------------------------------------\n";
		ClockDomainCrosser z(0.9, callback);
		cout <<"------------------------------------------\n";


		for (int i=0; i<10; i++)
		{
			
			x.update();
			cout << "UPDATE: counter1= " << x.counter1 << "; counter2= " << x.counter2 << "; " << endl;
		}

		return 0;
	}


}
