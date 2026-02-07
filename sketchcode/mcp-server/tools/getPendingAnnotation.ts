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

  if (!state.pendingAnnotation) {
    return {
      content: [{ type: 'text', text: 'No pending annotation. The phone has not sent any sketch annotations yet.' }],
    };
  }

  const ann = state.pendingAnnotation;
  const content: Array<{ type: string; text?: string; data?: string; mimeType?: string }> = [];

  // Return the sketch image so Claude can visually interpret annotations
  content.push({
    type: 'image',
    data: ann.sketchImageBase64,
    mimeType: 'image/png',
  });

  // Return text context
  let textContent = `## Annotation Details\n`;
  textContent += `- **File**: ${ann.codeFilename}\n`;
  textContent += `- **Timestamp**: ${new Date(ann.timestamp).toISOString()}\n\n`;

  if (ann.voiceTranscription) {
    textContent += `## Voice Command\n${ann.voiceTranscription}\n\n`;
  }

  textContent += `## Code Content (${ann.codeFilename})\n\`\`\`\n${ann.codeContent}\n\`\`\`\n`;

  content.push({ type: 'text', text: textContent });

  // Mark annotation as consumed so Claude doesn't re-read the same one
  state.pendingAnnotation = null;
  writeState(state);

  return { content };
}
