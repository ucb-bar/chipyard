#ifndef __HMC_H
#define __HMC_H

#include <functional>
#include <map>
#include <vector>

#include "dram_system.h"

namespace dramsim3 {

enum class HMCReqType {
    RD0,
    RD16,
    RD32,
    RD48,
    RD64,
    RD80,
    RD96,
    RD112,
    RD128,
    RD256,
    WR0,
    WR16,
    WR32,
    WR48,
    WR64,
    WR80,
    WR96,
    WR112,
    WR128,
    WR256,
    P_WR16,
    P_WR32,
    P_WR48,
    P_WR64,
    P_WR80,
    P_WR96,
    P_WR112,
    P_WR128,
    P_WR256,
    // TODO haven't properly implement the following atomic operations
    ADD8,  // 2ADD8, cannot name it like that in c++...
    ADD16,
    P_2ADD8,  // 2 8Byte imm operands + 8 8Byte mem operands read then write
    P_ADD16,
    ADDS8R,  // 2ADD8, cannot name it like that...
    ADDS16R,
    INC8,  // read, return(the original), then write
    P_INC8, // read, return(the original), then posted write
    // boolean op on imm operand and mem operand, read update write
    XOR16,  
    OR16,
    NOR16,
    AND16,
    NAND16,
    // comparison instructions, not sure if there's write untill read done
    CASGT8,
    CASGT16,
    CASLT8,
    CASLT16,
    CASEQ8,
    CASZERO16,
    // eq, only read
    EQ8,
    EQ16,
    BWR,
    P_BWR,  // bit write, 8B mask, 8B value, read update write
    BWR8R,  // bit write with return
    SWAP16,  // swap imm operand and mem operand, read then write
    SIZE
};

enum class HMCRespType { NONE, RD_RS, WR_RS, ERR, SIZE };

// for future use
enum class HMCLinkType { HOST_TO_DEV, DEV_TO_DEV, SIZE };

class HMCRequest {
   public:
    HMCRequest(HMCReqType req_type, uint64_t hex_addr, int vault);
    HMCReqType type;
    uint64_t mem_operand;
    int link;
    int quad;
    int vault;
    int flits;
    bool is_write;
    // this exit_time is the time to exit xbar to vaults
    uint64_t exit_time;
};

class HMCResponse {
   public:
    HMCResponse(uint64_t id, HMCReqType reqtype, int dest_link, int src_quad);
    uint64_t resp_id;
    HMCRespType type;
    int link;
    int quad;
    int flits;
    // this exit_time is the time to exit xbar to cpu
    uint64_t exit_time;
};

class HMCMemorySystem : public BaseDRAMSystem {
   public:
    HMCMemorySystem(Config& config, const std::string& output_dir,
                    std::function<void(uint64_t)> read_callback,
                    std::function<void(uint64_t)> write_callback);
    ~HMCMemorySystem();
    // assuming there are 2 clock domains one for logic die one for DRAM
    // we can unify them as one but then we'll have to convert all the
    // slow dram time units to faster logic units...
    void ClockTick() override;

    // had to have 3 insert interfaces cuz HMC is so different...
    bool WillAcceptTransaction(uint64_t hex_addr, bool is_write) const override;
    bool AddTransaction(uint64_t hex_addr, bool is_write) override;
    bool InsertReqToLink(HMCRequest* req, int link);
    bool InsertHMCReq(HMCRequest* req);

   private:
    uint64_t logic_clk_, ps_per_dram_, ps_per_logic_, logic_ps_, dram_ps_;

    void SetClockRatio();
    void DRAMClockTick();
    void DrainRequests();
    void DrainResponses();
    void InsertReqToDRAM(HMCRequest* req);
    void VaultCallback(uint64_t req_id);
    std::vector<int> BuildAgeQueue(std::vector<int>& age_counter);
    void XbarArbitrate();
    inline void IterateNextLink();

    int next_link_;
    int links_;
    size_t queue_depth_;

    // number of flits xbar can process per logic cycle
    const int xbar_bandwidth_ = 2;

    // had to use a multimap because the controller callback return hex addr
    // instead of unique id
    std::multimap<uint64_t, HMCResponse*> resp_lookup_table_;
    // these are essentially input/output buffers for xbars
    std::vector<std::vector<HMCRequest*>> link_req_queues_;
    std::vector<std::vector<HMCResponse*>> link_resp_queues_;
    std::vector<std::vector<HMCRequest*>> quad_req_queues_;
    std::vector<std::vector<HMCResponse*>> quad_resp_queues_;

    // input/output busy indicators, since each packet could be several
    // flits, as long as this != 0 then they're busy
    std::vector<int> link_busy_;
    std::vector<int> quad_busy_ = {0, 0, 0, 0};
    // used for arbitration
    std::vector<int> link_age_counter_;
    std::vector<int> quad_age_counter_ = {0, 0, 0, 0};
};

}  // namespace dramsim3

#endif
