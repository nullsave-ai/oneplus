package go;

import android.content.Context;
import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.logging.Logger;

/* JADX INFO: loaded from: classes.dex */
public class Seq {
    private static final int NULL_REFNUM = 41;
    static final RefTracker tracker;
    private static Logger log = Logger.getLogger("GoSeq");
    public static final Ref nullRef = new Ref(41, null);
    private static final GoRefQueue goRefQueue = new GoRefQueue();

    public interface GoObject {
        int incRefnum();
    }

    public static class GoRef extends PhantomReference<GoObject> {
        final int refnum;

        public GoRef(int i6, GoObject goObject, GoRefQueue goRefQueue) {
            super(goObject, goRefQueue);
            if (i6 > 0) {
                throw new RuntimeException("GoRef instantiated with a Java refnum " + i6);
            }
            this.refnum = i6;
        }
    }

    public static class GoRefQueue extends ReferenceQueue<GoObject> {
        private final Collection<GoRef> refs = Collections.synchronizedCollection(new HashSet());

        public GoRefQueue() {
            Thread thread = new Thread(new Runnable() { // from class: go.Seq.GoRefQueue.1
                @Override // java.lang.Runnable
                public void run() {
                    while (true) {
                        try {
                            GoRef goRef = (GoRef) GoRefQueue.this.remove();
                            GoRefQueue.this.refs.remove(goRef);
                            Seq.destroyRef(goRef.refnum);
                            goRef.clear();
                        } catch (InterruptedException unused) {
                        }
                    }
                }
            });
            thread.setDaemon(true);
            thread.setName("GoRefQueue Finalizer Thread");
            thread.start();
        }

        public void track(int i6, GoObject goObject) {
            this.refs.add(new GoRef(i6, goObject, this));
        }
    }

    public interface Proxy extends GoObject {
    }

    public static final class Ref {
        public final Object obj;
        private int refcnt;
        public final int refnum;

        public Ref(int i6, Object obj) {
            if (i6 < 0) {
                throw new RuntimeException("Ref instantiated with a Go refnum " + i6);
            }
            this.refnum = i6;
            this.refcnt = 0;
            this.obj = obj;
        }

        public void inc() {
            int i6 = this.refcnt;
            if (i6 == Integer.MAX_VALUE) {
                throw new RuntimeException("refnum " + this.refnum + " overflow");
            }
            this.refcnt = i6 + 1;
        }
    }

    public static final class RefMap {
        private int next = 0;
        private int live = 0;
        private int[] keys = new int[16];
        private Ref[] objs = new Ref[16];

        private void grow() {
            Ref[] refArr;
            int iRoundPow2 = roundPow2(this.live) * 2;
            int[] iArr = this.keys;
            if (iRoundPow2 > iArr.length) {
                iArr = new int[iArr.length * 2];
                refArr = new Ref[this.objs.length * 2];
            } else {
                refArr = this.objs;
            }
            int i6 = 0;
            int i10 = 0;
            while (true) {
                int[] iArr2 = this.keys;
                if (i6 >= iArr2.length) {
                    break;
                }
                Ref ref = this.objs[i6];
                if (ref != null) {
                    iArr[i10] = iArr2[i6];
                    refArr[i10] = ref;
                    i10++;
                }
                i6++;
            }
            for (int i11 = i10; i11 < iArr.length; i11++) {
                iArr[i11] = 0;
                refArr[i11] = null;
            }
            this.keys = iArr;
            this.objs = refArr;
            this.next = i10;
            if (this.live == i10) {
                return;
            }
            throw new RuntimeException("bad state: live=" + this.live + ", next=" + this.next);
        }

        private static int roundPow2(int i6) {
            int i10 = 1;
            while (i10 < i6) {
                i10 *= 2;
            }
            return i10;
        }

        public Ref get(int i6) {
            int iBinarySearch = Arrays.binarySearch(this.keys, 0, this.next, i6);
            if (iBinarySearch >= 0) {
                return this.objs[iBinarySearch];
            }
            return null;
        }

