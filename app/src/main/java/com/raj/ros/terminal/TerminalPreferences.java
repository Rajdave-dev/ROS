package com.raj.ros.terminal;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import androidx.annotation.IntDef;
import android.util.Log;
import android.util.TypedValue;
import android.widget.Toast;
import com.raj.ros.terminal.TerminalSession;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

final class TerminalPreferences {

    @IntDef({BELL_VIBRATE, BELL_BEEP, BELL_IGNORE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface AsciiBellBehaviour {
    }

    static final int BELL_VIBRATE = 1;
    static final int BELL_BEEP = 2;
    static final int BELL_IGNORE = 3;

    private final int MIN_FONTSIZE;
    private static final int MAX_FONTSIZE = 256;

    private static final String SHOW_EXTRA_KEYS_KEY = "show_extra_keys";
    private static final String FONTSIZE_KEY = "fontsize";
    private static final String CURRENT_SESSION_KEY = "current_session";

    private String home_path;

    private int mFontSize;

    @AsciiBellBehaviour
    int mBellBehaviour = BELL_VIBRATE;

    boolean mBackIsEscape;
    boolean mUseCtrlSpaceWorkaround;
    boolean mShowExtraKeys;

    static int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }

    TerminalPreferences(Context context) {
        home_path = context.getFilesDir().getAbsolutePath() + "/home";

        reloadFromProperties(context);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        float dipInPixels = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, context.getResources().getDisplayMetrics());

        
        MIN_FONTSIZE = (int) (4f * dipInPixels);

        mShowExtraKeys = prefs.getBoolean(SHOW_EXTRA_KEYS_KEY, true);

       
        int defaultFontSize = Math.round(12 * dipInPixels);
        if (defaultFontSize % 2 == 1) defaultFontSize--;

        try {
            mFontSize = Integer.parseInt(prefs.getString(FONTSIZE_KEY, Integer.toString(defaultFontSize)));
        } catch (NumberFormatException | ClassCastException e) {
            mFontSize = defaultFontSize;
        }
        mFontSize = clamp(mFontSize, MIN_FONTSIZE, MAX_FONTSIZE); 
    }

    boolean isShowExtraKeys() {
        return mShowExtraKeys;
    }

    boolean toggleShowExtraKeys(Context context) {
        mShowExtraKeys = !mShowExtraKeys;
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(SHOW_EXTRA_KEYS_KEY, mShowExtraKeys).apply();
        return mShowExtraKeys;
    }

    int getFontSize() {
        return mFontSize;
    }

    void changeFontSize(Context context, boolean increase) {
        mFontSize += (increase ? 1 : -1) * 2;
        mFontSize = Math.max(MIN_FONTSIZE, Math.min(mFontSize, MAX_FONTSIZE));

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putString(FONTSIZE_KEY, Integer.toString(mFontSize)).apply();
    }

