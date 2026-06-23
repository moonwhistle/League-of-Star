package com.sang.leagueofstar.domain.customgame.repository;

import com.sang.leagueofstar.domain.customgame.domain.CustomGameParticipant;
import com.sang.leagueofstar.domain.customgame.domain.CustomGameRoom;
import com.sang.leagueofstar.domain.customgame.domain.vo.CustomRoomParticipantRole;
import com.sang.leagueofstar.domain.customgame.domain.vo.CustomRoomStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class CustomGameRoomRepositoryTest {

    @Autowired
    private CustomGameRoomRepository customGameRoomRepository;

    @Autowired
    private CustomGameParticipantRepository customGameParticipantRepository;

    @Test
    @DisplayName("WAITING room은 ownerUserId와 status로 중복 생성 여부를 확인한다")
    void existsByOwnerUserIdAndStatus() {
        // given
        CustomGameRoom waitingRoom = customGameRoomRepository.save(CustomGameRoom.create(1L, "AB12CD"));
        CustomGameRoom startedRoom = CustomGameRoom.create(2L, "EF34GH");
        startedRoom.markStarted(LocalDateTime.now());
        customGameRoomRepository.save(startedRoom);

        // when & then
        assertThat(customGameRoomRepository.existsByOwnerUserIdAndStatus(
                waitingRoom.getOwnerUserId(),
                CustomRoomStatus.WAITING
        )).isTrue();
        assertThat(customGameRoomRepository.existsByOwnerUserIdAndStatus(
                startedRoom.getOwnerUserId(),
                CustomRoomStatus.WAITING
        )).isFalse();
    }

    @Test
    @DisplayName("WAITING room 목록만 id 오름차순으로 조회한다")
    void findByStatusOrderByIdAsc() {
        // given
        CustomGameRoom firstWaitingRoom = customGameRoomRepository.save(CustomGameRoom.create(1L, "AB12CD"));
        CustomGameRoom startedRoom = CustomGameRoom.create(2L, "EF34GH");
        startedRoom.markStarted(LocalDateTime.now());
        customGameRoomRepository.save(startedRoom);
        CustomGameRoom secondWaitingRoom = customGameRoomRepository.save(CustomGameRoom.create(3L, "IJ56KL"));

        // when
        List<CustomGameRoom> result = customGameRoomRepository.findByStatusOrderByIdAsc(CustomRoomStatus.WAITING);

        // then
        assertThat(result).extracting(CustomGameRoom::getId)
                .containsExactly(firstWaitingRoom.getId(), secondWaitingRoom.getId());
    }

    @Test
    @DisplayName("inviteCode로 room을 조회하고 unique 제약으로 중복 발급을 막는다")
    void findByInviteCodeAndUniqueConstraint() {
        // given
        customGameRoomRepository.saveAndFlush(CustomGameRoom.create(1L, "AB12CD"));

        // when & then
        assertThat(customGameRoomRepository.findByInviteCode("AB12CD")).isPresent();
        assertThat(customGameRoomRepository.existsByInviteCode("AB12CD")).isTrue();
        assertThatThrownBy(() -> customGameRoomRepository.saveAndFlush(CustomGameRoom.create(2L, "AB12CD")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("participant는 customRoomId 간접참조로 조회하고 같은 room/user 중복을 막는다")
    void participantIndirectReference() {
        // given
        CustomGameRoom firstRoom = customGameRoomRepository.saveAndFlush(CustomGameRoom.create(1L, "AB12CD"));
        CustomGameRoom secondRoom = customGameRoomRepository.saveAndFlush(CustomGameRoom.create(2L, "EF34GH"));

        CustomGameParticipant owner = customGameParticipantRepository.save(CustomGameParticipant.create(
                firstRoom.getId(),
                1L,
                CustomRoomParticipantRole.OWNER
        ));
        CustomGameParticipant player = customGameParticipantRepository.save(CustomGameParticipant.create(
                firstRoom.getId(),
                3L,
                CustomRoomParticipantRole.PLAYER
        ));
        CustomGameParticipant otherOwner = customGameParticipantRepository.save(CustomGameParticipant.create(
                secondRoom.getId(),
                2L,
                CustomRoomParticipantRole.OWNER
        ));
        customGameParticipantRepository.flush();

        // when
        List<CustomGameParticipant> firstRoomParticipants =
                customGameParticipantRepository.findByCustomRoomIdOrderByIdAsc(firstRoom.getId());
        List<CustomGameParticipant> allParticipants =
                customGameParticipantRepository.findByCustomRoomIdInOrderByCustomRoomIdAscIdAsc(
                        List.of(firstRoom.getId(), secondRoom.getId())
                );

        // then
        assertThat(customGameParticipantRepository.countByCustomRoomId(firstRoom.getId())).isEqualTo(2);
        assertThat(customGameParticipantRepository.existsByCustomRoomIdAndUserId(firstRoom.getId(), 1L)).isTrue();
        assertThat(firstRoomParticipants).extracting(CustomGameParticipant::getId)
                .containsExactly(owner.getId(), player.getId());
        assertThat(allParticipants).extracting(CustomGameParticipant::getId)
                .containsExactly(owner.getId(), player.getId(), otherOwner.getId());
        assertThatThrownBy(() -> customGameParticipantRepository.saveAndFlush(CustomGameParticipant.create(
                firstRoom.getId(),
                1L,
                CustomRoomParticipantRole.PLAYER
        ))).isInstanceOf(DataIntegrityViolationException.class);
    }
}
