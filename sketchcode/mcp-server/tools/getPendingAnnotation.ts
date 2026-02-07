import { readState, writeState } from '../stateReader.js';

export function getPendingAnnotation(): {
  content: Array<{ type: string; text?: string; data?: string; mimeType?: string }>;
} {
  const state = readState();

  if (!state || !state.sessionActive) {
    return {
      content: [{ type: 'text', text: 'No active SketchCode session. Start a session in VSCode first (Cmd+Shift+P → SketchCode: Start Session).' }],
    };
  }

  if (!state.pendingAnnotations || state.pendingAnnotations.length === 0) {
    return {
      content: [{ type: 'text', text: 'No pending annotations. The phone has not sent any sketch annotations yet.' }],
    };
  }

  const annotations = state.pendingAnnotations;
  const content: Array<{ type: string; text?: string; data?: string; mimeType?: string }> = [];

  // Return all annotations — each with its image and metadata
  for (let i = 0; i < annotations.length; i++) {
    const ann = annotations[i];
    const label = annotations.length > 1 ? ` (${i + 1} of ${annotations.length})` : '';

    content.push({
      type: 'image',
      data: ann.sketchImageBase64,
      mimeType: 'image/jpeg',
    });

    let textContent = `## Annotation${label}\n`;
    textContent += `- **File**: ${ann.codeFilename}\n`;
    textContent += `- **Timestamp**: ${new Date(ann.timestamp).toISOString()}\n`;
    if (ann.voiceTranscription) {
      textContent += `- **Voice Command**: ${ann.voiceTranscription}\n`;
    }
    textContent += '\n';

    content.push({ type: 'text', text: textContent });
  }

  // Append code content once (from the first annotation — they share the same file)
  const first = annotations[0];
  content.push({
    type: 'text',
    text: `## Code Content (${first.codeFilename})\n\`\`\`\n${first.codeContent}\n\`\`\`\n`,
  });

  // Clear all consumed annotations
  state.pendingAnnotations = [];
  writeState(state);

  return { content };
}
