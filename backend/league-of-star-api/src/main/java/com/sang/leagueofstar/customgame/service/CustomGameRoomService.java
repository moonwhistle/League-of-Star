package com.sang.leagueofstar.customgame.service;

import com.sang.leagueofstar.common.exception.ApiErrorCode;
import com.sang.leagueofstar.common.exception.ApiException;
import com.sang.leagueofstar.common.exception.CoreErrorCode;
import com.sang.leagueofstar.common.exception.CoreException;
import com.sang.leagueofstar.customgame.controller.response.CustomGameStartResponse;
import com.sang.leagueofstar.customgame.controller.response.CustomRoomListItemResponse;
import com.sang.leagueofstar.customgame.controller.response.CustomRoomListResponse;
import com.sang.leagueofstar.customgame.controller.response.CustomRoomParticipantResponse;
import com.sang.leagueofstar.customgame.controller.response.CustomRoomResponse;
import com.sang.leagueofstar.customgame.websocket.service.CustomRoomWebSocketNotifier;
import com.sang.leagueofstar.domain.customgame.domain.CustomGameParticipant;
import com.sang.leagueofstar.domain.customgame.domain.CustomGameRoom;
import com.sang.leagueofstar.domain.customgame.service.CustomGameRoomCommandService;
import com.sang.leagueofstar.domain.customgame.service.CustomGameRoomReadService;
import com.sang.leagueofstar.domain.customgame.service.dto.CustomGameRoomJoinResult;
import com.sang.leagueofstar.domain.customgame.service.dto.CustomGameRoomStartResult;
import com.sang.leagueofstar.domain.game.domain.GameRoom;
import com.sang.leagueofstar.domain.game.domain.vo.GameMode;
import com.sang.leagueofstar.domain.game.service.GameRoomCommandService;
import com.sang.leagueofstar.domain.game.service.GameRoomReadService;
import com.sang.leagueofstar.domain.user.domain.User;
import com.sang.leagueofstar.domain.user.service.UserReadService;
import com.sang.leagueofstar.game.end.service.GameEndScheduleService;
import com.sang.leagueofstar.game.start.common.constant.GameStartConstants;
import com.sang.leagueofstar.game.start.dto.GameStartScenarioPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomGameRoomService {

    private static final String ROOM_NAME_FORMAT = "%s's room";
    private static final String GAME_WEB_SOCKET_URL_FORMAT = "/ws/game/%d";

    private final CustomGameRoomCommandService customGameRoomCommandService;
    private final CustomGameRoomReadService customGameRoomReadService;
    private final GameRoomReadService gameRoomReadService;
    private final GameRoomCommandService gameRoomCommandService;
    private final GameEndScheduleService gameEndScheduleService;
    private final UserReadService userReadService;
    private final CustomRoomWebSocketNotifier customRoomWebSocketNotifier;
    private final Clock clock;

    public CustomRoomResponse createRoom(Long ownerUserId) {
        CustomGameRoom room = customGameRoomCommandService.createRoom(ownerUserId);
        return toRoomResponse(room, customGameRoomReadService.getParticipants(room.getId()));
    }

    public CustomRoomListResponse getPublicRooms() {
        List<CustomGameRoom> rooms = customGameRoomReadService.findWaitingRooms();
        Map<Long, List<CustomGameParticipant>> participantsByRoomId = findParticipantsByRoomId(rooms);
        Map<Long, User> ownersById = findUsersById(rooms.stream()
                .map(CustomGameRoom::getOwnerUserId)
                .collect(Collectors.toCollection(LinkedHashSet::new)));

        List<CustomRoomListItemResponse> roomResponses = rooms.stream()
                .map(room -> CustomRoomListItemResponse.from(
                        room,
                        roomName(resolveUser(ownersById, room.getOwnerUserId())),
                        participantsByRoomId.getOrDefault(room.getId(), List.of()).size()
                ))
                .toList();
        return new CustomRoomListResponse(roomResponses);
    }

    public CustomRoomResponse getInvitePreview(String inviteCode) {
        CustomGameRoom room = customGameRoomReadService.getWaitingRoomByInviteCode(inviteCode);
        return toRoomResponse(room, customGameRoomReadService.getParticipants(room.getId()));
    }

    public CustomRoomResponse getWaitingRoom(Long roomId) {
        CustomGameRoom room = customGameRoomReadService.getWaitingRoom(roomId);
        return toRoomResponse(room, customGameRoomReadService.getParticipants(room.getId()));
    }

    public CustomRoomResponse getWaitingRoom(Long roomId, Long userId) {
        customGameRoomReadService.validateWaitingParticipant(roomId, userId);
        return getWaitingRoom(roomId);
    }

    public CustomRoomResponse joinRoom(String inviteCode, Long userId) {
        CustomGameRoomJoinResult joinResult = customGameRoomCommandService.joinRoom(inviteCode, userId);
        notifyDepartedRoomsAfterCommit(joinResult.departedRooms(), userId);

        CustomGameRoom joinedRoom = joinResult.joinedRoom();
        CustomRoomResponse response = toRoomResponse(
                joinedRoom,
                customGameRoomReadService.getParticipants(joinedRoom.getId())
        );
        customRoomWebSocketNotifier.notifyRoomUpdatedAfterCommit(response);
        return response;
    }

    public CustomRoomResponse leaveRoom(Long roomId, Long userId) {
        CustomGameRoom room = customGameRoomCommandService.leaveRoom(roomId, userId);
        CustomRoomResponse response = toRoomResponse(room, customGameRoomReadService.getParticipants(room.getId()));
        if (room.isClosed()) {
            customRoomWebSocketNotifier.notifyRoomClosedAfterCommit(response);
            return response;
        }

        customRoomWebSocketNotifier.notifyParticipantLeftAfterCommit(response, userId);
        return response;
    }

    @Transactional
    public CustomGameStartResponse startRoom(Long roomId, Long ownerUserId) {
        Instant serverTime = Instant.now(clock);
        Instant startAt = serverTime.plusMillis(GameStartConstants.START_DELAY_MILLIS);
        CustomGameRoomStartResult startResult = customGameRoomCommandService.startRoom(
                roomId,
                ownerUserId,
                LocalDateTime.ofInstant(startAt, clock.getZone())
        );
        validateNoActiveGameRoom(startResult.participantUserIds());

        GameRoom gameRoom = createStartedCustomGameRoom(startResult.participantUserIds(), startAt);
        GameStartScenarioPayload scenario = GameStartScenarioPayload.from(gameRoom.getScenarioData());
        registerEndDeadline(gameRoom.getId(), startAt.toEpochMilli(), scenario.durationMs());

        CustomGameStartResponse response = new CustomGameStartResponse(
                startResult.room().getId(),
                gameRoom.getId(),
                GameMode.CUSTOM.name(),
                serverTime.toEpochMilli(),
                startAt.toEpochMilli(),
                GAME_WEB_SOCKET_URL_FORMAT.formatted(gameRoom.getId()),
                scenario
        );
        customRoomWebSocketNotifier.notifyRoomStartedAfterCommit(response);
        return response;
    }

    private void validateNoActiveGameRoom(List<Long> userIds) {
        boolean hasActiveGameRoom = userIds.stream()
                .anyMatch(gameRoomReadService::existsActiveGameRoomByUserId);
        if (hasActiveGameRoom) {
            throw new ApiException(ApiErrorCode.GAME_ACTIVE_ROOM_EXISTS);
        }
    }

    private GameRoom createStartedCustomGameRoom(List<Long> participantUserIds, Instant startAt) {
        GameRoom gameRoom = gameRoomCommandService.createCustomRoom(
                participantUserIds.get(0),
                participantUserIds.get(1)
        );
        boolean started = gameRoomCommandService.startReadyRoomIfReady(
                gameRoom.getId(),
                LocalDateTime.ofInstant(startAt, clock.getZone())
        );
        if (!started) {
            abortReadyRoom(gameRoom.getId());
            throw new ApiException(ApiErrorCode.GAME_CUSTOM_START_FAILED);
        }
        return gameRoom;
    }

    private void notifyDepartedRoomsAfterCommit(List<CustomGameRoom> departedRooms, Long userId) {
        departedRooms.forEach(room -> {
            CustomRoomResponse response = toRoomResponse(
                    room,
                    customGameRoomReadService.getParticipants(room.getId())
            );
            if (room.isClosed()) {
                customRoomWebSocketNotifier.notifyRoomClosedAfterCommit(response);
                return;
            }
            customRoomWebSocketNotifier.notifyParticipantLeftAfterCommit(response, userId);
        });
    }

    private void registerEndDeadline(Long gameRoomId, long startAtMillis, long durationMs) {
        try {
            gameEndScheduleService.registerEndDeadline(gameRoomId, startAtMillis, durationMs);
        } catch (RuntimeException exception) {
            abortInProgressRoom(gameRoomId);
            throw exception;
        }
    }

    private void abortReadyRoom(Long gameRoomId) {
        gameRoomCommandService.abortReadyRoomIfReady(gameRoomId);
    }

    private void abortInProgressRoom(Long gameRoomId) {
        gameRoomCommandService.abortInProgressRoomIfInProgress(gameRoomId);
    }

    private CustomRoomResponse toRoomResponse(CustomGameRoom room, List<CustomGameParticipant> participants) {
        Map<Long, User> usersById = findUsersById(responseUserIds(room, participants));
        List<CustomRoomParticipantResponse> participantResponses = participants.stream()
                .map(participant -> CustomRoomParticipantResponse.from(participant,
                        resolveUser(usersById, participant.getUserId())))
                .toList();
        User owner = resolveUser(usersById, room.getOwnerUserId());
        return CustomRoomResponse.from(room, roomName(owner), participantResponses);
    }

    private Map<Long, List<CustomGameParticipant>> findParticipantsByRoomId(List<CustomGameRoom> rooms) {
        Collection<Long> roomIds = rooms.stream()
                .map(CustomGameRoom::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return customGameRoomReadService.findParticipantsByRoomIds(roomIds).stream()
                .collect(Collectors.groupingBy(
                        CustomGameParticipant::getCustomRoomId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    private Collection<Long> responseUserIds(CustomGameRoom room, List<CustomGameParticipant> participants) {
        LinkedHashSet<Long> userIds = participants.stream()
                .map(CustomGameParticipant::getUserId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        userIds.add(room.getOwnerUserId());
        return userIds;
    }

    private Map<Long, User> findUsersById(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userReadService.findAllByIdsOrThrow(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private User resolveUser(Map<Long, User> usersById, Long userId) {
        User user = usersById.get(userId);
        if (user == null) {
            throw new CoreException(CoreErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    private String roomName(User owner) {
        return ROOM_NAME_FORMAT.formatted(owner.getNickname());
    }
}
