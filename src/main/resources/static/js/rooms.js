/* 채팅방 목록 실시간 갱신
 * - 내가 참여한 방마다 /topic/rooms/{id} 를 구독
 * - 새 메시지 도착 시 안 읽음 배지 +1 (내가 보낸 메시지는 무시)
 * - 참가자 입퇴장 시 👤 N 갱신
 * - 방이 삭제되면 해당 항목 제거
 */
(function () {
    if (typeof JOINED_ROOM_IDS === 'undefined' || JOINED_ROOM_IDS.length === 0) {
        return; // 참여한 방이 없으면 굳이 STOMP 연결할 필요 없음
    }

    const liByRoomId = new Map();
    document.querySelectorAll('li[data-room-id]').forEach((li) => {
        liByRoomId.set(li.getAttribute('data-room-id'), li);
    });

    const client = new StompJs.Client({
        webSocketFactory: () => new SockJS('/ws-stomp'),
        reconnectDelay: 3000,
    });

    client.onConnect = () => {
        JOINED_ROOM_IDS.forEach((roomId) => {
            client.subscribe('/topic/rooms/' + roomId, (frame) => {
                handleEvent(roomId, JSON.parse(frame.body));
            });
        });
    };

    client.activate();

    function handleEvent(roomId, ev) {
        const li = liByRoomId.get(roomId);
        if (!li) return;

        switch (ev.type) {
            case 'MESSAGE':
                // 내가 보낸 메시지는 안 읽음 카운트에서 제외 (내 lastRead 가 서버에서 이미 갱신됨)
                if (ev.message && ev.message.sender === ME) return;
                incrementUnread(li);
                break;
            case 'ENTER':
            case 'LEAVE':
                if (typeof ev.participantCount === 'number') {
                    updateParticipantCount(li, ev.participantCount);
                }
                break;
            case 'ROOM_DELETED':
                li.remove();
                liByRoomId.delete(roomId);
                break;
        }
    }

    function incrementUnread(li) {
        let badge = li.querySelector('.badge.unread');
        if (!badge) {
            badge = document.createElement('span');
            badge.className = 'badge unread';
            // 참가자 수 배지 바로 뒤에 끼워 넣기 (없으면 그냥 마지막 위치)
            const pcount = li.querySelector('.badge:not(.unread)');
            if (pcount) pcount.after(badge);
            else li.appendChild(badge);
        }
        const cur = Number(badge.textContent || '0');
        badge.textContent = String(cur + 1);
    }

    function updateParticipantCount(li, count) {
        const pcount = li.querySelector('.badge:not(.unread)');
        if (pcount) pcount.textContent = '👤 ' + count;
    }
})();
