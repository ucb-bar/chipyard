#include "stdio.h"
#include "rocc.h"
#include "encoding.h"
// #include "mmio.h"
#define MAXSIZE 4096
#define N 16
int main(void){
    long long a[MAXSIZE] = {0};
    long long b[MAXSIZE] = {0};
    long long len = 0;
    int ntests = 100;
    long long start, end;
    long long rcycle_c=0, wcycle_c=0, rcycle_d=0, wcycle_d=0;
    int size[N] = {1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048, 2560, 3072, 3584, 4095};
    long long dmaR[N] = {0};
    long long dmaW[N] = {0};
    long long cacheR[N] = {0};
    long long cacheW[N] = {0};
    
    for(int k=0; k<N; k++){
        if(1){
            for(int t=0; t<ntests; t++){
                // 
                for (int j = 0; j < size[k]; j++){
                    a[j] = (t+1)*(j+1); 
                }
                len = size[k];
                // for(int m=0; m<SIZE; m++){
                //     printf("a[%d]=%lld\t",m,a[m]);
                // }
                // printf("\n");
        //--------------------------------------DMA------------------------------
                // 
                start = rdcycle();
                asm volatile("fence");
                ROCC_INSTRUCTION_SS(0,len,a,0);
                asm volatile("fence":::"memory");
                end = rdcycle();
                dmaR[k] += end - start;

                // 
                start = rdcycle();
                asm volatile("fence");
                ROCC_INSTRUCTION_SS(0,len,b,1);
                asm volatile("fence":::"memory");
                end = rdcycle();
                dmaW[k] += end - start;

                // 
                for (int i = 0; i < size[k]; i++){
                    if(a[i]!=b[i]){
                        printf("error: i=%d, a=%lld, b=%lld\n", i, a[i], b[i]);
                        return 0;
                    }        
                }

            
        //------------------------------------Cache------------------------------------       
                // 
                start = rdcycle();
                asm volatile("fence");
                ROCC_INSTRUCTION_SS(0,len,a,2);
                asm volatile("fence":::"memory");
                end = rdcycle();
                cacheR[k] += end - start;


                // 
                start = rdcycle();
                asm volatile("fence");
                ROCC_INSTRUCTION_SS(0,len,b,3);
                asm volatile("fence":::"memory");
                end = rdcycle();            
                cacheW[k] += end - start;

                // 
                for (int i = 0; i < size[k]; i++){
                    if(a[i]!=b[i]){
                        printf("error: i=%d, a=%lld, b=%lld\n", i, a[i], b[i]);
                        return 0;
                    }        
                }
                

                // for(int n=0; n<SIZE; n++){
                //     printf("b[%d]=%lld\t",n,b[n]);
                // }
                // printf("\n");
                    
            }
            printf("test %d: pass!\n", k); 
        }
         
    }
//---------------------------------------printf 结果 -----------------------------------------------------------------------
    printf("DMA read: ");
    for(int i=0; i<N; i++){
        printf("%lld,", dmaR[i]/ntests);
    }
    printf("\n");

    printf("DMA write: ");
    for(int i=0; i<N; i++){
        printf("%lld,", dmaW[i]/ntests);
    }
    printf("\n");

    printf("Cache read: ");
    for(int i=0; i<N; i++){
        printf("%lld,", cacheR[i]/ntests);
    }
    printf("\n");

    printf("Cache writer: ");
    for(int i=0; i<N; i++){
        printf("%lld,", cacheW[i]/ntests);
    }
    printf("\n");

    return 0;    
}
