package com.fstudio.arkmenu;

import android.content.Context;
import android.content.Intent;

public final class NativeBridge {
    private static boolean loaded;
    static {
        try { System.loadLibrary("arkmenu"); loaded = true; }
        catch (Throwable t) { loaded = false; }   // no native lib: fall back to the broadcast path
    }
    private NativeBridge() {}

    private static native void execNative(String command);
    private static native boolean isReadyNative();

    public static boolean isReady() { return loaded && isReadyNative(); }

    /** Preferred path: native hook. Fallback: UE4's own ConsoleCmdReceiver (action android.intent.action.RUN,
     *  extra "cmd"), which is present in the APK you uploaded. Whether a Shipping build actually executes it
     *  must be tested on your device; no error is raised if the engine ignores it. */
    public static void exec(Context ctx, String command) {
        if (isReady()) execNative(command);
        else ctx.sendBroadcast(new Intent("android.intent.action.RUN").putExtra("cmd", command));
    }
}
