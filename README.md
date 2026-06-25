# chat

Spring Boot 4.0.6 / Java 21 기반 **WebSocket(STOMP) + Redis** 실시간 오픈 채팅 애플리케이션.

## 주요 기능

- 닉네임만 입력하는 간단 로그인 (Redis 기반 세션)
- 채팅방 목록 / 생성 / 삭제 (작성자만 삭제 가능)
- **명시적 참여 모델** — `내 채팅방` / `참여 가능한 방` 두 섹션, [참여] 버튼을 눌러야 멤버가 됨
- **방 나가기** — 멤버에서 탈퇴 (방장은 삭제만 가능)
- 멤버가 아닌 사용자의 채팅방 직접 접근 차단 (`/rooms/{id}` URL)
- 실시간 메시지 송수신
- 카카오톡 스타일 **메시지별 안 읽음 수** 표시
- 방 목록의 **방별 안 읽음 메시지 수** 배지 (다른 사용자가 메시지를 보내면 **새로고침 없이 실시간 +1**)
- 현재 입장 인원 실시간 표시 (입장/퇴장 자동 반영, 목록 화면도 라이브 갱신)
- 비정상 종료(탭 닫기·네트워크 단절) 시 자동 퇴장 처리
- **시스템 메시지**: 명시적 [참여] / [나가기] 시에만 표시 (단순 페이지 여닫기는 시스템 메시지 없이 조용히 처리)

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
| `room:r1:members` | Set | 명시적으로 [참여] 한 사용자 (멤버십·안 읽음 계산 대상) |
| `room:r1:online` | Set | 현재 WebSocket 접속 중인 사용자 (참가자 수) |
| `room:r1:lastRead` | Hash | 사용자별 마지막으로 읽은 msgId |

## HTTP 엔드포인트

| 메서드 | 경로 | 용도 |
|---|---|---|
| `GET` | `/login`, `/logout` | 닉네임 로그인 폼 / 로그아웃 |
| `GET` | `/rooms` | 채팅방 목록 (내 채팅방 + 참여 가능한 방) |
| `POST` | `/rooms` | 새 채팅방 생성 (작성자 자동 멤버) |
| `GET` | `/rooms/{id}` | 채팅방 진입 (**멤버만 가능**, 비멤버는 `/rooms` 로 리다이렉트) |
| `POST` | `/rooms/{id}/join` | 방 멤버 등록 후 채팅방으로 자동 이동 |
| `POST` | `/rooms/{id}/leave` | 방 멤버 탈퇴 (방장은 불가, 입장 중이었다면 LEAVE 이벤트 발행) |
| `POST` | `/rooms/{id}/delete` | 방 삭제 (작성자만 가능, ROOM_DELETED 이벤트 발행) |

## STOMP 엔드포인트

> HTTP `leave` (멤버 탈퇴) 와 STOMP `leave` (화면 이탈) 는 **다른 동작**입니다. 화면을 닫는 건 STOMP leave (online 만 정리), [나가기] 버튼은 HTTP leave (members 까지 정리).

| 방향 | destination | 용도 |
|---|---|---|
| 핸드셰이크 | `GET /ws-stomp` | SockJS 연결 |
| 클라이언트 → 서버 | `/app/rooms/{id}/enter` | 화면 입장 (online 추가, lastRead 갱신) |
| 클라이언트 → 서버 | `/app/rooms/{id}/leave` | 화면 이탈 (online 제거) |
| 클라이언트 → 서버 | `/app/rooms/{id}/message` | 메시지 전송 |
| 서버 → 클라이언트 | `/topic/rooms/{id}` | 방 이벤트 브로드캐스트 |

### 브로드캐스트 이벤트 타입

| `type` | 발생 시점 | 페이로드 | 클라이언트 처리 |
|---|---|---|---|
| `ENTER` | 누군가 채팅 페이지를 **열었다** (STOMP enter) | `user`, `participantCount` | 👤 카운트 갱신만, 시스템 메시지 X |
| `LEAVE` | 누군가 채팅 페이지를 **닫았다** (STOMP leave / 비정상 종료) | `user`, `participantCount` | 👤 카운트 갱신만, 시스템 메시지 X |
| `JOIN` | 누군가 [참여] 로 **멤버가 되었다** (HTTP join) | `user` | "○○ 님이 입장했습니다" 시스템 메시지 |
| `LEAVE_ROOM` | 누군가 [나가기] 로 **멤버에서 빠졌다** (HTTP leave) | `user` | "○○ 님이 나갔습니다" 시스템 메시지 |
| `MESSAGE` | 새 메시지 | `message` (id, sender, content, ts, unreadCount) | 메시지 추가, 목록 화면이면 안 읽음 배지 +1 |
| `UNREAD_UPDATE` | 누군가가 메시지를 읽음 또는 탈퇴 | `user`, `minMsgId`, `maxMsgId` | 이 범위의 unread -1 |
| `ROOM_DELETED` | 방이 삭제됨 | — | 채팅방이면 알림 후 목록 이동, 목록 화면이면 항목 제거 |

## 멤버십 & 읽음/안읽음 계산 로직

### 멤버십(members) 라이프사이클
- 방 생성 시: 작성자는 자동 멤버
- [참여] 클릭: members 추가 + `lastRead = currentMaxId` (참여 이전 메시지는 "읽음" 으로 시작), `JOIN` 이벤트 발행
- [나가기] 클릭:
  - members/online/lastRead 모두 정리
  - 탈퇴자가 못 본 메시지가 있다면 `UNREAD_UPDATE` 발행 → 다른 사용자 화면의 unread 표시가 즉시 −1
  - 입장 중이었다면 `LEAVE` 이벤트로 참가자 수 갱신
  - 마지막으로 `LEAVE_ROOM` 이벤트로 "나갔습니다" 시스템 메시지 표시
