/* 채팅방 클라이언트 — STOMP 연결, 이벤트 수신, 메시지 전송. */
(function () {
    const log = document.getElementById('log');
    const pcount = document.getElementById('pcount');
    const sendForm = document.getElementById('send');
    const input = document.getElementById('content');

    const client = new StompJs.Client({
        webSocketFactory: () => new SockJS('/ws-stomp'),
        reconnectDelay: 3000,
    });

    client.onConnect = () => {
        client.subscribe('/topic/rooms/' + ROOM_ID, (frame) => {
            const ev = JSON.parse(frame.body);
            handleEvent(ev);
        });
        client.publish({ destination: '/app/rooms/' + ROOM_ID + '/enter' });
    };

    client.activate();

    window.addEventListener('beforeunload', () => {
        try {
            client.publish({ destination: '/app/rooms/' + ROOM_ID + '/leave' });
        } catch (_) { /* ignore */ }
        client.deactivate();
    });

    sendForm.addEventListener('submit', (e) => {
        e.preventDefault();
        const content = input.value.trim();
        if (!content) return;
        client.publish({
            destination: '/app/rooms/' + ROOM_ID + '/message',
            body: JSON.stringify({ content }),
        });
        input.value = '';
    });

    function handleEvent(ev) {
        switch (ev.type) {
            case 'ENTER':
                pcount.textContent = ev.participantCount;
                appendSystem(ev.user + ' 님이 입장했습니다');
                break;
            case 'LEAVE':
                pcount.textContent = ev.participantCount;
                appendSystem(ev.user + ' 님이 나갔습니다');
                break;
            case 'MESSAGE':
                appendMessage(ev.message);
                break;
            case 'UNREAD_UPDATE':
                decrementUnreadInRange(ev.minMsgId, ev.maxMsgId, ev.user);
                break;
            case 'ROOM_DELETED':
                alert('이 방은 삭제되었습니다.');
                window.location.href = '/rooms';
                break;
        }
    }

    function appendMessage(m) {
        const row = document.createElement('div');
        row.className = 'msg' + (m.sender === ME ? ' me' : '');
        row.setAttribute('data-msg-id', m.id);

        if (m.sender !== ME) {
            const s = document.createElement('span');
            s.className = 'sender';
            s.textContent = m.sender;
            row.appendChild(s);
        }
        const u = document.createElement('span');
        u.className = 'unread';
        if (m.unreadCount > 0) u.textContent = m.unreadCount;
        row.appendChild(u);

        const b = document.createElement('div');
        b.className = 'bubble';
        b.textContent = m.content;
        row.appendChild(b);

        log.appendChild(row);
        log.scrollTop = log.scrollHeight;
    }

    function appendSystem(text) {
        const d = document.createElement('div');
        d.className = 'system';
        d.textContent = text;
        log.appendChild(d);
        log.scrollTop = log.scrollHeight;
    }

    function decrementUnreadInRange(minId, maxId, reader) {
        if (reader === ME) return; // 내가 읽은 건 표시에 영향 없음
        document.querySelectorAll('#log .msg').forEach((row) => {
            const id = Number(row.getAttribute('data-msg-id'));
            if (id >= minId && id <= maxId) {
                const span = row.querySelector('.unread');
                if (!span) return;
                const cur = Number(span.textContent || '0');
                const next = Math.max(0, cur - 1);
                span.textContent = next > 0 ? String(next) : '';
            }
        });
    }
})();
