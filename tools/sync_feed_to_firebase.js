const fs = require('fs');
const crypto = require('crypto');
const path = require('path');

const SA_PATH = process.env.FIREBASE_SA || '/storage/emulated/0/Filterlanguage/apklive-firebase-adminsdk-rfuda-fe97200747.json';
const PANEL_DATA_PATH = '/storage/emulated/0/Filterlanguage/panel/data.json';

const BLOCKED_KEYWORDS = [
  'stipsisyer', 'stepsister', 'step-sister', 'step sister', 'stepmom', 'step mom',
  'stepdaughter', 'stepson', 'brother', 'stepbrother', 'بروذر', 'أخت', 'اخت',
  'محارم', 'سكس', 'إباحي', 'اباحي', 'جنسي', 'جنس', 'للكبار فقط', 'بورن',
  'تعري', 'عري', '+18', '18+', 'r-18', 'erotic', 'erotica', 'porn', 'porno',
  'pornography', 'xxx', 'adult only', 'adult', 'nudity', 'nsfw', 'sensual',
  'softcore', 'hardcore', 'sex', 'sexual', 'taboo', 'incest', 'midnight', 'ميدنايت',
  'gay', 'lesbian', 'homosexual', 'queer', 'transgender', 'bisexual',
  'boys love', 'bl drama', 'شذوذ', 'مثلي', 'شاذ', 'قوس قزح',
  'lust', 'lover', 'lovers', 'affair', 'nude', 'naked', 'strip', 'prostitute', 'hooker',
  'شهوة', 'عاهرة', 'دعارة', 'زنا', 'خيانة زوجية'
];

const GENRE_MAP = {
  'action': 'أكشن',
  'drama': 'دراما',
  'crime': 'جريمة',
  'thriller': 'إثارة',
  'horror': 'رعب',
  'comedy': 'كوميديا',
  'sci-fi': 'خيال علمي',
  'science fiction': 'خيال علمي',
  'adventure': 'مغامرة',
  'family': 'عائلي',
  'animation': 'رسوم متحركة',
  'fantasy': 'فانتازيا',
  'mystery': 'غموض',
  'war': 'حرب',
  'history': 'تاريخي',
  'biography': 'سيرة ذاتية',
  'music': 'موسيقى',
  'romance': 'رومانسي',
  'western': 'غربي'
};

const HEADERS = {
  'User-Agent': 'Mozilla/5.0 (Linux; Android 15; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36',
  'x-tr-devtype': 'h5',
  'x-tr-region': 'CN',
  'x-md-global-color': 'lane4',
  'Accept': 'application/json, text/plain, */*'
};

function isContentBlocked(...texts) {
  for (const t of texts) {
    if (!t) continue;
    const lower = String(t).toLowerCase();
    for (const kw of BLOCKED_KEYWORDS) {
      if (lower.includes(kw)) return true;
    }
  }
  return false;
}

function mapGenres(genreStr) {
  if (!genreStr) return ['أفلام'];
  const list = genreStr.split(',').map(s => s.trim().toLowerCase());
  const mapped = list.map(g => GENRE_MAP[g] || g).filter(Boolean);
  return mapped.length > 0 ? mapped : ['أفلام'];
}

function parseDurationMinutes(dur) {
  if (!dur) return 100;
  let total = 0;
  const hMatch = dur.match(/(\d+)\s*h/i);
  const mMatch = dur.match(/(\d+)\s*m/i);
  if (hMatch) total += parseInt(hMatch[1], 10) * 60;
  if (mMatch) total += parseInt(mMatch[1], 10);
  return total > 0 ? total : 100;
}

async function getGoogleAccessToken(sa) {
  const now = Math.floor(Date.now() / 1000);
  const header = Buffer.from(JSON.stringify({ alg: 'RS256', typ: 'JWT' })).toString('base64url');
  const claim = Buffer.from(JSON.stringify({
    iss: sa.client_email,
    scope: 'https://www.googleapis.com/auth/userinfo.email https://www.googleapis.com/auth/firebase.database',
    aud: 'https://oauth2.googleapis.com/token',
    exp: now + 3600,
    iat: now
  })).toString('base64url');

  const sign = crypto.createSign('RSA-SHA256');
  sign.update(header + '.' + claim);
  const signature = sign.sign(sa.private_key, 'base64url');
  const jwt = header + '.' + claim + '.' + signature;

  const res = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: 'grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=' + jwt
  });
  const data = await res.json();
  if (!data.access_token) throw new Error('Failed to get token: ' + JSON.stringify(data));
  return data.access_token;
}

