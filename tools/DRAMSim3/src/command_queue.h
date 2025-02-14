#ifndef __COMMAND_QUEUE_H
#define __COMMAND_QUEUE_H

#include <unordered_set>
#include <vector>
#include "channel_state.h"
#include "common.h"
#include "configuration.h"
#include "simple_stats.h"

namespace dramsim3 {

using CMDIterator = std::vector<Command>::iterator;
using CMDQueue = std::vector<Command>;
enum class QueueStructure { PER_RANK, PER_BANK, SIZE };

class CommandQueue {
   public:
    CommandQueue(int channel_id, const Config& config,
                 const ChannelState& channel_state, SimpleStats& simple_stats);
    Command GetCommandToIssue();
    Command FinishRefresh();
    void ClockTick() { clk_ += 1; };
    bool WillAcceptCommand(int rank, int bankgroup, int bank) const;
    bool AddCommand(Command cmd);
    bool QueueEmpty() const;
    int QueueUsage() const;
    std::vector<bool> rank_q_empty;

   private:
    bool ArbitratePrecharge(const CMDIterator& cmd_it,
                            const CMDQueue& queue) const;
    bool HasRWDependency(const CMDIterator& cmd_it,
                         const CMDQueue& queue) const;
    Command GetFirstReadyInQueue(CMDQueue& queue) const;
    int GetQueueIndex(int rank, int bankgroup, int bank) const;
    CMDQueue& GetQueue(int rank, int bankgroup, int bank);
    CMDQueue& GetNextQueue();
    void GetRefQIndices(const Command& ref);
    void EraseRWCommand(const Command& cmd);
    Command PrepRefCmd(const CMDIterator& it, const Command& ref) const;

    QueueStructure queue_structure_;
    const Config& config_;
    const ChannelState& channel_state_;
    SimpleStats& simple_stats_;

    std::vector<CMDQueue> queues_;

    // Refresh related data structures
    std::unordered_set<int> ref_q_indices_;
    bool is_in_ref_;

    int num_queues_;
    size_t queue_size_;
    int queue_idx_;
    uint64_t clk_;
};

}  // namespace dramsim3
#endif
