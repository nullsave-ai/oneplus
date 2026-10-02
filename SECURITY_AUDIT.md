# مراجعة أمنية — وان+ (OnePlus)

**النطاق:** مراجعة ساكنة (قراءة الكود + فحص نمطي بـ grep للـ APIs الخطرة) على كامل المصدر والـ Manifest.
**لم يتم:** بناء التطبيق أو اختبار ديناميكي (لا يوجد Android SDK/Gradle في بيئة المراجعة).

## سطح الهجوم الفعلي
- لا توجد صلاحيات (`uses-permission`) ولا `INTERNET`.
- مكوّن واحد مُصدَّر فقط: `MainActivity` (المُشغِّل). لا Service / Receiver / Provider / Deep links.
- لا WebView، لا SQLite، لا كود native، لا تنفيذ أوامر، لا reflection، لا تحميل كود ديناميكي.
- التخزين الوحيد: `SharedPreferences` خاص بالتطبيق (إعدادات المظهر).

## النتيجة حسب القائمة

| # | الضعف | CWE | الحالة | السبب |
|---|-------|-----|--------|-------|
| 1 | Missing Authentication for Critical Function | 306 | غير منطبق | لا توجد وظائف حرجة ولا خادم |
| 2 | Improper Privilege Management | 269 | سليم | صفر صلاحيات، صفر مكوّنات مُصدَّرة سوى المُشغِّل |
| 3 | OS Command Injection | 78 | سليم | لا `Runtime.exec` / `ProcessBuilder` |
| 4 | Code Injection | 94* | سليم | لا eval / JS / DexClassLoader / reflection |
| 5 | SSRF | 918 | غير منطبق حاليًا | لا كود شبكة؛ `usesCleartextTraffic=false` مضبوط مسبقًا |
| 6 | Command Injection | 77 | سليم | كما في (3) |
| 7 | Missing Authorization | 862 | غير منطبق | لا موارد محمية؛ لا تُقرأ Intent extras |
| 8 | Incorrect Authorization | 863 | غير منطبق | كما في (7) |
| 9 | Improper Authentication | 287 | غير منطبق | لا تسجيل دخول |
| 10 | Trust Boundary Violation | 501 | سليم + تحصين | انظر أدناه |
| 11 | Session Fixation | 384 | غير منطبق | لا جلسات / توكنات / كوكيز |
| 12 | SQL Injection | 89 | سليم | لا قاعدة بيانات؛ البحث `in` على نصوص في الذاكرة |
| 13 | Buffer Overflow | 120 | سليم | Kotlin/JVM آمن الذاكرة، بلا JNI |
| 14 | XSS | 79 | سليم | لا WebView ولا HTML؛ النصوص تُرسم بـ BasicText |

\* في القائمة المرسلة كُتب Code Injection بالرقم 78؛ الرقم الصحيح CWE-94 (و78 هو OS Command Injection).

## تحصينات طُبّقت في هذه النسخة
- `android:allowBackup="false"` — منع استخراج بيانات التطبيق عبر النسخ الاحتياطي.
- `android:usesCleartextTraffic="false"` — سياسة HTTPS-only معلنة قبل ربط الـ API.
- **حدود الثقة (CWE-501):** قيم `SharedPreferences` تُعامل كمدخلات غير موثوقة: أسماء enum غير المعروفة، الأنواع الخاطئة، NaN/Infinity، والقيم خارج النطاق ترجع للافتراضي (`ThemeStore.load`).
- نص البحث محدود بـ 64 حرفًا.
- معرّف الفيلم يُتحقق منه بالبحث في الكتالوج؛ معرّف غير موجود يُغلق الصفحة بدل أن يُسبب كراشًا.
- R8 + shrinkResources مفعّلان في release؛ لا تسجيل (Log) في الكود.

## عند ربط الـ API الحقيقي (مطلوب وقتها)
1. HTTPS فقط + قائمة سماح (allowlist) للمضيفين، وعدم قبول عناوين URL من الخادم دون تحقق (SSRF/Open redirect).
2. التحقق من JSON (schema، أطوال، أنواع) قبل الاستخدام؛ لا ثقة بحقول الخادم.
3. أي توكن/مفتاح يُخزَّن في Android Keystore وليس SharedPreferences ولا في الكود.
4. إن أُضيف مشغّل فيديو بـ WebView: تعطيل JavaScript وfile access، وعدم استخدام `addJavascriptInterface`.
5. تجنّب تسجيل أي بيانات حساسة، وتحديث الاعتماديات دوريًا (OWASP dependency-check).
