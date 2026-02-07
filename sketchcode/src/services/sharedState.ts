import * as fs from 'fs';
import * as path from 'path';
import { SharedState, CommandQueueItem } from '../types';
import { log, logError } from '../utils/logger';

let stateFilePath = '';
let pollTimer: NodeJS.Timeout | null = null;

/** Initialize the shared state system */
export function initSharedState(filePath: string): void {
  stateFilePath = filePath;
  const dir = path.dirname(filePath);
  if (!fs.existsSync(dir)) {
    fs.mkdirSync(dir, { recursive: true });
  }
}

/** Get the default empty state */
export function getDefaultState(sessionId: string): SharedState {
  return {
    sessionActive: false,
    sessionId,
    extensionPid: process.pid,
    currentCode: null,
    pendingAnnotation: null,
    phoneConnected: false,
    commandQueue: [],
    lastUpdated: Date.now(),
  };
}

/**
 * Clean up stale state on extension startup.
 * If the state file claims sessionActive=true but the PID is dead, reset it.
 */
export function cleanupStaleState(): void {
  const state = readState();
  if (!state || !state.sessionActive) return;

  // Check if the PID in the state file is still alive
  let pidAlive = false;
  if (state.extensionPid) {
    try {
      process.kill(state.extensionPid, 0); // signal 0 = existence check
      pidAlive = true;
    } catch {
      pidAlive = false;
    }
  }

  if (!pidAlive) {
    log('Found stale session state (PID dead), cleaning up');
    state.sessionActive = false;
    state.phoneConnected = false;
    state.pendingAnnotation = null;
    state.commandQueue = [];
    writeState(state);
  }
}

/** Atomically write state to the shared file */
export function writeState(state: SharedState): void {
  if (!stateFilePath) return;
  state.lastUpdated = Date.now();
  const tmpPath = stateFilePath + '.tmp';
  try {
    fs.writeFileSync(tmpPath, JSON.stringify(state, null, 2), 'utf-8');
    fs.renameSync(tmpPath, stateFilePath);
  } catch (err) {
    logError('Failed to write shared state', err);
  }
}

/** Read the current state from the shared file */
export function readState(): SharedState | null {
  if (!stateFilePath || !fs.existsSync(stateFilePath)) return null;
  try {
    const raw = fs.readFileSync(stateFilePath, 'utf-8');
    return JSON.parse(raw) as SharedState;
  } catch (err) {
    logError('Failed to read shared state', err);
    return null;
  }
}

/**
 * Start polling the command queue for MCP server commands.
 * When a 'pending' command is found, calls the handler and updates status.
 */
export function startCommandPolling(
  handler: (cmd: CommandQueueItem) => Promise<string | undefined>
): void {
  pollTimer = setInterval(async () => {
    const state = readState();
    if (!state) return;

    let updated = false;
    for (const cmd of state.commandQueue) {
      if (cmd.status === 'pending') {
        log(`Executing MCP command: ${cmd.command} (${cmd.id})`);
        try {
          const result = await handler(cmd);
          cmd.status = 'completed';
          cmd.result = result;
        } catch (err) {
          cmd.status = 'failed';
          cmd.result = err instanceof Error ? err.message : String(err);
        }
        updated = true;
      }
    }

    // Clean up old completed/failed commands (keep last 10)
    if (state.commandQueue.length > 10) {
      state.commandQueue = state.commandQueue.slice(-10);
      updated = true;
    }

    if (updated) {
      writeState(state);
    }
  }, 500);
}

/** Stop polling */
export function stopCommandPolling(): void {
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
}

/** Clean up the state file on session end */
export function cleanupState(): void {
  stopCommandPolling();
  if (stateFilePath && fs.existsSync(stateFilePath)) {
    const state = readState();
    if (state) {
      state.sessionActive = false;
      state.phoneConnected = false;
      state.pendingAnnotation = null;
      writeState(state);
    }
  }
}
