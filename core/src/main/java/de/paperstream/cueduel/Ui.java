package de.paperstream.cueduel;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ExtendViewport;

// HUD und alle Menüs
final class Ui {
    private static final Color C_CARD = Color.valueOf("2a241f"), C_BTN = Color.valueOf("42382f"),
        C_GREEN = Color.valueOf("2fb36f"), C_RED = Color.valueOf("e2361f"), C_GOLD = Color.valueOf("ffe678"),
        C_DIM = Color.valueOf("b8b0a8"), C_FIELD = Color.valueOf("1a1613"), C_BAD = Color.valueOf("ff7b6b");
    private static final String CHARS = FreeTypeFontGenerator.DEFAULT_CHARS + "ğİıŞşÇç…·–‹›¡¿ñáíóúÖÜßäöü×";
    private static final String[] LEVEL_NAMES = {"Easy", "Medium", "Hard", "Pro"};
    private static final String[] LEVEL_DESC = {
        "Aims poorly, often picks hard shots and slips up a lot.",
        "Aims decently and picks good shots, but slips up now and then.",
        "Very accurate and thinks about where the cue ball ends up.",
        "Almost flawless and plays position. Good luck!"};

    final Game game;
    Stage stage;
    int fine;
    float fineT;

    private final List<String> stack = new ArrayList<>();
    private final List<Texture> textures = new ArrayList<>();
    private final List<BitmapFont> fonts = new ArrayList<>();
    private final Texture[] icons = new Texture[16];
    private BitmapFont f18, f22, f28, f34, f52, fToast;
    private NinePatchDrawable round;
    private Drawable knob, whiteDr;
    private Table hud, overlay, card, controls, rpBar;
    private final Table[] chips = new Table[2];
    private Cell<?> bottomCell;
    private Label toastLb, resultLb;
    private Slider powerSlider;
    private TextButton shootBtn, restartBtn, soundBtn, rpSpeed;
    private boolean settingPower, restartArmed, statsArmed, hostShow = true;
    private float restartT, resultT;
    private String netMsg = "", codeText = "", pwText = "", hostName = "", hostPw = "", pickPw = "";
    private Online.Lobby pick;
    private List<Online.Lobby> lobbyList;
    private boolean lobbyFailed, lobbyLoading;
    private int tLevel;
    private boolean tHaptic, tMute;
    private int tQuality;
    private String tLang = "";
    private String resTitle = "", resReason = "";
    private boolean resGood;
    private String[][] resTiles = new String[0][];

    Ui(Game game) {
        this.game = game;
    }

    void create() {
        ExtendViewport vp = new ExtendViewport(540, 960);
        stage = new Stage(vp);
        vp.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
        loadResources();
        buildHud();
    }

    private void loadResources() {
        float upp = stage.getViewport().getWorldWidth() / Math.max(1, Gdx.graphics.getWidth());
        FreeTypeFontGenerator bold = new FreeTypeFontGenerator(Gdx.files.internal("fonts/NotoSans-Bold.ttf"));
        FreeTypeFontGenerator black = new FreeTypeFontGenerator(Gdx.files.internal("fonts/NotoSans-Black.ttf"));
        f18 = font(bold, 18, upp, 0);
        f22 = font(bold, 22, upp, 0);
        f28 = font(bold, 28, upp, 0);
        fToast = font(bold, 25, upp, 2.2f);
        f34 = font(black, 34, upp, 0);
        f52 = font(black, 52, upp, 0);
        bold.dispose();
        black.dispose();
        round = new NinePatchDrawable(new NinePatch(roundTex(64, 18), 24, 24, 24, 24));
        round.getPatch().setPadding(16, 16, 8, 8);
        knob = new TextureRegionDrawable(new TextureRegion(roundTex(64, 32)));
        knob.setMinWidth(52);
        knob.setMinHeight(52);
        Pixmap pm = new Pixmap(2, 2, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE);
        pm.fill();
        Texture w = new Texture(pm);
        pm.dispose();
        textures.add(w);
        whiteDr = new TextureRegionDrawable(new TextureRegion(w));
        for (int i = 0; i < 16; i++) {
            icons[i] = new Texture(Gdx.files.internal("balls/icon_" + i + ".png"), true);
            icons[i].setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        }
    }

    private void disposeResources() {
        for (Texture t : textures) t.dispose();
        for (Texture t : icons) if (t != null) t.dispose();
        for (BitmapFont f : fonts) f.dispose();
        textures.clear();
        fonts.clear();
    }

    // nach der Rückkehr aus dem Hintergrund: Schriften und Grafiken neu erzeugen
    void reload() {
        disposeResources();
        loadResources();
        rebuild();
    }

