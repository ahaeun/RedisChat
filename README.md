# chat

Spring Boot 4.0.6 / Java 21 기반 **WebSocket(STOMP) + Redis** 실시간 오픈 채팅 애플리케이션.

## 주요 기능

- 닉네임만 입력하는 간단 로그인 (Redis 기반 세션)
- 채팅방 목록 / 생성 / 삭제 (작성자만 삭제 가능)
- 실시간 메시지 송수신
- 카카오톡 스타일 **메시지별 안 읽음 수** 표시
- 방 목록의 **방별 안 읽음 메시지 수** 배지
- 현재 입장 인원 실시간 표시 (입장/퇴장 자동 반영)
- 비정상 종료(탭 닫기·네트워크 단절) 시 자동 퇴장 처리

## 기술 스택

| 영역 | 사용 기술 |
|---|---|
| 백엔드 | Spring Boot 4.0.6, Java 21, Spring WebMVC, Spring WebSocket(STOMP) |
| 뷰 | Thymeleaf, Vanilla JS, SockJS, @stomp/stompjs |
| 저장소 | Redis (Lettuce) |
| 세션 | Spring Session Data Redis |
| 빌드 | Gradle 9.x |
| 테스트 | JUnit 5, Mockito, AssertJ |

## 실행 방법

### 1. Redis 띄우기
```bash
# Homebrew
brew services start redis

# 또는 Docker
docker run -d --name redis -p 6379:6379 redis
```

### 2. 앱 실행
```bash
./gradlew bootRun
```

### 3. 브라우저 접속
- http://localhost:8080 → 닉네임 입력
- 동시에 여러 브라우저(또는 시크릿 창)로 다른 닉네임으로 접속해 실시간 채팅 확인

## 아키텍처 개요

```
[브라우저] ──HTTP──► [Spring MVC] ──► [Redis]   (페이지 로드, 과거 메시지 조회)
[브라우저] ──STOMP─► [Spring WebSocket]         (실시간 송수신)
              ▲                  │
              └── /topic 브로드캐스트 ◄┘
```

- **Redis** — 모든 상태의 원본 (메시지, 입장자, 읽음 위치)
- **WebSocket/STOMP** — "방금 일어난 일"만 실시간 전달
- 페이지 로드 시 과거 데이터는 HTTP 요청으로 Redis에서 가져오고, 그 이후의 변화만 WebSocket으로 푸시

## Redis 키 구조

방 ID `r1` 기준 예시.

| 키 | 타입 | 역할 |
|---|---|---|
| `seq:room` | String (INCR) | 방 ID 시퀀스 |
| `rooms` | Sorted Set | 전체 방 목록 (score=createdAt) |
| `room:r1` | Hash | 방 메타 (name, createdAt, createdBy) |
| `room:r1:msgId` | String (INCR) | 메시지 ID 시퀀스 |
| `room:r1:messages` | Sorted Set | 메시지 JSON (score=msgId), TTL 7일 |
| `room:r1:members` | Set | 한 번이라도 입장한 사용자 (안 읽음 계산 대상) |
| `room:r1:online` | Set | 현재 WebSocket 접속 중인 사용자 (참가자 수) |
| `room:r1:lastRead` | Hash | 사용자별 마지막으로 읽은 msgId |

## STOMP 엔드포인트

| 방향 | destination | 용도 |
|---|---|---|
| 핸드셰이크 | `GET /ws-stomp` | SockJS 연결 |
| 클라이언트 → 서버 | `/app/rooms/{id}/enter` | 입장 |
| 클라이언트 → 서버 | `/app/rooms/{id}/leave` | 퇴장 |
| 클라이언트 → 서버 | `/app/rooms/{id}/message` | 메시지 전송 |
| 서버 → 클라이언트 | `/topic/rooms/{id}` | 방 이벤트 브로드캐스트 |

### 브로드캐스트 이벤트 타입

