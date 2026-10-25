package br.com.folha.fap.alerts;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.RingtoneManager;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Banner, tela colorida, botão "Alertas" e o painel de configuração (duas abas). */
final class AlertUi {
    private static final String TAG_BANNER = "fapAlertBanner";
    private static final String TAG_FLASH = "fapAlertFlash";
    private static final String TAG_BELL = "fapAlertBell";
    private static final String TAG_PANEL = "fapAlertPanel";

    private static final String[] COLOR_NAMES = {"Vermelho", "Laranja", "Amarelo", "Azul", "Roxo", "Verde"};
    private static final int[] COLORS = {0xFFD32F2F, 0xFFF57C00, 0xFFFBC02D, 0xFF1976D2, 0xFF7B1FA2, 0xFF388E3C};

    private final Activity a;
    private final AlertEngine eng;
    private final AlertPlayer player;
    private FrameLayout root;
    private TextView banner;
    private View flash;
    private ValueAnimator anim;
    private AlertDialog popup;

    private FrameLayout panel;
    private LinearLayout body;
    private LinearLayout listBox;
    private int tab = 0;
    private String filter = "";
    private final Map<Integer, TextView> live = new HashMap<>();
    private final Runnable refresher = new Runnable() {
        @Override public void run() {
            if (panel == null || root == null) return;
            Map<Integer, Double> v = eng.values();
            for (Map.Entry<Integer, TextView> e : live.entrySet()) {
                Double d = v.get(e.getKey());
                e.getValue().setText(d == null ? "—" : AlertStore.fmt(AlertEngine.round(d)));
            }
            root.postDelayed(this, 500);
        }
    };

    AlertUi(Activity a, AlertEngine eng, AlertPlayer player) {
        this.a = a;
        this.eng = eng;
        this.player = player;
    }

    // ---------- overlay ----------

