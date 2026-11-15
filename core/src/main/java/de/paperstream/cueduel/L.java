package de.paperstream.cueduel;

import java.util.HashMap;
import java.util.Locale;

import com.badlogic.gdx.Gdx;

public final class L {
    public static final String[] LANGS = {"en", "de", "es", "tr"};
    public static final String[] NAMES = {"English", "Deutsch", "Español", "Türkçe"};

    private static final HashMap<String, String[]> TAB = new HashMap<>();
    private static int cur;

    public static void load() {
        for (String line : Gdx.files.internal("lang.tsv").readString("UTF-8").split("\n")) {
            String[] p = line.split("\t");
            if (p.length == 4) TAB.put(p[0], p);
        }
    }

    public static String systemLang() {
        String l = Locale.getDefault().getLanguage();
        for (String k : LANGS) if (k.equals(l)) return k;
        return "en";
    }

    public static void setLang(String l) {
        cur = 0;
        for (int i = 0; i < LANGS.length; i++) if (LANGS[i].equals(l)) cur = i;
    }

    public static String lang() {
        return LANGS[cur];
    }

    public static String t(String key, Object... args) {
        String[] row = TAB.get(key);
        String s = row == null ? key : row[cur];
        for (int i = 0; i < args.length; i++) s = s.replace("{" + i + "}", String.valueOf(args[i]));
        return s;
    }
}
