import { useEffect, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const WS_URL = '/ws';

export default function useDpiSocket() {
  const [connected, setConnected] = useState(false);
  const [latestDpi, setLatestDpi] = useState(null);
  const [dpiByDevice, setDpiByDevice] = useState({});
  const clientRef = useRef(null);

  useEffect(() => {
    const client = new Client({
      webSocketFactory: () => new SockJS(WS_URL),
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      onConnect: () => {
        setConnected(true);
        client.subscribe('/topic/dpi', (message) => {
          if (!message.body) return;
          try {
            const payload = JSON.parse(message.body);
            const deviceId = Number(payload.deviceId);
            if (!Number.isFinite(deviceId)) return;

            const normalized = {
              ...payload,
              deviceId,
              serviceName: payload.serviceName || 'UNKNOWN',
              trafficCategory: payload.trafficCategory || 'UNKNOWN',
            };

            setLatestDpi(normalized);
            setDpiByDevice((prev) => ({ ...prev, [deviceId]: normalized }));
          } catch (err) {
            console.error('Failed to parse /topic/dpi payload', err);
          }
        });
      },
      onDisconnect: () => {
        setConnected(false);
      },
      onStompError: (frame) => {
        console.error('DPI STOMP error:', frame.headers?.message, frame.body);
      },
      onWebSocketError: (event) => {
        console.error('DPI WebSocket error:', event);
      },
    });

    client.activate();
    clientRef.current = client;

    return () => {
      if (clientRef.current) {
        clientRef.current.deactivate();
      }
    };
  }, []);

  return { connected, latestDpi, dpiByDevice };
}
