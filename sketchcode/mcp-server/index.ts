import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import { getPendingAnnotation } from './tools/getPendingAnnotation.js';
import { getCurrentCode } from './tools/getCurrentCode.js';
import { sendCodeToPhone } from './tools/sendCodeToPhone.js';
import { getSessionStatus } from './tools/getSessionStatus.js';

const server = new McpServer({
  name: 'sketchcode',
  version: '0.1.0',
});

// Tool: Get pending annotation (sketch image + voice transcription)
server.tool(
  'get_pending_annotation',
  'Get the latest sketch annotation from the phone. Returns the annotated code screenshot (with drawn annotations visible) and any voice transcription. Use this to see what code changes the user is requesting through their sketch and voice commands.',
  {},
  async () => {
    const result = getPendingAnnotation();
    return result as any;
  }
);

// Tool: Get current code in the active editor
server.tool(
  'get_current_code',
  'Get the current code from the active VSCode editor. Returns the filename, language, and full code content.',
  {},
  async () => {
    return getCurrentCode();
  }
);

// Tool: Send/refresh code on the phone
server.tool(
  'send_code_to_phone',
  'Trigger a code refresh on the connected phone. The phone will update to show the latest code from the active editor.',
  {},
  async () => {
    return await sendCodeToPhone();
  }
);

// Tool: Get session status
server.tool(
  'get_session_status',
  'Check the current SketchCode session status including whether a phone is connected and if there are pending annotations.',
  {},
  async () => {
    return getSessionStatus();
  }
);

// Start the server
async function main() {
  const transport = new StdioServerTransport();
  await server.connect(transport);
}

main().catch(console.error);