async function fetchSubjectList(page = 1, perPage = 20) {
  const form = new URLSearchParams();
  form.append('channelId', '1');
  form.append('subjectType', '1');
  form.append('page', String(page));
  form.append('perPage', String(perPage));

  const res = await fetch('https://h5.aoneroom.com/wefeed-h5-bff/mini/subject-list', {
    method: 'POST',
    headers: { ...HEADERS, 'Content-Type': 'application/x-www-form-urlencoded' },
    body: form.toString()
  });
  const json = await res.json();
  return json.data?.items || [];
}

async function fetchSubjectDetail(subjectId) {
  try {
    const res = await fetch(`https://h5.aoneroom.com/wefeed-h5-bff/mini/subject_detail?subjectId=${subjectId}`, {
      headers: HEADERS
    });
    const json = await res.json();
    return json.data || null;
  } catch {
    return null;
  }
}

async function collectMovies(targetCount = 40) {
  console.log(`[*] Harvesting movies from Media Catalog (Target: ${targetCount})...`);
  const collected = [];
  const seenIds = new Set();

  for (let page = 1; page <= 6 && collected.length < targetCount; page++) {
    console.log(`  -> Fetching page ${page}...`);
    const items = await fetchSubjectList(page, 20);
    if (!items || items.length === 0) break;

    for (const item of items) {
      if (collected.length >= targetCount) break;
      if (seenIds.has(item.subjectId)) continue;
      seenIds.add(item.subjectId);

      // 1. Strict Movie Type & Duration Check (feature film only)
      if (item.subjectType !== 1) continue;
      const durMin = parseDurationMinutes(item.duration);
      if (durMin < 50) continue; // must be a full feature movie

      // 2. Strict Content Block Check (Zero Tolerance for NSFW/Deviant terms)
      if (isContentBlocked(item.title, item.description, item.genre)) {
        console.log(`  [x] Blocked by content filter: ${item.title}`);
        continue;
      }

      // 3. Fetch full detail for direct streaming stream
      const detail = await fetchSubjectDetail(item.subjectId);
      if (!detail) continue;

      if (isContentBlocked(detail.title, detail.description, detail.genre)) {
        console.log(`  [x] Blocked detail by content filter: ${detail.title}`);
        continue;
      }

      const detector = detail.resourceDetectors?.[0];
      const resList = detector?.resolutionList || [];
      // Pick best available stream
      const sortedRes = [...resList].sort((a, b) => (b.resolution || 0) - (a.resolution || 0));
      const best = sortedRes[0];
      const streamUrl = best?.resourceLink || best?.sourceUrl || detector?.downloadUrl || detector?.resourceLink;

      if (!streamUrl || !streamUrl.startsWith('http')) continue;

      const poster = item.cover?.url || detail.cover?.url || '';
      if (!poster) continue;

      const year = parseInt(detail.releaseDate?.slice(0, 4) || item.releaseDate?.slice(0, 4) || '2024', 10);
      const rating = parseFloat(detail.imdbRatingValue || detail.score || item.score || '7.0') || 7.0;

      const id = collected.length + 1;
      collected.push({
        id,
        title: detail.title || item.title,
        year,
        rating,
        durationMin: durMin,
        genres: mapGenres(detail.genre || item.genre),
        synopsis: detail.description || item.description || 'فيلم مميز عالي الدقة بدون إعلانات.',
        director: 'إخراج سينمائي',
        cast: ['نجوم العمل'],
        url: streamUrl,
        backdrop: poster
      });

      console.log(`  [+] Approved: [${id}] ${detail.title} (${year}, ${durMin}m)`);
    }
  }

  return collected;
}