    static void storeCurrentSession(Context context, TerminalSession session) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putString(TerminalPreferences.CURRENT_SESSION_KEY, session.mHandle).apply();
    }

    static TerminalSession getCurrentSession(TerminalActivity context) {
        String sessionHandle = PreferenceManager.getDefaultSharedPreferences(context).getString(TerminalPreferences.CURRENT_SESSION_KEY, "");
        for (int i = 0, len = context.mTermService.getSessions().size(); i < len; i++) {
            TerminalSession session = context.mTermService.getSessions().get(i);
            if (session.mHandle.equals(sessionHandle)) return session;
        }
        return null;
    }
    
    public String[][] mExtraKeys;

    public void reloadFromProperties(Context context) {
        File propsFile = new File(home_path + "/.Terminal/Terminal.properties");
        if (!propsFile.exists())
            propsFile = new File(home_path + "/.config/Terminal/Terminal.properties");

        Properties props = new Properties();
        try {
            if (propsFile.isFile() && propsFile.canRead()) {
                String encoding = "utf-8"; // most useful default nowadays
                try (FileInputStream in = new FileInputStream(propsFile)) {
                    props.load(new InputStreamReader(in, encoding));
                }
            }
        } catch (IOException e) {
            Toast.makeText(context, "Could not open the propertiey file Terminal.properties.", Toast.LENGTH_LONG).show();
            Log.e("Terminal", "Error loading props", e);
        }

        switch (props.getProperty("bell-character", "vibrate")) {
            case "beep":
                mBellBehaviour = BELL_BEEP;
                break;
            case "ignore":
                mBellBehaviour = BELL_IGNORE;
                break;
            default: // "vibrate".
                mBellBehaviour = BELL_VIBRATE;
                break;
        }


        try {
            String keys = "[['ESC', '/', '-', 'HOME', 'UP', 'END', 'PGUP'], ['TAB', 'CTRL', 'ALT', 'LEFT', 'DOWN', 'RIGHT', 'PGDN']]";
            JSONArray arr = new JSONArray(props.getProperty("extra-keys", keys));

            mExtraKeys = new String[arr.length()][];
            for (int i = 0; i < arr.length(); i++) {
                JSONArray line = arr.getJSONArray(i);
                mExtraKeys[i] = new String[line.length()];
                for (int j = 0; j < line.length(); j++) {
                    mExtraKeys[i][j] = line.getString(j);
                }
            }

        } catch (JSONException e) {
            Toast.makeText(context, "Could not load the extra-keys property from the config: " + e.toString(), Toast.LENGTH_LONG).show();
            Log.e("Terminal", "Error loading props", e);
            mExtraKeys = new String[0][];
        }

        mBackIsEscape = "escape".equals(props.getProperty("back-key", "back"));
        mUseCtrlSpaceWorkaround = Boolean.parseBoolean(props.getProperty("ctrl-space-workaround"));

        shortcuts.clear();
        parseAction("shortcut.create-session", SHORTCUT_ACTION_CREATE_SESSION, props);
        parseAction("shortcut.next-session", SHORTCUT_ACTION_NEXT_SESSION, props);
        parseAction("shortcut.previous-session", SHORTCUT_ACTION_PREVIOUS_SESSION, props);
        parseAction("shortcut.rename-session", SHORTCUT_ACTION_RENAME_SESSION, props);
    }

    public static final int SHORTCUT_ACTION_CREATE_SESSION = 1;
    public static final int SHORTCUT_ACTION_NEXT_SESSION = 2;
    public static final int SHORTCUT_ACTION_PREVIOUS_SESSION = 3;
    public static final int SHORTCUT_ACTION_RENAME_SESSION = 4;

    public final static class KeyboardShortcut {

        public KeyboardShortcut(int codePoint, int shortcutAction) {
            this.codePoint = codePoint;
            this.shortcutAction = shortcutAction;
        }

        final int codePoint;
        final int shortcutAction;
    }

    final List<KeyboardShortcut> shortcuts = new ArrayList<>();

    private void parseAction(String name, int shortcutAction, Properties props) {
        String value = props.getProperty(name);
        if (value == null) return;
        String[] parts = value.toLowerCase().trim().split("\\+");
        String input = parts.length == 2 ? parts[1].trim() : null;
        if (!(parts.length == 2 && parts[0].trim().equals("ctrl")) || input.isEmpty() || input.length() > 2) {
            Log.e("Terminal", "Keyboard shortcut '" + name + "' is not Ctrl+<something>");
            return;
        }

        char c = input.charAt(0);
        int codePoint = c;
        if (Character.isLowSurrogate(c)) {
            if (input.length() != 2 || Character.isHighSurrogate(input.charAt(1))) {
                Log.e("Terminal", "Keyboard shortcut '" + name + "' is not Ctrl+<something>");
                return;
            } else {
                codePoint = Character.toCodePoint(input.charAt(1), c);
            }
        }
        shortcuts.add(new KeyboardShortcut(codePoint, shortcutAction));
    }

}
