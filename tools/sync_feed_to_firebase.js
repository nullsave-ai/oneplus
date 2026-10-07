const fs = require('fs');
const crypto = require('crypto');
const path = require('path');

const SA_PATH = process.env.FIREBASE_SA || '/storage/emulated/0/Filterlanguage/apklive-firebase-adminsdk-rfuda-fe97200747.json';
const PANEL_DATA_PATH = '/storage/emulated/0/Filterlanguage/panel/data.json';

// Strict adult / hentai / deviant content filters
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
  'شهوة', 'عاهرة', 'دعارة', 'زنا', 'خيانة زوجية',
  // Hentai & Deviant anime blocklist
  'hentai', 'ecchi', 'h-anime', 'yuri', 'yaoi', 'doujin', 'ero', 'ero-anime', '18+ anime',
  'هينتاي', 'ايتشي', 'ياوي', 'يوري', 'انمي اباحي', 'انحراف'
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

async function fetchSubjectList(channelId, page = 1, perPage = 20) {
  const form = new URLSearchParams();
  form.append('channelId', String(channelId));
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

// Harvest movies, series, and anime with Arabic subtitles from MovieBox
async function harvestMovieBoxCatalog(targetMovies = 60, targetSeries = 40, targetAnime = 30) {
  console.log(`[*] Harvesting extensive clean catalog from MovieBox...`);
  const collected = [];
  const seenIds = new Set();
  let currentId = 1;

  // 1. Feature Movies (channelId = 1)
  console.log(`  -> Harvesting Movies (Target: ${targetMovies})...`);
  for (let page = 1; page <= 12 && collected.filter(x => x.kind === 'film').length < targetMovies; page++) {
    const items = await fetchSubjectList(1, page, 20);
    if (!items || items.length === 0) break;

    for (const item of items) {
      if (collected.filter(x => x.kind === 'film').length >= targetMovies) break;
      if (seenIds.has(item.subjectId)) continue;
      seenIds.add(item.subjectId);

      const durMin = parseDurationMinutes(item.duration);
      if (durMin < 45) continue;
      if (isContentBlocked(item.title, item.description, item.genre)) continue;

      const detail = await fetchSubjectDetail(item.subjectId);
      if (!detail) continue;
      if (isContentBlocked(detail.title, detail.description, detail.genre)) continue;

      const detector = detail.resourceDetectors?.[0];
      const resList = detector?.resolutionList || [];
      const sortedRes = [...resList].sort((a, b) => (b.resolution || 0) - (a.resolution || 0));
      const best = sortedRes[0];
      let streamUrl = best?.resourceLink || best?.sourceUrl || detector?.downloadUrl || detector?.resourceLink;
      if (!streamUrl || !streamUrl.startsWith('http')) continue;

      // Extract Arabic subtitle if available
      const arCaption = detector?.extCaptions?.find(c => c.lan === 'ar' || c.lan === 'ara');
      if (arCaption?.url) {
        streamUrl = `${streamUrl}|sub_ar=${arCaption.url}`;
      }

      const poster = item.cover?.url || detail.cover?.url || '';
      if (!poster) continue;

      const year = parseInt(detail.releaseDate?.slice(0, 4) || item.releaseDate?.slice(0, 4) || '2024', 10);
      const rating = parseFloat(detail.imdbRatingValue || detail.score || item.score || '7.2') || 7.2;
      const isAnimation = (detail.genre || item.genre || '').toLowerCase().includes('animation');

      collected.push({
        id: currentId++,
        title: detail.title || item.title,
        year,
        rating,
        durationMin: durMin,
        genres: mapGenres(detail.genre || item.genre),
        synopsis: detail.description || item.description || 'فيلم مميز عالي الدقة مترجم.',
        director: 'إخراج سينمائي',
        cast: ['نجوم العمل'],
        url: streamUrl,
        backdrop: poster,
        kind: isAnimation ? 'anime' : 'film',
        episodes: []
      });
    }
  }

  // 2. TV Series & Anime (channelId = 2)
  console.log(`  -> Harvesting TV Series & Anime (Target Series: ${targetSeries}, Anime: ${targetAnime})...`);
  for (let page = 1; page <= 15; page++) {
    const seriesCount = collected.filter(x => x.kind === 'series').length;
    const animeCount = collected.filter(x => x.kind === 'anime').length;
    if (seriesCount >= targetSeries && animeCount >= (targetAnime + 10)) break;

    const items = await fetchSubjectList(2, page, 20);
    if (!items || items.length === 0) break;

    for (const item of items) {
      if (seenIds.has(item.subjectId)) continue;
      seenIds.add(item.subjectId);

      if (isContentBlocked(item.title, item.description, item.genre)) continue;

      const detail = await fetchSubjectDetail(item.subjectId);
      if (!detail) continue;
      if (isContentBlocked(detail.title, detail.description, detail.genre)) continue;

      const detector = detail.resourceDetectors?.[0];
      const resList = detector?.resolutionList || [];
      const best = resList[0];
      let streamUrl = best?.resourceLink || detector?.resourceLink || detector?.downloadUrl;
      if (!streamUrl || !streamUrl.startsWith('http')) continue;

      const arCaption = detector?.extCaptions?.find(c => c.lan === 'ar' || c.lan === 'ara');
      if (arCaption?.url) {
        streamUrl = `${streamUrl}|sub_ar=${arCaption.url}`;
      }

      const poster = item.cover?.url || detail.cover?.url || '';
      if (!poster) continue;

      const year = parseInt(detail.releaseDate?.slice(0, 4) || item.releaseDate?.slice(0, 4) || '2024', 10);
      const rating = parseFloat(detail.imdbRatingValue || detail.score || item.score || '7.5') || 7.5;
      const isAnimation = (detail.genre || item.genre || '').toLowerCase().includes('animation');
      const kind = isAnimation ? 'anime' : 'series';

      if (kind === 'series' && collected.filter(x => x.kind === 'series').length >= targetSeries) continue;
      if (kind === 'anime' && collected.filter(x => x.kind === 'anime').length >= targetAnime) continue;

      // Construct working episodes list
      const totalEp = detector?.totalEpisode || 8;
      const epCount = Math.min(Math.max(totalEp, 1), 12);
      const episodes = [];
      for (let e = 1; e <= epCount; e++) {
        episodes.push({
          title: `الحلقة ${e}`,
          url: streamUrl
        });
      }

      collected.push({
        id: currentId++,
        title: detail.title || item.title,
        year,
        rating,
        durationMin: parseDurationMinutes(item.duration) || 45,
        genres: mapGenres(detail.genre || item.genre),
        synopsis: detail.description || item.description || 'عمل درامي مميز مترجم عالي الدقة.',
        director: 'إخراج',
        cast: ['طاقم العمل'],
        url: streamUrl,
        backdrop: poster,
        kind,
        episodes
      });
    }
  }

  console.log(`[+] Total harvested titles: ${collected.length} (Movies: ${collected.filter(x => x.kind === 'film').length}, Series: ${collected.filter(x => x.kind === 'series').length}, Anime: ${collected.filter(x => x.kind === 'anime').length})`);
  return collected;
}

// Football matches sync via 365scores
function format365Date(d) {
  const day = String(d.getDate()).padStart(2, '0');
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const year = d.getFullYear();
  return `${day}/${month}/${year}`;
}

const MATCH_EXCLUDES = [
  'تحت 19', 'تحت 21', 'تحت 20', 'تحت 23', 'تحت 17', 'تحت 18',
  'u19', 'u21', 'u20', 'u23', 'u17', 'u18', 'للشباب', 'youth',
  'سيدات', 'نساء', 'women', 'female', 'amateur', 'الهواة', 'الرديف'
];

function isMatchValid(name, home, away) {
  const text = `${name} ${home} ${away}`.toLowerCase();
  for (const ex of MATCH_EXCLUDES) {
    if (text.includes(ex)) return false;
  }
  return true;
}

function getMatchPriority(g, compName, customIds = [572]) {
  const cId = g.competitionId;
  const name = (compName || '').toLowerCase();

  // Custom IDs added from panel (including 572 UEFA Champions League)
  if (customIds.includes(cId) || cId === 572 || name.includes('أبطال أوروبا') || name.includes('champions league')) {
    return 10;
  }

  // 1. Top 5 European Leagues
  if ([11, 17, 23, 25, 34].includes(cId) ||
      name.includes('الدوري الإنجليزي') || name.includes('premier league') ||
      name.includes('الدوري الإسباني') || name.includes('la liga') ||
      name.includes('الدوري الإيطالي') || name.includes('serie a') ||
      name.includes('الدوري الألماني') || name.includes('bundesliga') ||
      name.includes('الدوري الفرنسي') || name.includes('ligue 1')) {
    return 20;
  }

  // 2. National Teams
  if ([570, 7165, 590, 591, 592, 595].includes(cId) ||
      name.includes('منتخب') || name.includes('دولية') || name.includes('كأس العالم') ||
      name.includes('أمم') || name.includes('world cup') || name.includes('nations league') ||
      name.includes('euro') || name.includes('كوبا أمريكا') || name.includes('كأس آسيا') ||
      name.includes('كأس إفريقيا') || name.includes('ودية دولية')) {
    return 30;
  }

  // 3. Iraqi League (Priority 6)
  if (cId === 6822 || name.includes('العراقي') || name.includes('نجوم العراق') || name.includes('iraq')) {
    return 40;
  }

  // 4. Remaining European Leagues & Cups
  if ([573, 7105, 61, 63, 69, 65, 67, 80].includes(cId) ||
      name.includes('أوروب') || name.includes('europa') || name.includes('conference') ||
      name.includes('الهولندي') || name.includes('البرتغالي') || name.includes('التركي') ||
      name.includes('البلجيكي') || name.includes('الأسكتلندي') || name.includes('السويسري') ||
      name.includes('النمساوي') || name.includes('اليوناني') || name.includes('الروسي')) {
    return 50;
  }

  // 5. Other Premier Tournaments
  return 60;
}

async function fetchDayMatches(dateOffset, dayCode, customIds = [572]) {
  try {
    const d = new Date();
    d.setDate(d.getDate() + dateOffset);
    const dateStr = format365Date(d);

    const url = `https://webws.365scores.com/web/games/allscores/?appTypeId=5&langId=27&timezoneName=Asia/Baghdad&userCountryId=114&sports=1&startDate=${dateStr}&endDate=${dateStr}&showOdds=true`;
    console.log(`[*] Fetching football matches for date ${dateStr} (day=${dayCode})...`);

    const res = await fetch(url, { headers: { 'User-Agent': 'Mozilla/5.0' } });
    if (!res.ok) return [];
    const json = await res.json();

    const comps = {};
    (json.competitions || []).forEach(c => { comps[c.id] = c; });

    const rawGames = json.games || [];
    const list = [];

    for (const g of rawGames) {
      if (g.sportId && g.sportId !== 1) continue;
      const comp = comps[g.competitionId] || {};
      const compName = comp.name || 'كرة القدم';
      const homeName = g.homeCompetitor?.name || 'صاحب الأرض';
      const awayName = g.awayCompetitor?.name || 'الضيف';

      if (!isMatchValid(compName, homeName, awayName)) continue;

      const priority = getMatchPriority(g, compName, customIds);
      if (priority > 60) continue; // Keep only premier & requested leagues

      const live = g.statusGroup === 3 || Boolean(g.gameTimeDisplay && g.gameTimeDisplay.includes("'"));
      let time = '20:00';
      if (g.startTime) {
        try {
          const sd = new Date(g.startTime);
          time = `${String(sd.getHours()).padStart(2, '0')}:${String(sd.getMinutes()).padStart(2, '0')}`;
        } catch (_) {}
      }

      let status = live ? 'مباشر' : (g.statusText || 'قريبًا');
      if (g.statusGroup === 4) status = 'انتهت';

      const isUcl = g.competitionId === 572 || compName.includes('أبطال أوروبا');
      const channel = isUcl ? 'beIN SPORTS 1' : (compName.includes('روشن') ? 'SSC 1' : (compName.includes('العراقي') ? 'الرابعة الرياضية' : 'beIN SPORTS'));

      list.push({
        id: g.id || Math.floor(Math.random() * 100000),
        time,
        live,
        status,
        home: homeName,
        away: awayName,
        competition: compName,
        channel,
        day: dayCode,
        _prio: priority
      });
    }

    list.sort((a, b) => a._prio - b._prio || a.time.localeCompare(b.time));
    return list.map(({ _prio, ...m }) => m);
  } catch (e) {
    console.warn(`[!] Error fetching matches for day ${dayCode}:`, e.message);
    return [];
  }
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

async function main() {
  console.log('=== ONE+ Cloud Synchronization Service ===');

  if (!fs.existsSync(SA_PATH)) {
    throw new Error('Service Account JSON not found at: ' + SA_PATH);
  }

  const sa = JSON.parse(fs.readFileSync(SA_PATH, 'utf8'));
  const token = await getGoogleAccessToken(sa);
  console.log('[+] Authenticated with Firebase successfully.');

  // Load custom tournament IDs from panel if set
  let customTournamentIds = [572]; // UEFA Champions League default
  let panelExistingChannels = null;
  let panelExistingMovies = null;

  if (fs.existsSync(PANEL_DATA_PATH)) {
    try {
      const panelData = JSON.parse(fs.readFileSync(PANEL_DATA_PATH, 'utf8'));
      if (Array.isArray(panelData.tournament_ids) && panelData.tournament_ids.length > 0) {
        customTournamentIds = [...new Set([...customTournamentIds, ...panelData.tournament_ids])];
      }
      if (Array.isArray(panelData.channels) && panelData.channels.length > 0) {
        panelExistingChannels = panelData.channels;
      }
      if (Array.isArray(panelData.movies) && panelData.movies.length > 0) {
        panelExistingMovies = panelData.movies;
      }
    } catch (_) {}
  }
  console.log('[+] Active tournament IDs prioritized:', customTournamentIds);

  // 1. Harvest Movies, Series, and Anime
  const harvestedMovies = await harvestMovieBoxCatalog(60, 40, 30);
  const movies = panelExistingMovies && panelExistingMovies.length > 10 ? panelExistingMovies : harvestedMovies;

  // 2. Football Matches (Today & Tomorrow)
  const todayMatches = await fetchDayMatches(0, 0, customTournamentIds);
  const tomorrowMatches = await fetchDayMatches(1, 1, customTournamentIds);
  const matches = [...todayMatches, ...tomorrowMatches];
  console.log(`[+] Total Football Matches fetched: ${matches.length} (Today: ${todayMatches.length}, Tomorrow: ${tomorrowMatches.length})`);

  // 3. Channels
  const channels = panelExistingChannels && panelExistingChannels.length > 0 ? panelExistingChannels : DEFAULT_CHANNELS;

  // 4. Catalog Structure
  const catalog = {
    matches,
    movies,
    channels,
    last_updated: Date.now()
  };

  const appConfig = {
    min_version_code: 1,
    latest_version_code: 5,
    update_url: "https://apklive.web.app",
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

  // 5. Update local panel data.json
  try {
    const localPanelData = {
      next: Math.max(...movies.map(m => m.id), ...channels.map(c => c.id), 100) + 1,
      tournament_ids: customTournamentIds,
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
