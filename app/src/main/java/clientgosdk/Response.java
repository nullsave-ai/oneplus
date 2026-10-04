package clientgosdk;

import go.Seq;
import java.util.Arrays;

/* JADX INFO: loaded from: classes.dex */
public final class Response implements Seq.Proxy {
    private final int refnum;

    static {
        Clientgosdk.touch();
    }

    public Response(int i6) {
        this.refnum = i6;
        Seq.trackGoRef(i6, this);
    }

    private static native int __New();

    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof Response)) {
            return false;
        }
        Response response = (Response) obj;
        if (getStatusCode() != response.getStatusCode()) {
            return false;
        }
        String header = getHeader();
        String header2 = response.getHeader();
        if (header == null) {
            if (header2 != null) {
                return false;
            }
        } else if (!header.equals(header2)) {
            return false;
        }
        byte[] body = getBody();
        byte[] body2 = response.getBody();
        if (body == null) {
            return body2 == null;
        }
        return body.equals(body2);
    }

    public final native byte[] getBody();

    public final native String getHeader();

    public final native long getStatusCode();

    public int hashCode() {
        return Arrays.hashCode(new Object[]{Long.valueOf(getStatusCode()), getHeader(), getBody()});
    }

    @Override // go.Seq.GoObject
    public final int incRefnum() {
        Seq.incGoRef(this.refnum, this);
        return this.refnum;
    }

    public final native void setBody(byte[] bArr);

    public final native void setHeader(String str);

    public final native void setStatusCode(long j10);

    public String toString() {
        return "Response{StatusCode:" + getStatusCode() + ",Header:" + getHeader() + ",Body:" + getBody() + ",}";
    }

    public Response() {
        int i__New = __New();
        this.refnum = i__New;
        Seq.trackGoRef(i__New, this);
    }
}
