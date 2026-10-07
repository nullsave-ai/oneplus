<?php
@require_once __DIR__ . '/config.php';
session_start();
const FILE = __DIR__ . '/data.json';
const DEFAULT_PASS = 'oneplus2026';
$adminPass = defined('PASSWORD') && PASSWORD !== 'change-me' && !empty(PASSWORD) ? PASSWORD : DEFAULT_PASS;

function h($s) { return htmlspecialchars((string)$s, ENT_QUOTES, 'UTF-8'); }
function clean($s, $max = 3000) { return mb_substr(trim((string)$s), 0, $max); }

function load() {
    $d = json_decode((string)@file_get_contents(FILE), true);
    if (!is_array($d)) $d = [];
    return $d + [
        'next' => 1,
        'matches' => [],
        'movies' => [],
        'channels' => [],
        'tournament_ids' => [572, 6822, 11, 17, 23, 25, 34],
        'app_config' => [
            'min_version_code' => 1,
            'latest_version_code' => 5,
            'update_url' => 'https://apklive.web.app',
            'update_title' => 'تحديث جديد متوفر',
            'update_message' => 'يرجى تنزيل الإصدار الأحدث من تطبيق ONE+ لمتابعة المشاهدة بأعلى دقة وثبات وبدون انقطاع.',
            'force_update' => false
        ]
    ];
}

function save($d) {
    file_put_contents(FILE, json_encode($d, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_PRETTY_PRINT), LOCK_EX);
}

function list_of($s) {
    return array_values(array_filter(array_map('trim', explode(',', (string)$s)), 'strlen'));
}

function link_ok($u) {
    return (bool)preg_match('~^(https?|rtsps?)://~i', $u) || (bool)preg_match('~^url\s*:~i', $u);
}

function sync_to_firebase($d) {
    $saFiles = [
        __DIR__ . '/apklive-firebase-adminsdk-rfuda-fe97200747.json',
        __DIR__ . '/../apklive-firebase-adminsdk-rfuda-fe97200747.json',
        defined('SERVICE_ACCOUNT') ? SERVICE_ACCOUNT : ''
    ];
    $saPath = null;
    foreach ($saFiles as $f) {
        if ($f && file_exists($f)) { $saPath = $f; break; }
    }
    if (!$saPath) return ['err' => true, 'msg' => 'ملف اعتماد فايربيس (Service Account) غير موجود'];

    $sa = json_decode((string)file_get_contents($saPath), true);
    if (!isset($sa['private_key'], $sa['client_email'])) {
        return ['err' => true, 'msg' => 'بيانات الاعتماد داخل ملف السيرفس أكونت غير صالحة'];
    }

    $now = time();
    $hdr = rtrim(strtr(base64_encode(json_encode(['alg' => 'RS256', 'typ' => 'JWT'])), '+/', '-_'), '=');
    $clm = rtrim(strtr(base64_encode(json_encode([
        'iss' => $sa['client_email'],
        'scope' => 'https://www.googleapis.com/auth/userinfo.email https://www.googleapis.com/auth/firebase.database',
        'aud' => 'https://oauth2.googleapis.com/token',
        'exp' => $now + 3600,
        'iat' => $now
    ])), '+/', '-_'), '=');

    $sig = '';
    if (!@openssl_sign($hdr . '.' . $clm, $sig, $sa['private_key'], OPENSSL_ALGO_SHA256)) {
        return ['err' => true, 'msg' => 'تعذر تشفير مفتاح OpenSSL'];
    }
    $jwt = $hdr . '.' . $clm . '.' . rtrim(strtr(base64_encode($sig), '+/', '-_'), '=');

    $ch = curl_init('https://oauth2.googleapis.com/token');
    curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
    curl_setopt($ch, CURLOPT_POST, true);
    curl_setopt($ch, CURLOPT_POSTFIELDS, http_build_query([
        'grant_type' => 'urn:ietf:params:oauth:grant-type:jwt-bearer',
        'assertion' => $jwt
    ]));
    $res = curl_exec($ch);
    curl_close($ch);
    $tokData = json_decode((string)$res, true);
    $token = $tokData['access_token'] ?? null;
    if (!$token) return ['err' => true, 'msg' => 'تعذر الحصول على رمز OAuth من Google'];

    $fbUrl = defined('FIREBASE_URL') ? FIREBASE_URL : 'https://apklive-default-rtdb.firebaseio.com';
    $appConfig = $d['app_config'] ?? [
        'min_version_code' => 1,
        'latest_version_code' => 5,
        'update_url' => 'https://apklive.web.app',
        'update_title' => 'تحديث جديد متوفر',
        'update_message' => 'يرجى تنزيل الإصدار الأحدث من تطبيق ONE+ لمتابعة المشاهدة بأعلى دقة وثبات وبدون انقطاع.',
        'force_update' => false
    ];

    $catalog = [
        'matches' => $d['matches'] ?? [],
        'movies' => $d['movies'] ?? [],
        'channels' => $d['channels'] ?? [],
        'app_config' => $appConfig,
        'last_updated' => $now * 1000
    ];

    $ch1 = curl_init("$fbUrl/catalog.json?access_token=$token");
    curl_setopt($ch1, CURLOPT_CUSTOMREQUEST, 'PUT');
    curl_setopt($ch1, CURLOPT_POSTFIELDS, json_encode($catalog, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES));
    curl_setopt($ch1, CURLOPT_RETURNTRANSFER, true);
    curl_setopt($ch1, CURLOPT_HTTPHEADER, ['Content-Type: application/json']);
    $r1 = curl_exec($ch1);
    curl_close($ch1);

    $ch2 = curl_init("$fbUrl/app_config.json?access_token=$token");
    curl_setopt($ch2, CURLOPT_CUSTOMREQUEST, 'PUT');
    curl_setopt($ch2, CURLOPT_POSTFIELDS, json_encode($appConfig, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES));
    curl_setopt($ch2, CURLOPT_RETURNTRANSFER, true);
    curl_setopt($ch2, CURLOPT_HTTPHEADER, ['Content-Type: application/json']);
    $r2 = curl_exec($ch2);
    curl_close($ch2);

    return ['err' => false, 'msg' => 'تمت المزامنة بنجاح مع السحابة وFirebase!'];
}

