package clientgosdk;

import go.Seq;

/* JADX INFO: loaded from: /data/data/com.termux/files/home/.gemini/antigravity-cli/brain/79dbab01-4cf8-403d-91a8-7f97cc5a085b/scratch/classes.dex */
public abstract class Clientgosdk {
    public static final long InitCodeLoadFailed = 10;

    static {
        Seq.touch();
        _init();
    }

    private Clientgosdk() {
    }

    private static native void _init();

    public static native long cacheTime(String str);

    public static native boolean clearToken();

    public static native void close();

    public static native String getDownloadUrl(String str, String str2);

    public static native long getLiveChannelsCacheFileTime();

    public static native String getLivePlayUrl(long j7);

    public static native String getLivePlayUrlByTurboUrl(String str);

    public static native String getPlayUrl(String str, String str2);

    public static native Response httpProxy(Request request);

    public static native long initSdk(String str, String str2, String str3, String str4, String str5);

    public static native void setAppInfo(String str, long j7);

    public static native void setFileInfo(String str, String str2);

    public static native void setMethod(String str);

    public static native void setProxyCacheTTL(String str, long j7);

    public static void touch() {
    }

    public static native String version();
}
