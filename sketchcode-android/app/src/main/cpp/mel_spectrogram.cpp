#include <jni.h>
#include <cmath>
#include <cstring>
#include <vector>
#include <algorithm>
#include <android/log.h>

#define LOG_TAG "WhisperMel"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

// Whisper-Large-V3-Turbo parameters
static constexpr int SAMPLE_RATE = 16000;
static constexpr int N_FFT = 400;
static constexpr int HOP_LENGTH = 160;
static constexpr int N_MELS = 128;
static constexpr int CHUNK_LENGTH = 30; // seconds
static constexpr int N_SAMPLES = SAMPLE_RATE * CHUNK_LENGTH; // 480000
static constexpr int N_FRAMES = N_SAMPLES / HOP_LENGTH;      // 3000
static constexpr int FFT_SIZE = 512; // next power of 2 >= N_FFT
static constexpr int FFT_OUT = N_FFT / 2 + 1; // 201

// ---- FFT ----

static void fft(float* re, float* im, int n) {
    // Bit-reversal permutation
    for (int i = 1, j = 0; i < n; i++) {
        int bit = n >> 1;
        for (; j & bit; bit >>= 1) {
            j ^= bit;
        }
        j ^= bit;
        if (i < j) {
            std::swap(re[i], re[j]);
            std::swap(im[i], im[j]);
        }
    }
    // Cooley-Tukey
    for (int len = 2; len <= n; len <<= 1) {
        float ang = -2.0f * M_PI / len;
        float wRe = cosf(ang), wIm = sinf(ang);
        for (int i = 0; i < n; i += len) {
            float curRe = 1.0f, curIm = 0.0f;
            for (int j = 0; j < len / 2; j++) {
                float tRe = curRe * re[i + j + len/2] - curIm * im[i + j + len/2];
                float tIm = curRe * im[i + j + len/2] + curIm * re[i + j + len/2];
                re[i + j + len/2] = re[i + j] - tRe;
                im[i + j + len/2] = im[i + j] - tIm;
                re[i + j] += tRe;
                im[i + j] += tIm;
                float newCurRe = curRe * wRe - curIm * wIm;
                curIm = curRe * wIm + curIm * wRe;
                curRe = newCurRe;
            }
        }
    }
}

// ---- Mel Filterbank (computed analytically) ----

static inline float hzToMel(float hz) {
    return 2595.0f * log10f(1.0f + hz / 700.0f);
}

static inline float melToHz(float mel) {
    return 700.0f * (powf(10.0f, mel / 2595.0f) - 1.0f);
}

static void computeMelFilterbank(float* filters, int nMels, int nFft, int sampleRate) {
    int nFreqs = nFft / 2 + 1; // 201
    float fMin = 0.0f;
    float fMax = (float)sampleRate / 2.0f; // 8000 Hz
    float melMin = hzToMel(fMin);
    float melMax = hzToMel(fMax);

    // nMels + 2 evenly spaced points in mel space
    std::vector<float> melPoints(nMels + 2);
    for (int i = 0; i < nMels + 2; i++) {
        melPoints[i] = melMin + (melMax - melMin) * i / (nMels + 1);
    }

    // Convert to Hz and then to FFT bin indices
    std::vector<float> binFreqs(nMels + 2);
    for (int i = 0; i < nMels + 2; i++) {
        float hz = melToHz(melPoints[i]);
        binFreqs[i] = hz * nFft / sampleRate;
    }

    // Create triangular filters
    memset(filters, 0, nMels * nFreqs * sizeof(float));
    for (int m = 0; m < nMels; m++) {
        float left = binFreqs[m];
        float center = binFreqs[m + 1];
        float right = binFreqs[m + 2];

        for (int k = 0; k < nFreqs; k++) {
            float fk = (float)k;
            if (fk >= left && fk <= center && center > left) {
                filters[m * nFreqs + k] = (fk - left) / (center - left);
            } else if (fk > center && fk <= right && right > center) {
                filters[m * nFreqs + k] = (right - fk) / (right - center);
            }
        }
    }

    // Slaney-style normalization: each filter scaled by 2.0 / (right - left) in Hz
    for (int m = 0; m < nMels; m++) {
        float leftHz = melToHz(melPoints[m]);
        float rightHz = melToHz(melPoints[m + 2]);
        float enorm = 2.0f / (rightHz - leftHz);
        for (int k = 0; k < nFreqs; k++) {
            filters[m * nFreqs + k] *= enorm;
        }
    }
}

