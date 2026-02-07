import * as vscode from 'vscode';
import { Annotation } from '../types';

let panel: vscode.WebviewPanel | null = null;

export function showAnnotationPanel(annotation: Annotation): void {
  if (panel) {
    panel.reveal();
  } else {
    panel = vscode.window.createWebviewPanel(
      'sketchcodeAnnotation',
      'SketchCode - Annotation',
      vscode.ViewColumn.Beside,
      { enableScripts: false }
    );
    panel.onDidDispose(() => { panel = null; });
  }

  panel.webview.html = getAnnotationHtml(annotation);
}

export function disposeAnnotationPanel(): void {
  panel?.dispose();
  panel = null;
}

function getAnnotationHtml(annotation: Annotation): string {
  const voiceSection = annotation.voiceTranscription
    ? `<div class="voice-section">
        <h3>Voice Command</h3>
        <div class="voice-text">${escapeHtml(annotation.voiceTranscription)}</div>
      </div>`
    : '';

  return `<!DOCTYPE html>
<html>
<head>
  <style>
    body {
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
      padding: 16px;
      color: var(--vscode-foreground);
      background: var(--vscode-editor-background);
    }
    h2 { margin-bottom: 4px; }
    .meta {
      font-size: 12px;
      opacity: 0.6;
      margin-bottom: 16px;
    }
    .sketch-image {
      max-width: 100%;
      border: 1px solid var(--vscode-panel-border);
      border-radius: 8px;
    }
    .voice-section {
      margin-top: 16px;
      padding: 12px;
      background: var(--vscode-textBlockQuote-background);
      border-radius: 8px;
    }
    .voice-section h3 { margin: 0 0 8px 0; font-size: 14px; }
    .voice-text {
      font-size: 14px;
      line-height: 1.5;
    }
    .code-info {
      margin-top: 12px;
      font-size: 12px;
      opacity: 0.7;
    }
  </style>
</head>
<body>
  <h2>Received Annotation</h2>
  <div class="meta">
    ${new Date(annotation.timestamp).toLocaleTimeString()} | ${annotation.codeSnapshot.filename}
  </div>

  <img class="sketch-image" src="data:image/png;base64,${annotation.sketchImageBase64}" alt="Annotation" />

  ${voiceSection}

  <div class="code-info">
    File: ${escapeHtml(annotation.codeSnapshot.filename)} | ${annotation.codeSnapshot.lineCount} lines | ${annotation.codeSnapshot.language}
  </div>
</body>
</html>`;
}

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}