        public void put(int i6, Ref ref) {
            if (ref == null) {
                throw new RuntimeException("put a null ref (with key " + i6 + ")");
            }
            int iBinarySearch = Arrays.binarySearch(this.keys, 0, this.next, i6);
            if (iBinarySearch >= 0) {
                Ref[] refArr = this.objs;
                if (refArr[iBinarySearch] == null) {
                    refArr[iBinarySearch] = ref;
                    this.live++;
                }
                if (refArr[iBinarySearch] != ref) {
                    throw new RuntimeException("replacing an existing ref (with key " + i6 + ")");
                }
                return;
            }
            if (this.next >= this.keys.length) {
                grow();
                iBinarySearch = Arrays.binarySearch(this.keys, 0, this.next, i6);
            }
            int i10 = ~iBinarySearch;
            int i11 = this.next;
            if (i10 < i11) {
                int[] iArr = this.keys;
                int i12 = i10 + 1;
                System.arraycopy(iArr, i10, iArr, i12, i11 - i10);
                Ref[] refArr2 = this.objs;
                System.arraycopy(refArr2, i10, refArr2, i12, this.next - i10);
            }
            this.keys[i10] = i6;
            this.objs[i10] = ref;
            this.live++;
            this.next++;
        }

        public void remove(int i6) {
            int iBinarySearch = Arrays.binarySearch(this.keys, 0, this.next, i6);
            if (iBinarySearch >= 0) {
                Ref[] refArr = this.objs;
                if (refArr[iBinarySearch] != null) {
                    refArr[iBinarySearch] = null;
                    this.live--;
                }
            }
        }
    }

    public static final class RefTracker {
        private static final int REF_OFFSET = 42;
        private int next = 42;
        private final RefMap javaObjs = new RefMap();
        private final IdentityHashMap<Object, Integer> javaRefs = new IdentityHashMap<>();

        public synchronized void dec(int i6) {
            try {
                if (i6 <= 0) {
                    Seq.log.severe("dec request for Go object " + i6);
                    return;
                }
                if (i6 == Seq.nullRef.refnum) {
                    return;
                }
                Ref ref = this.javaObjs.get(i6);
                if (ref == null) {
                    throw new RuntimeException("referenced Java object is not found: refnum=" + i6);
                }
                ref.refcnt--;
                if (ref.refcnt <= 0) {
                    this.javaObjs.remove(i6);
                    this.javaRefs.remove(ref.obj);
                }
            } catch (Throwable th) {
                throw th;
            }
        }

        public synchronized Ref get(int i6) {
            try {
                if (i6 < 0) {
                    throw new RuntimeException("ref called with Go refnum " + i6);
                }
                if (i6 == 41) {
                    return Seq.nullRef;
                }
                Ref ref = this.javaObjs.get(i6);
                if (ref != null) {
                    return ref;
                }
                throw new RuntimeException("unknown java Ref: " + i6);
            } catch (Throwable th) {
                throw th;
            }
        }

        public synchronized int inc(Object obj) {
            if (obj == null) {
                return 41;
            }
            if (obj instanceof Proxy) {
                return ((Proxy) obj).incRefnum();
            }
            Integer numValueOf = this.javaRefs.get(obj);
            if (numValueOf == null) {
                int i6 = this.next;
                if (i6 == Integer.MAX_VALUE) {
                    throw new RuntimeException("createRef overflow for " + obj);
                }
                this.next = i6 + 1;
                numValueOf = Integer.valueOf(i6);
                this.javaRefs.put(obj, numValueOf);
            }
            int iIntValue = numValueOf.intValue();
            Ref ref = this.javaObjs.get(iIntValue);
            if (ref == null) {
                ref = new Ref(iIntValue, obj);
                this.javaObjs.put(iIntValue, ref);
            }
            ref.inc();
            return iIntValue;
        }

        public synchronized void incRefnum(int i6) {
            Ref ref = this.javaObjs.get(i6);
            if (ref == null) {
                throw new RuntimeException("referenced Java object is not found: refnum=" + i6);
            }
            ref.inc();
        }
    }

    static {
        System.loadLibrary("gojni");
        init();
        Universe.touch();
        tracker = new RefTracker();
    }

    private Seq() {
    }

    public static void decRef(int i6) {
        tracker.dec(i6);
    }

    public static native void destroyRef(int i6);

    public static Ref getRef(int i6) {
        return tracker.get(i6);
    }

    public static int incGoObjectRef(GoObject goObject) {
        return goObject.incRefnum();
    }

    public static native void incGoRef(int i6, GoObject goObject);

    public static int incRef(Object obj) {
        return tracker.inc(obj);
    }

    public static void incRefnum(int i6) {
        tracker.incRefnum(i6);
    }

    private static native void init();

    public static void setContext(Context context) {
        setContext((Object) context);
    }

    public static native void setContext(Object obj);

    public static void touch() {
    }

    public static void trackGoRef(int i6, GoObject goObject) {
        if (i6 > 0) {
            throw new RuntimeException("trackGoRef called with Java refnum " + i6);
        }
        goRefQueue.track(i6, goObject);
    }
}
