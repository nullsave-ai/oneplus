package clientgosdk;

import go.Seq;
import java.util.Arrays;

/* JADX INFO: loaded from: classes.dex */
public final class Request implements Seq.Proxy {
    private final int refnum;

    static {
        Clientgosdk.touch();
    }

    public Request(int i6) {
        this.refnum = i6;
        Seq.trackGoRef(i6, this);
    }

    private static native int __New();

    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof Request)) {
            return false;
        }
        Request request = (Request) obj;
        String method = getMethod();
        String method2 = request.getMethod();
        if (method == null) {
            if (method2 != null) {
                return false;
            }
        } else if (!method.equals(method2)) {
            return false;
        }
        String header = getHeader();
        String header2 = request.getHeader();
        if (header == null) {
            if (header2 != null) {
                return false;
            }
        } else if (!header.equals(header2)) {
            return false;
        }
        String uri = getUri();
        String uri2 = request.getUri();
        if (uri == null) {
            if (uri2 != null) {
                return false;
            }
        } else if (!uri.equals(uri2)) {
            return false;
        }
        byte[] body = getBody();
        byte[] body2 = request.getBody();
        if (body == null) {
            if (body2 != null) {
                return false;
            }
        } else if (!body.equals(body2)) {
            return false;
        }
        return getTimeout() == request.getTimeout();
    }

    public final native byte[] getBody();

    public final native String getHeader();

    public final native String getMethod();

    public final native long getTimeout();

    public final native String getUri();

    public int hashCode() {
        return Arrays.hashCode(new Object[]{getMethod(), getHeader(), getUri(), getBody(), Long.valueOf(getTimeout())});
    }

    @Override // go.Seq.GoObject
    public final int incRefnum() {
        Seq.incGoRef(this.refnum, this);
        return this.refnum;
    }

    public final native void setBody(byte[] bArr);

    public final native void setHeader(String str);

    public final native void setMethod(String str);

    public final native void setTimeout(long j10);

    public final native void setUri(String str);

    public String toString() {
        return "Request{Method:" + getMethod() + ",Header:" + getHeader() + ",Uri:" + getUri() + ",Body:" + getBody() + ",Timeout:" + getTimeout() + ",}";
    }

    public Request() {
        int i__New = __New();
        this.refnum = i__New;
        Seq.trackGoRef(i__New, this);
    }
}
