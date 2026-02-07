import * as vscode from 'vscode';
import * as path from 'path';
import * as fs from 'fs';
import * as os from 'os';
import { log, logError } from '../utils/logger';

let claudeTerminal: vscode.Terminal | null = null;
let mcpConfigPath: string | null = null;
let configPath: string | null = null;
let workspaceCwd: string | null = null;

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
  const cfgPath = path.join(configDir, 'mcp-config.json');
  const config = {
    mcpServers: {
      sketchcode: {
        command: 'node',
        args: [mcpServerPath],
      },
    },
  };
  fs.writeFileSync(cfgPath, JSON.stringify(config, null, 2), 'utf-8');
  mcpConfigPath = cfgPath;
  log(`MCP config written to ${cfgPath}`);
  return cfgPath;
}

/**
 * Start a long-running interactive Claude Code session in a terminal.
 * The user types prompts and hits enter to execute.
 */
export function startClaudeCode(extensionPath: string): void {
  if (claudeTerminal) {
    log('Claude Code terminal already exists');
    return;
  }

  configPath = writeMcpConfig(extensionPath);
  workspaceCwd = vscode.workspace.workspaceFolders?.[0]?.uri.fsPath || os.homedir();

  claudeTerminal = vscode.window.createTerminal({
    name: 'SketchCode — Claude',
    cwd: workspaceCwd,
    env: {
      SKETCHCODE_STATE_PATH: path.join(os.homedir(), '.sketchcode', 'state.json'),
    },
  });

  claudeTerminal.show(true); // true = preserve focus on editor

  // Launch interactive claude with MCP config
  claudeTerminal.sendText(`claude --mcp-config ${JSON.stringify(configPath)}`);

  // Listen for terminal close to clean up
  const disposable = vscode.window.onDidCloseTerminal((t) => {
    if (t === claudeTerminal) {
      claudeTerminal = null;
      disposable.dispose();
      log('Claude Code terminal closed');
    }
  });

  log(`Claude Code terminal ready (cwd: ${workspaceCwd})`);
}

/**
 * Type a message into the Claude terminal for the user to review and send.
 * Does NOT press enter — the user decides when to execute.
 */
export function sendToClaudeCode(message: string): void {
  if (!claudeTerminal) {
    log('Cannot send to Claude Code — terminal not running');
    return;
  }

  // Type the message into the terminal without pressing enter (false = no newline)
  claudeTerminal.sendText(message, false);
  claudeTerminal.show(true);
  log(`Typed prompt into Claude terminal (waiting for user to press enter)`);
}

/**
 * Stop the Claude Code terminal.
 */
export function stopClaudeCode(): void {
  if (claudeTerminal) {
    try {
      claudeTerminal.dispose();
    } catch {
      // already disposed
    }
    claudeTerminal = null;
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
  configPath = null;
  workspaceCwd = null;
}

/**
 * Check if Claude Code is running.
 */
export function isClaudeCodeRunning(): boolean {
  return claudeTerminal !== null;
}
