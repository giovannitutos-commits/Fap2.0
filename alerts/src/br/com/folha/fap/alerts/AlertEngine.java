package br.com.folha.fap.alerts;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Lê as leituras do app (via reflexão no ViewModel) e dispara os alertas. */
final class AlertEngine implements Runnable {
    private static final String TAG = "FapAlerts";
    private static final long TICK_MS = 400;

    private final Activity act;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final AlertPlayer player;
    private final AlertUi ui;
    private final Map<String, Method> cache = new HashMap<>();
    private final Map<Integer, Long> since = new HashMap<>();
    private final Map<Integer, Long> lastAlert = new HashMap<>();
    private final Set<Integer> dismissed = new HashSet<>();
    private final Set<Integer> active = new HashSet<>();
    private final Map<Integer, Double> values = new HashMap<>();
    private List<Object[]> channels = new ArrayList<>(); // {Integer id, String nome, String unidade}
    private boolean running;

    AlertEngine(Activity a) {
        act = a;
        player = new AlertPlayer(a);
        ui = new AlertUi(a, this, player);
    }

    Activity owner() { return act; }
    List<Object[]> channels() { return channels; }
    Map<Integer, Double> values() { return values; }

    void start() {
        if (running) return;
        running = true;
        ui.attach();
        h.post(this);
    }

    void stop() {
        running = false;
        h.removeCallbacks(this);
        player.stop();
        ui.detach();
    }

    /** Toque no banner: silencia até o valor voltar ao normal. */
    void dismiss() {
        dismissed.addAll(active);
        active.clear();
        player.stop();
        ui.hideBanner();
    }

    @Override public void run() {
        if (!running) return;
        try { tick(); } catch (Throwable t) { Log.w(TAG, "tick", t); }
        h.postDelayed(this, TICK_MS);
    }

    private void tick() throws Exception {
        Object model = call(act, "getModel");
        if (model == null) return;
        Object flow = call(model, "getUi");
        Object state = flow == null ? null : call(flow, "getValue");
        if (state == null) return;

        List<?> defs = (List<?>) call(state, "getChannels");
        if (defs != null) {
            List<Object[]> list = new ArrayList<>();
            for (Object d : defs) {
                Object id = call(d, "getId");
                if (id instanceof Number) {
                    list.add(new Object[]{((Number) id).intValue(),
                            String.valueOf(call(d, "getName")), String.valueOf(call(d, "getUnit"))});
                }
            }
            channels = list;
        }

        Object connected = call(state, "getConnected");
        Map<?, ?> readings = (Map<?, ?>) call(state, "getReadings");
        values.clear();
        if (readings != null) {
            for (Object[] ch : channels) {
                Double v = value(readings.get((Integer) ch[0]));
                if (v != null) values.put((Integer) ch[0], v);
            }
        }

        if (!AlertStore.all() || Boolean.FALSE.equals(connected) || readings == null) {
            reset();
            return;
        }

        long now = SystemClock.uptimeMillis();
        boolean fire = false;
        int color = 0;
        StringBuilder text = new StringBuilder();
        active.clear();

        for (Object[] ch : channels) {
            int id = (Integer) ch[0];
            if (!AlertStore.ruleOn(id)) continue;
            Double v = values.get(id);
            Double min = AlertStore.ruleMin(id);
            Double max = AlertStore.ruleMax(id);
            boolean bad = v != null && ((min != null && v < min) || (max != null && v > max));
            if (!bad) {
                since.remove(id);
                dismissed.remove(id);
                lastAlert.remove(id);
                continue;
            }
            Long t0 = since.get(id);
            if (t0 == null) { t0 = now; since.put(id, t0); }
            if (now - t0 < AlertStore.confirmSec() * 1000L) continue;
            if (dismissed.contains(id)) continue;

            active.add(id);
            if (color == 0) color = AlertStore.color(id);
            if (text.length() > 0) text.append('\n');
            boolean high = max != null && v > max;
            String unit = ch[2].equals("null") ? "" : (String) ch[2];
            text.append("⚠ ").append(ch[1]).append(": ").append(AlertStore.fmt(round(v)))
                    .append(' ').append(unit)
                    .append(high ? "  (acima de " + AlertStore.fmt(max) + ")" : "  (abaixo de " + AlertStore.fmt(min) + ")");
            Long last = lastAlert.get(id);
            if (last == null || now - last >= AlertStore.cooldownSec() * 1000L) {
                lastAlert.put(id, now);
                fire = true;
            }
        }

        if (active.isEmpty()) {
            ui.hideBanner();
        } else {
            if (AlertStore.visual()) ui.showBanner(text.toString(), color); else ui.hideBanner();
            if (fire) {
                player.play();
                if (AlertStore.popup()) ui.popup(text.toString());
            }
        }
    }

    private void reset() {
        since.clear(); lastAlert.clear(); dismissed.clear(); active.clear();
        ui.hideBanner();
    }

    private Double value(Object reading) throws Exception {
        if (reading == null) return null;
        Object decoded = call(reading, "getDecoded");
        Object v = decoded == null ? null : call(decoded, "getValue");
        return v instanceof Number ? ((Number) v).doubleValue() : null;
    }

    static double round(double v) { return Math.round(v * 100.0) / 100.0; }

    /** Chama um método público sem argumentos por reflexão (com cache). */
    private Object call(Object o, String name) throws Exception {
        if (o == null) return null;
        String key = o.getClass().getName() + "#" + name;
        Method m = cache.get(key);
        if (m == null) {
            m = o.getClass().getMethod(name);
            m.setAccessible(true);
            cache.put(key, m);
        }
        return m.invoke(o);
    }
}
