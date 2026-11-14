package android.util;

/** Host-side Log: android.jar only throws "Stub!", but the adapter logs while it mounts. */
public final class Log {
    public static int v(String tag, String message) { return 0; }
    public static int d(String tag, String message) { return 0; }
    public static int i(String tag, String message) { return 0; }
    public static int w(String tag, String message) { return 0; }
    public static int e(String tag, String message) { return 0; }
    public static int e(String tag, String message, Throwable error) { return 0; }
}
