package com.gathin.tvhome;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Narrow framework Home signals. No app allowlist and no raw log retention. */
final class HomeSignal {
    static final int NONE = 0, SYSTEM_HOME = 1, APP_HOME = 2, RESUMED_HOME = 3,
            EXTERNAL_RESUME = 4, SETTINGS_RESUME = 5, TVHOME_RESUME = 6, EXTERNAL_START = 7;
    private static final String HOME = "com\\.amazon\\.tv\\.launcher/(?:com\\.amazon\\.tv\\.launcher)?\\.ui\\.HomeActivity(?:_[A-Za-z0-9]+)?";
    private static final Pattern COMPONENT = Pattern.compile("\\bcmp=" + HOME + "(?=[\\s},\\]])");
    private static final Pattern CATEGORY = Pattern.compile("android\\.intent\\.category\\.HOME(?=[ \\],])");
    private static final Pattern UID = Pattern.compile("\\bfrom uid (\\d+)(?=\\s|$)");
    private static final Pattern RESUME_GENERIC = Pattern.compile("^I/(?:am_resume_activity|wm_resume_activity)\\s*\\(\\s*\\d+\\):\\s*\\[0,\\d+,\\d+,([^\\]]+)\\]\\s*$");
    private static final Pattern CMP_EXTRACT = Pattern.compile("\\bcmp=([^\\s},\\]]+)");

    static int kind(String line) {
        Matcher rm = RESUME_GENERIC.matcher(line);
        if (rm.matches()) {
            String target = rm.group(1);
            if (target.contains("com.amazon.tv.launcher") && target.contains("HomeActivity")) {
                return RESUMED_HOME;
            }
            if (target.contains("com.amazon.tv.settings") || target.contains("SettingsActivity") || target.contains("quicksettings")) {
                return SETTINGS_RESUME;
            }
            if (target.contains("com.gathin.tvhome")) {
                return TVHOME_RESUME;
            }
            return EXTERNAL_RESUME;
        }

        if (line.startsWith("I/ActivityManager") || line.startsWith("I/ActivityTaskManager")) {
            if (line.contains("START u0 ")) {
                if (CATEGORY.matcher(line).find() && COMPONENT.matcher(line).find()) {
                    Matcher uid = UID.matcher(line);
                    if (!uid.find()) return NONE;
                    try {
                        int value = Integer.parseInt(uid.group(1));
                        return value == 1000 || value == 0 ? SYSTEM_HOME : APP_HOME;
                    } catch (NumberFormatException ignored) {
                        return NONE;
                    }
                }
                Matcher cm = CMP_EXTRACT.matcher(line);
                if (cm.find()) {
                    String cmp = cm.group(1);
                    if (cmp.contains("com.amazon.tv.settings") || cmp.contains("SettingsActivity") || cmp.contains("quicksettings")) {
                        return SETTINGS_RESUME;
                    }
                    if (cmp.contains("com.gathin.tvhome")) {
                        return NONE;
                    }
                    if (!cmp.contains("com.amazon.tv.launcher")) {
                        return EXTERNAL_START;
                    }
                }
            }
        }
        return NONE;
    }

    static boolean isHome(int kind) {
        return kind == SYSTEM_HOME || kind == APP_HOME || kind == RESUMED_HOME;
    }

    static boolean matches(String line) {
        return isHome(kind(line));
    }

    static boolean shouldRedirect(int kind, boolean launching, boolean settingsGrace, boolean bypass) {
        return isHome(kind) && !launching && (kind == SYSTEM_HOME || (!settingsGrace && !bypass));
    }
}