const DEFAULT_CHANNELS = [
  { id: 101, name: "beIN SPORTS 1 HD", group: "beIN SPORTS HD", number: 1, url: "https://partneta.cdn.mgmlcdn.com/omsport/smil:omsport.stream.smil/chunklist.m3u8", logo: "" },
  { id: 102, name: "beIN SPORTS 2 HD", group: "beIN SPORTS HD", number: 2, url: "https://partneta.cdn.mgmlcdn.com/omsport/smil:omsport.stream.smil/chunklist.m3u8", logo: "" },
  { id: 103, name: "beIN SPORTS 3 HD", group: "beIN SPORTS HD", number: 3, url: "https://partneta.cdn.mgmlcdn.com/omsport/smil:omsport.stream.smil/chunklist.m3u8", logo: "" },
  { id: 104, name: "beIN SPORTS 4 HD", group: "beIN SPORTS HD", number: 4, url: "https://live-stream.skynewsarabia.com/c-horizontal-channel/horizontal-stream/index.m3u8", logo: "" },
  { id: 105, name: "beIN SPORTS 5 HD", group: "beIN SPORTS HD", number: 5, url: "https://live.alarabiya.net/alarabiapublish/alarabiya.smil/playlist.m3u8", logo: "" },
  { id: 106, name: "beIN SPORTS 6 HD", group: "beIN SPORTS HD", number: 6, url: "https://live-hls-apps-ajm-fa.getaj.net/AJM/index.m3u8", logo: "" },
  { id: 107, name: "beIN SPORTS 7 HD", group: "beIN SPORTS HD", number: 7, url: "https://shd-gcp-live.edgenextcdn.net/live/bitmovin-mbc-1/15cf99af5de54063fdabfefe66adc075/index.m3u8", logo: "" },
  { id: 108, name: "beIN SPORTS 8 HD", group: "beIN SPORTS HD", number: 8, url: "https://shd-gcp-live.edgenextcdn.net/live/bitmovin-mbc-masr/956eac069c78a35d47245db6cdbb1575/index.m3u8", logo: "" },
  { id: 109, name: "beIN SPORTS 9 HD", group: "beIN SPORTS HD", number: 9, url: "https://shd-gcp-live.edgenextcdn.net/live/bitmovin-mbc-drama/2c28a458e2f3253e678b07ac7d13fe71/index.m3u8", logo: "" },
  { id: 110, name: "beIN SPORTS XTRA HD", group: "beIN SPORTS HD", number: 10, url: "https://bein-xtra-bein.amagi.tv/playlist.m3u8", logo: "" },
  { id: 201, name: "Alkass 1 HD", group: "Alkass Sports HD", number: 1, url: "https://kwtspta.cdn.mangomolo.com/sp/smil:sp.stream.smil/chunklist.m3u8", logo: "" },
  { id: 202, name: "Alkass 2 HD", group: "Alkass Sports HD", number: 2, url: "https://partneta.cdn.mgmlcdn.com/omsport/smil:omsport.stream.smil/chunklist.m3u8", logo: "" },
  { id: 203, name: "Alkass 4 HD", group: "Alkass Sports HD", number: 3, url: "https://bein-xtra-bein.amagi.tv/playlist.m3u8", logo: "" },
  { id: 301, name: "الكويت الرياضية HD", group: "قنوات رياضية وإخبارية", number: 1, url: "https://kwtspta.cdn.mangomolo.com/sp/smil:sp.stream.smil/chunklist.m3u8", logo: "" },
  { id: 302, name: "عمان الرياضية HD", group: "قنوات رياضية وإخبارية", number: 2, url: "https://partneta.cdn.mgmlcdn.com/omsport/smil:omsport.stream.smil/chunklist.m3u8", logo: "" },
  { id: 303, name: "الجزيرة الإخبارية HD", group: "قنوات رياضية وإخبارية", number: 3, url: "https://live-hls-apps-aja-fa.getaj.net/AJA/01.m3u8", logo: "" },
  { id: 304, name: "الجزيرة مباشر", group: "قنوات رياضية وإخبارية", number: 4, url: "https://live-hls-apps-ajm-fa.getaj.net/AJM/index.m3u8", logo: "" },
  { id: 305, name: "العربية HD", group: "قنوات رياضية وإخبارية", number: 5, url: "https://live.alarabiya.net/alarabiapublish/alarabiya.smil/playlist.m3u8", logo: "" },
  { id: 306, name: "سكاي نيوز عربية HD", group: "قنوات رياضية وإخبارية", number: 6, url: "https://live-stream.skynewsarabia.com/c-horizontal-channel/horizontal-stream/index.m3u8", logo: "" },
  { id: 401, name: "MBC 1 HD", group: "شبكة قنوات MBC", number: 1, url: "https://shd-gcp-live.edgenextcdn.net/live/bitmovin-mbc-1/15cf99af5de54063fdabfefe66adc075/index.m3u8", logo: "" },
  { id: 402, name: "MBC 4 HD", group: "شبكة قنوات MBC", number: 2, url: "https://shd-gcp-live.edgenextcdn.net/live/bitmovin-mbc-4/24f134f1cd63db9346439e96b86ca6ed/index.m3u8", logo: "" },
  { id: 403, name: "MBC مصر HD", group: "شبكة قنوات MBC", number: 3, url: "https://shd-gcp-live.edgenextcdn.net/live/bitmovin-mbc-masr/956eac069c78a35d47245db6cdbb1575/index.m3u8", logo: "" },
  { id: 404, name: "MBC مصر 2 HD", group: "شبكة قنوات MBC", number: 4, url: "https://shd-gcp-live.edgenextcdn.net/live/bitmovin-mbc-masr-2/754931856515075b0aabf0e583495c68/index.m3u8", logo: "" },
  { id: 405, name: "MBC دراما HD", group: "شبكة قنوات MBC", number: 5, url: "https://shd-gcp-live.edgenextcdn.net/live/bitmovin-mbc-drama/2c28a458e2f3253e678b07ac7d13fe71/index.m3u8", logo: "" },
  { id: 406, name: "MBC 5 HD", group: "شبكة قنوات MBC", number: 6, url: "https://shd-gcp-live.edgenextcdn.net/live/bitmovin-mbc-5/ee6b000cee0629411b666ab26cb13e9b/index.m3u8", logo: "" },
  { id: 407, name: "MBC العراق HD", group: "شبكة قنوات MBC", number: 7, url: "https://shd-gcp-live.edgenextcdn.net/live/bitmovin-mbc-iraq/e38c44b1b43474e1c39cb5b90203691e/index.m3u8", logo: "" },
  { id: 501, name: "أفلام أكشن Movies Action", group: "مسلسلات ومنوعات", number: 1, url: "https://shd-amg-fast.edgenextcdn.net/tx011/playlist.m3u8", logo: "" },
  { id: 502, name: "قناة أفلام Aflam HD", group: "مسلسلات ومنوعات", number: 2, url: "https://shd-amg-fast.edgenextcdn.net/tx001/playlist.m3u8", logo: "" },
  { id: 503, name: "قناة باب الحارة HD", group: "مسلسلات ومنوعات", number: 3, url: "https://shd-amg-fast.edgenextcdn.net/tx010/playlist.m3u8", logo: "" },
  { id: 504, name: "قناة مرايا HD", group: "مسلسلات ومنوعات", number: 4, url: "https://shd-amg-fast.edgenextcdn.net/tx008/playlist.m3u8", logo: "" },
  { id: 505, name: "الشرق ديسكفري HD", group: "مسلسلات ومنوعات", number: 5, url: "https://svs.itworkscdn.net/asharqdiscoverylive/asharqd.smil/playlist.m3u8", logo: "" },
  { id: 506, name: "الشرق الوثائقية HD", group: "مسلسلات ومنوعات", number: 6, url: "https://svs.itworkscdn.net/asharqdocumentarylive/asharqdocumentary/playlist.m3u8", logo: "" }
];

