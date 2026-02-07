import { enqueueCommand } from '../stateReader.js';

export async function sendCodeToPhone(): Promise<{
  content: Array<{ type: string; text: string }>;
}> {
  try {
    const result = await enqueueCommand('send_code_to_phone');
    return {
      content: [{ type: 'text', text: `Code sent to phone: ${result}` }],
    };
  } catch (err) {
    const msg = err instanceof Error ? err.message : String(err);
    return {
      content: [{ type: 'text', text: `Failed to send code to phone: ${msg}` }],
    };
  }
}
