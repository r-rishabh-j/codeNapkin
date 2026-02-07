/**
 * Shared state file schema (~/.sketchcode/state.json).
 * Written by the VSCode extension, read by the MCP server.
 */
export interface SharedState {
  sessionActive: boolean;
  sessionId: string;

  /** PID of the VSCode extension host process — used for liveness checks */
  extensionPid: number;

  currentCode: {
    filename: string;
    code: string;
    language: string;
    lineCount: number;
  } | null;

  pendingAnnotation: {
    id: string;
    sketchImageBase64: string;
    voiceTranscription: string;
    codeFilename: string;
    codeContent: string;
    timestamp: number;
  } | null;

  phoneConnected: boolean;

  /** Command queue: MCP server writes commands, extension reads and executes */
  commandQueue: CommandQueueItem[];

  lastUpdated: number;
}

export interface CommandQueueItem {
  id: string;
  command: 'send_code_to_phone' | 'refresh_code';
  params?: Record<string, unknown>;
  status: 'pending' | 'completed' | 'failed';
  result?: string;
  timestamp: number;
}
