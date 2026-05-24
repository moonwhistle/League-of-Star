package com.sang.smite.domain.record.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class GameRecordRankSettlementService {

    /**
     * 이미 FINISHED로 확정된 gameRoom을 기준으로 record/rank 정산을 수행합니다.
     *
     * <p>gameRoom 종료 transaction과 분리된 별도 transaction 경계입니다.</p>
     */
    public void settleFinishedGameRoom(Long gameRoomId) {
        // Step 9 task 1 only fixes the transaction boundary.
        // Record/rank mutations are added by the following Issue 52 tasks.
    }
}
