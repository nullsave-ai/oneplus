# صيغة الروابط في المشغّل

أي رابط يُشغَّل كما هو. الخيارات الإضافية تُكتب بعد `|` وتُفصل بـ `&`:

```
الرابط|User-Agent=VLC/3.0&Referer=https://site.com&Cookie=a=b
```

| الخيار | المعنى |
|---|---|
| `User-Agent` / `Referer` / `Origin` / `Cookie` / أي ترويسة أخرى | تُرسل مع طلبات الفيديو والمفاتيح والترخيص |
| `drmScheme` | `widevine` أو `playready` أو `clearkey` (الاسم القديم `license_type`) |
| `drmLicense` | رابط خادم الترخيص، وفي clearkey يمكن أن يكون المفاتيح نفسها (الاسم القديم `license_key`) |

## أمثلة

**Widevine**
```
https://cdn.example.com/a/manifest.mpd|drmScheme=widevine&drmLicense=https://lic.example.com/wv
```

**ClearKey بمفاتيح داخل الرابط** (hex، 32 خانة لكل من kid وkey، ويمكن أكثر من زوج بفاصلة)
```
https://cdn.example.com/a/manifest.mpd|drmScheme=clearkey&drmLicense=0123456789abcdef0123456789abcdef:fedcba9876543210fedcba9876543210
```

**ClearKey من خادم**
```
https://cdn.example.com/a/manifest.mpd|drmScheme=clearkey&drmLicense=https://lic.example.com/ck
```

**User-Agent فقط (بث مباشر)**
```
https://alkatlanhd.xmax1tv.com/live/2.m3u8|User-Agent=Mozilla/5.0
```

## ملاحظات
- إن احتوت قيمة على `&` أو `|` فاكتبها مشفّرة (`%26` و`%7C`).
- المخططات المسموحة: `http` و`https` و`rtsp` و`rtsps` فقط.
- رابط DRM غير صالح (مخطط غير معروف، مفتاح ليس 32 خانة hex، ترخيص غير http) يُرفض ويظهر "رابط التشغيل غير صالح".
- التشفير AES-128 في HLS يعمل تلقائيًا ويستعمل نفس الترويسات.
- الصيغ: MP4/MKV/WebM/TS/FLV/MP3/AAC/OGG/FLAC/WAV، وHLS، وDASH، وSmooth Streaming، وRTSP.
- منشور على X (`x.com/.../status/ID`): يُجلب فيديوه تلقائيًا. بثوث X الحية (`/i/broadcasts/`) غير مدعومة.
- الأمثلة أعلاه توضيحية؛ المفاتيح والمضيفات ليست حقيقية.
