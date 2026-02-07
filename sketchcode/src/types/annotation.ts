/** A received annotation from the phone */
export interface Annotation {
  id: string;
  sketchImageBase64: string;
  voiceTranscription: string;
  codeSnapshot: {
    filename: string;
    code: string;
    language: string;
    lineCount: number;
  };
  timestamp: number;
  processed: boolean;
}
