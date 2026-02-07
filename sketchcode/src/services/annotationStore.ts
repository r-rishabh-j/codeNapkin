import { v4 as uuidv4 } from 'uuid';
import { Annotation } from '../types';
import { EventEmitter } from 'events';

class AnnotationStore extends EventEmitter {
  private annotations: Annotation[] = [];

  /** Add a new annotation from the phone */
  add(data: {
    sketchImageBase64: string;
    voiceTranscription: string;
    codeSnapshot: {
      filename: string;
      code: string;
      language: string;
      lineCount: number;
    };
  }): Annotation {
    const annotation: Annotation = {
      id: uuidv4(),
      sketchImageBase64: data.sketchImageBase64,
      voiceTranscription: data.voiceTranscription,
      codeSnapshot: data.codeSnapshot,
      timestamp: Date.now(),
      processed: false,
    };
    this.annotations.push(annotation);
    this.emit('annotation', annotation);
    return annotation;
  }

  /** Get the latest unprocessed annotation */
  getPending(): Annotation | null {
    for (let i = this.annotations.length - 1; i >= 0; i--) {
      if (!this.annotations[i].processed) {
        return this.annotations[i];
      }
    }
    return null;
  }

  /** Mark an annotation as processed by Claude Code */
  markProcessed(id: string): void {
    const annotation = this.annotations.find(a => a.id === id);
    if (annotation) {
      annotation.processed = true;
    }
  }

  /** Get the latest annotation regardless of status */
  getLatest(): Annotation | null {
    return this.annotations.length > 0
      ? this.annotations[this.annotations.length - 1]
      : null;
  }

  /** Clear all annotations */
  clear(): void {
    this.annotations = [];
  }
}

export const annotationStore = new AnnotationStore();
