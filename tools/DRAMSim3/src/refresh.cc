#include "refresh.h"

namespace dramsim3 {
Refresh::Refresh(const Config &config, ChannelState &channel_state)
    : clk_(0),
      config_(config),
      channel_state_(channel_state),
      refresh_policy_(config.refresh_policy),
      next_rank_(0),
      next_bg_(0),
      next_bank_(0) {
    if (refresh_policy_ == RefreshPolicy::RANK_LEVEL_SIMULTANEOUS) {
        refresh_interval_ = config_.tREFI;
    } else if (refresh_policy_ == RefreshPolicy::BANK_LEVEL_STAGGERED) {
        refresh_interval_ = config_.tREFIb;
    } else {  // default refresh scheme: RANK STAGGERED
        refresh_interval_ = config_.tREFI / config_.ranks;
    }
}

void Refresh::ClockTick() {
    if (clk_ % refresh_interval_ == 0 && clk_ > 0) {
        InsertRefresh();
    }
    clk_++;
    return;
}

void Refresh::InsertRefresh() {
    switch (refresh_policy_) {
        // Simultaneous all rank refresh
        case RefreshPolicy::RANK_LEVEL_SIMULTANEOUS:
            for (auto i = 0; i < config_.ranks; i++) {
                if (!channel_state_.IsRankSelfRefreshing(i)) {
                    channel_state_.RankNeedRefresh(i, true);
                    break;
                }
            }
            break;
        // Staggered all rank refresh
        case RefreshPolicy::RANK_LEVEL_STAGGERED:
            if (!channel_state_.IsRankSelfRefreshing(next_rank_)) {
                channel_state_.RankNeedRefresh(next_rank_, true);
            }
            IterateNext();
            break;
        // Fully staggered per bank refresh
        case RefreshPolicy::BANK_LEVEL_STAGGERED:
            if (!channel_state_.IsRankSelfRefreshing(next_rank_)) {
                channel_state_.BankNeedRefresh(next_rank_, next_bg_, next_bank_,
                                               true);
            }
            IterateNext();
            break;
        default:
            AbruptExit(__FILE__, __LINE__);
            break;
    }
    return;
}

void Refresh::IterateNext() {
    switch (refresh_policy_) {
        case RefreshPolicy::RANK_LEVEL_STAGGERED:
            next_rank_ = (next_rank_ + 1) % config_.ranks;
            return;
        case RefreshPolicy::BANK_LEVEL_STAGGERED:
            // Note - the order issuing bank refresh commands is static and
            // non-configurable as per JEDEC standard
            next_bg_ = (next_bg_ + 1) % config_.bankgroups;
            if (next_bg_ == 0) {
                next_bank_ = (next_bank_ + 1) % config_.banks_per_group;
                if (next_bank_ == 0) {
                    next_rank_ = (next_rank_ + 1) % config_.ranks;
                }
            }
            return;
        default:
            AbruptExit(__FILE__, __LINE__);
            return;
    }
}

}  // namespace dramsim3
