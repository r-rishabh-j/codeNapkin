/**
 * WebSocket client for SketchCode.
 * Connects to the VSCode extension, authenticates, and handles messages.
 */
const SketchCodeWS = (() => {
  let ws = null;
  let token = null;
  let connected = false;
  let reconnectAttempts = 0;
  const maxReconnectAttempts = 10;
  const listeners = {};

  function getConnectionInfo() {
    const params = new URLSearchParams(window.location.search);
    token = params.get('token');
    const host = window.location.hostname;
    const port = window.location.port;
    return { host, port, token };
  }

  function connect() {
    const { host, port, token: t } = getConnectionInfo();
    if (!t) {
      emit('error', 'No auth token found in URL');
      return;
    }

    const url = `ws://${host}:${port}`;
    ws = new WebSocket(url);

    ws.onopen = () => {
      // Send auth message
      ws.send(JSON.stringify({ type: 'auth', token: t }));
    };

    ws.onmessage = (event) => {
      try {
        const msg = JSON.parse(event.data);
        handleMessage(msg);
      } catch (e) {
        console.error('Failed to parse message:', e);
      }
    };

    ws.onclose = (event) => {
      connected = false;
      emit('disconnected', event.reason);
      attemptReconnect();
    };

    ws.onerror = () => {
      emit('error', 'WebSocket connection error');
    };
  }

  function handleMessage(msg) {
    switch (msg.type) {
      case 'status':
        if (msg.payload.status === 'connected') {
          connected = true;
          reconnectAttempts = 0;
          emit('connected');
        }
        break;
      case 'code_update':
        emit('code_update', msg.payload);
        break;
      default:
        console.log('Unknown message type:', msg.type);
    }
  }

  function attemptReconnect() {
    if (reconnectAttempts >= maxReconnectAttempts) {
      emit('error', 'Max reconnection attempts reached');
      return;
    }
    reconnectAttempts++;
    const delay = Math.min(1000 * Math.pow(2, reconnectAttempts - 1), 10000);
    emit('reconnecting', { attempt: reconnectAttempts, delay });
    setTimeout(connect, delay);
  }

  function send(message) {
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify(message));
      return true;
    }
    return false;
  }

  function sendAnnotation(sketchImageBase64, voiceTranscription, codeSnapshotTimestamp) {
    return send({
      type: 'annotation',
      payload: {
        sketchImageBase64,
        voiceTranscription,
        codeSnapshotTimestamp,
        timestamp: Date.now(),
      },
    });
  }

  function on(event, callback) {
    if (!listeners[event]) listeners[event] = [];
    listeners[event].push(callback);
  }

  function emit(event, data) {
    const cbs = listeners[event];
    if (cbs) cbs.forEach(cb => cb(data));
  }

  function isConnected() {
    return connected;
  }

  return { connect, send, sendAnnotation, on, isConnected };
})();