const DEFAULT_MATCHES = [
  { id: 1, time: "20:00", live: true, status: "مباشر", home: "الهلال", away: "النصر", competition: "دوري أبطال آسيا", channel: "beIN SPORTS 1 HD", day: 0 },
  { id: 2, time: "22:00", live: false, status: "قريبًا", home: "ريال مدريد", away: "برشلونة", competition: "الدوري الإسباني", channel: "beIN SPORTS 1 HD", day: 0 },
  { id: 3, time: "23:30", live: false, status: "قريبًا", home: "ليفربول", away: "مانشستر سيتي", competition: "الدوري الإنجليزي", channel: "beIN SPORTS 1 HD", day: 0 },
  { id: 4, time: "01:00", live: false, status: "قريبًا", home: "الأهلي", away: "الاتحاد", competition: "كأس السوبر", channel: "beIN SPORTS 2 HD", day: 0 },
  { id: 5, time: "21:00", live: false, status: "قريبًا", home: "يوفنتوس", away: "ميلان", competition: "الدوري الإيطالي", channel: "beIN SPORTS 2 HD", day: 0 },
  { id: 6, time: "22:45", live: false, status: "قريبًا", home: "بايرن ميونخ", away: "دورتموند", competition: "الدوري الألماني", channel: "beIN SPORTS 3 HD", day: 0 },
  { id: 7, time: "19:00", live: false, status: "قريبًا", home: "الشباب", away: "الفتح", competition: "دوري روشن", channel: "Alkass 1 HD", day: 1 },
  { id: 8, time: "21:30", live: false, status: "قريبًا", home: "أتلتيكو مدريد", away: "إشبيلية", competition: "الدوري الإسباني", channel: "beIN SPORTS 1 HD", day: 1 },
  { id: 9, time: "22:00", live: false, status: "قريبًا", home: "تشيلسي", away: "آرسنال", competition: "الدوري الإنجليزي", channel: "beIN SPORTS 2 HD", day: 1 },
  { id: 10, time: "23:00", live: false, status: "قريبًا", home: "إنتر", away: "نابولي", competition: "الدوري الإيطالي", channel: "beIN SPORTS 3 HD", day: 1 }
];

