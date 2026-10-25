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