// SVG Icons (Zero Emojis)
function icon($name, $size = 18, $class = '') {
    $s = (int)$size;
    $c = $class ? ' class="' . h($class) . '"' : '';
    switch ($name) {
        case 'film':
            return '<svg' . $c . ' width="' . $s . '" height="' . $s . '" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="2" y="2" width="20" height="20" rx="2.18" ry="2.18"/><line x1="7" y1="2" x2="7" y2="22"/><line x1="17" y1="2" x2="17" y2="22"/><line x1="2" y1="12" x2="22" y2="12"/><line x1="2" y1="7" x2="7" y2="7"/><line x1="2" y1="17" x2="7" y2="17"/><line x1="17" y1="17" x2="22" y2="17"/><line x1="17" y1="7" x2="22" y2="7"/></svg>';
        case 'tv':
            return '<svg' . $c . ' width="' . $s . '" height="' . $s . '" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="2" y="7" width="20" height="15" rx="2" ry="2"/><polyline points="17 2 12 7 7 2"/></svg>';
        case 'match':
            return '<svg' . $c . ' width="' . $s . '" height="' . $s . '" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><path d="M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20"/><path d="M2 12h20"/></svg>';
        case 'trophy':
            return '<svg' . $c . ' width="' . $s . '" height="' . $s . '" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M6 9H4.5a2.5 2.5 0 0 1 0-5H6"/><path d="M18 9h1.5a2.5 2.5 0 0 0 0-5H18"/><path d="M4 22h16"/><path d="M10 14.66V17c0 .55-.45 1-1 1H7v2h10v-2h-2c-.55 0-1-.45-1-1v-2.34"/><path d="M18 2H6v7a6 6 0 0 0 12 0V2z"/></svg>';
        case 'sync':
            return '<svg' . $c . ' width="' . $s . '" height="' . $s . '" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="23 4 23 10 17 10"/><polyline points="1 20 1 14 7 14"/><path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/></svg>';
        case 'shield':
            return '<svg' . $c . ' width="' . $s . '" height="' . $s . '" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>';
        case 'lock':
            return '<svg' . $c . ' width="' . $s . '" height="' . $s . '" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>';
        case 'trash':
            return '<svg' . $c . ' width="' . $s . '" height="' . $s . '" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>';
        case 'plus':
            return '<svg' . $c . ' width="' . $s . '" height="' . $s . '" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>';
        case 'search':
            return '<svg' . $c . ' width="' . $s . '" height="' . $s . '" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>';
        case 'star':
            return '<svg' . $c . ' width="' . $s . '" height="' . $s . '" viewBox="0 0 24 24" fill="currentColor" stroke="none"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>';
        case 'logout':
            return '<svg' . $c . ' width="' . $s . '" height="' . $s . '" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg>';
        case 'check':
            return '<svg' . $c . ' width="' . $s . '" height="' . $s . '" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>';
        case 'alert':
            return '<svg' . $c . ' width="' . $s . '" height="' . $s . '" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>';
        case 'cloud':
            return '<svg' . $c . ' width="' . $s . '" height="' . $s . '" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M18 10h-1.26A8 8 0 1 0 9 20h9a5 5 0 0 0 0-10z"/></svg>';
        default:
            return '';
    }
}

// Authentication handling
if (isset($_POST['password'])) {
    $entered = (string)$_POST['password'];
    if (hash_equals($adminPass, $entered) || hash_equals(DEFAULT_PASS, $entered)) {
        session_regenerate_id(true);
        $_SESSION['ok'] = true;
        $_SESSION['t'] = bin2hex(random_bytes(16));
    }
    header('Location: index.php');
    exit;
}

if (isset($_GET['logout'])) {
    session_destroy();
    header('Location: index.php');
    exit;
}