async function main() {
  console.log('[*] Initializing Media & Cloud Bridge...');
  const sa = JSON.parse(fs.readFileSync(SA_PATH, 'utf8'));
  const token = await getGoogleAccessToken(sa);
  console.log('[+] Authenticated with Firebase as:', sa.client_email);

  // 1. Harvest & filter movies
  const movies = await collectMovies(35);
  console.log(`[+] Harvested ${movies.length} clean feature movies.`);

  // 2. Read panel data if exists
  let channels = DEFAULT_CHANNELS;
  let matches = DEFAULT_MATCHES;
  try {
    if (fs.existsSync(PANEL_DATA_PATH)) {
      const panelData = JSON.parse(fs.readFileSync(PANEL_DATA_PATH, 'utf8'));
      if (Array.isArray(panelData.channels) && panelData.channels.length > 0) {
        channels = panelData.channels.map((c, i) => ({
          id: c.id || (i + 1),
          name: c.name || `قناة ${i + 1}`,
          url: c.url || '',
          group: c.group || 'عام',
          number: i + 1,
          logo: c.logo || ''
        }));
      }
      if (Array.isArray(panelData.matches) && panelData.matches.length > 0) {
        matches = panelData.matches;
      }
    }
  } catch (e) {
    console.warn('[!] Note reading panel data:', e.message);
  }

  const catalog = {
    matches,
    movies,
    channels,
    last_updated: Date.now()
  };

  const appConfig = {
    min_version_code: 1,
    latest_version_code: 2,
    update_url: "https://github.com/nullsave-ai/oneplus/releases/latest",
    update_title: "تحديث جديد متوفر",
    update_message: "يرجى تنزيل الإصدار الأحدث من تطبيق ONE+ لمتابعة المشاهدة بأعلى دقة وثبات وبدون انقطاع.",
    force_update: false
  };

  console.log('[*] Uploading catalog to Firebase Realtime Database...');
  const catRes = await fetch('https://apklive-default-rtdb.firebaseio.com/catalog.json?access_token=' + token, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(catalog)
  });
  console.log('[+] Catalog upload status:', catRes.status);

  console.log('[*] Uploading app configuration to Firebase...');
  const cfgRes = await fetch('https://apklive-default-rtdb.firebaseio.com/app_config.json?access_token=' + token, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(appConfig)
  });
  console.log('[+] App config upload status:', cfgRes.status);

  // 3. Save to panel data.json
  try {
    const localPanelData = {
      next: Math.max(...movies.map(m => m.id), ...channels.map(c => c.id), 100) + 1,
      matches,
      movies,
      channels,
      app_config: appConfig
    };
    fs.mkdirSync(path.dirname(PANEL_DATA_PATH), { recursive: true });
    fs.writeFileSync(PANEL_DATA_PATH, JSON.stringify(localPanelData, null, 2), 'utf8');
    console.log('[+] Local panel data.json updated at:', PANEL_DATA_PATH);
  } catch (e) {
    console.warn('[!] Failed to update local panel data:', e.message);
  }

  console.log('[✓] Done! Feed and configuration are now live in Firebase.');
}

main().catch(console.error);
