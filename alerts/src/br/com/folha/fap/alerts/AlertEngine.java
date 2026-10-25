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