// ---- Hann window ----

static void computeHannWindow(float* window, int length) {
    for (int i = 0; i < length; i++) {
        window[i] = 0.5f * (1.0f - cosf(2.0f * M_PI * i / length));
    }
}

// ---- JNI Entry Point ----

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_com_sketchcode_app_whisper_MelSpectrogram_nativeComputeMelSpectrogram(
        JNIEnv *env, jobject /* this */, jfloatArray audioArray) {

    jsize audioLen = env->GetArrayLength(audioArray);
    float* audio = env->GetFloatArrayElements(audioArray, nullptr);

    LOGI("Input audio: %d samples (%.2fs)", (int)audioLen, (float)audioLen / SAMPLE_RATE);

    // Pad to N_SAMPLES + N_FFT to prevent buffer overflow on last STFT frame.
    // The last frame starts at (N_FRAMES-1)*HOP_LENGTH = 479840 and reads N_FFT=400
    // samples, accessing index 480239 which exceeds N_SAMPLES=480000.
    // Extra N_FFT zeros ensure safe access for all frames.
    static constexpr int PADDED_SIZE = N_SAMPLES + N_FFT; // 480400
    std::vector<float> padded(PADDED_SIZE, 0.0f);
    int copyLen = std::min((int)audioLen, N_SAMPLES);
    memcpy(padded.data(), audio, copyLen * sizeof(float));
    env->ReleaseFloatArrayElements(audioArray, audio, 0);

    // Pre-compute Hann window
    float hannWindow[N_FFT];
    computeHannWindow(hannWindow, N_FFT);

    // Pre-compute mel filterbank
    std::vector<float> melFilters(N_MELS * FFT_OUT);
    computeMelFilterbank(melFilters.data(), N_MELS, N_FFT, SAMPLE_RATE);

    // Output: N_MELS x N_FRAMES
    std::vector<float> melSpec(N_MELS * N_FRAMES, 0.0f);

    // FFT buffers
    float fftRe[FFT_SIZE];
    float fftIm[FFT_SIZE];

    // Process each frame
    for (int frame = 0; frame < N_FRAMES; frame++) {
        int start = frame * HOP_LENGTH;

        // Zero-pad FFT buffer
        memset(fftRe, 0, FFT_SIZE * sizeof(float));
        memset(fftIm, 0, FFT_SIZE * sizeof(float));

        // Apply Hann window
        for (int i = 0; i < N_FFT; i++) {
            fftRe[i] = padded[start + i] * hannWindow[i];
        }

        // FFT
        fft(fftRe, fftIm, FFT_SIZE);

        // Magnitude squared of first FFT_OUT bins
        float magnitudes[FFT_OUT];
        for (int k = 0; k < FFT_OUT; k++) {
            magnitudes[k] = fftRe[k] * fftRe[k] + fftIm[k] * fftIm[k];
        }

        // Apply mel filterbank
        for (int m = 0; m < N_MELS; m++) {
            float sum = 0.0f;
            for (int k = 0; k < FFT_OUT; k++) {
                sum += melFilters[m * FFT_OUT + k] * magnitudes[k];
            }
            // Log mel spectrogram
            melSpec[m * N_FRAMES + frame] = log10f(fmaxf(sum, 1e-10f));
        }
    }

    // Normalize: clamp to (max - 8.0), then (x + 4.0) / 4.0
    float maxVal = *std::max_element(melSpec.begin(), melSpec.end());
    for (int i = 0; i < N_MELS * N_FRAMES; i++) {
        melSpec[i] = fmaxf(melSpec[i], maxVal - 8.0f);
        melSpec[i] = (melSpec[i] + 4.0f) / 4.0f;
    }

    // Return as Java float array
    jfloatArray result = env->NewFloatArray(N_MELS * N_FRAMES);
    env->SetFloatArrayRegion(result, 0, N_MELS * N_FRAMES, melSpec.data());

    LOGI("Mel spectrogram computed: %d frames, %d mels, input %d samples", N_FRAMES, N_MELS, copyLen);
    return result;
}
