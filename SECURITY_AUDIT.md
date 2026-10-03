# مراجعة أمنية — وان+ (OnePlus)

**النطاق:** مراجعة ساكنة (قراءة الكود + فحص نمطي للـ APIs الخطرة) على كامل المصدر والـ Manifest.
**لم يتم:** بناء التطبيق أو اختبار ديناميكي (لا يوجد Android SDK/Gradle في بيئة المراجعة).

## سطح الهجوم الفعلي (بعد إضافة المشغّل)
- صلاحية واحدة: `INTERNET` (للمشغّل فقط). لا صلاحيات أخرى؛ السطوع/الصوت يُضبطان بدونها.
- مكوّن واحد مُصدَّر: `MainActivity` (المُشغِّل). لا Service / Receiver / Provider / Deep links.
- لا WebView، لا SQLite، لا كود native، لا تنفيذ أوامر، لا reflection، لا تحميل كود ديناميكي.
- الشبكة: عبر Media3 (`DefaultHttpDataSource`) فقط. لا HTTP client آخر، ولا `DefaultDataSource` (الذي يفتح file/content).
- التخزين: `SharedPreferences` (المظهر) + كاش فيديو LRU بحد 64MB داخل `cacheDir` (أفلام MP4 فقط).

## النتيجة حسب القائمة

| # | الضعف | CWE | الحالة | السبب |
|---|-------|-----|--------|-------|
| 1 | Missing Authentication for Critical Function | 306 | غير منطبق | لا وظائف حرجة ولا خادم خاص بالتطبيق |
| 2 | Improper Privilege Management | 269 | سليم | صلاحية INTERNET فقط، ولا مكوّنات مُصدَّرة سوى المُشغِّل |
| 3 | OS Command Injection | 78 | سليم | لا `Runtime.exec` / `ProcessBuilder` |
| 4 | Code Injection | 94* | سليم | لا eval / JS / DexClassLoader / reflection |
| 5 | SSRF | 918 | مخفَّف | روابط التشغيل تأتي من الكتالوج (بيانات غير موثوقة): يُقبل `http/https` مع host فقط (`PlaySource.toUriOrNull`)؛ يُرفض file/content/data/android.resource وغيرها |
| 6 | Command Injection | 77 | سليم | كما في (3) |
| 7 | Missing Authorization | 862 | غير منطبق | لا موارد محمية؛ لا تُقرأ Intent extras |
| 8 | Incorrect Authorization | 863 | غير منطبق | كما في (7) |
| 9 | Improper Authentication | 287 | غير منطبق | لا تسجيل دخول |
| 10 | Trust Boundary Violation | 501 | سليم + تحصين | قيم SharedPreferences تُتحقق عند القراءة؛ بحث ≤ 64 حرفًا؛ المشغّل يحفظ **معرّفًا** فقط ويستخرج الرابط من الكتالوج |
| 11 | Session Fixation | 384 | غير منطبق | لا جلسات / توكنات / كوكيز |
| 12 | SQL Injection | 89 | سليم | لا قاعدة بيانات (قاعدة Media3 الداخلية للكاش لا تتلقى مدخلات) |
| 13 | Buffer Overflow | 120 | سليم | Kotlin/JVM آمن الذاكرة، بلا JNI |
| 14 | XSS | 79 | سليم | لا WebView ولا HTML؛ النصوص تُرسم بـ BasicText |

\* في القائمة المرسلة كُتب Code Injection بالرقم 78؛ الرقم الصحيح CWE-94 (و78 هو OS Command Injection).

## قرار مقصود: `usesCleartextTraffic="true"`
روابط IPTV/الأفلام المباشرة كثيرًا ما تكون `http://`، وحظر HTTP يكسر التشغيل. المخاطرة: قابلية اعتراض/تعديل الفيديو في الشبكات غير الموثوقة (لا تأثير على بيانات اعتماد لأن التطبيق لا يرسل أيًا منها).
عند إضافة API خاص بالتطبيق: استخدم HTTPS فقط في الكود، ويمكن حينها استبدال هذا الخيار بـ `networkSecurityConfig` يحصر HTTP بنطاقات البث.

## تحصينات في هذه النسخة
- `allowBackup="false"`.
- التحقق من مخطط الرابط قبل إنشاء المشغّل، ورابط غير صالح يعرض رسالة بدل المحاولة.
- الكاش للـ MP4 فقط وبحد أقصى 64MB؛ البث المباشر وHLS/DASH لا يكتبون في الكاش.
- تحرير فوري: يُحرَّر ExoPlayer بمجرد مغادرة الشاشة، ويُوقَف مؤقتًا عند ON_STOP.
- R8 + shrinkResources في release؛ لا تسجيل (Log) في الكود.

## قبل الإنتاج
1. استبدل روابط الاختبار في `Data.kt` بروابط الكتالوج الحقيقية.
2. أي توكن/مفتاح يُخزَّن في Android Keystore وليس SharedPreferences ولا في الكود.
3. تحقق من JSON القادم من الخادم (أطوال/أنواع) قبل استخدامه.
4. حدّث الاعتماديات دوريًا (OWASP dependency-check). إصدار Media3 مثبّت على 1.5.1 لأن minSdk=21.