- 방 삭제 시: 모든 멤버십 데이터가 Redis 키 단위로 일괄 정리, `ROOM_DELETED` 발행

### 메시지별 안 읽음 수 (카카오톡 스타일)
- 메시지 발행 시점: `unreadCount = members - online` (보낸이는 online 에 포함됨)
- 즉, **참여 중인 멤버 중 지금 화면을 보고 있지 않은 사람 수**
- 누군가 화면에 입장하면 그가 못 본 메시지 범위 `(oldLastRead, currentMaxId]` 에 대해 `UNREAD_UPDATE` 이벤트 발행 → 다른 클라이언트들이 표시된 unread 를 −1

### 방별 안 읽음 메시지 수 (내 채팅방 목록 배지)
- 사용자별 `unreadCount = currentMaxId - lastRead[user]`
- `/rooms` 진입 시 내 채팅방 각각에 대해 계산해서 배지 표시 (참여 가능한 방은 안 보임)

## 프로젝트 구조

```
src/main/java/com/haeun/chat/
├── ChatApplication.java
├── config/             # Redis, WebSocket(STOMP) 설정
├── controller/         # LoginController, RoomController, ChatStompController
├── domain/             # ChatRoom, ChatMessage, RoomEventType
├── dto/                # RoomEvent, RoomListItem, RoomLists, SendMessageRequest
├── listener/           # WsSessionRegistry, WebSocketEventListener (비정상 종료 처리)
├── repository/         # Room/Message/Presence/ReadRedisRepository
└── service/            # RoomService, ChatService

src/main/resources/
├── application.properties
├── static/js/          # room.js (채팅방 STOMP), rooms.js (목록 라이브 갱신)
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

### 참여 / 나가기 + 시스템 메시지 확인
1. 브라우저 A 와 B (시크릿 창) 로 각각 다른 닉네임으로 로그인
2. A 가 방 만들기 → A 의 `내 채팅방` 에 표시, B 의 `참여 가능한 방` 에 표시
3. B 가 [참여] 클릭 → 채팅방으로 자동 이동
   - A 화면: **"bob 님이 입장했습니다"** 시스템 메시지 + `👤 2` 로 증가
4. B 가 "← 목록" 으로 채팅 페이지만 닫고 돌아감 (멤버십은 유지)
   - A 화면: `👤 1` 로 감소, **시스템 메시지는 안 뜸** (조용히 처리)
5. B 가 다시 그 방으로 들어옴
   - A 화면: `👤 2` 로 증가, **시스템 메시지는 안 뜸**
6. B 가 [나가기] 버튼 클릭 → 멤버 탈퇴
   - A 화면: `👤 1` + **"bob 님이 나갔습니다"** 시스템 메시지
7. B 가 URL `/rooms/{id}` 로 직접 접근 → 멤버가 아니므로 `/rooms` 로 리다이렉트

### 방 목록 실시간 갱신 확인
1. A 와 B 가 둘 다 방 r1 의 멤버. A 는 채팅 페이지, B 는 `/rooms` 목록 페이지
2. A 가 메시지 "hi" 전송
   → B 의 목록 화면에서 r1 옆에 빨간 `1` 배지가 **즉시 나타남** (새로고침 불필요)
3. A 가 메시지 추가로 "ㅎㅇ" 전송
   → B 의 배지가 `2` 로 즉시 증가
4. C 가 같은 방에 [참여] → 그 방의 채팅 페이지 진입
   → B 의 목록 화면 👤 숫자가 즉시 +1

### 안 읽음 표시 확인 (메시지별)
1. A 와 B 가 방에 멤버이고 함께 있는 상태
2. B 가 방 페이지를 떠남 (멤버는 유지, online 만 빠짐)
3. A 가 메시지 전송 → A 화면의 메시지 옆에 빨간 `1` 표시
4. B 가 같은 방에 다시 입장 → A 의 `1` 이 사라짐 (UNREAD_UPDATE)

### 탈퇴 시 unread 자동 정리 확인
1. A 와 B 가 방의 멤버. B 는 채팅 페이지 밖에 있음
2. A 가 메시지 전송 → A 화면의 메시지 옆에 빨간 `1` (B 가 안 읽음)
3. B 가 그 방에서 [나가기] 클릭 (멤버 탈퇴)
   → A 화면의 `1` 이 즉시 사라짐 (탈퇴자는 unread 카운트에서 빠지므로)

### 방 삭제 확인
1. A 가 만든 방에는 [삭제] 버튼, B 가 만든 방에는 [나가기] 버튼 표시
2. A 가 [삭제] 클릭 → confirm → 삭제
3. 그 방에 들어가 있던 B 의 채팅 화면: "이 방은 삭제되었습니다" alert 후 목록으로 이동
4. 다른 멤버의 `/rooms` 목록 화면: 해당 방 항목이 **즉시 사라짐**

## 알려진 제약

- 단일 인스턴스 가정 (`WsSessionRegistry` 는 in-memory). 다중 인스턴스 운영 시 Redis Hash 로 옮기고 STOMP 브로커도 외부 Relay 로 교체 필요
- 메시지 TTL 7일 — 그 이전 메시지는 Redis 에서 자동 삭제됨
- 로그인은 닉네임 중복 검사 없음 (학습용)
- **방장은 본인 방에서 [나가기] 불가** — 방을 삭제만 가능 (양도 기능 없음)
- 멤버 탈퇴 시 `lastRead` 도 함께 정리되므로, 다시 참여하면 그 시점의 최신 메시지부터 시작 (탈퇴 이전 안 읽음 위치는 복원 안 됨)
