#include "hmc.h"

namespace dramsim3 {

HMCRequest::HMCRequest(HMCReqType req_type, uint64_t hex_addr, int vault)
    : type(req_type), mem_operand(hex_addr), vault(vault) {
    is_write = type >= HMCReqType::WR0 && type <= HMCReqType::P_WR256;
    // given that vaults could be 16 (Gen1) or 32(Gen2), using % 4
    // to partition vaults to quads
    quad = vault % 4;
    switch (req_type) {
        case HMCReqType::RD0:
        case HMCReqType::WR0:
            flits = 0;
            break;
        case HMCReqType::RD16:
        case HMCReqType::RD32:
        case HMCReqType::RD48:
        case HMCReqType::RD64:
        case HMCReqType::RD80:
        case HMCReqType::RD96:
        case HMCReqType::RD112:
        case HMCReqType::RD128:
        case HMCReqType::RD256:
            flits = 1;
            break;
        case HMCReqType::WR16:
        case HMCReqType::P_WR16:
            flits = 2;
            break;
        case HMCReqType::WR32:
        case HMCReqType::P_WR32:
            flits = 3;
            break;
        case HMCReqType::WR48:
        case HMCReqType::P_WR48:
            flits = 4;
            break;
        case HMCReqType::WR64:
        case HMCReqType::P_WR64:
            flits = 5;
            break;
        case HMCReqType::WR80:
        case HMCReqType::P_WR80:
            flits = 6;
            break;
        case HMCReqType::WR96:
        case HMCReqType::P_WR96:
            flits = 7;
            break;
        case HMCReqType::WR112:
        case HMCReqType::P_WR112:
            flits = 8;
            break;
        case HMCReqType::WR128:
        case HMCReqType::P_WR128:
            flits = 9;
            break;
        case HMCReqType::WR256:
        case HMCReqType::P_WR256:
            flits = 17;
            break;
        case HMCReqType::ADD8:
        case HMCReqType::ADD16:
            flits = 2;
            break;
        case HMCReqType::P_2ADD8:
        case HMCReqType::P_ADD16:
            flits = 2;
            break;
        case HMCReqType::ADDS8R:
        case HMCReqType::ADDS16R:
            flits = 2;
            break;
        case HMCReqType::INC8:
            flits = 1;
            break;
        case HMCReqType::P_INC8:
            flits = 1;
            break;
        case HMCReqType::XOR16:
        case HMCReqType::OR16:
        case HMCReqType::NOR16:
        case HMCReqType::AND16:
        case HMCReqType::NAND16:
        case HMCReqType::CASGT8:
        case HMCReqType::CASGT16:
        case HMCReqType::CASLT8:
        case HMCReqType::CASLT16:
        case HMCReqType::CASEQ8:
        case HMCReqType::CASZERO16:
            flits = 2;
            break;
        case HMCReqType::EQ8:
        case HMCReqType::EQ16:
        case HMCReqType::BWR:
            flits = 2;
            break;
        case HMCReqType::P_BWR:
            flits = 2;
            break;
        case HMCReqType::BWR8R:
        case HMCReqType::SWAP16:
            flits = 2;
            break;
        default:
            AbruptExit(__FILE__, __LINE__);
            break;
    }
}

HMCResponse::HMCResponse(uint64_t id, HMCReqType req_type, int dest_link,
                         int src_quad)
    : resp_id(id), link(dest_link), quad(src_quad) {
    switch (req_type) {
        case HMCReqType::RD0:
            type = HMCRespType::RD_RS;
            flits = 0;
            break;
        case HMCReqType::RD16:
            type = HMCRespType::RD_RS;
            flits = 2;
            break;
        case HMCReqType::RD32:
            type = HMCRespType::RD_RS;
            flits = 3;
            break;
        case HMCReqType::RD48:
            type = HMCRespType::RD_RS;
            flits = 4;
            break;
        case HMCReqType::RD64:
            type = HMCRespType::RD_RS;
            flits = 5;
            break;
        case HMCReqType::RD80:
            type = HMCRespType::RD_RS;
            flits = 6;
            break;
        case HMCReqType::RD96:
            type = HMCRespType::RD_RS;
            flits = 7;
            break;
        case HMCReqType::RD112:
            type = HMCRespType::RD_RS;
            flits = 8;
            break;
        case HMCReqType::RD128:
            type = HMCRespType::RD_RS;
            flits = 9;
            break;
        case HMCReqType::RD256:
            type = HMCRespType::RD_RS;
            flits = 17;
            break;
        case HMCReqType::WR0:
            flits = 0;
            type = HMCRespType::WR_RS;
            break;
        case HMCReqType::WR16:
        case HMCReqType::WR32:
        case HMCReqType::WR48:
        case HMCReqType::WR64:
        case HMCReqType::WR80:
        case HMCReqType::WR96:
        case HMCReqType::WR112:
        case HMCReqType::WR128:
        case HMCReqType::WR256:
            type = HMCRespType::WR_RS;
            flits = 1;
            break;
        case HMCReqType::P_WR16:
        case HMCReqType::P_WR32:
        case HMCReqType::P_WR48:
        case HMCReqType::P_WR64:
        case HMCReqType::P_WR80:
        case HMCReqType::P_WR96:
        case HMCReqType::P_WR112:
        case HMCReqType::P_WR128:
        case HMCReqType::P_WR256:
            type = HMCRespType::NONE;
            flits = 0;
            break;
        case HMCReqType::ADD8:
        case HMCReqType::ADD16:
            type = HMCRespType::WR_RS;
            flits = 1;
            break;
        case HMCReqType::P_2ADD8:
        case HMCReqType::P_ADD16:
            type = HMCRespType::NONE;
            flits = 0;
            break;
        case HMCReqType::ADDS8R:
        case HMCReqType::ADDS16R:
            type = HMCRespType::RD_RS;
            flits = 2;
            break;
        case HMCReqType::INC8:
            type = HMCRespType::WR_RS;
            flits = 1;
            break;
        case HMCReqType::P_INC8:
            type = HMCRespType::NONE;
            flits = 0;
            break;
        case HMCReqType::XOR16:
        case HMCReqType::OR16:
        case HMCReqType::NOR16:
        case HMCReqType::AND16:
        case HMCReqType::NAND16:
        case HMCReqType::CASGT8:
        case HMCReqType::CASGT16:
        case HMCReqType::CASLT8:
        case HMCReqType::CASLT16:
        case HMCReqType::CASEQ8:
        case HMCReqType::CASZERO16:
            type = HMCRespType::RD_RS;
            flits = 2;
            break;
        case HMCReqType::EQ8:
        case HMCReqType::EQ16:
        case HMCReqType::BWR:
            type = HMCRespType::WR_RS;
            flits = 1;
            break;
        case HMCReqType::P_BWR:
            type = HMCRespType::NONE;
            flits = 0;
            break;
        case HMCReqType::BWR8R:
        case HMCReqType::SWAP16:
            type = HMCRespType::RD_RS;
            flits = 2;
            break;
        default:
            AbruptExit(__FILE__, __LINE__);
            break;
    }
    return;
}

HMCMemorySystem::HMCMemorySystem(Config &config, const std::string &output_dir,
                                 std::function<void(uint64_t)> read_callback,
                                 std::function<void(uint64_t)> write_callback)
    : BaseDRAMSystem(config, output_dir, read_callback, write_callback),
      logic_clk_(0),
      logic_ps_(0),
      dram_ps_(0),
      next_link_(0) {
    // sanity check, this constructor should only be intialized using HMC
    if (!config_.IsHMC()) {
        std::cerr << "Initialzed an HMC system without an HMC config file!"
                  << std::endl;
        AbruptExit(__FILE__, __LINE__);
    }

    // setting up clock
    SetClockRatio();

    ctrls_.reserve(config_.channels);
    for (int i = 0; i < config_.channels; i++) {
#ifdef THERMAL
        ctrls_.push_back(new Controller(i, config_, timing_, thermal_calc_));
#else
        ctrls_.push_back(new Controller(i, config_, timing_));
#endif  // THERMAL
    }
    // initialize vaults and crossbar
    // the first layer of xbar will be num_links * 4 (4 for quadrants)
    // the second layer will be a 1:8 xbar
    // (each quadrant has 8 vaults and each quadrant can access any ohter
    // quadrant)
    queue_depth_ = static_cast<size_t>(config_.xbar_queue_depth);
    links_ = config_.num_links;
    link_req_queues_.reserve(links_);
    link_resp_queues_.reserve(links_);
    for (int i = 0; i < links_; i++) {
        link_req_queues_.push_back(std::vector<HMCRequest *>());
        link_resp_queues_.push_back(std::vector<HMCResponse *>());
    }

    // don't want to hard coding it but there are 4 quads so it's kind of fixed
    quad_req_queues_.reserve(4);
    quad_resp_queues_.reserve(4);
    for (int i = 0; i < 4; i++) {
        quad_req_queues_.push_back(std::vector<HMCRequest *>());
        quad_resp_queues_.push_back(std::vector<HMCResponse *>());
    }

    link_busy_.reserve(links_);
    link_age_counter_.reserve(links_);
    for (int i = 0; i < links_; i++) {
        link_busy_.push_back(0);
        link_age_counter_.push_back(0);
    }
}

HMCMemorySystem::~HMCMemorySystem() {
    for (auto &&vault_ptr : ctrls_) {
        delete (vault_ptr);
    }
}

void HMCMemorySystem::SetClockRatio() {
    // There are 3 clock domains here, Link (super fast), logic (fast), DRAM
    // (slow) We assume the logic process 1 flit per logic cycle and since the
    // link takes several cycles to process 1 flit (128b), we can deduce logic
    // speed according to link speed
    ps_per_dram_ = 800;  // 800 ps
    int link_cycles_per_flit = 128 / config_.link_width;
    int logic_speed = config_.link_speed / link_cycles_per_flit;  // MHz
    ps_per_logic_ =
        static_cast<uint64_t>(1000000 / static_cast<double>(logic_speed));
    if (ps_per_logic_ > ps_per_dram_) {
        ps_per_logic_ = ps_per_dram_;
    }
    return;
}

inline void HMCMemorySystem::IterateNextLink() {
    // determinining which link a request goes to has great impact on
    // performance round robin , we can implement other schemes here later such
    // as random but there're only at most 4 links so I suspect it would make a
    // difference
    next_link_ = (next_link_ + 1) % links_;
    return;
}

bool HMCMemorySystem::WillAcceptTransaction(uint64_t hex_addr,
                                            bool is_write) const {
    bool insertable = false;
    for (auto link_queue = link_req_queues_.begin();
         link_queue != link_req_queues_.end(); link_queue++) {
        if ((*link_queue).size() < queue_depth_) {
            insertable = true;
            break;
        }
    }
    return insertable;
}

bool HMCMemorySystem::AddTransaction(uint64_t hex_addr, bool is_write) {
    // to be compatible with other protocol we have this interface
    // when using this intreface the size of each transaction will be block_size
    HMCReqType req_type;
    if (is_write) {
        switch (config_.block_size) {
            case 0:
                req_type = HMCReqType::WR0;
                break;
            case 32:
                req_type = HMCReqType::WR32;
                break;
            case 64:
                req_type = HMCReqType::WR64;
                break;
            case 128:
                req_type = HMCReqType::WR128;
                break;
            case 256:
                req_type = HMCReqType::WR256;
                break;
            default:
                req_type = HMCReqType::SIZE;
                AbruptExit(__FILE__, __LINE__);
                break;
        }
    } else {
        switch (config_.block_size) {
            case 0:
                req_type = HMCReqType::RD0;
                break;
            case 32:
                req_type = HMCReqType::RD32;
                break;
            case 64:
                req_type = HMCReqType::RD64;
                break;
            case 128:
                req_type = HMCReqType::RD128;
                break;
            case 256:
                req_type = HMCReqType::RD256;
                break;
            default:
                req_type = HMCReqType::SIZE;
                AbruptExit(__FILE__, __LINE__);
                break;
        }
    }
    int vault = GetChannel(hex_addr);
    HMCRequest *req = new HMCRequest(req_type, hex_addr, vault);
    return InsertHMCReq(req);
}

bool HMCMemorySystem::InsertReqToLink(HMCRequest *req, int link) {
    // These things need to happen when an HMC request is inserted to a link:
    // 1. check if link queue full
    // 2. set link field in the request packet
    // 3. create corresponding response
    // 4. increment link_age_counter_ so that arbitrate logic works
    if (link_req_queues_[link].size() < queue_depth_) {
        req->link = link;
        link_req_queues_[link].push_back(req);
        HMCResponse *resp =
            new HMCResponse(req->mem_operand, req->type, link, req->quad);
        resp_lookup_table_.insert(
            std::pair<uint64_t, HMCResponse *>(resp->resp_id, resp));
        link_age_counter_[link] = 1;
        // stats_.interarrival_latency.AddValue(clk_ - last_req_clk_);
        last_req_clk_ = clk_;
        return true;
    } else {
        return false;
    }
}

bool HMCMemorySystem::InsertHMCReq(HMCRequest *req) {
    // most CPU models does not support simultaneous insertions
    // if you want to actually simulate the multi-link feature
    // then you have to call this function multiple times in 1 cycle
    // TODO put a cap limit on how many times you can call this function per
    // cycle
    bool is_inserted = InsertReqToLink(req, next_link_);
    if (!is_inserted) {
        int start_link = next_link_;
        IterateNextLink();
        while (start_link != next_link_) {
            if (InsertReqToLink(req, next_link_)) {
                IterateNextLink();
                return true;
            } else {
                IterateNextLink();
            }
        }
        return false;
    } else {
        IterateNextLink();
        return true;
    }
}

void HMCMemorySystem::DrainRequests() {
    // drain quad request queue to vaults
    for (int i = 0; i < 4; i++) {
        if (!quad_req_queues_[i].empty() &&
            quad_resp_queues_[i].size() < queue_depth_) {
            HMCRequest *req = quad_req_queues_[i].front();
            if (req->exit_time <= logic_clk_) {
                if (ctrls_[req->vault]->WillAcceptTransaction(req->mem_operand,
                                                              req->is_write)) {
                    InsertReqToDRAM(req);
                    delete (req);
                    quad_req_queues_[i].erase(quad_req_queues_[i].begin());
                }
            }
        }
    }

    // drain xbar
    for (auto &&i : quad_busy_) {
        if (i > 0) {
            i -= 2;
        }
    }

    // drain requests from link to quad buffers
    std::vector<int> age_queue = BuildAgeQueue(link_age_counter_);
    while (!age_queue.empty()) {
        int src_link = age_queue.front();
        int dest_quad = link_req_queues_[src_link].front()->quad;
        if (quad_req_queues_[dest_quad].size() < queue_depth_ &&
            quad_busy_[dest_quad] <= 0) {
            HMCRequest *req = link_req_queues_[src_link].front();
            link_req_queues_[src_link].erase(
                link_req_queues_[src_link].begin());
            quad_req_queues_[dest_quad].push_back(req);
            quad_busy_[dest_quad] = req->flits;
            req->exit_time = logic_clk_ + req->flits;
            if (link_req_queues_[src_link].empty()) {
                link_age_counter_[src_link] = 0;
            } else {
                link_age_counter_[src_link] = 1;
            }
        } else {  // stalled this cycle, update age counter
            link_age_counter_[src_link]++;
        }
        age_queue.erase(age_queue.begin());
    }
    age_queue.clear();
}

void HMCMemorySystem::DrainResponses() {
    // Link resp to CPU
    for (int i = 0; i < links_; i++) {
        if (!link_resp_queues_[i].empty()) {
            HMCResponse *resp = link_resp_queues_[i].front();
            if (resp->exit_time <= logic_clk_) {
                if (resp->type == HMCRespType::RD_RS) {
                    read_callback_(resp->resp_id);
                } else {
                    write_callback_(resp->resp_id);
                }
                delete (resp);
                link_resp_queues_[i].erase(link_resp_queues_[i].begin());
            }
        }
    }

    // drain xbar
    for (auto &&i : link_busy_) {
        if (i > 0) {
            i -= 2;
        }
    }

    // drain responses from quad to link buffers
    auto age_queue = BuildAgeQueue(quad_age_counter_);
    while (!age_queue.empty()) {
        int src_quad = age_queue.front();
        int dest_link = quad_resp_queues_[src_quad].front()->link;
        if (link_resp_queues_[dest_link].size() < queue_depth_ &&
            link_busy_[dest_link] <= 0) {
            HMCResponse *resp = quad_resp_queues_[src_quad].front();
            quad_resp_queues_[src_quad].erase(
                quad_resp_queues_[src_quad].begin());
            link_resp_queues_[dest_link].push_back(resp);
            link_busy_[dest_link] = resp->flits;
            resp->exit_time = logic_clk_ + resp->flits;
            if (quad_resp_queues_[src_quad].size() == 0) {
                quad_age_counter_[src_quad] = 0;
            } else {
                quad_age_counter_[src_quad] = 1;
            }
        } else {  // stalled this cycle, update age counter
            quad_age_counter_[src_quad]++;
        }
        age_queue.erase(age_queue.begin());
    }
    age_queue.clear();
}

void HMCMemorySystem::DRAMClockTick() {
    for (size_t i = 0; i < ctrls_.size(); i++) {
        // look ahead and return earlier
        while (true) {
            auto pair = ctrls_[i]->ReturnDoneTrans(clk_);
            if (pair.second == 1) {  // write
                VaultCallback(pair.first);
            } else if (pair.second == 0) {  // read
                VaultCallback(pair.first);
            } else {
                break;
            }
        }
    }
    for (size_t i = 0; i < ctrls_.size(); i++) {
        ctrls_[i]->ClockTick();
    }
    clk_++;

    if (clk_ % config_.epoch_period == 0) {
        PrintEpochStats();
    }
    return;
}

void HMCMemorySystem::ClockTick() {
    if (dram_ps_ == logic_ps_) {
        DrainResponses();
        DRAMClockTick();
        DrainRequests();
        logic_ps_ += ps_per_logic_;
        logic_clk_ += 1;
    } else {
        DRAMClockTick();
    }
    while (logic_ps_ < dram_ps_ + ps_per_dram_) {
        DrainResponses();
        DrainRequests();
        logic_ps_ += ps_per_logic_;
        logic_clk_ += 1;
    }
    dram_ps_ += ps_per_dram_;
    return;
}

std::vector<int> HMCMemorySystem::BuildAgeQueue(std::vector<int> &age_counter) {
    // return a vector of indices sorted in decending order
    // meaning that the oldest age link/quad should be processed first
    std::vector<int> age_queue;
    int queue_len = age_counter.size();
    age_queue.reserve(queue_len);
    int start_pos = logic_clk_ % queue_len;  // round robin start pos
    for (int i = 0; i < queue_len; i++) {
        int pos = (i + start_pos) % queue_len;
        if (age_counter[pos] > 0) {
            bool is_inserted = false;
            for (auto it = age_queue.begin(); it != age_queue.end(); it++) {
                if (age_counter[pos] > *it) {
                    age_queue.insert(it, pos);
                    is_inserted = true;
                    break;
                }
            }
            if (!is_inserted) {
                age_queue.push_back(pos);
            }
        }
    }
    return age_queue;
}

void HMCMemorySystem::InsertReqToDRAM(HMCRequest *req) {
    Transaction trans(req->mem_operand, req->is_write);
    ctrls_[req->vault]->AddTransaction(trans);
    return;
}

void HMCMemorySystem::VaultCallback(uint64_t req_id) {
    // we will use hex addr as the req_id and use a multimap to lookup the
    // requests the vaults cannot directly talk to the CPU so this callback will
    // be passed to the vaults and is responsible to put the responses back to
    // response queues

    auto it = resp_lookup_table_.find(req_id);
    HMCResponse *resp = it->second;
    // all data from dram received, put packet in xbar and return
    resp_lookup_table_.erase(it);
    // put it in xbar
    quad_resp_queues_[resp->quad].push_back(resp);
    quad_age_counter_[resp->quad] = 1;
    return;
}

}  // namespace dramsim3
