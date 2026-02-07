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
    filename?: string;                // Which file this annotation belongs to
    timestamp: number;
  };
}

/** Extension → Phone: List of open editor tabs */
export interface OpenFilesMessage {
  type: 'open_files';
  payload: {
    files: Array<{ filename: string; fullPath: string }>;
    activeFile: string; // filename of the currently active tab
  };
}

/** Phone → Extension: User selected a file tab */
export interface FileSelectMessage {
  type: 'file_select';
  payload: {
    filename: string;
    fullPath: string;
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
  | OpenFilesMessage
  | FileSelectMessage
  | StatusMessage;

/** Inbound messages from phone */
export type InboundMessage = AnnotationMessage | FileSelectMessage | StatusMessage;

/** Outbound messages to phone */
export type OutboundMessage = CodeUpdateMessage | OpenFilesMessage | StatusMessage;
