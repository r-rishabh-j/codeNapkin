/**
 * Voice recording using Web Speech API.
 * Provides speech-to-text for voice commands.
 */
const VoiceRecorder = (() => {
  let recognition = null;
  let isRecording = false;
  let transcription = '';
  let supported = false;
  const listeners = {};

  function init() {
    const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SpeechRecognition) {
      supported = false;
      emit('unsupported');
      return;
    }

    supported = true;
    recognition = new SpeechRecognition();
    recognition.continuous = false;
    recognition.interimResults = true;
    recognition.lang = 'en-US';

    recognition.onresult = (event) => {
      let interim = '';
      let final = '';

      for (let i = event.resultIndex; i < event.results.length; i++) {
        const result = event.results[i];
        if (result.isFinal) {
          final += result[0].transcript;
        } else {
          interim += result[0].transcript;
        }
      }

      if (final) {
        transcription = (transcription + ' ' + final).trim();
        emit('result', { text: transcription, isFinal: true });
      } else if (interim) {
        emit('result', { text: (transcription + ' ' + interim).trim(), isFinal: false });
      }
    };

    recognition.onerror = (event) => {
      if (event.error !== 'aborted') {
        emit('error', event.error);
      }
      isRecording = false;
      emit('stopped');
    };

    recognition.onend = () => {
      isRecording = false;
      emit('stopped');
    };
  }

  function start() {
    if (!supported || !recognition) return false;
    if (isRecording) {
      stop();
      return false;
    }

    transcription = '';
    isRecording = true;
    recognition.start();
    emit('started');
    return true;
  }

  function stop() {
    if (!recognition || !isRecording) return;
    recognition.stop();
    isRecording = false;
    emit('stopped');
  }

  function toggle() {
    if (isRecording) {
      stop();
    } else {
      start();
    }
  }

  function getTranscription() {
    return transcription;
  }

  function clearTranscription() {
    transcription = '';
  }

  function isActive() {
    return isRecording;
  }

  function isSupported() {
    return supported;
  }

  function on(event, callback) {
    if (!listeners[event]) listeners[event] = [];
    listeners[event].push(callback);
  }

  function emit(event, data) {
    const cbs = listeners[event];
    if (cbs) cbs.forEach(cb => cb(data));
  }

  return { init, start, stop, toggle, getTranscription, clearTranscription, isActive, isSupported, on };
})();
