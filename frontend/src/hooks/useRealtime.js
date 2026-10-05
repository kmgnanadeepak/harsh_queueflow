import { useEffect, useRef } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

import { readToken } from '../api/client';

/**
 * Opens one authenticated STOMP connection and exposes it to the tree.
 *
 * The backend rejects CONNECT without a valid `Authorization: Bearer` native
 * header, and authorises each SUBSCRIBE against the principal, so the client
 * only ever asks for topics the caller is entitled to.
 *
 * Reconnection is handled by stomp-js; the JWT is re-read on every connect
 * attempt so a refreshed token is picked up automatically.
 */
export function useRealtime({ enabled, subscriptions, onEvent }) {
  const clientRef = useRef(null);
  const handlersRef = useRef(onEvent);
  const subsRef = useRef(subscriptions);

  handlersRef.current = onEvent;
  subsRef.current = subscriptions;

  useEffect(() => {
    if (!enabled) {
      return undefined;
    }

    const connect = () => {
      const client = new Client({
        webSocketFactory: () => new SockJS('/ws'),
        connectHeaders: { Authorization: `Bearer ${readToken() ?? ''}` },
        reconnectDelay: 4000,
        heartbeatIncoming: 10000,
        heartbeatOutgoing: 10000,
        onConnect: () => {
          (subsRef.current ?? []).forEach((destination) =>
            client.subscribe(destination, (message) => {
              try {
                handlersRef.current?.(JSON.parse(message.body), message);
              } catch {
                handlersRef.current?.(null, message);
              }
            }),
          );
        },
      });

      client.activate();
      clientRef.current = client;
    };

    connect();

    return () => {
      const client = clientRef.current;
      clientRef.current = null;
      if (client) {
        void client.deactivate();
      }
    };
  }, [enabled]);

  return clientRef;
}