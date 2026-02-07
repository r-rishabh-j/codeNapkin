import { readState } from '../stateReader.js';

export function getCurrentCode(): {
  content: Array<{ type: string; text: string }>;
} {
  const state = readState();
  if (!state || !state.currentCode) {
    return {
      content: [{ type: 'text', text: 'No active editor code available. Make sure a file is open in VSCode and a SketchCode session is running.' }],
    };
  }

  const code = state.currentCode;
  const text = `## Current Editor Code
- **File**: ${code.filename}
- **Language**: ${code.language}
- **Lines**: ${code.lineCount}

\`\`\`${code.language}
${code.code}
\`\`\``;

  return { content: [{ type: 'text', text }] };
}
