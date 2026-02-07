// WebSocket message protocol between VSCode extension and phone/web client

/** Extension → Phone: Live code sync update */
export interface CodeUpdateMessage {
  type: 'code_update';
  payload: {
    filename: string;
    code: string;
    language: string;
    cursorLine: number;
    lineCount: number;
    timestamp: number;
  };
}

/** Phone → Extension: Annotation with sketch + voice */
export interface AnnotationMessage {
  type: 'annotation';
  payload: {
    sketchImageBase64: string;       // PNG of code + drawn annotations composited
    voiceTranscription: string;       // Text from speech-to-text (may be empty)
    codeSnapshotTimestamp: number;    // Which code_update this annotates
    timestamp: number;
  };
}

/** Bidirectional: Status updates */
export interface StatusMessage {
  type: 'status';
  payload: {
    status: 'connected' | 'disconnected' | 'processing' | 'ready' | 'error';
    message?: string;
  };
}

/** Auth handshake: First message from client */
export interface AuthMessage {
  type: 'auth';
  token: string;
}

/** All possible WebSocket messages */
export type WSMessage =
  | CodeUpdateMessage
  | AnnotationMessage
  | StatusMessage;

/** Inbound messages from phone */
export type InboundMessage = AnnotationMessage | StatusMessage;

/** Outbound messages to phone */
export type OutboundMessage = CodeUpdateMessage | StatusMessage;
