import * as fs from 'fs';
import * as path from 'path';
import * as os from 'os';

interface SharedState {
  sessionActive: boolean;
  sessionId: string;
  extensionPid: number;
  currentCode: {
    filename: string;
    code: string;
    language: string;
    lineCount: number;
  } | null;
  pendingAnnotations: Array<{
    id: string;
    sketchImageBase64: string;
    voiceTranscription: string;
    codeFilename: string;
    codeContent: string;
    timestamp: number;
  }>;
  phoneConnected: boolean;
  commandQueue: Array<{
    id: string;
    command: string;
    params?: Record<string, unknown>;
    status: 'pending' | 'completed' | 'failed';
    result?: string;
    timestamp: number;
  }>;
  lastUpdated: number;
}

function getStateFilePath(): string {
  return process.env.SKETCHCODE_STATE_PATH ||
    path.join(os.homedir(), '.sketchcode', 'state.json');
}

/**
 * Check if a process with the given PID is alive.
 */
function isPidAlive(pid: number): boolean {
  try {
    process.kill(pid, 0); // signal 0 = existence check only
    return true;
  } catch {
    return false;
  }
}

/**
 * Read state and validate the session is actually alive.
 * Returns null if no state, session inactive, or extension PID is dead.
 */
export function readState(): SharedState | null {
  const filePath = getStateFilePath();
  if (!fs.existsSync(filePath)) return null;
  try {
    const raw = fs.readFileSync(filePath, 'utf-8');
    const state = JSON.parse(raw) as SharedState;

    // If session says active but the extension PID is dead, it's stale
    if (state.sessionActive && state.extensionPid) {
      if (!isPidAlive(state.extensionPid)) {
        // Extension crashed — mark session as dead
        state.sessionActive = false;
        state.phoneConnected = false;
        writeState(state);
        return state; // return the now-inactive state
      }
    }

    // If state is older than 60 seconds and session says active, warn but still return
    // (the extension updates lastUpdated on every code change and every 500ms poll)
    return state;
  } catch {
    return null;
  }
}

export function writeState(state: SharedState): void {
  const filePath = getStateFilePath();
  const dir = path.dirname(filePath);
  if (!fs.existsSync(dir)) {
    fs.mkdirSync(dir, { recursive: true });
  }
  const tmpPath = filePath + '.tmp';
  fs.writeFileSync(tmpPath, JSON.stringify(state, null, 2), 'utf-8');
  fs.renameSync(tmpPath, filePath);
}

/**
 * Add a command to the queue and wait for the extension to process it.
 */
export async function enqueueCommand(
  command: string,
  params?: Record<string, unknown>,
  timeoutMs = 5000
): Promise<string> {
  const state = readState();
  if (!state || !state.sessionActive) {
    throw new Error('No active SketchCode session');
  }

  const cmdId = `cmd-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  state.commandQueue.push({
    id: cmdId,
    command,
    params,
    status: 'pending',
    timestamp: Date.now(),
  });
  writeState(state);

  // Poll for completion
  const startTime = Date.now();
  while (Date.now() - startTime < timeoutMs) {
    await new Promise(resolve => setTimeout(resolve, 200));
    const updated = readState();
    if (!updated) throw new Error('State file disappeared');

    const cmd = updated.commandQueue.find(c => c.id === cmdId);
    if (!cmd) throw new Error('Command disappeared from queue');

    if (cmd.status === 'completed') return cmd.result || 'Done';
    if (cmd.status === 'failed') throw new Error(cmd.result || 'Command failed');
  }

  throw new Error('Command timed out');
}
