package br.com.folha.fap.alerts;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

final class AlertPlayer {
    static final String[] TONE_NAMES = {
            "Bipe simples", "Bipe duplo", "Erro", "Emergência",
            "Alerta contínuo", "Alerta curto", "Pip", "Alerta leve"
    };
    static final int[] TONE_IDS = {
            ToneGenerator.TONE_PROP_BEEP, ToneGenerator.TONE_PROP_BEEP2,
            ToneGenerator.TONE_SUP_ERROR, ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK,
            ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, ToneGenerator.TONE_CDMA_ABBR_ALERT,
            ToneGenerator.TONE_CDMA_PIP, ToneGenerator.TONE_CDMA_ALERT_INCALL_LITE
    };
    private static final long MAX_URI_MS = 4000;

    private final Context ctx;
    private final Handler h = new Handler(Looper.getMainLooper());
    private ToneGenerator tg;
    private MediaPlayer mp;
    private Runnable pending;
    private int remaining;
    private int gen;

    AlertPlayer(Context c) { ctx = c.getApplicationContext(); }

    void play() { play(AlertStore.repeats()); }

    void play(int repeats) {
        stop();
        remaining = repeats;
        step(gen);
    }

    void stop() {
        gen++;
        remaining = 0;
        if (pending != null) { h.removeCallbacks(pending); pending = null; }
        releaseTone();
        releasePlayer();
    }

    private void step(final int g) {
        if (g != gen || remaining <= 0) return;
        remaining--;
        String uri = AlertStore.uri();
        if (AlertStore.mode() == 1 && uri != null) playUri(uri, g); else playTone(g);
    }

    private void playTone(final int g) {
        try {
            int i = Math.max(0, Math.min(TONE_IDS.length - 1, AlertStore.tone()));
            tg = new ToneGenerator(AudioManager.STREAM_ALARM, AlertStore.volume());
            tg.startTone(TONE_IDS[i], 400);
            later(new Runnable() {
                @Override public void run() { releaseTone(); step(g); }
            }, 700);
        } catch (Throwable t) {
            Log.w("FapAlerts", "tone", t);
        }
    }

    private void playUri(String uri, final int g) {
        try {
            mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            mp.setDataSource(ctx, Uri.parse(uri));
            float v = AlertStore.volume() / 100f;
            mp.setVolume(v, v);
            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override public void onCompletion(MediaPlayer m) { next(g); }
            });
            mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override public boolean onError(MediaPlayer m, int what, int extra) { next(g); return true; }
            });
            mp.prepare();
            mp.start();
            later(new Runnable() {
                @Override public void run() { next(g); }
            }, MAX_URI_MS);
        } catch (Throwable t) {
            Log.w("FapAlerts", "uri", t);
            releasePlayer();
            playTone(g);
        }
    }

    private void next(int g) {
        if (g != gen) return;
        if (pending != null) { h.removeCallbacks(pending); pending = null; }
        releasePlayer();
        step(g);
    }

    private void later(Runnable r, long ms) {
        if (pending != null) h.removeCallbacks(pending);
        pending = r;
        h.postDelayed(r, ms);
    }

    private void releaseTone() {
        if (tg != null) { try { tg.release(); } catch (Throwable ignored) {} tg = null; }
    }

    private void releasePlayer() {
        if (mp != null) {
            try { mp.stop(); } catch (Throwable ignored) {}
            try { mp.release(); } catch (Throwable ignored) {}
            mp = null;
        }
    }
}
