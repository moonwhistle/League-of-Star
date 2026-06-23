package com.sang.leagueofstar.customgame.service;

import com.sang.leagueofstar.common.exception.CoreErrorCode;
import com.sang.leagueofstar.common.exception.CoreException;
import com.sang.leagueofstar.customgame.controller.response.CustomRoomListItemResponse;
import com.sang.leagueofstar.customgame.controller.response.CustomRoomListResponse;
import com.sang.leagueofstar.customgame.controller.response.CustomRoomParticipantResponse;
import com.sang.leagueofstar.customgame.controller.response.CustomRoomResponse;
import com.sang.leagueofstar.domain.customgame.domain.CustomGameParticipant;
import com.sang.leagueofstar.domain.customgame.domain.CustomGameRoom;
import com.sang.leagueofstar.domain.customgame.service.CustomGameRoomCommandService;
import com.sang.leagueofstar.domain.customgame.service.CustomGameRoomReadService;
import com.sang.leagueofstar.domain.user.domain.User;
import com.sang.leagueofstar.domain.user.service.UserReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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

    private final CustomGameRoomCommandService customGameRoomCommandService;
    private final CustomGameRoomReadService customGameRoomReadService;
    private final UserReadService userReadService;

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

    public CustomRoomResponse joinRoom(String inviteCode, Long userId) {
        CustomGameRoom room = customGameRoomCommandService.joinRoom(inviteCode, userId);
        return toRoomResponse(room, customGameRoomReadService.getParticipants(room.getId()));
    }

    public CustomRoomResponse leaveRoom(Long roomId, Long userId) {
        CustomGameRoom room = customGameRoomCommandService.leaveRoom(roomId, userId);
        return toRoomResponse(room, customGameRoomReadService.getParticipants(room.getId()));
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