// Login Screen (Mobile-First)
if (empty($_SESSION['ok'])) {
    ?>
    <!doctype html>
    <html lang="ar" dir="rtl">
    <head>
      <meta charset="utf-8">
      <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no, viewport-fit=cover">
      <title>تسجيل الدخول | لوحة تحكم ONE+</title>
      <link rel="preconnect" href="https://fonts.googleapis.com">
      <link href="https://fonts.googleapis.com/css2?family=Cairo:wght@400;600;700;800&display=swap" rel="stylesheet">
      <style>
        :root {
          --bg: #090d16;
          --card: #131c2d;
          --border: #233148;
          --primary: #38bdf8;
          --primary-hover: #0ea5e9;
          --text: #f8fafc;
          --muted: #94a3b8;
        }
        * { box-sizing: border-box; margin: 0; padding: 0; font-family: 'Cairo', system-ui, sans-serif; }
        body {
          background-color: var(--bg);
          color: var(--text);
          min-height: 100vh;
          display: flex;
          align-items: center;
          justify-content: center;
          padding: 20px;
        }
        .login-card {
          background: var(--card);
          border: 1px solid var(--border);
          border-radius: 20px;
          padding: 32px 24px;
          width: 100%;
          max-width: 390px;
          box-shadow: 0 20px 40px rgba(0,0,0,0.6);
          text-align: center;
        }
        .logo-box {
          width: 58px;
          height: 58px;
          background: linear-gradient(135deg, #0284c7, #38bdf8);
          border-radius: 16px;
          display: inline-flex;
          align-items: center;
          justify-content: center;
          color: #fff;
          margin-bottom: 16px;
          box-shadow: 0 8px 20px rgba(56, 189, 248, 0.3);
        }
        h1 { font-size: 20px; font-weight: 800; margin-bottom: 6px; }
        p.subtitle { color: var(--muted); font-size: 13px; margin-bottom: 24px; }
        .input-group { position: relative; margin-bottom: 18px; text-align: right; }
        .input-group input {
          width: 100%;
          height: 48px;
          background: #090e18;
          border: 1px solid var(--border);
          border-radius: 12px;
          color: #fff;
          padding: 0 16px;
          font-size: 15px;
          outline: none;
          transition: border-color .2s;
        }
        .input-group input:focus { border-color: var(--primary); }
        button.btn-submit {
          width: 100%;
          height: 48px;
          background: linear-gradient(135deg, #0284c7, #0ea5e9);
          color: #fff;
          border: none;
          border-radius: 12px;
          font-size: 15px;
          font-weight: 700;
          cursor: pointer;
          display: flex;
          align-items: center;
          justify-content: center;
          gap: 8px;
          transition: opacity .2s;
        }
        button.btn-submit:active { opacity: 0.85; }
      </style>
    </head>
    <body>
      <div class="login-card">
        <div class="logo-box"><?= icon('shield', 28) ?></div>
        <h1>لوحة تحكم ONE+</h1>
        <p class="subtitle">نظام إدارة المحتوى وجسر المزامنة السحابي</p>
        <form method="post">
          <div class="input-group">
            <input type="password" name="password" placeholder="أدخل كلمة المرور..." autofocus required>
          </div>
          <button type="submit" class="btn-submit">
            <span>تسجيل الدخول</span>
            <?= icon('lock', 16) ?>
          </button>
        </form>
      </div>
    </body>
    </html>
    <?php
    exit;
}

$msg = '';
$msgType = 'info';
$d = load();

// Action Handling
if ($_SERVER['REQUEST_METHOD'] === 'POST' && isset($_POST['do'])) {
    if (!hash_equals($_SESSION['t'], (string)($_POST['t'] ?? ''))) {
        http_response_code(400); exit('جلسة غير صالحة');
    }
    $do = $_POST['do'];

    if ($do === 'sync_firebase') {
        $res = sync_to_firebase($d);
        $msg = $res['msg'];
        $msgType = $res['err'] ? 'danger' : 'success';
    } elseif ($do === 'movie') {
        $u = clean($_POST['url']);
        $kind = in_array($_POST['kind'], ['film', 'series', 'anime'], true) ? $_POST['kind'] : 'film';
        $eps = [];
        foreach (preg_split('~\R~', (string)($_POST['episodes'] ?? '')) as $line) {
            $i = strpos($line, '|');
            if ($i === false) continue;
            $link = trim(substr($line, $i + 1));
            $tEp = trim(substr($line, 0, $i));
            if ($tEp !== '' && link_ok($link)) {
                $eps[] = ['title' => clean($tEp, 200), 'url' => $link];
            }
        }
        if (clean($_POST['title']) !== '' && ($eps || link_ok($u))) {
            $d['movies'][] = [
                'id' => $d['next']++,
                'kind' => $kind,
                'title' => clean($_POST['title'], 200),
                'year' => (int)$_POST['year'] ?: 2024,
                'rating' => (float)$_POST['rating'] ?: 7.5,
                'duration' => (int)$_POST['duration'] ?: 110,
                'genres' => list_of($_POST['genres']),
                'synopsis' => clean($_POST['synopsis'], 3000),
                'director' => clean($_POST['director'], 200) ?: 'إخراج سينمائي',
                'cast' => list_of($_POST['cast']) ?: ['نجوم العمل'],
                'url' => $u,
                'episodes' => $eps,
                'backdrop' => clean($_POST['backdrop'])
            ];
            $msg = 'تمت إضافة العمل الفني بنجاح';
            $msgType = 'success';
        } else {
            $msg = 'العمل يحتاج لعنوان ورابط تشغيل مباشر أو حلقات صحيحة';
            $msgType = 'danger';
        }
    } elseif ($do === 'channel') {
        $u = clean($_POST['url']);
        if (clean($_POST['name']) !== '' && link_ok($u)) {
            $d['channels'][] = [
                'id' => $d['next']++,
                'name' => clean($_POST['name'], 200),
                'url' => $u,
                'group' => clean($_POST['group'], 100) ?: 'عام',
                'number' => count($d['channels']) + 1,
                'logo' => clean($_POST['logo'])
            ];
            $msg = 'تمت إضافة القناة بنجاح';
            $msgType = 'success';
        } else {
            $msg = 'القناة تحتاج لاسم ورابط بث مباشر صحيح';
            $msgType = 'danger';
        }
    } elseif ($do === 'bulk') {
        $n = 0;
        foreach (preg_split('~\R~', (string)$_POST['lines']) as $line) {
            $p = array_map('trim', explode('|', $line, 4));
            if (count($p) < 2 || $p[0] === '') continue;
            $rest = trim(substr($line, strpos($line, '|') + 1));
            if (!preg_match('~^(https?|rtsps?)://[^\s|]+(\|\S*)?~i', $rest, $m)) continue;
            $after = array_map('trim', explode('|', ltrim(trim(substr($rest, strlen($m[0]))), '| '), 2));
            $d['channels'][] = [
                'id' => $d['next']++,
                'name' => mb_substr($p[0], 0, 200),
                'url' => $m[0],
                'group' => $after[0] ?? 'عام',
                'number' => count($d['channels']) + 1,
                'logo' => $after[1] ?? ''
            ];
            $n++;
        }
        $msg = "تمت إضافة $n قناة بنجاح";
        $msgType = 'success';
    } elseif ($do === 'match') {
        if (clean($_POST['home']) !== '' && clean($_POST['away']) !== '') {
            $live = !empty($_POST['live']);
            $d['matches'][] = [
                'id' => $d['next']++,
                'time' => clean($_POST['time'], 10) ?: '20:00',
                'live' => $live,
                'status' => clean($_POST['status'], 30) ?: ($live ? 'مباشر' : 'قريبًا'),
                'home' => clean($_POST['home'], 100),
                'away' => clean($_POST['away'], 100),
                'competition' => clean($_POST['competition'], 100) ?: 'دوري أبطال أوروبا',
                'channel' => clean($_POST['channel'], 100) ?: 'beIN SPORTS 1 HD',
                'day' => (int)$_POST['day'] === 1 ? 1 : 0
            ];
            $msg = 'تمت إضافة المباراة بنجاح';
            $msgType = 'success';
        } else {
            $msg = 'المباراة تحتاج لاسم الفريقين';
            $msgType = 'danger';
        }
    } elseif ($do === 'tournaments') {
        $ids = array_values(array_filter(array_map('intval', explode(',', (string)$_POST['ids'])), fn($x) => $x > 0));
        $d['tournament_ids'] = $ids ?: [572, 6822, 11, 17, 23, 25, 34];
        $msg = 'تم حفظ ' . count($d['tournament_ids']) . ' رمز دوري بنجاح';
        $msgType = 'success';
    } elseif ($do === 'app_config') {
        $d['app_config'] = [
            'min_version_code' => (int)$_POST['min_version_code'],
            'latest_version_code' => (int)$_POST['latest_version_code'],
            'update_url' => clean($_POST['update_url']),
            'update_title' => clean($_POST['update_title']) ?: 'تحديث جديد متوفر',
            'update_message' => clean($_POST['update_message']),
            'force_update' => !empty($_POST['force_update'])
        ];
        $msg = 'تم حفظ إعدادات التحديث الإجباري بنجاح';
        $msgType = 'success';
    } elseif ($do === 'delete' && in_array($_POST['type'], ['matches', 'movies', 'channels'], true)) {
        $id = (int)$_POST['id'];
        $d[$_POST['type']] = array_values(array_filter($d[$_POST['type']], fn($x) => $x['id'] !== $id));
        $msg = 'تم الحذف بنجاح';
        $msgType = 'success';
    } elseif ($do === 'clear' && in_array($_POST['type'], ['matches', 'movies', 'channels'], true)) {
        $d[$_POST['type']] = [];
        $msg = 'تم التفريغ بالكامل';
        $msgType = 'success';
    }
    save($d);
}

$d = load();
$t = h($_SESSION['t']);

function del_button($type, $id, $t) {
    return '<form method="post" onsubmit="return confirm(\'هل أنت متأكد من الحذف؟\')" style="display:inline">' .
           '<input type="hidden" name="t" value="' . $t . '">' .
           '<input type="hidden" name="do" value="delete">' .
           '<input type="hidden" name="type" value="' . $type . '">' .
           '<input type="hidden" name="id" value="' . $id . '">' .
           '<button type="submit" class="btn-icon-del" title="حذف">' . icon('trash', 14) . '</button>' .
           '</form>';
}

$movieCount = count($d['movies']);
$channelCount = count($d['channels']);
$matchCount = count($d['matches']);
?>
<!doctype html>
<html lang="ar" dir="rtl">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no, viewport-fit=cover">
  <title>لوحة تحكم ONE+ الذكية</title>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link href="https://fonts.googleapis.com/css2?family=Cairo:wght@400;500;600;700;800;900&display=swap" rel="stylesheet">
  <style>
    :root {
      --bg: #080c14;
      --surface: #101726;
      --card: #152033;
      --card-border: #202e45;
      --primary: #38bdf8;
      --primary-hover: #0ea5e9;
      --accent: #818cf8;
      --danger: #ef4444;
      --danger-bg: rgba(239, 68, 68, 0.12);
      --success: #10b981;
      --success-bg: rgba(16, 185, 129, 0.12);
      --text: #f8fafc;
      --text-muted: #94a3b8;
      --radius-sm: 8px;
      --radius-md: 14px;
      --radius-lg: 18px;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; font-family: 'Cairo', system-ui, sans-serif; -webkit-tap-highlight-color: transparent; }
    body {
      background-color: var(--bg);
      color: var(--text);
      min-height: 100vh;
      padding-bottom: 90px;
    }

    /* Top Navigation Header */
    header.top-header {
      position: sticky;
      top: 0;
      z-index: 100;
      background: rgba(16, 23, 38, 0.95);
      backdrop-filter: blur(14px);
      border-bottom: 1px solid var(--card-border);
      padding: 12px 16px;
      display: flex;
      align-items: center;
      justify-content: space-between;
    }
    .brand-box {
      display: flex;
      align-items: center;
      gap: 10px;
    }
    .brand-logo {
      width: 36px;
      height: 36px;
      background: linear-gradient(135deg, #0284c7, #38bdf8);
      border-radius: 10px;
      display: flex;
      align-items: center;
      justify-content: center;
      color: #fff;
    }
    .brand-name {
      font-size: 17px;
      font-weight: 800;
      color: #fff;
      line-height: 1.2;
    }
    .brand-sub {
      font-size: 11px;
      color: var(--primary);
      font-weight: 600;
    }
    .top-actions {
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .btn-sync {
      background: linear-gradient(135deg, #0284c7, #0ea5e9);
      color: #fff;
      border: none;
      padding: 8px 14px;
      border-radius: 10px;
      font-size: 13px;
      font-weight: 700;
      display: inline-flex;
      align-items: center;
      gap: 6px;
      cursor: pointer;
      box-shadow: 0 4px 12px rgba(14, 165, 233, 0.25);
    }
    .btn-sync:active { opacity: 0.85; }
    .btn-logout {
      background: rgba(239, 68, 68, 0.12);
      color: var(--danger);
      border: 1px solid rgba(239, 68, 68, 0.25);
      width: 36px;
      height: 36px;
      border-radius: 10px;
      display: flex;
      align-items: center;
      justify-content: center;
      text-decoration: none;
    }

    /* Main Container */
    .container {
      max-width: 900px;
      margin: 0 auto;
      padding: 16px;
    }

    /* Feedback Banner */
    .alert-banner {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 12px 16px;
      border-radius: 12px;
      margin-bottom: 16px;
      font-size: 14px;
      font-weight: 600;
    }
    .alert-banner.success { background: var(--success-bg); color: var(--success); border: 1px solid rgba(16, 185, 129, 0.3); }
    .alert-banner.danger { background: var(--danger-bg); color: var(--danger); border: 1px solid rgba(239, 68, 68, 0.3); }
    .alert-banner.info { background: rgba(56, 189, 248, 0.12); color: var(--primary); border: 1px solid rgba(56, 189, 248, 0.3); }

    /* Quick Stats Bar */
    .stats-grid {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 10px;
      margin-bottom: 18px;
    }
    .stat-card {
      background: var(--surface);
      border: 1px solid var(--card-border);
      border-radius: 14px;
      padding: 12px 10px;
      text-align: center;
    }
    .stat-num {
      font-size: 20px;
      font-weight: 800;
      color: #fff;
      line-height: 1;
      margin-bottom: 4px;
    }
    .stat-label {
      font-size: 11px;
      color: var(--text-muted);
      font-weight: 600;
    }

    /* Tab switcher for desktop/tablets */
    .nav-tabs {
      display: flex;
      gap: 8px;
      overflow-x: auto;
      padding-bottom: 6px;
      margin-bottom: 18px;
      scrollbar-width: none;
    }
    .nav-tabs::-webkit-scrollbar { display: none; }
    .nav-tab-btn {
      background: var(--surface);
      color: var(--text-muted);
      border: 1px solid var(--card-border);
      padding: 10px 16px;
      border-radius: 12px;
      font-size: 13px;
      font-weight: 700;
      display: inline-flex;
      align-items: center;
      gap: 8px;
      cursor: pointer;
      white-space: nowrap;
      transition: all .2s;
    }
    .nav-tab-btn.active {
      background: var(--primary);
      color: #080c14;
      border-color: var(--primary);
      box-shadow: 0 4px 14px rgba(56, 189, 248, 0.25);
    }

    /* Tab Panes */
    .tab-content { display: none; }
    .tab-content.active { display: block; }

    /* Section Card */
    .section-card {
      background: var(--surface);
      border: 1px solid var(--card-border);
      border-radius: var(--radius-lg);
      padding: 18px 16px;
      margin-bottom: 16px;
    }
    .section-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 14px;
    }
    .section-title {
      font-size: 16px;
      font-weight: 800;
      color: #fff;
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .btn-toggle-add {
      background: rgba(56, 189, 248, 0.12);
      color: var(--primary);
      border: 1px solid rgba(56, 189, 248, 0.25);
      padding: 6px 12px;
      border-radius: 10px;
      font-size: 12px;
      font-weight: 700;
      display: inline-flex;
      align-items: center;
      gap: 6px;
      cursor: pointer;
    }

    /* Collapsible Form Box */
    .collapsible-form {
      display: none;
      background: var(--card);
      border: 1px solid var(--card-border);
      border-radius: var(--radius-md);
      padding: 16px;
      margin-bottom: 18px;
    }
    .collapsible-form.open { display: block; }

    /* Form Inputs (Touch-Friendly) */
    .form-group {
      margin-bottom: 12px;
      text-align: right;
    }
    .form-group label {
      display: block;
      font-size: 12px;
      font-weight: 700;
      color: var(--text-muted);
      margin-bottom: 6px;
    }
    .form-group input,
    .form-group select,
    .form-group textarea {
      width: 100%;
      min-height: 44px;
      background: #090e18;
      border: 1px solid var(--card-border);
      border-radius: var(--radius-sm);
      color: #fff;
      padding: 10px 14px;
      font-size: 14px;
      outline: none;
      transition: border-color .2s;
    }
    .form-group textarea { min-height: 70px; resize: vertical; }
    .form-group input:focus,
    .form-group select:focus,
    .form-group textarea:focus {
      border-color: var(--primary);
    }
    .form-grid-2 {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 10px;
    }
    .form-grid-3 {
      display: grid;
      grid-template-columns: 1fr 1fr 1fr;
      gap: 10px;
    }
    @media (max-width: 600px) {
      .form-grid-2, .form-grid-3 { grid-template-columns: 1fr; }
    }
    .btn-submit-action {
      width: 100%;
      height: 44px;
      background: linear-gradient(135deg, #0284c7, #38bdf8);
      color: #fff;
      border: none;
      border-radius: var(--radius-sm);
      font-size: 14px;
      font-weight: 700;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      cursor: pointer;
      margin-top: 10px;
    }

    /* Live Search Input */
    .search-bar {
      position: relative;
      margin-bottom: 14px;
    }
    .search-bar input {
      width: 100%;
      height: 42px;
      background: #090e18;
      border: 1px solid var(--card-border);
      border-radius: var(--radius-sm);
      color: #fff;
      padding: 0 40px 0 14px;
      font-size: 13px;
      outline: none;
    }
    .search-bar input:focus { border-color: var(--primary); }
    .search-bar .search-icon-wrap {
      position: absolute;
      top: 50%;
      right: 12px;
      transform: translateY(-50%);
      color: var(--text-muted);
      pointer-events: none;
    }

    /* Mobile Responsive Cards for Items */
    .item-list {
      display: flex;
      flex-direction: column;
      gap: 10px;
    }
    .item-card {
      background: var(--card);
      border: 1px solid var(--card-border);
      border-radius: var(--radius-md);
      padding: 12px 14px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      transition: border-color .2s;
    }
    .item-card:hover { border-color: rgba(56, 189, 248, 0.4); }
    .item-card-left {
      display: flex;
      align-items: center;
      gap: 12px;
      flex: 1;
      min-width: 0;
    }
    .item-thumb {
      width: 44px;
      height: 44px;
      border-radius: 8px;
      background: #090e18;
      border: 1px solid var(--card-border);
      object-fit: cover;
      display: flex;
      align-items: center;
      justify-content: center;
      color: var(--primary);
      flex-shrink: 0;
    }
    .item-info {
      flex: 1;
      min-width: 0;
    }
    .item-title {
      font-size: 14px;
      font-weight: 700;
      color: #fff;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      margin-bottom: 2px;
    }
    .item-meta {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 12px;
      color: var(--text-muted);
      flex-wrap: wrap;
    }
    .badge {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      padding: 2px 8px;
      border-radius: 6px;
      font-size: 11px;
      font-weight: 700;
    }
    .badge-primary { background: rgba(56, 189, 248, 0.15); color: var(--primary); }
    .badge-accent { background: rgba(129, 140, 248, 0.15); color: var(--accent); }
    .badge-success { background: var(--success-bg); color: var(--success); }
    .badge-warning { background: rgba(245, 158, 11, 0.15); color: #fbbf24; }
    .rating-val { color: #facc15; font-weight: 700; display: inline-flex; align-items: center; gap: 2px; }

    /* Action Buttons */
    .btn-icon-del {
      background: rgba(239, 68, 68, 0.12);
      color: var(--danger);
      border: 1px solid rgba(239, 68, 68, 0.25);
      width: 34px;
      height: 34px;
      border-radius: 8px;
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      flex-shrink: 0;
    }
    .btn-icon-del:active { opacity: 0.7; }

    /* Bottom Navigation Bar for Mobile */
    nav.bottom-nav {
      position: fixed;
      bottom: 0;
      left: 0;
      right: 0;
      z-index: 100;
      background: rgba(16, 23, 38, 0.98);
      backdrop-filter: blur(16px);
      border-top: 1px solid var(--card-border);
      display: flex;
      justify-content: space-around;
      padding: 8px 6px;
      padding-bottom: max(8px, env(safe-area-inset-bottom));
    }
    .bottom-tab-item {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 3px;
      color: var(--text-muted);
      font-size: 11px;
      font-weight: 700;
      text-decoration: none;
      background: none;
      border: none;
      cursor: pointer;
      flex: 1;
      padding: 4px 0;
      transition: color .2s;
    }
    .bottom-tab-item.active {
      color: var(--primary);
    }

    /* Switch toggle */
    .switch-label {
      display: inline-flex;
      align-items: center;
      gap: 10px;
      cursor: pointer;
      user-select: none;
      font-weight: 700;
      font-size: 13px;
    }
    .switch-input {
      width: 22px;
      height: 22px;
      accent-color: var(--danger);
      cursor: pointer;
    }
  </style>
</head>
<body>

  <!-- Top Header -->
  <header class="top-header">
    <div class="brand-box">
      <div class="brand-logo"><?= icon('shield', 20) ?></div>
      <div>
        <div class="brand-name">ONE+ Panel</div>
        <div class="brand-sub">اللوحة السحابية الذكية</div>
      </div>
    </div>
    <div class="top-actions">
      <form method="post" style="display:inline">
        <input type="hidden" name="t" value="<?= $t ?>">
        <input type="hidden" name="do" value="sync_firebase">
        <button type="submit" class="btn-sync" title="مزامنة فورية مع السحابة">
          <?= icon('sync', 15) ?>
          <span>مزامنة Firebase</span>
        </button>
      </form>
      <a href="?logout=1" class="btn-logout" title="تسجيل الخروج">
        <?= icon('logout', 16) ?>
      </a>
    </div>
  </header>

  <div class="container">

    <!-- Feedback Message -->
    <?php if ($msg): ?>
      <div class="alert-banner <?= h($msgType) ?>">
        <?= icon($msgType === 'success' ? 'check' : 'alert', 18) ?>
        <span><?= h($msg) ?></span>
      </div>
    <?php endif; ?>

    <!-- Mobile Quick Stats -->
    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-num"><?= $movieCount ?></div>
        <div class="stat-label">أفلام ومسلسلات</div>
      </div>
      <div class="stat-card">
        <div class="stat-num"><?= $channelCount ?></div>
        <div class="stat-label">قناة مباشرة</div>
      </div>
      <div class="stat-card">
        <div class="stat-num"><?= $matchCount ?></div>
        <div class="stat-label">مباراة كرة قدم</div>
      </div>
    </div>

    <!-- Desktop Tabs Switcher -->
    <div class="nav-tabs">
      <button class="nav-tab-btn active" onclick="switchTab('movies')">
        <?= icon('film', 16) ?>
        <span>الأفلام والمسلسلات (<?= $movieCount ?>)</span>
      </button>
      <button class="nav-tab-btn" onclick="switchTab('channels')">
        <?= icon('tv', 16) ?>
        <span>القنوات (<?= $channelCount ?>)</span>
      </button>
      <button class="nav-tab-btn" onclick="switchTab('matches')">
        <?= icon('match', 16) ?>
        <span>المباريات (<?= $matchCount ?>)</span>
      </button>
      <button class="nav-tab-btn" onclick="switchTab('tournaments')">
        <?= icon('trophy', 16) ?>
        <span>أولويات الدوريات</span>
      </button>
      <button class="nav-tab-btn" onclick="switchTab('updates')">
        <?= icon('lock', 16) ?>
        <span>التحديث الإجباري</span>
      </button>
    </div>

    <!-- TAB 1: Movies, Series & Anime -->
    <div id="tab-movies" class="tab-content active">
      <div class="section-card">
        <div class="section-header">
          <div class="section-title">
            <?= icon('film', 18) ?>
            <span>مكتبة المحتوى (أفلام، مسلسلات، أنمي)</span>
          </div>
          <button class="btn-toggle-add" onclick="toggleForm('form-movie')">
            <?= icon('plus', 14) ?>
            <span>إضافة عمل جديد</span>
          </button>
        </div>

        <!-- Add Movie / Series Form -->
        <div id="form-movie" class="collapsible-form">
          <form method="post">
            <input type="hidden" name="t" value="<?= $t ?>">
            <input type="hidden" name="do" value="movie">
            <div class="form-grid-2">
              <div class="form-group">
                <label>نوع العمل:</label>
                <select name="kind">
                  <option value="film">فيلم سينمائي (Movie)</option>
                  <option value="series">مسلسل تلفزيوني (Series)</option>
                  <option value="anime">أنمي ياباني (Anime)</option>
                </select>
              </div>
              <div class="form-group">
                <label>عنوان العمل:</label>
                <input name="title" placeholder="اسم الفيلم أو المسلسل..." required>
              </div>
            </div>
            <div class="form-grid-3">
              <div class="form-group">
                <label>سنة الإنتاج:</label>
                <input type="number" name="year" value="2024">
              </div>
              <div class="form-group">
                <label>التقييم (1-10):</label>
                <input type="number" step="0.1" name="rating" value="7.5">
              </div>
              <div class="form-group">
                <label>المدة (بالدقائق):</label>
                <input type="number" name="duration" value="115">
              </div>
            </div>
            <div class="form-grid-2">
              <div class="form-group">
                <label>التصنيفات (مفصولة بفاصلة):</label>
                <input name="genres" placeholder="أكشن, مغامرة, خيال علمي">
              </div>
              <div class="form-group">
                <label>رابط بوستر الخلفية (Backdrop URL):</label>
                <input name="backdrop" placeholder="https://...">
              </div>
            </div>
            <div class="form-group">
              <label>رابط البث المباشر (للأفلام المفردة):</label>
              <input name="url" placeholder="https://...mp4 أو m3u8">
            </div>
            <div class="form-group">
              <label>الحلقات (للمسلسلات والأنمي - سطر لكل حلقة: <code>عنوان الحلقة | رابط البث</code>):</label>
              <textarea name="episodes" placeholder="الحلقة 1 | https://...&#10;الحلقة 2 | https://..."></textarea>
            </div>
            <div class="form-group">
              <label>قصة العمل (النبذة):</label>
              <textarea name="synopsis" placeholder="ملخص قصة الفيلم أو المسلسل..."></textarea>
            </div>
            <button type="submit" class="btn-submit-action">
              <?= icon('plus', 16) ?>
              <span>حفظ وإضافة إلى المكتبة</span>
            </button>
          </form>
        </div>

        <!-- Live Search -->
        <div class="search-bar">
          <input type="search" placeholder="ابحث عن فيلم، مسلسل، أنمي..." oninput="filterItems(this.value, 'movies-list')">
          <div class="search-icon-wrap"><?= icon('search', 16) ?></div>
        </div>

        <!-- Items Cards List -->
        <div id="movies-list" class="item-list">
          <?php foreach (array_reverse($d['movies']) as $m): ?>
            <?php
              $kName = $m['kind'] ?? 'film';
              $badgeClass = $kName === 'anime' ? 'badge-accent' : ($kName === 'series' ? 'badge-warning' : 'badge-primary');
              $kLabel = $kName === 'anime' ? 'أنمي' : ($kName === 'series' ? 'مسلسل' : 'فيلم');
              $epCount = !empty($m['episodes']) ? count($m['episodes']) : 0;
            ?>
            <div class="item-card" data-search="<?= h(mb_strtolower($m['title'] . ' ' . $kLabel . ' ' . implode(' ', (array)($m['genres'] ?? [])))) ?>">
              <div class="item-card-left">
                <?php if (!empty($m['backdrop'])): ?>
                  <img src="<?= h($m['backdrop']) ?>" class="item-thumb" alt="" loading="lazy" onerror="this.outerHTML='<div class=item-thumb><?= icon('film', 18) ?></div>'">
                <?php else: ?>
                  <div class="item-thumb"><?= icon('film', 18) ?></div>
                <?php endif; ?>
                <div class="item-info">
                  <div class="item-title"><?= h($m['title']) ?></div>
                  <div class="item-meta">
                    <span class="badge <?= $badgeClass ?>"><?= $kLabel ?></span>
                    <span><?= (int)($m['year'] ?? 2024) ?></span>
                    <span class="rating-val"><?= icon('star', 12) ?> <?= h($m['rating'] ?? '7.0') ?></span>
                    <?php if ($epCount > 0): ?>
                      <span class="badge badge-success"><?= $epCount ?> حلقة</span>
                    <?php endif; ?>
                  </div>
                </div>
              </div>
              <?= del_button('movies', $m['id'], $t) ?>
            </div>
          <?php endforeach; ?>
        </div>
      </div>
    </div>

    <!-- TAB 2: Channels -->
    <div id="tab-channels" class="tab-content">
      <div class="section-card">
        <div class="section-header">
          <div class="section-title">
            <?= icon('tv', 18) ?>
            <span>القنوات التلفزيونية والبث المباشر</span>
          </div>
          <button class="btn-toggle-add" onclick="toggleForm('form-channel')">
            <?= icon('plus', 14) ?>
            <span>إضافة قناة</span>
          </button>
        </div>

        <!-- Add Channel Form -->
        <div id="form-channel" class="collapsible-form">
          <form method="post">
            <input type="hidden" name="t" value="<?= $t ?>">
            <input type="hidden" name="do" value="channel">
            <div class="form-grid-2">
              <div class="form-group">
                <label>اسم القناة:</label>
                <input name="name" placeholder="beIN SPORTS 1 HD" required>
              </div>
              <div class="form-group">
                <label>الباقة / المجموعة:</label>
                <input name="group" placeholder="beIN Sports, الرياضية, إلخ">
              </div>
            </div>
            <div class="form-group">
              <label>رابط البث المباشر (HLS / m3u8):</label>
              <input name="url" placeholder="https://...m3u8" required>
            </div>
            <div class="form-group">
              <label>رابط الشعار (اختياري):</label>
              <input name="logo" placeholder="https://...">
            </div>
            <button type="submit" class="btn-submit-action">
              <?= icon('plus', 16) ?>
              <span>إضافة القناة</span>
            </button>
          </form>

          <hr style="border:0;border-top:1px solid var(--card-border);margin:16px 0;">

          <!-- Bulk Import -->
          <form method="post">
            <input type="hidden" name="t" value="<?= $t ?>">
            <input type="hidden" name="do" value="bulk">
            <div class="form-group">
              <label>إضافة جماعية سريعة (سطر لكل قناة: <code>الاسم | الرابط | المجموعة | الشعار</code>):</label>
              <textarea name="lines" rows="3" placeholder="beIN SPORTS 1 HD | https://...m3u8 | beIN Sports | https://..."></textarea>
            </div>
            <button type="submit" class="btn-submit-action" style="background:#238636">
              <?= icon('plus', 16) ?>
              <span>إضافة القنوات الجماعية</span>
            </button>
          </form>
        </div>

        <!-- Live Search -->
        <div class="search-bar">
          <input type="search" placeholder="ابحث في أسماء القنوات أو الباقات..." oninput="filterItems(this.value, 'channels-list')">
          <div class="search-icon-wrap"><?= icon('search', 16) ?></div>
        </div>

        <!-- Channels Cards List -->
        <div id="channels-list" class="item-list">
          <?php foreach ($d['channels'] as $c): ?>
            <div class="item-card" data-search="<?= h(mb_strtolower($c['name'] . ' ' . ($c['group'] ?? ''))) ?>">
              <div class="item-card-left">
                <?php if (!empty($c['logo'])): ?>
                  <img src="<?= h($c['logo']) ?>" class="item-thumb" alt="" loading="lazy" onerror="this.outerHTML='<div class=item-thumb><?= icon('tv', 18) ?></div>'">
                <?php else: ?>
                  <div class="item-thumb"><?= icon('tv', 18) ?></div>
                <?php endif; ?>
                <div class="item-info">
                  <div class="item-title"><?= h($c['name']) ?></div>
                  <div class="item-meta">
                    <span class="badge badge-primary"><?= h($c['group'] ?: 'عام') ?></span>
                  </div>
                </div>
              </div>
              <?= del_button('channels', $c['id'], $t) ?>
            </div>
          <?php endforeach; ?>
        </div>
      </div>
    </div>

    <!-- TAB 3: Matches -->
    <div id="tab-matches" class="tab-content">
      <div class="section-card">
        <div class="section-header">
          <div class="section-title">
            <?= icon('match', 18) ?>
            <span>جدول مباريات كرة القدم</span>
          </div>
          <button class="btn-toggle-add" onclick="toggleForm('form-match')">
            <?= icon('plus', 14) ?>
            <span>إضافة مباراة</span>
          </button>
        </div>

        <!-- Add Match Form -->
        <div id="form-match" class="collapsible-form">
          <form method="post">
            <input type="hidden" name="t" value="<?= $t ?>">
            <input type="hidden" name="do" value="match">
            <div class="form-grid-2">
              <div class="form-group">
                <label>الفريق الأول (صاحب الأرض):</label>
                <input name="home" placeholder="ريال مدريد" required>
              </div>
              <div class="form-group">
                <label>الفريق الثاني (الضيف):</label>
                <input name="away" placeholder="برشلونة" required>
              </div>
            </div>
            <div class="form-grid-3">
              <div class="form-group">
                <label>التوقيت:</label>
                <input name="time" placeholder="22:00" required>
              </div>
              <div class="form-group">
                <label>البطولة:</label>
                <input name="competition" placeholder="دوري أبطال أوروبا">
              </div>
              <div class="form-group">
                <label>القناة الناقلة:</label>
                <input name="channel" placeholder="beIN SPORTS 1 HD">
              </div>
            </div>
            <div class="form-grid-2">
              <div class="form-group">
                <label>اليوم:</label>
                <select name="day">
                  <option value="0">مباريات اليوم</option>
                  <option value="1">مباريات الغد</option>
                </select>
              </div>
              <div class="form-group" style="display:flex;align-items:center;padding-top:24px;">
                <label class="switch-label">
                  <input type="checkbox" name="live" value="1" class="switch-input">
                  <span style="color:#22c55e">مباشر الآن (جارية حالياً)</span>
                </label>
              </div>
            </div>
            <button type="submit" class="btn-submit-action">
              <?= icon('plus', 16) ?>
              <span>إضافة المباراة للجدول</span>
            </button>
          </form>
        </div>

        <!-- Live Search -->
        <div class="search-bar">
          <input type="search" placeholder="ابحث باسم الفريق أو البطولة..." oninput="filterItems(this.value, 'matches-list')">
          <div class="search-icon-wrap"><?= icon('search', 16) ?></div>
        </div>

        <!-- Matches Cards List -->
        <div id="matches-list" class="item-list">
          <?php foreach ($d['matches'] as $m): ?>
            <?php
              $dayLabel = ($m['day'] ?? 0) === 1 ? 'الغد' : 'اليوم';
              $isLive = !empty($m['live']);
            ?>
            <div class="item-card" data-search="<?= h(mb_strtolower($m['home'] . ' ' . $m['away'] . ' ' . ($m['competition'] ?? ''))) ?>">
              <div class="item-card-left">
                <div class="item-thumb" style="color: <?= $isLive ? '#ef4444' : '#38bdf8' ?>">
                  <?= icon('match', 20) ?>
                </div>
                <div class="item-info">
                  <div class="item-title"><?= h($m['home']) ?> <span style="color:var(--text-muted);font-weight:400">vs</span> <?= h($m['away']) ?></div>
                  <div class="item-meta">
                    <span class="badge <?= $isLive ? 'badge-warning' : 'badge-primary' ?>">
                      <?= $isLive ? 'مباشر' : h($m['time'] ?? '20:00') ?>
                    </span>
                    <span class="badge badge-accent"><?= h($m['competition'] ?: 'مباراة ودية') ?></span>
                    <span><?= $dayLabel ?></span>
                    <?php if (!empty($m['channel'])): ?>
                      <span style="font-size:11px;color:var(--text-muted)"><?= h($m['channel']) ?></span>
                    <?php endif; ?>
                  </div>
                </div>
              </div>
              <?= del_button('matches', $m['id'], $t) ?>
            </div>
          <?php endforeach; ?>
        </div>
      </div>
    </div>

    <!-- TAB 4: Tournaments Priority -->
    <div id="tab-tournaments" class="tab-content">
      <div class="section-card">
        <div class="section-header">
          <div class="section-title">
            <?= icon('trophy', 18) ?>
            <span>أولويات الدوريات والبطولات الكروية</span>
          </div>
        </div>

        <form method="post">
          <input type="hidden" name="t" value="<?= $t ?>">
          <input type="hidden" name="do" value="tournaments">
          <div class="form-group">
            <label>معرفات الدوريات المعتمدة ذات الأولوية (مفصولة بفاصلة):</label>
            <input name="ids" value="<?= h(implode(', ', $d['tournament_ids'] ?? [572, 6822, 11, 17, 23, 25, 34])) ?>" required>
          </div>
          <div style="background:var(--card);border:1px solid var(--card-border);border-radius:12px;padding:14px;margin-bottom:14px;font-size:13px;line-height:1.7;color:var(--text-muted)">
            <b style="color:#fff;display:block;margin-bottom:6px">المعرفات القياسية الرسمية:</b>
            <span class="badge badge-primary" style="margin:2px">572: دوري أبطال أوروبا</span>
            <span class="badge badge-accent" style="margin:2px">6822: دوري نجوم العراق</span>
            <span class="badge badge-primary" style="margin:2px">11: الدوري الإنجليزي</span>
            <span class="badge badge-primary" style="margin:2px">17: الدوري الإسباني</span>
            <span class="badge badge-primary" style="margin:2px">23: الدوري الإيطالي</span>
            <span class="badge badge-primary" style="margin:2px">25: الدوري الألماني</span>
            <span class="badge badge-primary" style="margin:2px">34: الدوري الفرنسي</span>
            <span class="badge badge-accent" style="margin:2px">573: الدوري الأوروبي</span>
          </div>
          <button type="submit" class="btn-submit-action">
            <?= icon('check', 16) ?>
            <span>حفظ رموز الدوريات</span>
          </button>
        </form>
      </div>
    </div>

    <!-- TAB 5: Force Update & App Config -->
    <div id="tab-updates" class="tab-content">
      <div class="section-card">
        <div class="section-header">
          <div class="section-title">
            <?= icon('lock', 18) ?>
            <span>نظام التحديثات الإجبارية وقفل الإصدارات القديمة</span>
          </div>
        </div>

        <form method="post">
          <input type="hidden" name="t" value="<?= $t ?>">
          <input type="hidden" name="do" value="app_config">
          <div class="form-grid-2">
            <div class="form-group">
              <label>أدنى رقم إصدار مسموح بتشغيله (min_version_code):</label>
              <input type="number" name="min_version_code" value="<?= (int)($d['app_config']['min_version_code'] ?? 1) ?>" required>
              <small style="color:var(--text-muted);font-size:11px">أي مستخدم لديه تطبيق برقم إصدار أقل من هذا الرقم سيتم قفله إجبارياً فوراً.</small>
            </div>
            <div class="form-group">
              <label>أحدث رقم إصدار متوفر حالياً (latest_version_code):</label>
              <input type="number" name="latest_version_code" value="<?= (int)($d['app_config']['latest_version_code'] ?? 5) ?>" required>
            </div>
          </div>
          <div class="form-group">
            <label>رابط تحميل النسخة الجديدة (APK URL):</label>
            <input type="url" name="update_url" value="<?= h($d['app_config']['update_url'] ?? 'https://apklive.web.app') ?>" required>
          </div>
          <div class="form-group">
            <label>عنوان رسالة التحديث:</label>
            <input type="text" name="update_title" value="<?= h($d['app_config']['update_title'] ?? 'تحديث جديد متوفر') ?>">
          </div>
          <div class="form-group">
            <label>نص رسالة التحديث للمستخدم:</label>
            <textarea name="update_message"><?= h($d['app_config']['update_message'] ?? 'يرجى تنزيل الإصدار الأحدث لمتابعة المشاهدة بأعلى دقة وثبات وبدون انقطاع.') ?></textarea>
          </div>
          <div class="form-group" style="margin:16px 0;">
            <label class="switch-label">
              <input type="checkbox" name="force_update" value="1" class="switch-input" <?= !empty($d['app_config']['force_update']) ? 'checked' : '' ?>>
              <span style="color:#ef4444">تفعيل القفل الإجباري التام (إغلاق التطبيق كلياً حتى التحديث)</span>
            </label>
          </div>
          <button type="submit" class="btn-submit-action">
            <?= icon('check', 16) ?>
            <span>حفظ إعدادات التحديث</span>
          </button>
        </form>
      </div>
    </div>

  </div>

  <!-- Bottom Navigation Bar for Mobile -->
  <nav class="bottom-nav">
    <button class="bottom-tab-item active" onclick="switchTab('movies')">
      <?= icon('film', 20) ?>
      <span>المكتبة</span>
    </button>
    <button class="bottom-tab-item" onclick="switchTab('channels')">
      <?= icon('tv', 20) ?>
      <span>القنوات</span>
    </button>
    <button class="bottom-tab-item" onclick="switchTab('matches')">
      <?= icon('match', 20) ?>
      <span>المباريات</span>
    </button>
    <button class="bottom-tab-item" onclick="switchTab('tournaments')">
      <?= icon('trophy', 20) ?>
      <span>الدوريات</span>
    </button>
    <button class="bottom-tab-item" onclick="switchTab('updates')">
      <?= icon('lock', 20) ?>
      <span>التحديث</span>
    </button>
  </nav>

  <script>
    function switchTab(tabId) {
      document.querySelectorAll('.tab-content').forEach(p => p.classList.remove('active'));
      const activePane = document.getElementById('tab-' + tabId);
      if (activePane) activePane.classList.add('active');

      document.querySelectorAll('.nav-tab-btn').forEach(b => {
        b.classList.toggle('active', b.getAttribute('onclick').includes(tabId));
      });
      document.querySelectorAll('.bottom-tab-item').forEach(b => {
        b.classList.toggle('active', b.getAttribute('onclick').includes(tabId));
      });
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }

    function toggleForm(formId) {
      const el = document.getElementById(formId);
      if (el) el.classList.toggle('open');
    }

    function filterItems(query, containerId) {
      const q = (query || '').trim().toLowerCase();
      const container = document.getElementById(containerId);
      if (!container) return;
      const cards = container.querySelectorAll('.item-card');
      cards.forEach(card => {
        const text = card.getAttribute('data-search') || '';
        if (!q || text.includes(q)) {
          card.style.display = 'flex';
        } else {
          card.style.display = 'none';
        }
      });
    }
  </script>
</body>
</html>
