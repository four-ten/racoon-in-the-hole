package com.ssafy.a410.room.controller;

import com.ssafy.a410.game.domain.Message;
import com.ssafy.a410.game.domain.player.Player;
import com.ssafy.a410.room.controller.dto.CreateRoomReq;
import com.ssafy.a410.room.controller.dto.JoinRoomReq;
import com.ssafy.a410.room.controller.dto.JoinRoomResp;
import com.ssafy.a410.room.controller.dto.RoomResp;
import com.ssafy.a410.room.domain.Room;
import com.ssafy.a410.room.service.RoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@RequiredArgsConstructor
@RestController
public class RoomController {
    private final RoomService roomService;

    // 새 방을 생성한다.
    @PostMapping("/api/rooms")
    public ResponseEntity<RoomResp> createRoom(@RequestBody CreateRoomReq req, Principal principal) {
        Room newRoom = roomService.createRoom(principal.getName(), req.password());
        RoomResp roomResp = new RoomResp(newRoom);
        return ResponseEntity.status(HttpStatus.CREATED).body(roomResp);
    }

    @GetMapping("/api/rooms/{roomId}/joined-players")
    public ResponseEntity<List<Player>> getRoomInfo(@PathVariable String roomId) {
        Room room = roomService.getRoomById(roomId);
        Map<String, Player> roomPlayers = room.getPlayers();
        List<Player> players = new ArrayList<>();
        for (Player player : roomPlayers.values()) {
            players.add(player);
        }
        return ResponseEntity.status(HttpStatus.OK).body(players);
    }

    @GetMapping("/api/rooms/{roomId}/ready-players")
    public ResponseEntity<List<Player>> getReadyInfo(@PathVariable String roomId) {
        Room room = roomService.getRoomById(roomId);
        Map<String, Player> players = room.getPlayers();
        List<Player> readyPlayers = new ArrayList<>();
        for (Player player : players.values()) {
            if (player.isReadyToStart()) {
                readyPlayers.add(player);
            }
        }
        return ResponseEntity.status(HttpStatus.OK).body(readyPlayers);
    }

    // 해당 방에 입장하기 위한 토큰들을 반환한다.
    @PostMapping("/api/rooms/{roomId}/join")
    public ResponseEntity<JoinRoomResp> joinRoom(@PathVariable String roomId, Principal principal, @RequestBody JoinRoomReq req) {
        // 방에 입장시켜 플레이어를 만들고,
        Player player = roomService.joinRoomWithPassword(roomId, principal.getName(), req.password());
        // 방에 입장함과 동시에 구독할 수 있는 token들에 대한 정보를 반환한다.
        JoinRoomResp tokens = roomService.getJoinRoomSubscriptionTokens(roomId, player.getId());
        return ResponseEntity.ok(tokens);
    }

    // 방 나가기
    @PostMapping("/api/rooms/{roomId}/leave")
    public ResponseEntity<Void> leaveRoom(@PathVariable String roomId, Principal principal) {
        Room room = roomService.findRoomById(roomId).orElseThrow(() -> new RuntimeException("Room not found"));
        Player player = room.getPlayerWith(principal.getName());
        roomService.leaveRoom(room, player);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    // 개발완료되면 삭제할 메소드
    @GetMapping("/api/rooms/{roomId}/players")
    public ResponseEntity<Set<String>> getRoomPlayers(@PathVariable String roomId) {
        Room room = roomService.findRoomById(roomId).orElseThrow(() -> new RuntimeException("Room not found"));
        Set<String> playerIds = room.getPlayers().keySet();
        return ResponseEntity.ok(playerIds);
    }

    // 개발완료되면 삭제할 메소드
    @MessageMapping("/check/{roomId}")
    @SendTo("/topic/check/{roomId}")
    public String checkPlayerInRoom(@DestinationVariable String roomId, Message message) {
        Room room = roomService.findRoomById(roomId).orElseThrow(() -> new RuntimeException("Room not found"));
        if (room.getPlayers().containsKey(message.getSender())) {
            return "Player " + message.getSender() + " is in room " + roomId;
        } else {
            return "Player " + message.getSender() + " is not in room " + roomId;
        }
    }

    // 해당 방에 현재 접속해 있을 때, 레디 상태로 전환된다.
    @MessageMapping("/rooms/{roomId}/ready")
    public void setPlayerReady(@DestinationVariable String roomId, Principal principal) {
        roomService.setPlayerReady(roomId, principal.getName());
    }
}
