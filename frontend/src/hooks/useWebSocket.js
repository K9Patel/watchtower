import { useState, useEffect, useRef, useCallback } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const WS_URL = '/ws';

export function useWebSocket() {
  const [dashboardData, setDashboardData] = useState(null);
  const [latestAlert, setLatestAlert]     = useState(null);
  const [deviceUpdates, setDeviceUpdates] = useState(null);
  const [connected, setConnected]         = useState(false);
  const clientRef = useRef(null);

  useEffect(() => {
    // Initialize STOMP client
    const client = new Client({
      // We use SockJS as the fallback transport since our backend specifies .withSockJS()
      webSocketFactory: () => new SockJS(WS_URL),
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      onConnect: () => {
        console.log('STOMP: Connected to WebSocket');
        setConnected(true);

        // 1. Subscribe to Dashboard updates (triggered by RealDataService)
        client.subscribe('/topic/dashboard', (msg) => {
          if (msg.body) {
            setDashboardData(JSON.parse(msg.body));
          }
        });

        // 2. Subscribe to new Alerts (triggered by DiagnosisEngine)
        client.subscribe('/topic/alerts', (msg) => {
          if (msg.body) {
            setLatestAlert(JSON.parse(msg.body));
          }
        });

        // 3. Subscribe to Device updates (triggered by NetworkDiscoveryService)
        client.subscribe('/topic/devices', (msg) => {
          if (msg.body) {
            setDeviceUpdates(JSON.parse(msg.body));
          }
        });
      },
      onDisconnect: () => {
        console.log('STOMP: Disconnected');
        setConnected(false);
      },
      onStompError: (frame) => {
        console.error('Broker reported error: ' + frame.headers['message']);
        console.error('Additional details: ' + frame.body);
      },
      onWebSocketError: (event) => {
        console.error('WebSocket Error:', event);
      }
    });

    client.activate();
    clientRef.current = client;

    // Cleanup on unmount
    return () => {
      if (clientRef.current) {
        clientRef.current.deactivate();
      }
    };
  }, []);

  return { dashboardData, latestAlert, deviceUpdates, connected };
}
