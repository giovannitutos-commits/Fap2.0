package br.com.folha.fap.alerts;

import android.content.Context;
import android.content.SharedPreferences;

final class AlertStore {
    private static SharedPreferences sp;

    static void init(Context c) {
        sp = c.getSharedPreferences("fap_alerts", Context.MODE_PRIVATE);
    }

    static boolean all() { return sp.getBoolean("all", true); }
    static void all(boolean v) { sp.edit().putBoolean("all", v).apply(); }

    static boolean visual() { return sp.getBoolean("visual", true); }
    static void visual(boolean v) { sp.edit().putBoolean("visual", v).apply(); }

    static boolean blink() { return sp.getBoolean("blink", true); }
    static void blink(boolean v) { sp.edit().putBoolean("blink", v).apply(); }

    /** 0 = bipe do sistema, 1 = som do aparelho (uri). */
    static int mode() { return sp.getInt("mode", 0); }
    static void mode(int v) { sp.edit().putInt("mode", v).apply(); }

    static int tone() { return sp.getInt("tone", 0); }
    static void tone(int v) { sp.edit().putInt("tone", v).apply(); }

    static String uri() { return sp.getString("uri", null); }
    static void uri(String v) { sp.edit().putString("uri", v).apply(); }

    static int volume() { return sp.getInt("volume", 90); }
    static void volume(int v) { sp.edit().putInt("volume", clamp(v, 0, 100)).apply(); }

    static int repeats() { return sp.getInt("repeats", 3); }
    static void repeats(int v) { sp.edit().putInt("repeats", clamp(v, 1, 20)).apply(); }

    static int cooldownSec() { return sp.getInt("cooldown", 5); }
    static void cooldownSec(int v) { sp.edit().putInt("cooldown", clamp(v, 1, 120)).apply(); }

    static int confirmSec() { return sp.getInt("confirm", 0); }
    static void confirmSec(int v) { sp.edit().putInt("confirm", clamp(v, 0, 30)).apply(); }

    static boolean popup() { return sp.getBoolean("popup", false); }
    static void popup(boolean v) { sp.edit().putBoolean("popup", v).apply(); }

    static int color(int id) { return sp.getInt("col_" + id, 0xFFD32F2F); }
    static void color(int id, int c) { sp.edit().putInt("col_" + id, c).apply(); }

    static boolean ruleOn(int id) { return sp.getBoolean("en_" + id, false); }
    static Double ruleMin(int id) { return parse(sp.getString("min_" + id, "")); }
    static Double ruleMax(int id) { return parse(sp.getString("max_" + id, "")); }

    static void saveRule(int id, boolean on, String min, String max) {
        sp.edit().putBoolean("en_" + id, on)
                .putString("min_" + id, min == null ? "" : min.trim())
                .putString("max_" + id, max == null ? "" : max.trim())
                .apply();
    }

    static String fmt(Double d) {
        if (d == null) return "";
        if (d == Math.rint(d) && Math.abs(d) < 1e9) return String.valueOf(d.longValue());
        return String.valueOf(d);
    }

    private static Double parse(String s) {
        if (s == null) return null;
        s = s.trim().replace(',', '.');
        if (s.isEmpty()) return null;
        try { return Double.valueOf(s); } catch (NumberFormatException e) { return null; }
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    private AlertStore() {}
}
