package dev.borich.kunigamicalc;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.SoundPool;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SoundEngine {
    private static final int SAMPLE_RATE = 24000;
    private final ExecutorService audioExecutor = Executors.newSingleThreadExecutor();
    private SoundPool soundPool;
    private final Map<String, Integer> voiceMap = new HashMap<>();
    private volatile boolean muted = false;
    private int lastVoiceStreamId = 0;

    private static final String[] VOICE_KEYS = {
            "d0", "d1", "d2", "d3", "d4", "d5", "d6", "d7", "d8", "d9",
            "plus", "minus", "mul", "div", "equals", "bs", "clear", "dot", "percent", "sign",
            "ee_67", "ee_87", "ee_50", "ee_993", "ee_777", "ee_69", "ee_420", "ee_666", "ee_1337", "ee_42", "ee_228", "ee_1488"
    };

    private static final float[] DIGIT_FREQS = {
            261.63f, 293.66f, 329.63f, 392.00f, 440.00f,
            523.25f, 587.33f, 659.25f, 783.99f, 880.00f
    };

    public SoundEngine(Context context) {
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
        soundPool = new SoundPool.Builder()
                .setMaxStreams(6)
                .setAudioAttributes(attrs)
                .build();

        AssetManager am = context.getAssets();
        for (String k : VOICE_KEYS) {
            try (AssetFileDescriptor afd = am.openFd("voice/" + k + ".mp3")) {
                int id = soundPool.load(afd, 1);
                voiceMap.put(k, id);
            } catch (Exception ignored) {
            }
        }
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
        if (muted && soundPool != null && lastVoiceStreamId != 0) {
            soundPool.stop(lastVoiceStreamId);
        }
    }

    public boolean isMuted() {
        return muted;
    }

    public void playKeySound(final String key) {
        if (muted) return;
        String clip = mapKeyToVoiceClip(key);
        playVoiceClip(clip);
        playSubtleClick(key);
    }

    public void playEasterEggVoice(final String eeClip) {
        if (muted) return;
        playVoiceClip(eeClip);
        playSubtleClick("=");
    }

    private void playVoiceClip(String clipName) {
        if (soundPool == null || clipName == null) return;
        Integer soundId = voiceMap.get(clipName);
        if (soundId != null) {
            if (lastVoiceStreamId != 0) {
                soundPool.stop(lastVoiceStreamId);
            }
            lastVoiceStreamId = soundPool.play(soundId, 1.0f, 1.0f, 2, 0, 1.0f);
        }
    }

    private String mapKeyToVoiceClip(String key) {
        if (key.length() == 1 && key.charAt(0) >= '0' && key.charAt(0) <= '9') {
            return "d" + key;
        }
        switch (key) {
            case "+": return "plus";
            case "-": return "minus";
            case "×": return "mul";
            case "÷": return "div";
            case "=": return "equals";
            case "⌫": return "bs";
            case "C": return "clear";
            case ".": return "dot";
            case "%": return "percent";
            case "±": return "sign";
            default:  return null;
        }
    }

    private void playSubtleClick(final String key) {
        audioExecutor.execute(() -> {
            try {
                short[] pcm = synthesizeClick(key);
                if (pcm == null || pcm.length == 0) return;
                AudioTrack track = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_GAME)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build())
                        .setAudioFormat(new AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(SAMPLE_RATE)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build())
                        .setBufferSizeInBytes(pcm.length * 2)
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build();
                track.write(pcm, 0, pcm.length);
                track.play();
                Thread.sleep(Math.max(30L, (pcm.length * 1000L) / SAMPLE_RATE + 15L));
                track.release();
            } catch (Throwable ignored) {
            }
        });
    }

    private short[] synthesizeClick(String key) {
        float freq = 440.0f;
        if (key.length() == 1 && key.charAt(0) >= '0' && key.charAt(0) <= '9') {
            freq = DIGIT_FREQS[key.charAt(0) - '0'];
        } else if ("=".equals(key)) {
            freq = 587.33f;
        }
        int n = (int) (SAMPLE_RATE * 0.045f);
        short[] out = new short[n];
        double ph = 0.0;
        for (int i = 0; i < n; i++) {
            float u = (float) i / n;
            float env = (1.0f - u) * (1.0f - u);
            ph += 2.0 * Math.PI * freq / SAMPLE_RATE;
            out[i] = (short) (Math.sin(ph) * env * 4200);
        }
        return out;
    }

    public void shutdown() {
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
        audioExecutor.shutdownNow();
    }
}