    void attach() {
        root = (FrameLayout) a.findViewById(android.R.id.content);
        if (root == null) return;
        clearViews();

        flash = new View(a);
        flash.setTag(TAG_FLASH);
        flash.setClickable(false);
        flash.setVisibility(View.GONE);
        root.addView(flash, new FrameLayout.LayoutParams(-1, -1));

        banner = new TextView(a);
        banner.setTag(TAG_BANNER);
        banner.setTextColor(Color.WHITE);
        banner.setTypeface(Typeface.DEFAULT_BOLD);
        banner.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        banner.setGravity(Gravity.CENTER);
        banner.setPadding(dp(12), dp(12), dp(12), dp(12));
        banner.setVisibility(View.GONE);
        banner.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { eng.dismiss(); }
        });
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        bp.topMargin = statusBarHeight();
        root.addView(banner, bp);

        TextView bell = new TextView(a);
        bell.setTag(TAG_BELL);
        bell.setText("🔔 Alertas");
        bell.setTextColor(Color.WHITE);
        bell.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        bell.setGravity(Gravity.CENTER);
        bell.setPadding(dp(12), dp(8), dp(12), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(20));
        bg.setColor(0xCC000000);
        bell.setBackground(bg);
        bell.setAlpha(0.8f);
        bell.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openPanel(); }
        });
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        lp.leftMargin = dp(8);
        lp.bottomMargin = dp(96);
        root.addView(bell, lp);
    }

    void detach() {
        stopFlash();
        closePanel();
        if (popup != null) { try { popup.dismiss(); } catch (Throwable ignored) {} popup = null; }
        if (root != null) clearViews();
    }

    private void clearViews() {
        for (String t : new String[]{TAG_BANNER, TAG_FLASH, TAG_BELL, TAG_PANEL}) {
            View v;
            while ((v = root.findViewWithTag(t)) != null) ((ViewGroup) v.getParent()).removeView(v);
        }
        panel = null;
    }

    void showBanner(String text, int color) {
        if (banner == null) return;
        banner.setText(text);
        banner.setBackgroundColor((color & 0x00FFFFFF) | 0xE6000000);
        banner.setVisibility(View.VISIBLE);
        flash.setBackgroundColor((color & 0x00FFFFFF) | 0x66000000);
        if (AlertStore.blink()) {
            startFlash();
        } else {
            if (anim != null) { anim.cancel(); anim = null; }
            flash.setAlpha(1f);
            flash.setVisibility(View.VISIBLE);
        }
    }

    void hideBanner() {
        if (banner != null) banner.setVisibility(View.GONE);
        stopFlash();
    }

    void popup(String text) {
        try {
            if (popup != null && popup.isShowing()) { popup.setMessage(text); return; }
            popup = new AlertDialog.Builder(a).setTitle("⚠ ALERTA").setMessage(text)
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int w) { eng.dismiss(); }
                    }).create();
            popup.show();
        } catch (Throwable ignored) {}
    }

    private void startFlash() {
        if (flash == null) return;
        flash.setVisibility(View.VISIBLE);
        if (anim != null) return;
        anim = ValueAnimator.ofFloat(0.1f, 1f);
        anim.setDuration(450);
        anim.setRepeatMode(ValueAnimator.REVERSE);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator va) {
                if (flash != null) flash.setAlpha((Float) va.getAnimatedValue());
            }
        });
        anim.start();
    }

    private void stopFlash() {
        if (anim != null) { anim.cancel(); anim = null; }
        if (flash != null) flash.setVisibility(View.GONE);
    }

    // ---------- painel (abas) ----------

    private void openPanel() {
        if (panel != null || root == null) return;
        panel = new FrameLayout(a);
        panel.setTag(TAG_PANEL);
        panel.setBackgroundColor(0xFF121212);
        panel.setClickable(true);

        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(10), statusBarHeight() + dp(8), dp(10), dp(8));

        LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label("🔔 Alertas", 20, Color.WHITE, true);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView close = label("✕ Fechar", 15, Color.WHITE, true);
        close.setPadding(dp(12), dp(8), dp(12), dp(8));
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { closePanel(); }
        });
        head.addView(close);
        col.addView(head);

        LinearLayout tabs = new LinearLayout(a);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.addView(tabButton("LEITURAS", 0), new LinearLayout.LayoutParams(0, -2, 1));
        tabs.addView(tabButton("AJUSTES", 1), new LinearLayout.LayoutParams(0, -2, 1));
        col.addView(tabs);

        ScrollView sv = new ScrollView(a);
        body = new LinearLayout(a);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, dp(8), 0, dp(40));
        sv.addView(body);
        col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        panel.addView(col, new FrameLayout.LayoutParams(-1, -1));
        root.addView(panel, new FrameLayout.LayoutParams(-1, -1));
        showTab();
        root.postDelayed(refresher, 500);
    }

    private void closePanel() {
        if (panel == null) return;
        if (root != null) {
            root.removeCallbacks(refresher);
            root.removeView(panel);
        }
        panel = null;
        live.clear();
    }

    private TextView tabButton(String text, final int idx) {
        TextView t = label(text, 14, Color.WHITE, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(12), 0, dp(12));
        t.setBackgroundColor(tab == idx ? 0xFF1976D2 : 0xFF2A2A2A);
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                tab = idx;
                // reconstrói o painel para atualizar a cor das abas
                closePanel();
                openPanel();
            }
        });
        return t;
    }

    private void showTab() {
        body.removeAllViews();
        live.clear();
        if (tab == 0) buildReadings(); else buildSettings();
    }

    // ----- aba LEITURAS -----

    private void buildReadings() {
        final EditText search = new EditText(a);
        search.setHint("Buscar parâmetro");
        search.setHintTextColor(0xFF888888);
        search.setTextColor(Color.WHITE);
        search.setSingleLine(true);
        search.setText(filter);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int af) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable e) { filter = e.toString(); fillList(); }
        });
        body.addView(search);

        TextView hint = label("Marque “Alarme” e defina os limites. O alarme dispara quando a leitura fica "
                + "abaixo do mínimo ou acima do máximo. Toque na cor para escolher a cor da tela no alerta.",
                12, 0xFFAAAAAA, false);
        hint.setPadding(0, dp(4), 0, dp(8));
        body.addView(hint);

        listBox = new LinearLayout(a);
        listBox.setOrientation(LinearLayout.VERTICAL);
        body.addView(listBox);
        fillList();
    }

    private void fillList() {
        if (listBox == null) return;
        listBox.removeAllViews();
        live.clear();
        List<Object[]> ch = eng.channels();
        if (ch.isEmpty()) {
            listBox.addView(label("Conecte ao carro para listar os parâmetros.", 15, Color.WHITE, false));
            return;
        }
        String q = filter.trim().toLowerCase();
        for (int pass = 0; pass < 2; pass++) {          // regras ativas primeiro
            for (Object[] c : ch) {
                int id = (Integer) c[0];
                if ((pass == 0) != AlertStore.ruleOn(id)) continue;
                if (!q.isEmpty() && !String.valueOf(c[1]).toLowerCase().contains(q)) continue;
                listBox.addView(row(id, String.valueOf(c[1]), String.valueOf(c[2])));
            }
        }
    }

    private View row(final int id, String name, String unit) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(0xFF1E1E1E);
        box.setPadding(dp(10), dp(8), dp(10), dp(8));
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, -2);
        bl.bottomMargin = dp(8);
        box.setLayoutParams(bl);

        LinearLayout top = new LinearLayout(a);
        top.setOrientation(LinearLayout.HORIZONTAL);
        String u = unit.equals("null") || unit.isEmpty() ? "" : " [" + unit + "]";
        top.addView(label(name + u, 15, Color.WHITE, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView val = label("—", 16, 0xFF4DD0E1, true);
        live.put(id, val);
        top.addView(val);
        box.addView(top);

        final CheckBox on = new CheckBox(a);
        on.setText("Alarme");
        on.setTextColor(Color.WHITE);
        on.setChecked(AlertStore.ruleOn(id));
        box.addView(on);

        LinearLayout lim = new LinearLayout(a);
        lim.setOrientation(LinearLayout.HORIZONTAL);
        lim.setGravity(Gravity.CENTER_VERTICAL);
        final EditText min = numField("menor que", AlertStore.fmt(AlertStore.ruleMin(id)));
        final EditText max = numField("maior que", AlertStore.fmt(AlertStore.ruleMax(id)));
        lim.addView(label("Menor que ", 12, 0xFFAAAAAA, false));
        lim.addView(min, new LinearLayout.LayoutParams(0, -2, 1));
        lim.addView(label("  Maior que ", 12, 0xFFAAAAAA, false));
        lim.addView(max, new LinearLayout.LayoutParams(0, -2, 1));

        final TextView sw = new TextView(a);
        sw.setText("  cor  ");
        sw.setTextColor(Color.WHITE);
        sw.setGravity(Gravity.CENTER);
        sw.setPadding(dp(8), dp(8), dp(8), dp(8));
        sw.setBackgroundColor(AlertStore.color(id));
        sw.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                new AlertDialog.Builder(a).setTitle("Cor do alerta")
                        .setItems(COLOR_NAMES, new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int which) {
                                AlertStore.color(id, COLORS[which]);
                                sw.setBackgroundColor(COLORS[which]);
                            }
                        }).show();
            }
        });
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-2, -2);
        sp.leftMargin = dp(8);
        lim.addView(sw, sp);
        box.addView(lim);

        final Runnable save = new Runnable() {
            @Override public void run() {
                AlertStore.saveRule(id, on.isChecked(), min.getText().toString(), max.getText().toString());
            }
        };
        TextWatcher tw = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int af) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable e) { save.run(); }
        };
        min.addTextChangedListener(tw);
        max.addTextChangedListener(tw);
        on.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(android.widget.CompoundButton b, boolean c) { save.run(); }
        });
        return box;
    }

    // ----- aba AJUSTES -----

    private void buildSettings() {
        body.addView(btn("Alertas: " + (AlertStore.all() ? "LIGADOS" : "DESLIGADOS"), new Runnable() {
            @Override public void run() { AlertStore.all(!AlertStore.all()); showTab(); } }));
        body.addView(btn("Som do alerta…", new Runnable() {
            @Override public void run() { soundMenu(); } }));
        body.addView(btn("Aviso e cor na tela: " + (AlertStore.visual() ? "ligado" : "desligado"), new Runnable() {
            @Override public void run() { AlertStore.visual(!AlertStore.visual()); showTab(); } }));
        body.addView(btn("Tela piscando: " + (AlertStore.blink() ? "sim" : "não (cor fixa)"), new Runnable() {
            @Override public void run() { AlertStore.blink(!AlertStore.blink()); showTab(); } }));
        body.addView(btn("Popup de aviso (OK para fechar): " + (AlertStore.popup() ? "ligado" : "desligado"), new Runnable() {
            @Override public void run() { AlertStore.popup(!AlertStore.popup()); showTab(); } }));
        body.addView(btn("Repetições do som: " + AlertStore.repeats(), new Runnable() {
            @Override public void run() { askNumber("Repetições (1-20)", AlertStore.repeats(), new NumCb() {
                @Override public void ok(int v) { AlertStore.repeats(v); } }); } }));
        body.addView(btn("Intervalo entre alertas: " + AlertStore.cooldownSec() + " s", new Runnable() {
            @Override public void run() { askNumber("Intervalo em segundos (1-120)", AlertStore.cooldownSec(), new NumCb() {
                @Override public void ok(int v) { AlertStore.cooldownSec(v); } }); } }));
        body.addView(btn("Confirmar após: " + AlertStore.confirmSec() + " s fora do limite", new Runnable() {
            @Override public void run() { askNumber("Segundos fora do limite antes de alertar (0-30)", AlertStore.confirmSec(), new NumCb() {
                @Override public void ok(int v) { AlertStore.confirmSec(v); } }); } }));
        body.addView(btn("Volume: " + AlertStore.volume() + "%", new Runnable() {
            @Override public void run() { askNumber("Volume (0-100)", AlertStore.volume(), new NumCb() {
                @Override public void ok(int v) { AlertStore.volume(v); } }); } }));
        body.addView(btn("▶ Testar alerta", new Runnable() {
            @Override public void run() {
                closePanel();
                player.play();
                showBanner("⚠ Teste de alerta", 0xFFD32F2F);
                root.postDelayed(new Runnable() { @Override public void run() { hideBanner(); } }, 2500);
            } }));
    }

    private void soundMenu() {
        String[] items = {"Bipes do sistema (escolher tom)", "Sons do aparelho (alarme, notificação, toque)"};
        new AlertDialog.Builder(a).setTitle("Som do alerta").setItems(items, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                if (which == 0) tonePicker(); else ringPicker();
            }
        }).setNegativeButton("Voltar", null).show();
    }

    private void tonePicker() {
        int cur = AlertStore.mode() == 0 ? AlertStore.tone() : -1;
        new AlertDialog.Builder(a).setTitle("Bipe").setSingleChoiceItems(AlertPlayer.TONE_NAMES, cur,
                new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        AlertStore.tone(which);
                        AlertStore.mode(0);
                        player.play(1);
                    }
                }).setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int w) { player.stop(); }
        }).show();
    }

    private void ringPicker() {
        final List<String> titles = new ArrayList<>();
        final List<String> uris = new ArrayList<>();
        try {
            RingtoneManager rm = new RingtoneManager(a);
            rm.setType(RingtoneManager.TYPE_ALARM | RingtoneManager.TYPE_NOTIFICATION | RingtoneManager.TYPE_RINGTONE);
            Cursor c = rm.getCursor();
            while (c.moveToNext()) {
                titles.add(c.getString(RingtoneManager.TITLE_COLUMN_INDEX));
                uris.add(rm.getRingtoneUri(c.getPosition()).toString());
            }
        } catch (Throwable t) {
            Toast.makeText(a, "Não foi possível listar os sons", Toast.LENGTH_LONG).show();
            return;
        }
        int cur = AlertStore.mode() == 1 ? uris.indexOf(AlertStore.uri()) : -1;
        new AlertDialog.Builder(a).setTitle("Som do aparelho")
                .setSingleChoiceItems(titles.toArray(new String[0]), cur, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        AlertStore.uri(uris.get(which));
                        AlertStore.mode(1);
                        player.play(1);
                    }
                }).setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int w) { player.stop(); }
        }).show();
    }

    // ---------- helpers ----------

    private interface NumCb { void ok(int v); }

    private void askNumber(String title, int cur, final NumCb cb) {
        final EditText et = new EditText(a);
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setText(String.valueOf(cur));
        et.setSelectAllOnFocus(true);
        FrameLayout box = new FrameLayout(a);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(et);
        new AlertDialog.Builder(a).setTitle(title).setView(box)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        try { cb.ok(Integer.parseInt(et.getText().toString().trim())); } catch (NumberFormatException ignored) {}
                        if (panel != null) showTab();
                    }
                })
                .setNegativeButton("Cancelar", null).show();
    }

    private TextView btn(String text, final Runnable r) {
        TextView t = label(text, 15, Color.WHITE, false);
        t.setBackgroundColor(0xFF1E1E1E);
        t.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        t.setLayoutParams(lp);
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { r.run(); }
        });
        return t;
    }

    private TextView label(String text, int sp, int color, boolean bold) {
        TextView t = new TextView(a);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private EditText numField(String hint, String value) {
        EditText et = new EditText(a);
        et.setHint(hint);
        et.setHintTextColor(0xFF666666);
        et.setTextColor(Color.WHITE);
        et.setSingleLine(true);
        et.setText(value);
        et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        return et;
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, a.getResources().getDisplayMetrics()));
    }

    private int statusBarHeight() {
        int id = a.getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? a.getResources().getDimensionPixelSize(id) : 0;
    }
}
