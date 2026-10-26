package com.finndog.justenoughstructures;

/**
 * Takes JSON5's trailing commas out of a file people may edit by hand, before Gson reads it. Gson
 * reads comments, but after the last member of an object a comma makes it throw, losing the whole
 * file, and after the last item in a list it reads one as an extra null. Commas in strings and
 * comments are left alone, and each comma taken out becomes a space, so the line and column Gson
 * gives for a real mistake still point at it.
 */
public final class TrailingCommas {
    private TrailingCommas() {
    }

    /** The text with every comma taken out that only whitespace and comments keep from a closing brace or bracket. */
    public static String strip(String json) {
        StringBuilder out = new StringBuilder(json.length());
        // Where in out the last comma is, while nothing but whitespace and comments has come after it.
        int comma = -1;
        int i = 0;
        while (i < json.length()) {
            char c = json.charAt(i);
            int end = skip(json, i);
            if (end > i) {
                // A string is something after the comma; a comment isn't.
                if (c == '"' || c == '\'') {
                    comma = -1;
                }
                out.append(json, i, end);
                i = end;
                continue;
            }
            if ((c == '}' || c == ']') && comma >= 0) {
                out.setCharAt(comma, ' ');
            }
            if (c == ',') {
                comma = out.length();
            } else if (!whitespace(c)) {
                comma = -1;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    /**
     * Where the string or comment that starts at {@code start} ends, or {@code start} if none does.
     * Strings are in double or single quotes, with backslash escapes. Comments are as Gson reads them:
     * {@code //} and {@code #} to the end of the line, and block comments to where they close.
     */
    private static int skip(String json, int start) {
        char c = json.charAt(start);
        char next = start + 1 < json.length() ? json.charAt(start + 1) : 0;
        if (c == '"' || c == '\'') {
            for (int i = start + 1; i < json.length(); i++) {
                char at = json.charAt(i);
                if (at == '\\') {
                    i++;
                } else if (at == c) {
                    return i + 1;
                }
            }
            return json.length();
        }
        if (c == '#' || (c == '/' && next == '/')) {
            int i = start;
            while (i < json.length() && json.charAt(i) != '\n' && json.charAt(i) != '\r') {
                i++;
            }
            return i;
        }
        if (c == '/' && next == '*') {
            int close = json.indexOf("*/", start + 2);
            return close < 0 ? json.length() : close + 2;
        }
        return start;
    }

    // The whitespace Gson skips between values.
    private static boolean whitespace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r';
    }
}
