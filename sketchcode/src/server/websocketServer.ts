import WebSocket, { WebSocketServer } from 'ws';
import * as http from 'http';
import { EventEmitter } from 'events';
import { validateToken } from './auth';
import { AuthMessage, WSMessage, OutboundMessage, AnnotationMessage, FileSelectMessage } from '../types';
import { log, logError } from '../utils/logger';

export interface SketchCodeWSServer extends EventEmitter {
  start(httpServer: http.Server): void;
  stop(): void;
  sendToPhone(message: OutboundMessage): void;
  isPhoneConnected(): boolean;
}

class WSServer extends EventEmitter implements SketchCodeWSServer {
  private wss: WebSocketServer | null = null;
  private authenticatedClient: WebSocket | null = null;
  private heartbeatInterval: NodeJS.Timeout | null = null;

  start(httpServer: http.Server): void {
    this.wss = new WebSocketServer({ server: httpServer });

    this.wss.on('connection', (ws) => {
      log('WebSocket: New connection, waiting for auth...');
      let authenticated = false;

      // Auth timeout: close if not authenticated within 10 seconds
      const authTimeout = setTimeout(() => {
        if (!authenticated) {
          log('WebSocket: Auth timeout, closing connection');
          ws.close(4001, 'Authentication timeout');
        }
      }, 10000);

      ws.on('message', (data) => {
        try {
          const msg = JSON.parse(data.toString());

          // First message must be auth
          if (!authenticated) {
            if (msg.type === 'auth' && validateToken(msg.token)) {
              authenticated = true;
              clearTimeout(authTimeout);
              this.authenticatedClient = ws;
              log('WebSocket: Client authenticated');
              this.emit('phone_connected');

              // Send ready status
              this.sendToPhone({
                type: 'status',
                payload: { status: 'connected', message: 'Connected to SketchCode' },
              });
            } else {
              log('WebSocket: Invalid auth token');
              ws.close(4001, 'Invalid token');
            }
            return;
          }

          // Handle authenticated messages
          this.handleMessage(msg as WSMessage);
        } catch (err) {
          logError('WebSocket: Failed to parse message', err);
        }
      });

      ws.on('close', () => {
        if (authenticated && this.authenticatedClient === ws) {
          this.authenticatedClient = null;
          log('WebSocket: Phone disconnected');
          this.emit('phone_disconnected');
        }
        clearTimeout(authTimeout);
      });

      ws.on('error', (err) => {
        logError('WebSocket: Connection error', err);
      });

      // Respond to pings with pongs (ws handles this automatically)
    });

    // Heartbeat: ping connected clients every 30 seconds
    this.heartbeatInterval = setInterval(() => {
      if (this.authenticatedClient && this.authenticatedClient.readyState === WebSocket.OPEN) {
        this.authenticatedClient.ping();
      }
    }, 30000);

    log('WebSocket server started');
  }

  stop(): void {
    if (this.heartbeatInterval) {
      clearInterval(this.heartbeatInterval);
      this.heartbeatInterval = null;
    }
    if (this.authenticatedClient) {
      this.authenticatedClient.close(1000, 'Session ended');
      this.authenticatedClient = null;
    }
    if (this.wss) {
      this.wss.close();
      this.wss = null;
    }
    log('WebSocket server stopped');
  }

  sendToPhone(message: OutboundMessage): void {
    if (!this.authenticatedClient || this.authenticatedClient.readyState !== WebSocket.OPEN) {
      return;
    }
    try {
      this.authenticatedClient.send(JSON.stringify(message));
    } catch (err) {
      logError('WebSocket: Failed to send message', err);
    }
  }

  isPhoneConnected(): boolean {
    return this.authenticatedClient !== null &&
      this.authenticatedClient.readyState === WebSocket.OPEN;
  }

  private handleMessage(msg: WSMessage): void {
    switch (msg.type) {
      case 'annotation':
        log(`WebSocket: Received annotation (voice: "${(msg as AnnotationMessage).payload.voiceTranscription.substring(0, 50)}...")`);
        this.emit('annotation', msg as AnnotationMessage);
        break;
      case 'file_select':
        log(`WebSocket: File select from phone: ${(msg as FileSelectMessage).payload.filename}`);
        this.emit('file_select', msg as FileSelectMessage);
        break;
      case 'status':
        log(`WebSocket: Status from phone: ${msg.payload.status}`);
        break;
      default:
        log(`WebSocket: Unknown message type: ${(msg as any).type}`);
    }
  }
}

export function createWSServer(): SketchCodeWSServer {
  return new WSServer();
}
