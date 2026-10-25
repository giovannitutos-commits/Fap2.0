package br.com.folha.fap.alerts;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.RingtoneManager;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Banner, borda piscante, botão de sino e telas de configuração (tudo em Views simples). */
final class AlertUi {
    private static final String TAG_BANNER = "fapAlertBanner";
    private static final String TAG_FLASH = "fapAlertFlash";
    private static final String TAG_BELL = "fapAlertBell";

    private final Activity a;
    private final AlertEngine eng;
    private final AlertPlayer player;
    private FrameLayout root;
    private TextView banner;
    private View flash;
    private ValueAnimator anim;

    AlertUi(Activity a, AlertEngine eng, AlertPlayer player) {
        this.a = a;
        this.eng = eng;
        this.player = player;
    }

    // ---------- overlay ----------

    void attach() {
        root = (FrameLayout) a.findViewById(android.R.id.content);
        if (root == null) return;
        removeTagged(TAG_BANNER);
        removeTagged(TAG_FLASH);
        removeTagged(TAG_BELL);

        flash = new View(a);
        flash.setTag(TAG_FLASH);
        flash.setBackgroundColor(0x55FF0000);
        flash.setClickable(false);
        flash.setVisibility(View.GONE);
        root.addView(flash, new FrameLayout.LayoutParams(-1, -1));

        banner = new TextView(a);
        banner.setTag(TAG_BANNER);
        banner.setTextColor(Color.WHITE);
        banner.setTypeface(Typeface.DEFAULT_BOLD);
        banner.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        banner.setGravity(Gravity.CENTER);
        banner.setBackgroundColor(0xE6B71C1C);
        banner.setPadding(dp(12), dp(10), dp(12), dp(10));
        banner.setVisibility(View.GONE);
        banner.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { eng.dismiss(); }
        });
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        bp.topMargin = statusBarHeight();
        root.addView(banner, bp);

        TextView bell = new TextView(a);
        bell.setTag(TAG_BELL);
        bell.setText("🔔");
        bell.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        bell.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0x99000000);
        bell.setBackground(bg);
        bell.setAlpha(0.65f);
        bell.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { mainMenu(); }
        });
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(42), dp(42), Gravity.BOTTOM | Gravity.START);
        lp.leftMargin = dp(8);
        lp.bottomMargin = dp(96);
        root.addView(bell, lp);
    }

    void detach() {
        stopFlash();
        if (root != null) {
            removeTagged(TAG_BANNER);
            removeTagged(TAG_FLASH);
            removeTagged(TAG_BELL);
        }
    }

    void showBanner(String text) {
        if (banner == null) return;
        banner.setText(text);
        banner.setVisibility(View.VISIBLE);
        if (AlertStore.blink()) startFlash(); else stopFlash();
    }

    void hideBanner() {
        if (banner != null) banner.setVisibility(View.GONE);
        stopFlash();
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

    private void removeTagged(String tag) {
        View v;
        while ((v = root.findViewWithTag(tag)) != null) ((ViewGroup) v.getParent()).removeView(v);
    }

    // ---------- menus ----------

    private void mainMenu() {
        final String[] items = {
                "Alertas: " + (AlertStore.all() ? "LIGADOS" : "DESLIGADOS"),
                "Regras por parâmetro…",
                "Som do alerta…",
                "Alerta na tela: " + (AlertStore.visual() ? "ligado" : "desligado"),
                "Borda piscando: " + (AlertStore.blink() ? "ligada" : "desligada"),
                "Repetições do som: " + AlertStore.repeats(),
                "Intervalo entre alertas: " + AlertStore.cooldownSec() + " s",
                "Confirmar após: " + AlertStore.confirmSec() + " s fora do limite",
                "Volume: " + AlertStore.volume() + "%",
                "Testar alerta"
        };
        new AlertDialog.Builder(a).setTitle("Alertas").setItems(items, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                switch (which) {
                    case 0: AlertStore.all(!AlertStore.all()); mainMenu(); break;
                    case 1: rulesList(); break;
                    case 2: soundMenu(); break;
                    case 3: AlertStore.visual(!AlertStore.visual()); mainMenu(); break;
                    case 4: AlertStore.blink(!AlertStore.blink()); mainMenu(); break;
                    case 5: askNumber("Repetições (1-20)", AlertStore.repeats(), new NumCb() {
                        @Override public void ok(int v) { AlertStore.repeats(v); } }); break;
                    case 6: askNumber("Intervalo em segundos (1-120)", AlertStore.cooldownSec(), new NumCb() {
                        @Override public void ok(int v) { AlertStore.cooldownSec(v); } }); break;
                    case 7: askNumber("Segundos fora do limite antes de alertar (0-30)", AlertStore.confirmSec(), new NumCb() {
                        @Override public void ok(int v) { AlertStore.confirmSec(v); } }); break;
                    case 8: askNumber("Volume (0-100)", AlertStore.volume(), new NumCb() {
                        @Override public void ok(int v) { AlertStore.volume(v); } }); break;
                    default:
                        player.play();
                        showBanner("⚠ Teste de alerta");
                        banner.postDelayed(new Runnable() {
                            @Override public void run() { hideBanner(); }
                        }, 2500);
                }
            }
        }).setNegativeButton("Fechar", null).show();
    }

    private void rulesList() {
        final List<Object[]> ch = new ArrayList<>(eng.channels());
        if (ch.isEmpty()) {
            Toast.makeText(a, "Conecte ao carro primeiro para listar os parâmetros", Toast.LENGTH_LONG).show();
            return;
        }
        String[] names = new String[ch.size()];
        for (int i = 0; i < names.length; i++) {
            Object[] c = ch.get(i);
            int id = (Integer) c[0];
            String rule = AlertStore.ruleOn(id)
                    ? "  ✔ " + nz(AlertStore.fmt(AlertStore.ruleMin(id)), "−∞") + " … " + nz(AlertStore.fmt(AlertStore.ruleMax(id)), "+∞")
                    : "";
            names[i] = c[1] + (c[2].equals("null") || c[2].equals("") ? "" : " [" + c[2] + "]") + rule;
        }
        new AlertDialog.Builder(a).setTitle("Parâmetros").setItems(names, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                Object[] c = ch.get(which);
                ruleEditor((Integer) c[0], String.valueOf(c[1]));
            }
        }).setNegativeButton("Voltar", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int w) { mainMenu(); }
        }).show();
    }

    private void ruleEditor(final int id, String name) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        final CheckBox on = new CheckBox(a);
        on.setText("Alertar neste parâmetro");
        on.setChecked(AlertStore.ruleOn(id));
        final EditText min = numField("Mínimo (vazio = sem limite)", AlertStore.fmt(AlertStore.ruleMin(id)));
        final EditText max = numField("Máximo (vazio = sem limite)", AlertStore.fmt(AlertStore.ruleMax(id)));
        box.addView(on);
        box.addView(min);
        box.addView(max);
        new AlertDialog.Builder(a).setTitle(name).setView(box)
                .setPositiveButton("Salvar", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        AlertStore.saveRule(id, on.isChecked(), min.getText().toString(), max.getText().toString());
                        rulesList();
                    }
                })
                .setNegativeButton("Cancelar", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { rulesList(); }
                }).show();
    }

    private void soundMenu() {
        String[] items = {"Bipes do sistema (escolher tom)", "Sons do aparelho (alarme, notificação, toque)"};
        new AlertDialog.Builder(a).setTitle("Som do alerta").setItems(items, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                if (which == 0) tonePicker(); else ringPicker();
            }
        }).setNegativeButton("Voltar", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int w) { mainMenu(); }
        }).show();
    }

    private void tonePicker() {
        final int[] sel = {AlertStore.mode() == 0 ? AlertStore.tone() : -1};
        new AlertDialog.Builder(a).setTitle("Bipe").setSingleChoiceItems(AlertPlayer.TONE_NAMES, sel[0],
                new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        sel[0] = which;
                        AlertStore.tone(which);
                        AlertStore.mode(0);
                        player.play(1);
                    }
                }).setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int w) { player.stop(); mainMenu(); }
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
            @Override public void onClick(DialogInterface d, int w) { player.stop(); mainMenu(); }
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
                        mainMenu();
                    }
                })
                .setNegativeButton("Cancelar", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { mainMenu(); }
                }).show();
    }

    private EditText numField(String hint, String value) {
        EditText et = new EditText(a);
        et.setHint(hint);
        et.setText(value);
        et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        return et;
    }

    private static String nz(String s, String def) { return s == null || s.isEmpty() ? def : s; }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, a.getResources().getDisplayMetrics()));
    }

    private int statusBarHeight() {
        int id = a.getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? a.getResources().getDimensionPixelSize(id) : 0;
    }
}