| `type` | 발생 시점 | 페이로드 |
|---|---|---|
| `ENTER` | 누군가 입장 | `user`, `participantCount` |
| `LEAVE` | 누군가 퇴장 | `user`, `participantCount` |
| `MESSAGE` | 새 메시지 | `message` (id, sender, content, ts, unreadCount) |
| `UNREAD_UPDATE` | 누군가가 메시지를 읽음 | `user`, `minMsgId`, `maxMsgId` (이 범위의 unread -1) |
| `ROOM_DELETED` | 방이 삭제됨 | — (클라이언트가 목록으로 이동) |

## 읽음/안읽음 계산 로직

### 메시지별 안 읽음 수 (카카오톡 스타일)
- 메시지 발행 시점: `unreadCount = members - online` (보낸이는 online 에 포함됨)
- 누군가 입장하면 그가 못 본 메시지 범위 `(oldLastRead, currentMaxId]` 에 대해 `UNREAD_UPDATE` 이벤트 발행 → 다른 클라이언트들이 표시된 unread 를 −1

### 방별 안 읽음 메시지 수 (목록 배지)
- 사용자별 `unreadCount = currentMaxId - lastRead[user]`
- `/rooms` 진입 시 각 방마다 계산해서 배지 표시

## 프로젝트 구조

```
src/main/java/com/haeun/chat/
├── ChatApplication.java
├── config/             # Redis, WebSocket(STOMP) 설정
├── controller/         # LoginController, RoomController, ChatStompController
├── domain/             # ChatRoom, ChatMessage, RoomEventType
├── dto/                # RoomEvent, RoomListItem, SendMessageRequest
├── listener/           # WsSessionRegistry, WebSocketEventListener (비정상 종료 처리)
├── repository/         # Room/Message/Presence/ReadRedisRepository
└── service/            # RoomService, ChatService

src/main/resources/
├── application.properties
├── static/js/          # room.js (STOMP 클라이언트)
└── templates/          # login.html, rooms.html, room.html

src/test/java/com/haeun/chat/
├── ChatApplicationTests.java   # 컨텍스트 로드 (Redis mock)
└── service/ChatServiceTest.java
```

## 테스트

```bash
./gradlew test
```

- `ChatApplicationTests` — Spring 컨텍스트 로딩 (LettuceConnectionFactory mock)
- `ChatServiceTest` — 메시지 전송·입장 시 unread 계산과 이벤트 발행 검증

## 설정 항목 (`application.properties`)

| 키 | 기본값 | 설명 |
|---|---|---|
| `spring.data.redis.host` | `localhost` | Redis 호스트 |
| `spring.data.redis.port` | `6379` | Redis 포트 |
| `spring.session.timeout` | `30m` | HTTP 세션 만료 시간 |
| `server.port` | `8080` | 서버 포트 |

## 동작 확인 시나리오

### 참가자 수 확인
1. 브라우저 A 와 B (시크릿 창) 로 각각 다른 닉네임으로 로그인
2. 같은 방에 둘 다 입장 → 우측 상단 `👤 2` 표시
3. B가 목록으로 나가면 A 의 화면에서 `👤 1` 로 즉시 갱신

### 안 읽음 표시 확인
1. A 와 B 가 방에 함께 있는 상태
2. B 가 방을 떠남
3. A 가 메시지 전송 → A 화면의 메시지 옆에 빨간 `1` 표시
4. B 가 같은 방에 다시 입장 → A 의 `1` 이 사라짐 (UNREAD_UPDATE)

### 방 삭제 확인
1. A 가 만든 방에는 삭제 버튼이 보이고, B 가 만든 방에는 안 보임
2. A 가 삭제 클릭 → confirm → 삭제
3. 그 방에 들어가 있던 B 의 화면: "이 방은 삭제되었습니다" alert 후 목록으로 이동

## 알려진 제약

- 단일 인스턴스 가정 (`WsSessionRegistry` 는 in-memory). 다중 인스턴스 운영 시 Redis Hash 로 옮기고 STOMP 브로커도 외부 Relay 로 교체 필요
- 메시지 TTL 7일 — 그 이전 메시지는 Redis 에서 자동 삭제됨
- 로그인은 닉네임 중복 검사 없음 (학습용)
