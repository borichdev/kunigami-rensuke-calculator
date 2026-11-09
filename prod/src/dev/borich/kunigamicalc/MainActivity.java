package dev.borich.kunigamicalc;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private GLSurfaceView glView;
    private KunigamiRenderer renderer;
    private final CalculatorEngine calcEngine = new CalculatorEngine();
    private SoundEngine soundEngine;

    private TextView exprTextView;
    private TextView mainNumberView;
    private TextView voiceBadgeView;
    private LinearLayout bottomCalcContainer;
    private GridLayout keypadGrid;

    private TextView btnSound;
    private TextView btnEye;
    private TextView btnEgo;
    private TextView btnCompact;

    private boolean uiHidden = false;
    private boolean egoLocked = false;
    private boolean compactMode = false;

    private float lastTouchX, lastTouchY;
    private float downTouchX, downTouchY;
    private int tapPoseCycle = 0;

    private static final String[] SHOWCASE_POSES = {
            "DIGIT_1", "DIGIT_2", "DIGIT_5", "EASTER_67_A", "EASTER_50", "OP_PLUS", "EQUALS_EGO", "BACKSPACE", "IDLE"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        );

        soundEngine = new SoundEngine(this);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#140906"));

        // 1. 3D OpenGL ES 3.0 Kunigami View
        glView = new GLSurfaceView(this);
        glView.setEGLContextClientVersion(3);
        glView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        renderer = new KunigamiRenderer(this);
        glView.setRenderer(renderer);
        glView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        setupTouchInteraction(glView);
        root.addView(glView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // 2. Decorative Top & Bottom Tactical Frame Overlay
        FrameTrimView trimView = new FrameTrimView(this);
        root.addView(trimView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // 3. Top-Left Header: Fiery Orange "KC" Logo Emblem + Striker Tag + Voice Bubble
        LinearLayout topLeftBox = new LinearLayout(this);
        topLeftBox.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams tlLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START
        );
        tlLp.setMargins(dp(14), dp(40), dp(68), 0);

        LinearLayout headerPill = new LinearLayout(this);
        headerPill.setOrientation(LinearLayout.HORIZONTAL);
        headerPill.setGravity(Gravity.CENTER_VERTICAL);
        headerPill.setPadding(dp(5), dp(4), dp(12), dp(4));
        GradientDrawable pillBg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.parseColor("#CC240F08"), Color.parseColor("#99180A05")}
        );
        pillBg.setCornerRadius(dp(16));
        pillBg.setStroke(dp(1.2f), Color.parseColor("#99FF6B1A"));
        headerPill.setBackground(pillBg);

        // Fiery orange "KC" mini logo badge
        TextView kcLogo = new TextView(this);
        kcLogo.setText("KC");
        kcLogo.setTextColor(Color.parseColor("#FF731D"));
        kcLogo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        kcLogo.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        kcLogo.setGravity(Gravity.CENTER);
        kcLogo.setPadding(dp(7), dp(2), dp(7), dp(2));
        GradientDrawable kcBg = new GradientDrawable();
        kcBg.setColor(Color.parseColor("#FF160804"));
        kcBg.setCornerRadius(dp(11));
        kcBg.setStroke(dp(1.2f), Color.parseColor("#FFFF7722"));
        kcLogo.setBackground(kcBg);
        headerPill.addView(kcLogo);

        TextView strikerBadge = new TextView(this);
        strikerBadge.setText("BLUELOCK #50  •  R. KUNIGAMI");
        strikerBadge.setTextColor(Color.parseColor("#FFE0C8"));
        strikerBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        strikerBadge.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        strikerBadge.setLetterSpacing(0.08f);
        strikerBadge.setPadding(dp(8), 0, 0, 0);
        headerPill.addView(strikerBadge);

        headerPill.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            renderer.resetCamera();
            renderer.model.triggerPose("IDLE", 0.35f, 0.0f, 0f, 0f);
            showVoiceBubble("⚽ CAMERA RESET • #50 HERO");
        });
        topLeftBox.addView(headerPill);

        voiceBadgeView = new TextView(this);
        voiceBadgeView.setText("「 いち！ [1] 」");
        voiceBadgeView.setTextColor(Color.parseColor("#FFF8F2"));
        voiceBadgeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        voiceBadgeView.setTypeface(Typeface.create("sans-serif-black", Typeface.ITALIC));
        voiceBadgeView.setPadding(dp(13), dp(5), dp(13), dp(5));
        GradientDrawable voiceBg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.parseColor("#EEFF5500"), Color.parseColor("#D8FF801A")}
        );
        voiceBg.setCornerRadius(dp(14));
        voiceBg.setStroke(dp(1.2f), Color.parseColor("#FFFFDEC2"));
        voiceBadgeView.setBackground(voiceBg);
        LinearLayout.LayoutParams vbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        vbLp.topMargin = dp(7);
        voiceBadgeView.setLayoutParams(vbLp);
        voiceBadgeView.setAlpha(0f);
        topLeftBox.addView(voiceBadgeView);

        root.addView(topLeftBox, tlLp);

        // 4. Top-Right Floating Glass Pill Controls Column
        LinearLayout rightControls = new LinearLayout(this);
        rightControls.setOrientation(LinearLayout.VERTICAL);
        rightControls.setGravity(Gravity.CENTER_HORIZONTAL);
        rightControls.setPadding(dp(4), dp(6), dp(4), dp(6));
        GradientDrawable dockBg = new GradientDrawable();
        dockBg.setColor(Color.parseColor("#661A0B06"));
        dockBg.setCornerRadius(dp(24));
        dockBg.setStroke(dp(1), Color.parseColor("#44FF7324"));
        rightControls.setBackground(dockBg);

        FrameLayout.LayoutParams rcLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END
        );
        rcLp.setMargins(0, dp(38), dp(12), 0);

        btnSound = createSideIconButton("♪", true, v -> {
            boolean muted = !soundEngine.isMuted();
            soundEngine.setMuted(muted);
            styleSideButton(btnSound, !muted);
            btnSound.setText(muted ? "✕" : "♪");
            if (!muted) soundEngine.playKeySound("1");
            showVoiceBubble(muted ? "🔇 VOICE MUTE" : "🔊 JA VOICE ON");
        });
        rightControls.addView(btnSound);

        btnEye = createSideIconButton("👁", false, v -> {
            uiHidden = !uiHidden;
            styleSideButton(btnEye, uiHidden);
            renderer.setShowcaseMode(uiHidden);
            bottomCalcContainer.animate()
                    .alpha(uiHidden ? 0f : 1f)
                    .translationY(uiHidden ? dp(60) : 0f)
                    .setDuration(220)
                    .withEndAction(() -> bottomCalcContainer.setVisibility(uiHidden ? View.GONE : View.VISIBLE))
                    .start();
            if (!uiHidden) {
                bottomCalcContainer.setVisibility(View.VISIBLE);
            } else {
                showVoiceBubble("👁 FREE VIEW • DRAG OR TAP KUNIGAMI");
            }
        });
        rightControls.addView(btnEye);

        btnEgo = createSideIconButton("🔥", false, v -> {
            egoLocked = !egoLocked;
            styleSideButton(btnEgo, egoLocked);
            renderer.model.setEgoMode(egoLocked);
            if (egoLocked) {
                soundEngine.playEasterEggVoice("ee_50");
                renderer.model.triggerPose("EQUALS_EGO", 0.30f, 1.4f, 0f, -3f);
                showVoiceBubble("「 俺はスーパーヒーローになる！ 」");
            } else {
                renderer.model.triggerPose("IDLE", 0.35f, 0.0f, 0f, 0f);
            }
        });
        rightControls.addView(btnEgo);

        btnCompact = createSideIconButton("⊞", false, v -> {
            compactMode = !compactMode;
            styleSideButton(btnCompact, compactMode);
            rebuildKeypadGrid();
        });
        LinearLayout.LayoutParams lastBtnLp = (LinearLayout.LayoutParams) btnCompact.getLayoutParams();
        lastBtnLp.bottomMargin = 0;
        rightControls.addView(btnCompact);

        root.addView(rightControls, rcLp);

        // 5. Bottom Calculator Container (compact height so Kunigami's full upper/mid body & hand gestures are always visible)
        bottomCalcContainer = new LinearLayout(this);
        bottomCalcContainer.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams bcLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
        );
        bcLp.setMargins(0, 0, 0, dp(16));

        RuledDisplayLayout displayPanel = new RuledDisplayLayout(this);
        displayPanel.setOrientation(LinearLayout.VERTICAL);
        displayPanel.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        displayPanel.setPadding(dp(18), dp(6), dp(18), dp(6));
        LinearLayout.LayoutParams dpLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(82)
        );
        dpLp.setMargins(dp(10), 0, dp(10), dp(6));

        exprTextView = new TextView(this);
        exprTextView.setText("");
        exprTextView.setTextColor(Color.parseColor("#FFAE78"));
        exprTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
        exprTextView.setGravity(Gravity.END);
        exprTextView.setSingleLine(true);
        exprTextView.setEllipsize(TextUtils.TruncateAt.START);
        exprTextView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        displayPanel.addView(exprTextView);

        mainNumberView = new TextView(this);
        mainNumberView.setText("0");
        mainNumberView.setTextColor(Color.parseColor("#FFF9F5"));
        mainNumberView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 36f);
        mainNumberView.setGravity(Gravity.END);
        mainNumberView.setSingleLine(true);
        mainNumberView.setEllipsize(TextUtils.TruncateAt.START);
        mainNumberView.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        mainNumberView.setShadowLayer(dp(6), 0, dp(2), Color.parseColor("#AA000000"));
        displayPanel.addView(mainNumberView);

        // Tap display to copy active number/result to clipboard
        displayPanel.setOnClickListener(v -> {
            String textToCopy = calcEngine.getMainLine();
            if (textToCopy != null && !textToCopy.isEmpty() && !"ERROR".equals(textToCopy)) {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("Kunigami Calc", textToCopy));
                    v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    showVoiceBubble("📋 COPIED: " + textToCopy);
                }
            }
        });

        bottomCalcContainer.addView(displayPanel, dpLp);

        keypadGrid = new GridLayout(this);
        keypadGrid.setColumnCount(4);
        keypadGrid.setPadding(dp(8), dp(2), dp(8), dp(2));
        rebuildKeypadGrid();

        bottomCalcContainer.addView(keypadGrid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        root.addView(bottomCalcContainer, bcLp);
        setContentView(root);
    }

    private void rebuildKeypadGrid() {
        keypadGrid.removeAllViews();
        String[][] rows;
        if (compactMode) {
            rows = new String[][]{
                    {"⌫", "7", "8", "9", "÷"},
                    {"C", "4", "5", "6", "×"},
                    {"0", "1", "2", "3", "+", "="}
            };
            keypadGrid.setColumnCount(6);
            int btnHeight = dp(41);
            for (String[] row : rows) {
                for (String key : row) {
                    View btn = createCalcButton(key);
                    GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
                    glp.width = 0;
                    glp.height = btnHeight;
                    if (row.length == 5 && ("⌫".equals(key) || "C".equals(key))) {
                        glp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 2, 2f);
                    } else {
                        glp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f);
                    }
                    glp.setMargins(dp(3), dp(2.5f), dp(3), dp(2.5f));
                    keypadGrid.addView(btn, glp);
                }
            }
        } else {
            keypadGrid.setColumnCount(4);
            rows = new String[][]{
                    {"C", "⌫", "%", "÷"},
                    {"7", "8", "9", "×"},
                    {"4", "5", "6", "-"},
                    {"1", "2", "3", "+"},
                    {"±", "0", ".", "="}
            };
            int btnHeight = dp(43);
            for (String[] row : rows) {
                for (String key : row) {
                    View btn = createCalcButton(key);
                    GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
                    glp.width = 0;
                    glp.height = btnHeight;
                    glp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f);
                    glp.setMargins(dp(4), dp(2.5f), dp(4), dp(2.5f));
                    keypadGrid.addView(btn, glp);
                }
            }
        }
    }

    private View createCalcButton(final String key) {
        final TextView tv = new TextView(this);
        tv.setText(key);
        tv.setGravity(Gravity.CENTER);

        boolean isEquals = "=".equals(key);
        boolean isOperator = "+".equals(key) || "-".equals(key) || "×".equals(key) || "÷".equals(key);
        boolean isAction = "C".equals(key) || "⌫".equals(key) || "%".equals(key);

        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, (isOperator || isEquals) ? 21.5f : 19.5f);
        tv.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));

        int bgStart, bgEnd, strokeCol, textCol;
        if (isEquals) {
            bgStart   = Color.parseColor("#F2FF5E0E");
            bgEnd     = Color.parseColor("#F2E63E00");
            strokeCol = Color.parseColor("#FFFFCEA6");
            textCol   = Color.parseColor("#FFFFFF");
        } else if (isOperator) {
            bgStart   = Color.parseColor("#C84E210C");
            bgEnd     = Color.parseColor("#C8331306");
            strokeCol = Color.parseColor("#DDFF7724");
            textCol   = Color.parseColor("#FFAA6B");
        } else if (isAction) {
            bgStart   = Color.parseColor("#C43B1710");
            bgEnd     = Color.parseColor("#C4240C08");
            strokeCol = Color.parseColor("#AAFF5C33");
            textCol   = Color.parseColor("#FF9275");
        } else {
            bgStart   = Color.parseColor("#B524140F");
            bgEnd     = Color.parseColor("#C2140B08");
            strokeCol = Color.parseColor("#6BFF8547");
            textCol   = Color.parseColor("#FFF7F0");
        }

        final GradientDrawable normalBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{bgStart, bgEnd}
        );
        normalBg.setCornerRadius(dp(14));
        normalBg.setStroke(dp(1.2f), strokeCol);

        final GradientDrawable pressedBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.parseColor("#F0FF7322"), Color.parseColor("#F0CC3D00")}
        );
        pressedBg.setCornerRadius(dp(14));
        pressedBg.setStroke(dp(2f), Color.parseColor("#FFFFE4CC"));

        tv.setTextColor(textCol);
        tv.setBackground(normalBg);

        tv.setOnTouchListener((v, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                v.setBackground(pressedBg);
                v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(55).start();
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                onCalculatorKeyPressed(key);
                return true;
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                v.setBackground(normalBg);
                v.animate().scaleX(1.0f).scaleY(1.0f)
                        .setInterpolator(new OvershootInterpolator(2.4f))
                        .setDuration(140)
                        .start();
                return true;
            }
            return false;
        });

        return tv;
    }

    private void onCalculatorKeyPressed(String key) {
        calcEngine.onButtonPress(key);
        exprTextView.setText(calcEngine.getTopLine());
        String mainText = calcEngine.getMainLine();
        mainNumberView.setText(mainText);

        if (mainText.length() > 14) {
            mainNumberView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 23f);
        } else if (mainText.length() > 10) {
            mainNumberView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f);
        } else if (mainText.length() > 7) {
            mainNumberView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32f);
        } else {
            mainNumberView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 36f);
        }

        mainNumberView.setScaleX(1.06f);
        mainNumberView.setScaleY(1.06f);
        mainNumberView.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(140)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        String activeNum = calcEngine.getActiveNumberPlain();
        if (checkAndTriggerEasterEgg(activeNum)) {
            return;
        }

        soundEngine.playKeySound(key);
        triggerKunigamiReactionForKey(key);
    }

    /**
     * Checks for iconic meme / anime / Blue Lock numbers (67, 87, 50, 993, 777, 69, 420, 666, 1337, 42, 228, 1488)
     * and triggers dedicated Easter Egg animation + Japanese voice line.
     */
    private boolean checkAndTriggerEasterEgg(String numStr) {
        if (numStr == null || numStr.isEmpty()) return false;
        switch (numStr) {
            case "67":
                soundEngine.playEasterEggVoice("ee_67");
                renderer.model.triggerSixSevenDance();
                showVoiceBubble("🔥 67?! ろくじゅうなな！ SIX-SEVEN!! 🔥");
                return true;
            case "87":
                soundEngine.playEasterEggVoice("ee_87");
                renderer.model.triggerEasterEggPose("EASTER_87", 3.5f, 2.2f);
                showVoiceBubble("😱 87?! WAS THAT THE BITE OF '87?! 😱");
                return true;
            case "50":
                soundEngine.playEasterEggVoice("ee_50");
                renderer.model.triggerEasterEggPose("EASTER_50", 3.5f, 2.0f);
                showVoiceBubble("⚽ #50! 俺はスーパーヒーローになる！ ⚽");
                return true;
            case "52":
                soundEngine.playEasterEggVoice("ee_52");
                renderer.model.triggerEasterEggPose("EASTER_52", 3.5f, 2.0f);
                showVoiceBubble("🔥 52! ごじゅうに！ YEAH FIFTY-TWO!! 🖐️✌️");
                return true;
            case "993":
                soundEngine.playEasterEggVoice("ee_993");
                renderer.model.triggerEasterEggPose("EASTER_993", 4.0f, 2.4f);
                showVoiceBubble("🩸 1000 - 7 = 993... せんひくなな！ 🩸");
                return true;
            case "777":
                soundEngine.playEasterEggVoice("ee_777");
                renderer.model.triggerEasterEggPose("EQUALS_EGO", 3.5f, 2.0f);
                showVoiceBubble("🎰 777! なな・なな・なな！ JACKPOT!! 🎰");
                return true;
            case "69":
                soundEngine.playEasterEggVoice("ee_69");
                renderer.model.triggerEasterEggPose("DIGIT_2", 2.0f, 1.5f);
                showVoiceBubble("😏 69... ろくじゅうきゅう！ NICE! 😏");
                return true;
            case "420":
                soundEngine.playEasterEggVoice("ee_420");
                renderer.model.triggerEasterEggPose("BACKSPACE", 2.5f, 1.6f);
                showVoiceBubble("🔥 420! 燃えてきたぜ！ 🔥");
                return true;
            case "666":
                soundEngine.playEasterEggVoice("ee_666");
                renderer.model.triggerEasterEggPose("EASTER_993", 4.0f, 2.0f);
                showVoiceBubble("👁 666! WILD CARD EGOIST AWAKENED! 👁");
                return true;
            case "1337":
                soundEngine.playEasterEggVoice("ee_1337");
                renderer.model.triggerEasterEggPose("EASTER_50", 3.0f, 1.5f);
                showVoiceBubble("⚡ 1337! エリート・ストライカーだ！ ⚡");
                return true;
            case "42":
                soundEngine.playEasterEggVoice("ee_42");
                renderer.model.triggerEasterEggPose("DIGIT_4", 2.0f, 1.6f);
                showVoiceBubble("🌌 42! 宇宙の全ての答えだな！ 🌌");
                return true;
            case "228":
                soundEngine.playEasterEggVoice("ee_228");
                renderer.model.triggerEasterEggPose("EASTER_87", 2.5f, 1.8f);
                showVoiceBubble("🚨 228?! おいおい、やばい数字だな！ 🚨");
                return true;
            case "1488":
                soundEngine.playEasterEggVoice("ee_1488");
                renderer.model.triggerEasterEggPose("OP_MUL", 2.0f, 1.5f);
                showVoiceBubble("✖ おい！計算に集中しろ！ ✖");
                return true;
            default:
                return false;
        }
    }

    private void triggerKunigamiReactionForKey(String key) {
        String pose = "IDLE";
        String voiceLine = "";
        float animDur = 0.24f;
        float speechDur = 0.35f;
        float yaw = 0.0f;
        float pitch = 2.0f;

        switch (key) {
            case "0":
                pose = "DIGIT_0";
                voiceLine = "「 ゼロ [0] 」";
                yaw = -2.5f;
                break;
            case "1":
                pose = "DIGIT_1";
                voiceLine = "「 いち [1] 」";
                yaw = -3.0f;
                break;
            case "2":
                pose = "DIGIT_2";
                voiceLine = "「 にっ [2] 」";
                yaw = 2.5f;
                break;
            case "3":
                pose = "DIGIT_3";
                voiceLine = "「 さん [3] 」";
                yaw = -2.0f;
                break;
            case "4":
                pose = "DIGIT_4";
                voiceLine = "「 よん [4] 」";
                yaw = 3.0f;
                break;
            case "5":
                pose = "DIGIT_5";
                voiceLine = "「 ごっ [5] 」";
                yaw = -3.5f;
                break;
            case "6":
                pose = "DIGIT_6";
                voiceLine = "「 ろく [6] 」";
                yaw = 0.0f;
                break;
            case "7":
                pose = "DIGIT_7";
                voiceLine = "「 なな [7] 」";
                yaw = -2.5f;
                break;
            case "8":
                pose = "DIGIT_8";
                voiceLine = "「 はち [8] 」";
                yaw = 2.5f;
                break;
            case "9":
                pose = "DIGIT_9";
                voiceLine = "「 きゅう [9] 」";
                yaw = 0.0f;
                break;
            case "+":
                pose = "OP_PLUS";
                voiceLine = "「 たす [+] 」";
                animDur = 0.26f;
                break;
            case "-":
                pose = "OP_MINUS";
                voiceLine = "「 ひく [-] 」";
                animDur = 0.26f;
                break;
            case "×":
                pose = "OP_MUL";
                voiceLine = "「 かける [×] 」";
                animDur = 0.26f;
                break;
            case "÷":
                pose = "OP_DIV";
                voiceLine = "「 わる [÷] 」";
                animDur = 0.26f;
                break;
            case "=":
                pose = "EQUALS_EGO";
                voiceLine = "「 はっ！答えが出たぜ！ [=] 」";
                animDur = 0.30f;
                speechDur = 1.35f;
                pitch = 4.0f;
                break;
            case "⌫":
                pose = "BACKSPACE";
                voiceLine = "「 消す、消す！ [⌫] 」";
                animDur = 0.25f;
                speechDur = 0.75f;
                pitch = -4.0f;
                break;
            case "C":
                pose = "CLEAR";
                voiceLine = "「 リセットだ！ [C] 」";
                animDur = 0.28f;
                speechDur = 0.65f;
                break;
            case ".":
                pose = "DIGIT_1";
                voiceLine = "「 てん [.] 」";
                break;
            case "%":
                pose = "OP_DIV";
                voiceLine = "「 パーセント [%] 」";
                break;
            case "±":
                pose = "OP_MINUS";
                voiceLine = "「 プラスマイナス [±] 」";
                break;
        }

        renderer.model.triggerPose(pose, animDur, speechDur, yaw, pitch);
        if (!voiceLine.isEmpty()) {
            showVoiceBubble(voiceLine);
        }
    }

    private void showVoiceBubble(String text) {
        voiceBadgeView.setText(text);
        voiceBadgeView.animate().cancel();
        voiceBadgeView.setAlpha(1f);
        voiceBadgeView.setScaleX(0.86f);
        voiceBadgeView.setScaleY(0.86f);
        voiceBadgeView.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(140)
                .setInterpolator(new OvershootInterpolator(2.0f))
                .withEndAction(() -> voiceBadgeView.animate()
                        .alpha(0f)
                        .setStartDelay(1800)
                        .setDuration(300)
                        .start())
                .start();
    }

    private void setupTouchInteraction(View view) {
        view.setOnTouchListener((v, ev) -> {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downTouchX = lastTouchX = ev.getX();
                    downTouchY = lastTouchY = ev.getY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = ev.getX() - lastTouchX;
                    float dy = ev.getY() - lastTouchY;
                    lastTouchX = ev.getX();
                    lastTouchY = ev.getY();
                    renderer.onTouchDrag(dx, dy, v.getWidth(), v.getHeight());
                    return true;
                case MotionEvent.ACTION_UP:
                    float moveDist = (float) Math.hypot(ev.getX() - downTouchX, ev.getY() - downTouchY);
                    if (moveDist < dp(12)) {
                        String p = SHOWCASE_POSES[tapPoseCycle % SHOWCASE_POSES.length];
                        tapPoseCycle++;
                        if ("EASTER_67_A".equals(p)) {
                            soundEngine.playEasterEggVoice("ee_67");
                            renderer.model.triggerSixSevenDance();
                            showVoiceBubble("🔥 SIX-SEVEN!! (67) 🔥");
                        } else {
                            soundEngine.playKeySound("5");
                            renderer.model.triggerPose(p, 0.28f, 0.35f, 0f, 2f);
                            showVoiceBubble("「 POSE: " + p.replace("DIGIT_", "#").replace("OP_", "") + " 」");
                        }
                    }
                    return true;
            }
            return false;
        });
    }

    private TextView createSideIconButton(String symbol, boolean active, View.OnClickListener listener) {
        TextView tv = new TextView(this);
        tv.setText(symbol);
        tv.setGravity(Gravity.CENTER);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(38), dp(38));
        lp.bottomMargin = dp(8);
        tv.setLayoutParams(lp);
        styleSideButton(tv, active);
        tv.setOnClickListener(listener);
        return tv;
    }

    private void styleSideButton(TextView tv, boolean active) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        if (active) {
            gd.setColor(Color.parseColor("#E6FF6614"));
            gd.setStroke(dp(1.5f), Color.parseColor("#FFFFDFC4"));
            tv.setTextColor(Color.WHITE);
        } else {
            gd.setColor(Color.parseColor("#A6241109"));
            gd.setStroke(dp(1.1f), Color.parseColor("#88FF7729"));
            tv.setTextColor(Color.parseColor("#FFD6BA"));
        }
        tv.setBackground(gd);
    }

    private int dp(float val) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, val, getResources().getDisplayMetrics()));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (glView != null) glView.onResume();
    }

    @Override
    protected void onPause() {
        if (glView != null) glView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (soundEngine != null) soundEngine.shutdown();
        super.onDestroy();
    }

    private class RuledDisplayLayout extends LinearLayout {
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bracketPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint watermarkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint kcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF cardRect = new RectF();

        public RuledDisplayLayout(Activity context) {
            super(context);
            setWillNotDraw(false);
            linePaint.setColor(Color.parseColor("#2EFF7A29"));
            linePaint.setStrokeWidth(dp(1));

            borderPaint.setColor(Color.parseColor("#99FF6B1A"));
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(dp(1.4f));

            bracketPaint.setColor(Color.parseColor("#FFFF944D"));
            bracketPaint.setStyle(Paint.Style.STROKE);
            bracketPaint.setStrokeWidth(dp(2.2f));

            watermarkPaint.setColor(Color.parseColor("#24FF7324"));
            watermarkPaint.setTextSize(dp(44));
            watermarkPaint.setTypeface(Typeface.create("sans-serif-black", Typeface.ITALIC));

            kcPaint.setColor(Color.parseColor("#55FF6B1A"));
            kcPaint.setTextSize(dp(13));
            kcPaint.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            float r = dp(16);
            cardRect.set(dp(1), dp(1), w - dp(1), h - dp(1));

            bgPaint.setShader(new LinearGradient(
                    0, 0, 0, h,
                    Color.parseColor("#B51F0F09"),
                    Color.parseColor("#CC120804"),
                    Shader.TileMode.CLAMP
            ));
            canvas.drawRoundRect(cardRect, r, r, bgPaint);
            canvas.drawRoundRect(cardRect, r, r, borderPaint);

            for (int i = 1; i <= 3; i++) {
                float y = (h * i) / 4.0f;
                canvas.drawLine(dp(14), y, w - dp(14), y, linePaint);
            }

            // Tactical orange KC & #50 watermarks on the left of the display card
            canvas.drawText("KC", dp(16), dp(22), kcPaint);
            canvas.drawText("#50", dp(14), h - dp(12), watermarkPaint);

            super.onDraw(canvas);
        }
    }

    private class FrameTrimView extends View {
        private final Paint trimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public FrameTrimView(Activity ctx) {
            super(ctx);
            trimPaint.setColor(Color.parseColor("#66FF6B1A"));
            trimPaint.setStyle(Paint.Style.STROKE);
            trimPaint.setStrokeWidth(dp(1.2f));

            dotPaint.setColor(Color.parseColor("#99FF883B"));
            dotPaint.setStyle(Paint.Style.FILL);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            int step = dp(24);
            int topY = dp(26);
            int botY = h - dp(8);

            for (int x = step / 2; x < w; x += step) {
                canvas.drawCircle(x, topY, dp(1.8f), dotPaint);
                canvas.drawCircle(x, botY, dp(1.8f), dotPaint);
            }
            canvas.drawLine(dp(14), topY + dp(6), w - dp(14), topY + dp(6), trimPaint);
        }
    }
}