    private BitmapFont font(FreeTypeFontGenerator gen, int size, float upp, float border) {
        FreeTypeFontGenerator.FreeTypeFontParameter p = new FreeTypeFontGenerator.FreeTypeFontParameter();
        p.size = Math.max(8, Math.round(size / upp));
        p.characters = CHARS;
        p.minFilter = Texture.TextureFilter.Linear;
        p.magFilter = Texture.TextureFilter.Linear;
        if (border > 0) {
            p.borderWidth = border / upp;
            p.borderColor = new Color(0, 0, 0, 0.85f);
        }
        BitmapFont f = gen.generateFont(p);
        f.getData().setScale(upp);
        f.setUseIntegerPositions(false);
        fonts.add(f);
        return f;
    }

    // weißes abgerundetes Quadrat mit weicher Kante
    private Texture roundTex(int size, float rad) {
        Pixmap pm = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float cx = Math.max(rad, Math.min(size - rad, x + 0.5f)), cy = Math.max(rad, Math.min(size - rad, y + 0.5f));
                float d = (float) Math.hypot(x + 0.5f - cx, y + 0.5f - cy);
                float a = Math.max(0f, Math.min(1f, rad - d + 0.5f));
                pm.drawPixel(x, y, Color.rgba8888(1, 1, 1, a));
            }
        }
        Texture t = new Texture(pm);
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pm.dispose();
        textures.add(t);
        return t;
    }

    // ---------- widgets ----------

    private Drawable bg(Color c) {
        NinePatchDrawable d = round.tint(c);
        d.setMinWidth(8);
        d.setMinHeight(8);
        return d;
    }

    private Label label(String text, BitmapFont f, Color c) {
        Label l = new Label(text, new Label.LabelStyle(f, c));
        l.setAlignment(Align.center);
        return l;
    }

    private Label wrap(String text, BitmapFont f, Color c) {
        Label l = label(text, f, c);
        l.setWrap(true);
        return l;
    }

    private TextButton btn(String text, Color c, final Runnable cb) {
        TextButton.TextButtonStyle st = new TextButton.TextButtonStyle();
        st.up = bg(c);
        st.down = bg(c.cpy().lerp(Color.WHITE, 0.25f));
        st.disabled = bg(c.cpy().mul(0.6f, 0.6f, 0.6f, 0.7f));
        st.font = f22;
        st.fontColor = Color.WHITE;
        st.disabledFontColor = new Color(1, 1, 1, 0.4f);
        TextButton b = new TextButton(text, st);
        b.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                Sfx.play("click", 0.5f);
                cb.run();
            }
        });
        return b;
    }

    private TextField field(String text, String hint, final java.util.function.Consumer<String> onChange) {
        TextField.TextFieldStyle st = new TextField.TextFieldStyle();
        st.font = f22;
        st.fontColor = Color.WHITE;
        st.messageFont = f22;
        st.messageFontColor = new Color(1, 1, 1, 0.35f);
        st.background = bg(C_FIELD);
        st.cursor = ((TextureRegionDrawable) whiteDr).tint(Color.WHITE);
        st.cursor.setMinWidth(2);
        st.selection = ((TextureRegionDrawable) whiteDr).tint(new Color(0.18f, 0.7f, 0.43f, 0.5f));
        TextField f = new TextField(text, st);
        f.setMessageText(hint);
        f.setMaxLength(24);
        f.setTextFieldListener((tf, c) -> {
            onChange.accept(tf.getText());
            if (c == '\n' || c == '\r') unfocus();
        });
        return f;
    }

    private Table tile(String value, String key) {
        Table t = new Table();
        t.setBackground(bg(C_BTN));
        t.pad(6, 4, 6, 4);
        t.add(label(value, f28, Color.WHITE)).row();
        t.add(wrap(L.t(key), f18, C_DIM)).growX();
        return t;
    }

    // ---------- HUD ----------

    private void buildHud() {
        stage.clear();
        float upp = stage.getViewport().getWorldWidth() / Math.max(1, Gdx.graphics.getWidth());
        float top = 16 + Gdx.graphics.getSafeInsetTop() * upp, bottom = 22 + Gdx.graphics.getSafeInsetBottom() * upp;
        hud = new Table();
        hud.setFillParent(true);
        hud.setTouchable(Touchable.childrenOnly);
        hud.pad(top, 14, bottom, 14);
        stage.addActor(hud);

        Table row = new Table();
        row.add(btn(L.t("Menu"), C_BTN, this::openMenu)).height(56).padRight(10);
        row.add(btn(L.t("Balls"), C_BTN, () -> open("balls"))).height(56);
        row.add().growX();
        restartBtn = btn(L.t("Restart"), C_BTN, this::restartTap);
        row.add(restartBtn).height(56);
        hud.add(row).growX().row();

        Table chipRow = new Table();
        for (int i = 0; i < 2; i++) {
            chips[i] = new Table();
            chipRow.add(chips[i]).height(48).pad(10, 6, 0, 6);
        }
        hud.add(chipRow).row();

        toastLb = wrap("", fToast, Color.WHITE);
        toastLb.getColor().a = 0;
        toastLb.setTouchable(Touchable.disabled);
        hud.add(toastLb).growX().padTop(10).row();
        hud.add().grow().row();

        controls = new Table();
        Table r1 = new Table();
        r1.add(holdBtn("‹", -1)).size(76, 60).padRight(10);
        r1.add(holdBtn("›", 1)).size(76, 60);
        r1.add().growX();
        r1.add(btn(L.t("View"), C_BTN, () -> game.followCam = !game.followCam)).height(60).padRight(10);
        soundBtn = btn("", C_BTN, () -> {
            Store.mute = !Store.mute;
            Store.save();
            updateSoundBtn();
        });
        r1.add(soundBtn).height(60);
        controls.add(r1).growX().row();
        Table r2 = new Table();
        Slider.SliderStyle ss = new Slider.SliderStyle();
        // eigene schmale Grafik, die große Eckengrafik zerfällt bei 16 Höhe
        NinePatchDrawable bar = new NinePatchDrawable(new NinePatch(roundTex(16, 8), 7, 7, 7, 7));
        ss.background = bar.tint(new Color(0, 0, 0, 0.6f));
        ss.background.setMinHeight(16);
        ss.background.setMinWidth(16);
        ss.background.setLeftWidth(0);
        ss.background.setRightWidth(0);
        ss.knobBefore = bar.tint(C_GREEN);
        ss.knobBefore.setMinHeight(16);
        ss.knobBefore.setMinWidth(0);
        ss.knob = knob;
        powerSlider = new Slider(5, 100, 1, false, ss);
        powerSlider.setValue(55);
        powerSlider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (settingPower) return;
                game.myPower = powerSlider.getValue() / 100.0;
                if (game.state == Game.St.AIM || game.state == Game.St.PLACE) game.power = game.myPower;
            }
        });
        r2.add(powerSlider).growX().height(80).padLeft(10).padRight(14);
        shootBtn = btn(L.t("Shoot"), C_RED, game::shootPressed);
        shootBtn.getLabel().setStyle(new Label.LabelStyle(f34, Color.WHITE));
        r2.add(shootBtn).size(200, 80);
        controls.add(r2).growX().padTop(10);

        rpBar = new Table();
        rpBar.add(btn(L.t("Stop"), C_BTN, game::stopReplay)).height(60).padRight(10);
        rpSpeed = btn("x1", C_BTN, () -> {
            game.spd = game.spd == 1 ? 2 : (game.spd == 2 ? 4 : 1);
            rpSpeed.setText("x" + game.spd);
        });
        rpBar.add(rpSpeed).size(90, 60);
        rpBar.add().growX();

        bottomCell = hud.add(controls).growX();

        overlay = new Table();
        overlay.setFillParent(true);
        overlay.setBackground(((TextureRegionDrawable) whiteDr).tint(new Color(0.05f, 0.04f, 0.035f, 0.78f)));
        overlay.setTouchable(Touchable.enabled);
        overlay.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                return true;
            }
        });
        card = new Table();
        card.setBackground(bg(C_CARD));
        card.pad(22);
        overlay.add(card).width(492);
        overlay.setVisible(false);
        stage.addActor(overlay);
        updateSoundBtn();
        setReplayMode(game.replay != null);
        updateHud();
    }

    void rebuild() {
        buildHud();
        if (!stack.isEmpty()) show();
    }

    private TextButton holdBtn(String text, final int dir) {
        TextButton b = btn(text, C_BTN, () -> {
        });
        b.getLabel().setStyle(new Label.LabelStyle(f34, Color.WHITE));
        b.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                fine = dir;
                fineT = 0;
                if (game.state == Game.St.AIM) game.aim += dir * 0.002;
                return true;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                fine = 0;
            }
        });
        return b;
    }

    private void fillChip(int side) {
        Table c = chips[side];
        c.clear();
        boolean active = game.turn == side && game.state != Game.St.IDLE;
        c.setBackground(bg(active ? new Color(0.18f, 0.7f, 0.43f, 0.85f) : new Color(0.1f, 0.09f, 0.08f, 0.7f)));
        c.add(label(game.name(side), f22, Color.WHITE)).padRight(10);
        int g = game.groups[side];
        if (g == Rules.NONE) {
            c.add(label("–", f22, C_DIM));
        } else {
            int left = game.sim.groupLeft(g);
            c.add(new Image(icons[left > 0 ? (g == Rules.SOLID ? 1 : 9) : 8])).size(30).padRight(6);
            c.add(label(left > 0 ? String.valueOf(left) : "8", f22, Color.WHITE));
        }
    }

    void updateHud() {
        if (hud == null) return;
        fillChip(0);
        fillChip(1);
        boolean mine = game.state == Game.St.AIM || game.state == Game.St.PLACE;
        shootBtn.setDisabled(!mine);
        shootBtn.setText(game.state == Game.St.PLACE ? "OK" : L.t("Shoot"));
        restartBtn.setVisible(game.replay == null && (!game.online || game.net.host));
    }

    void setPower(double p) {
        if (powerSlider == null) return;
        settingPower = true;
        powerSlider.setValue((float) (p * 100));
        settingPower = false;
    }

    void setReplayMode(boolean on) {
        if (bottomCell == null) return;
        bottomCell.setActor(on ? rpBar : controls);
        rpSpeed.setText("x" + game.spd);
        restartBtn.setVisible(!on);
        hud.invalidateHierarchy();
    }

    void toast(String t) {
        if (toastLb == null) return;
        toastLb.setText(t);
        toastLb.clearActions();
        toastLb.addAction(Actions.sequence(Actions.alpha(1), Actions.delay(2.8f), Actions.fadeOut(0.6f)));
    }

    private void restartTap() {
        if (!restartArmed) {
            restartArmed = true;
            restartT = 3;
            restartBtn.setText(L.t("Sure?"));
            return;
        }
        restartArmed = false;
        restartBtn.setText(L.t("Restart"));
        game.restart();
    }

    private void updateSoundBtn() {
        soundBtn.setText(L.t("Sound") + ": " + L.t(Store.mute ? "Off" : "On"));
    }

    // ---------- overlays ----------

    boolean hasOverlay() {
        return !stack.isEmpty();
    }

    void open(String name) {
        if (!stack.isEmpty() && stack.get(stack.size() - 1).equals(name)) {
            show();
            return;
        }
        if (name.equals("settings")) {
            tLevel = Store.level;
            tHaptic = Store.haptic;
            tMute = Store.mute;
            tQuality = Store.quality;
            tLang = L.lang();
        }
        if (name.equals("lobbies")) loadLobbies();
        if (name.equals("online") || name.equals("host")) netMsg = "";
        statsArmed = false;
        stack.add(name);
        show();
    }

    void closeTop() {
        unfocus();
        if (!stack.isEmpty()) stack.remove(stack.size() - 1);
        show();
    }

    void closeAll() {
        unfocus();
        stack.clear();
        show();
    }

    void openMenu() {
        unfocus();
        stack.clear();
        open("menu");
    }

    private void unfocus() {
        if (stage == null) return;
        stage.setKeyboardFocus(null);
        Gdx.input.setOnscreenKeyboardVisible(false);
    }

    void back() {
        if (stack.isEmpty()) {
            openMenu();
            return;
        }
        String top = stack.get(stack.size() - 1);
        if (top.equals("menu")) {
            if (game.inGame || game.replay != null) closeAll();
            else Gdx.app.exit();
        } else if (top.equals("result")) {
            openMenu();
        } else {
            leave(top);
        }
    }

    // Zurück aus einem Online-Dialog bricht die Verbindung ab
    private void leave(String top) {
        if ((top.equals("host") || top.equals("online") || top.equals("password") || top.equals("lobbies"))
                && game.net.active && !game.online) {
            game.net.stop();
        }
        netMsg = "";
        closeTop();
    }

    private void show() {
        if (overlay == null) return;
        overlay.setVisible(!stack.isEmpty());
        card.clear();
        if (stack.isEmpty()) return;
        String cur = stack.get(stack.size() - 1);
        boolean typing = cur.equals("online") || cur.equals("host") || cur.equals("password");
        overlay.clearChildren();
        Cell<?> cc = overlay.add(card).width(492);
        if (typing) cc.expandY().top().padTop(28 + Gdx.graphics.getSafeInsetTop() * stage.getViewport().getWorldWidth() / Math.max(1, Gdx.graphics.getWidth()));
        switch (stack.get(stack.size() - 1)) {
            case "menu":
                buildMenu();
                break;
            case "online":
                buildOnline();
                break;
            case "host":
                buildHost();
                break;
            case "lobbies":
                buildLobbies();
                break;
            case "password":
                buildPassword();
                break;
            case "settings":
                buildSettings();
                break;
            case "stats":
                buildStats();
                break;
            case "replays":
                buildReplays();
                break;
            case "balls":
                buildBalls();
                break;
            case "result":
                buildResult();
                break;
            case "tutorial":
                buildTutorial();
                break;
            default:
                break;
        }
    }

    private void title(String key) {
        card.add(label(L.t(key), f34, Color.WHITE)).padBottom(14).row();
    }

    private void full(Actor a) {
        card.add(a).growX().height(64).padTop(10).row();
    }

    private void buildMenu() {
        card.add(label("CUE DUEL", f52, C_GOLD)).row();
        card.add(wrap(L.t("8-ball against the computer"), f22, C_DIM)).growX().padBottom(12).row();
        boolean cont = game.inGame && game.state != Game.St.OVER;
        if (cont) full(btn(L.t("Continue"), C_GREEN, this::closeAll));
        full(btn(L.t("Play"), cont ? C_BTN : C_GREEN, () -> {
            closeAll();
            game.startGame(false, false);
            if (!Store.tutorialSeen) openTutorial();
        }));
        full(btn(L.t("2 players"), C_BTN, () -> {
            closeAll();
            game.startGame(true, false);
        }));
        full(btn(L.t("Online"), C_BTN, () -> open("online")));
        Table r = new Table();
        r.add(btn(L.t("Replays"), C_BTN, () -> open("replays"))).growX().height(64).padRight(10).uniformX();
        r.add(btn(L.t("Statistics"), C_BTN, () -> open("stats"))).growX().height(64).uniformX();
        card.add(r).growX().padTop(10).row();
        full(btn(L.t("Settings"), C_BTN, () -> open("settings")));
        card.add(label(Game.VERSION, f18, new Color(1, 1, 1, 0.3f))).padTop(12);
    }

    // ---------- online ----------

    private void buildOnline() {
        title("Online game");
        card.add(label(L.t("Join with code"), f22, C_DIM)).row();
        Table fr = new Table();
        fr.add(field(codeText, L.t("Room code"), s -> codeText = s)).growX().minWidth(0).height(60).padRight(8).uniformX();
        fr.add(field(pwText, L.t("Password"), s -> pwText = s)).growX().minWidth(0).height(60).uniformX();
        card.add(fr).growX().padTop(8).row();
        if (!netMsg.isEmpty()) card.add(wrap(netMsg, f22, C_GOLD)).growX().padTop(8).row();
        full(btn(L.t("Join"), C_GREEN, () -> {
            if (codeText.trim().isEmpty()) return;
            join(codeText, pwText);
        }));
        full(btn(L.t("Find games"), C_BTN, () -> {
            unfocus();
            open("lobbies");
        }));
        full(btn(L.t("Host a game"), C_BTN, () -> {
            unfocus();
            open("host");
        }));
        full(btn(L.t("Back"), C_BTN, () -> leave("online")));
    }

    private void join(String code, String pw) {
        unfocus();
        netMsg = L.t("Connecting…");
        game.net.join(code, pw);
        show();
    }

    private void buildHost() {
        title("Host a game");
        boolean hosting = game.net.active && game.net.host;
        if (!hosting) {
            full(field(hostName, L.t("Game name"), s -> hostName = s));
            full(field(hostPw, L.t("Password (optional)"), s -> hostPw = s));
            Table hr = new Table();
            hr.add(btn(L.t("Back"), C_BTN, () -> leave("host"))).growX().minWidth(0).height(64).padRight(8).uniformX();
            hr.add(btn(L.t("Create game"), C_GREEN, () -> {
                unfocus();
                game.leaveOnline();
                game.net.hostGame(hostName.trim().isEmpty() ? "Cue Duel" : hostName.trim(), hostPw, hostShow);
            })).growX().minWidth(0).height(64).uniformX();
            card.add(hr).growX().padTop(10).row();
            full(btn(L.t("Show in the list") + ": " + L.t(hostShow ? "On" : "Off"), C_BTN, () -> {
                hostShow = !hostShow;
                show();
            }));
            return;
        } else if (game.net.listFailed) {
            card.add(wrap(L.t("The list could not be reached"), f22, C_BAD)).growX().pad(12).row();
        } else if (game.net.lobbyId.isEmpty()) {
            card.add(label(L.t("Loading…"), f22, C_DIM)).pad(12).row();
        } else {
            card.add(wrap(L.t("Waiting for a friend…"), f22, C_DIM)).growX().padTop(6).row();
            card.add(label(game.net.lobbyId, f52, C_GOLD)).padTop(8).row();
            card.add(wrap(L.t("Give this code to your friend"), f22, C_DIM)).growX().padBottom(8).row();
        }
        full(btn(L.t("Back"), C_BTN, () -> leave("host")));
    }

    private void loadLobbies() {
        lobbyLoading = true;
        lobbyFailed = false;
        lobbyList = null;
        game.net.fetchLobbies();
    }

    void lobbies(List<Online.Lobby> list) {
        lobbyLoading = false;
        lobbyFailed = list == null;
        lobbyList = list;
        if (isTop("lobbies")) show();
    }

    private boolean isTop(String name) {
        return !stack.isEmpty() && stack.get(stack.size() - 1).equals(name);
    }

    private void buildLobbies() {
        title("Find games");
        Table list = new Table();
        list.top();
        if (lobbyLoading) {
            list.add(label(L.t("Loading…"), f22, C_DIM)).pad(20);
        } else if (lobbyFailed) {
            list.add(wrap(L.t("The list could not be reached"), f22, C_BAD)).growX().pad(20);
        } else if (lobbyList == null || lobbyList.isEmpty()) {
            list.add(label(L.t("No games found"), f22, C_DIM)).pad(20);
        } else {
            for (final Online.Lobby l : lobbyList) {
                boolean fullGame = l.players >= 2;
                String text = l.name + "   " + l.players + "/2" + (l.locked ? "   · " + L.t("Password") : "");
                TextButton b = btn(text, C_BTN, () -> {
                    pick = l;
                    if (l.locked) {
                        pickPw = "";
                        netMsg = "";
                        open("password");
                    } else {
                        join(l.id, "");
                    }
                });
                b.setDisabled(fullGame);
                list.add(b).growX().height(60).padBottom(8).row();
            }
        }
        ScrollPane sp = new ScrollPane(list);
        sp.setScrollingDisabled(true, false);
        sp.setFadeScrollBars(false);
        card.add(sp).growX().height(380).row();
        if (!netMsg.isEmpty()) card.add(wrap(netMsg, f22, C_GOLD)).growX().padTop(8).row();
        Table r = new Table();
        r.add(btn(L.t("Back"), C_BTN, () -> leave("lobbies"))).growX().height(64).padRight(10).uniformX();
        r.add(btn(L.t("Refresh"), C_GREEN, () -> {
            loadLobbies();
            show();
        })).growX().height(64).uniformX();
        card.add(r).growX().padTop(10);
    }

    private void buildPassword() {
        card.add(label(pick == null ? "" : pick.name, f34, Color.WHITE)).padBottom(14).row();
        full(field(pickPw, L.t("Password"), s -> pickPw = s));
        if (!netMsg.isEmpty()) card.add(wrap(netMsg, f22, C_GOLD)).growX().padTop(8).row();
        Table pr = new Table();
        pr.add(btn(L.t("Back"), C_BTN, () -> leave("password"))).growX().minWidth(0).height(64).padRight(8).uniformX();
        pr.add(btn(L.t("Join"), C_GREEN, () -> {
            if (pick != null) join(pick.id, pickPw);
        })).growX().minWidth(0).height(64).uniformX();
        card.add(pr).growX().padTop(10);
    }

    void netStatus() {
        if (!game.net.host && game.net.active) netMsg = L.t("Connected, waiting for the host…");
        if (isTop("host") || isTop("online") || isTop("lobbies") || isTop("password")) show();
    }

    void netFailed(String why) {
        netMsg = L.t(why);
        if (isTop("host") || isTop("online") || isTop("lobbies") || isTop("password")) show();
        else toast(netMsg);
    }

    // ---------- settings ----------

    private TextButton pickBtn(String text, boolean on, Runnable cb) {
        return btn(text, on ? C_GREEN : C_BTN, cb);
    }

    private void optionRow(String key, String[] names, int sel, final java.util.function.IntConsumer cb) {
        card.add(label(L.t(key), f22, C_DIM)).padTop(14).row();
        Table r = new Table();
        for (int i = 0; i < names.length; i++) {
            final int k = i;
            r.add(pickBtn(names[i], i == sel, () -> {
                cb.accept(k);
                show();
            })).growX().minWidth(0).height(56).pad(4, 3, 0, 3).uniformX();
        }
        card.add(r).growX().row();
    }

    private void buildSettings() {
        title("Settings");
        String[] lv = new String[4];
        for (int i = 0; i < 4; i++) lv[i] = L.t(LEVEL_NAMES[i]);
        optionRow("AI level", lv, tLevel - 1, k -> tLevel = k + 1);
        card.add(wrap(L.t(LEVEL_DESC[tLevel - 1]), f18, Color.WHITE)).growX().padTop(8).row();
        if (tLevel != Store.level && game.inGame && !game.duo) {
            card.add(wrap(L.t("A new AI level starts a new game."), f18, C_GOLD)).growX().padTop(4).row();
        }
        String[] onOff = {L.t("Off"), L.t("On")};
        optionRow("Vibration", onOff, tHaptic ? 1 : 0, k -> tHaptic = k == 1);
        optionRow("Sound", onOff, tMute ? 0 : 1, k -> tMute = k == 0);
        optionRow("Graphics", new String[] {L.t("Low"), L.t("Medium"), L.t("High")}, tQuality, k -> tQuality = k);
        int ls = 0;
        for (int i = 0; i < L.LANGS.length; i++) if (L.LANGS[i].equals(tLang)) ls = i;
        optionRow("Language", L.NAMES, ls, k -> tLang = L.LANGS[k]);
        full(btn(L.t("Tutorial"), C_BTN, this::openTutorial));
        Table r = new Table();
        r.add(btn(L.t("Cancel"), C_BTN, this::closeTop)).growX().height(64).padRight(10).uniformX();
        r.add(btn(L.t("Apply"), C_GREEN, this::applySettings)).growX().height(64).uniformX();
        card.add(r).growX().padTop(20);
    }

    private void applySettings() {
        Store.haptic = tHaptic;
        Store.mute = tMute;
        Store.quality = tQuality;
        boolean langChanged = !tLang.equals(L.lang());
        if (langChanged) {
            Store.lang = tLang;
            L.setLang(tLang);
        }
        game.view.applyQuality();
        if (!stack.isEmpty()) stack.remove(stack.size() - 1);
        game.setLevel(tLevel);
        Store.save();
        if (langChanged) rebuild();
        else show();
        updateSoundBtn();
        updateHud();
    }

    // ---------- tutorial ----------

    private static final String[][] TUT = {
        {"Welcome to Cue Duel", "Pot all balls of your group, then the black 8. Whoever pots the 8 cleanly wins."},
        {"Aiming", "Drag across the table to turn the cue. Use the arrow buttons for fine aiming. The line shows where the balls will go."},
        {"Power and shot", "Set the power with the slider, then tap Shoot."},
        {"Groups", "The first ball you pot after the break decides your group: solids or stripes. Balls shows what is left."},
        {"Fouls", "Potting the cue ball, hitting the wrong ball first or no rail after contact is a foul. Your opponent then gets ball in hand and can place the cue ball anywhere."},
        {"Views", "View switches between the camera behind the cue and the top view. Have fun!"},
    };
    private int tutStep;

    void openTutorial() {
        tutStep = 0;
        open("tutorial");
    }

    private void endTutorial() {
        Store.tutorialSeen = true;
        Store.save();
        closeTop();
    }

    private void buildTutorial() {
        title(TUT[tutStep][0]);
        card.add(wrap(L.t(TUT[tutStep][1]), f22, Color.WHITE)).growX().padBottom(10).row();
        card.add(label((tutStep + 1) + " / " + TUT.length, f18, C_DIM)).row();
        boolean last = tutStep == TUT.length - 1;
        Table r = new Table();
        r.add(btn(L.t("Skip"), C_BTN, this::endTutorial)).growX().height(64).padRight(10).uniformX();
        r.add(btn(L.t(last ? "Play" : "Next"), C_GREEN, () -> {
            if (last) endTutorial();
            else {
                tutStep++;
                show();
            }
        })).growX().height(64).uniformX();
        card.add(r).growX().padTop(16);
    }

    // ---------- statistics ----------

    private void buildStats() {
        title("Statistics");
        Table g = new Table();
        String[][] tiles = {
            {String.valueOf(Store.games), "Games"}, {String.valueOf(Store.wins), "Wins"},
            {String.valueOf(Store.losses), "Losses"}, {Game.pct(Store.wins, Store.games), "Win rate"},
            {String.valueOf(Store.bestStreak), "Best win streak"}, {Game.pct(Store.pots, Store.shots), "Pots per shot"}};
        for (int i = 0; i < tiles.length; i++) {
            g.add(tile(tiles[i][0], tiles[i][1])).growX().uniformX().height(96).pad(4);
            if (i % 3 == 2) g.row();
        }
        card.add(g).growX().row();
        card.add(label(L.t("Win rate per level"), f22, C_DIM)).padTop(14).row();
        for (int i = 0; i < 4; i++) {
            int w = Store.levels[i][0], l = Store.levels[i][1];
            String line = L.t(LEVEL_NAMES[i]) + ":  " + L.t("{0} W · {1} L", w, l) + "   " + Game.pct(w, w + l);
            card.add(label(line, f22, Color.WHITE)).padTop(4).row();
        }
        Table r = new Table();
        r.add(btn(L.t(statsArmed ? "Sure?" : "Reset"), statsArmed ? C_RED : C_BTN, () -> {
            if (statsArmed) Store.resetStats();
            statsArmed = !statsArmed;
            show();
        })).growX().height(64).padRight(10).uniformX();
        r.add(btn(L.t("Back"), C_GREEN, this::closeTop)).growX().height(64).uniformX();
        card.add(r).growX().padTop(18);
    }

    // ---------- replays ----------

    private void buildReplays() {
        title("Replays");
        Table list = new Table();
        list.top();
        if (Store.replays.isEmpty()) {
            list.add(wrap(L.t("No replays yet. Every game is saved automatically."), f22, C_DIM)).growX().pad(20);
        }
        for (final Store.Replay rp : Store.replays) {
            String who = rp.duo ? L.t("2 players") : L.t(LEVEL_NAMES[Math.max(1, Math.min(4, rp.level)) - 1]);
            String text = rp.date + "\n" + who + " · " + L.t(rp.res) + " · " + L.t("{0} shots", rp.shots.size());
            TextButton b = btn(text, C_BTN, () -> {
                closeAll();
                game.startReplay(rp);
            });
            b.getLabel().setStyle(new Label.LabelStyle(f18, Color.WHITE));
            list.add(b).growX().height(72).padBottom(8).row();
        }
        ScrollPane sp = new ScrollPane(list);
        sp.setScrollingDisabled(true, false);
        card.add(sp).growX().height(480).row();
        full(btn(L.t("Back"), C_GREEN, this::closeTop));
    }

    // ---------- balls ----------

    private void buildBalls() {
        title("Balls");
        for (int side = 0; side < 2; side++) {
            int g = game.groups[side];
            String head = game.name(side) + (g == Rules.NONE ? "" : " – " + L.t(g == Rules.SOLID ? "Solids" : "Stripes"));
            Label hl = label(head, f22, Color.WHITE);
            hl.setAlignment(Align.left);
            card.add(hl).growX().padTop(10).row();
            Table r = new Table();
            r.left();
            if (g == Rules.NONE) {
                r.add(label(L.t("Open table"), f18, C_DIM));
            } else {
                int left = 0;
                for (int n = g == Rules.SOLID ? 1 : 9; n <= (g == Rules.SOLID ? 7 : 15); n++) {
                    Image im = new Image(icons[n]);
                    boolean on = game.sim.isAlive(n);
                    im.getColor().a = on ? 1f : 0.2f;
                    if (on) left++;
                    r.add(im).size(42).padRight(5);
                }
                r.add(label(left > 0 ? L.t("{0} left", left) : L.t("only the 8 left!"), f18, C_DIM)).padLeft(6);
            }
            card.add(r).growX().padTop(6).row();
        }
        card.add(btn(L.t("Close"), C_GREEN, this::closeTop)).growX().height(64).padTop(20);
    }

    // ---------- result ----------

    void showResult(String title, boolean good, String reason, String[][] tiles) {
        resTitle = title;
        resGood = good;
        resReason = reason;
        resTiles = tiles;
        resultT = 15;
        stack.clear();
        open("result");
    }

    private void buildResult() {
        card.add(label(resTitle, f52, resGood ? C_GOLD : C_BAD)).row();
        card.add(wrap(resReason, f22, C_DIM)).growX().padBottom(12).row();
        Table g = new Table();
        for (int i = 0; i < resTiles.length; i++) {
            g.add(tile(resTiles[i][0], resTiles[i][1])).growX().uniformX().height(96).pad(4);
            if (i % 3 == 2) g.row();
        }
        card.add(g).growX().row();
        resultLb = label("", f18, C_DIM);
        card.add(resultLb).padTop(10).row();
        Table r = new Table();
        r.add(btn(L.t("Menu"), C_BTN, this::openMenu)).growX().height(64).padRight(8);
        if (!game.online) {
            r.add(btn(L.t("Replay"), C_BTN, () -> {
                closeAll();
                if (!Store.replays.isEmpty()) game.startReplay(Store.replays.get(0));
                else openMenu();
            })).growX().height(64).padRight(8);
        }
        r.add(btn(L.t("Again"), C_GREEN, () -> {
            if (!game.online || game.net.host) closeAll();
            game.rematch();
        })).growX().height(64);
        card.add(r).growX().padTop(10);
    }

    // ---------- frame ----------

    void render(float dt) {
        if (restartArmed) {
            restartT -= dt;
            if (restartT <= 0) {
                restartArmed = false;
                restartBtn.setText(L.t("Restart"));
            }
        }
        if (isTop("result") && resultLb != null) {
            resultT -= dt;
            if (resultT <= 0) {
                if (game.online) game.leaveOnline();
                openMenu();
            } else {
                resultLb.setText(L.t("Back to menu in {0} s", (int) Math.ceil(resultT)));
            }
        }
        stage.getViewport().apply();
        stage.act(dt);
        stage.draw();
    }

    void resize(int w, int h) {
        stage.getViewport().update(w, h, true);
    }

    void dispose() {
        stage.dispose();
        disposeResources();
    }
}
