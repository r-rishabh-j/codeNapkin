import * as vscode from 'vscode';
import * as path from 'path';
import * as fs from 'fs';
import * as os from 'os';
import { log, logError } from '../utils/logger';

let claudeTerminal: vscode.Terminal | null = null;
let mcpConfigPath: string | null = null;

/**
 * Write a temporary MCP config JSON that points to our bundled MCP server.
 * Returns the path to the config file.
 */
function writeMcpConfig(extensionPath: string): string {
  const mcpServerPath = path.join(extensionPath, 'dist', 'mcp-server', 'index.js');
  const configDir = path.join(os.homedir(), '.sketchcode');
  if (!fs.existsSync(configDir)) {
    fs.mkdirSync(configDir, { recursive: true });
  }
  const configPath = path.join(configDir, 'mcp-config.json');
  const config = {
    mcpServers: {
      sketchcode: {
        command: 'node',
        args: [mcpServerPath],
      },
    },
  };
  fs.writeFileSync(configPath, JSON.stringify(config, null, 2), 'utf-8');
  mcpConfigPath = configPath;
  log(`MCP config written to ${configPath}`);
  return configPath;
}

/**
 * Start Claude Code as a child process in a VSCode terminal.
 * Claude runs interactively so the user can see its output and interact.
 * We pass --mcp-config so it has access to SketchCode tools.
 */
export function startClaudeCode(extensionPath: string): void {
  if (claudeTerminal) {
    log('Claude Code terminal already exists');
    return;
  }

  const configPath = writeMcpConfig(extensionPath);

  // Get the workspace folder to use as cwd
  const workspaceFolder = vscode.workspace.workspaceFolders?.[0]?.uri.fsPath || os.homedir();

  // Build the system prompt that tells Claude about SketchCode
  const systemPrompt = [
    'You are working with SketchCode — a phone-based code annotation system.',
    'You have access to SketchCode MCP tools:',
    '- get_pending_annotation: Gets the latest sketch annotation (image + voice) from the phone. Call this when notified.',
    '- get_current_code: Gets the current file open in the editor.',
    '- get_session_status: Checks if the phone is connected.',
    '- send_code_to_phone: Pushes updated code to the phone display.',
    '',
    'When you receive a message about a new annotation, call get_pending_annotation to see the sketch and voice command,',
    'then make the requested code changes to the file indicated. After editing, the phone will auto-update.',
  ].join(' ');

  // Create a terminal running Claude with our MCP config
  claudeTerminal = vscode.window.createTerminal({
    name: 'SketchCode — Claude',
    cwd: workspaceFolder,
    env: {
      // Ensure Claude can find the state file
      SKETCHCODE_STATE_PATH: path.join(os.homedir(), '.sketchcode', 'state.json'),
    },
  });

  // Send the claude command to the terminal
  const claudeCmd = [
    'claude',
    '--mcp-config', JSON.stringify(configPath),
    '--append-system-prompt', JSON.stringify(systemPrompt),
  ].join(' ');

  claudeTerminal.sendText(claudeCmd);
  claudeTerminal.show(true); // true = preserve focus on editor

  // Listen for terminal close to clean up our reference
  const disposable = vscode.window.onDidCloseTerminal((t) => {
    if (t === claudeTerminal) {
      claudeTerminal = null;
      disposable.dispose();
      log('Claude Code terminal closed');
    }
  });

  log(`Claude Code started in terminal (cwd: ${workspaceFolder})`);
}

/**
 * Send a prompt to the running Claude Code terminal.
 * Used to notify Claude when a new annotation arrives.
 */
export function sendToClaudeCode(message: string): void {
  if (!claudeTerminal) {
    log('Cannot send to Claude Code — terminal not running');
    return;
  }
  claudeTerminal.sendText(message);
  log(`Sent to Claude Code: ${message.substring(0, 80)}...`);
}

/**
 * Stop the Claude Code terminal.
 */
export function stopClaudeCode(): void {
  if (claudeTerminal) {
    // Send /exit to cleanly quit Claude, then dispose the terminal
    try {
      claudeTerminal.sendText('/exit');
    } catch {
      // terminal might already be dead
    }
    // Give Claude a moment to exit, then force-dispose
    setTimeout(() => {
      try {
        claudeTerminal?.dispose();
      } catch {
        // already disposed
      }
      claudeTerminal = null;
    }, 1000);
    log('Claude Code terminal stopped');
  }

  // Clean up MCP config
  if (mcpConfigPath && fs.existsSync(mcpConfigPath)) {
    try {
      fs.unlinkSync(mcpConfigPath);
    } catch {
      // not critical
    }
    mcpConfigPath = null;
  }
}

/**
 * Check if Claude Code is running.
 */
export function isClaudeCodeRunning(): boolean {
  return claudeTerminal !== null;
}
