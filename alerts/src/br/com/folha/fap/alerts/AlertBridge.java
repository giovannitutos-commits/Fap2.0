package br.com.folha.fap.alerts;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.util.Log;

/** Ponto de entrada chamado por FapApplication.onCreate (patch smali). */
public final class AlertBridge {
    private static final String MAIN = "br.com.folha.fap.MainActivity";
    private static boolean started;
    private static AlertEngine engine;

    public static void init(Application app) {
        if (started) return;
        started = true;
        try {
            AlertStore.init(app);
            app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override public void onActivityResumed(Activity a) {
                    if (!MAIN.equals(a.getClass().getName())) return;
                    if (engine != null) engine.stop();
                    engine = new AlertEngine(a);
                    engine.start();
                }
                @Override public void onActivityPaused(Activity a) {
                    if (engine != null && engine.owner() == a) { engine.stop(); engine = null; }
                }
                @Override public void onActivityCreated(Activity a, Bundle b) {}
                @Override public void onActivityStarted(Activity a) {}
                @Override public void onActivityStopped(Activity a) {}
                @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
                @Override public void onActivityDestroyed(Activity a) {}
            });
        } catch (Throwable t) {
            Log.e("FapAlerts", "init falhou", t);
        }
    }

    private AlertBridge() {}
}
