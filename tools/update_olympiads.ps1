<#
.SYNOPSIS
  Автоматическое обновление олимпиад и пересборка сайта «Звонок» на Windows (без установки Python).
.DESCRIPTION
  1. Читает data/olympiads.json.
  2. Полностью удаляет завершившиеся олимпиады, у которых прошло более 3 дней после даты окончания (endDate).
  3. Пересчитывает статусы олимпиад по текущему календарю («Идёт сейчас», «Скоро старт», «По графику»).
  4. Подтягивает дату ближайшего открытого онлайн-раунда с официального API Codeforces.
  5. Проверяет каждую ссылку HTTP-запросом (200 OK), чтобы на сайт не попадали нерабочие ссылки, и проверяет, что оффлайн-олимпиады проходят в Алматы.
  6. Сохраняет data/olympiads.json, site/olympiads.json и пересобирает site/index.html и site/sw.js.
#>
$ErrorActionPreference = "Stop"
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$ROOT = Split-Path -Parent $PSScriptRoot
$SRC = Join-Path $ROOT "site\src"
$OUT = Join-Path $ROOT "site"
$DATA_PATH = Join-Path $ROOT "data\olympiads.json"
$SITE_URL = "https://danick-dack.github.io/Zvonok/"
$APK_URL = "https://github.com/Danick-Dack/Zvonok/releases/latest/download/Zvonok-165.apk"

function Read-Utf8([string]$path) {
    return ([System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)) -replace "`r`n", "`n"
}

function Write-Utf8([string]$path, [string]$text) {
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($path, ($text -replace "`r`n", "`n"), $utf8NoBom)
}

function Test-OlympUrl([string]$url) {
    if (-not $url -or -not $url.StartsWith("https://")) { return $false }
    try {
        $req = [System.Net.HttpWebRequest]::Create($url)
        $req.Method = "GET"
        $req.UserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/131.0.0.0"
        $req.Timeout = 8000
        $resp = [System.Net.HttpWebResponse]$req.GetResponse()
        $code = [int]$resp.StatusCode
        $resp.Close()
        return ($code -ge 200 -and $code -lt 400)
    } catch {
        return $false
    }
}

Write-Host "=== Проверка и обновление списка олимпиад ===" -ForegroundColor Cyan
$olympObj = (Read-Utf8 $DATA_PATH) | ConvertFrom-Json
$today = (Get-Date).Date
$monthsRu = @("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря")

$kept = New-Object System.Collections.Generic.List[object]
$removedCount = 0

foreach ($item in $olympObj.items) {
    # 1. Проверка даты окончания: полное удаление, если прошло > 3 дней после endDate
    if ($item.endDate -and $item.endDate -match '^\d{4}-\d\d-\d\d$') {
        $endDt = [datetime]::ParseExact($item.endDate, "yyyy-MM-dd", [System.Globalization.CultureInfo]::InvariantCulture)
        $daysPassed = ($today - $endDt).TotalDays
        if ($daysPassed -gt 3) {
            Write-Host "  [Удалена (>3 дней после окончания $($item.endDate))] $($item.title)" -ForegroundColor Yellow
            $removedCount++
            continue
        }
    }

    # 2. Если олимпиада оффлайн — проверяем, что она проходит в Алматы
    if ($item.place -match 'Оффлайн' -and $item.place -notmatch 'Алматы') {
        Write-Host "  [Пропущена иногородняя оффлайн-олимпиада] $($item.title)" -ForegroundColor Yellow
        continue
    }

    # 3. Автоматическое обновление статуса по дате начала
    if ($item.startDate -and $item.startDate -match '^\d{4}-\d\d-\d\d$') {
        $startDt = [datetime]::ParseExact($item.startDate, "yyyy-MM-dd", [System.Globalization.CultureInfo]::InvariantCulture)
        $daysToStart = ($startDt - $today).TotalDays
        if ($daysToStart -le 0 -and @("По графику", "Зимний финал", "Весенний набор", "Скоро старт") -contains $item.status) {
            $item.status = "Идёт сейчас"
            $item.statusKind = "hot"
        } elseif ($daysToStart -gt 0 -and $daysToStart -le 21 -and $item.status -eq "По графику") {
            $item.status = "Скоро старт"
            $item.statusKind = "warn"
        }
    }

    $kept.Add($item)
}

# 4. Живое обновление ближайшего раунда Codeforces по открытому API
try {
    $cfResp = Invoke-RestMethod -Uri "https://codeforces.com/api/contest.list?gym=false" -TimeoutSec 8
    if ($cfResp.status -eq "OK" -and $cfResp.result) {
        $nowSec = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
        $upcoming = $cfResp.result | Where-Object { $_.phase -eq "BEFORE" -and $_.startTimeSeconds -gt $nowSec } | Sort-Object startTimeSeconds
        if ($upcoming) {
            $nextRound = $upcoming[0]
            $dt = [DateTimeOffset]::FromUnixTimeSeconds($nextRound.startTimeSeconds).ToOffset([TimeSpan]::FromHours(5))
            $dateStr = "$($dt.Day) $($monthsRu[$dt.Month - 1])"
            foreach ($it in $kept) {
                if ($it.id -eq "codeforces-rounds") {
                    $it.status = "Ближайший раунд"
                    $it.statusKind = "hot"
                    $it.dateText = "$dateStr · 2–3 раунда в неделю"
                    $it.endDate = $today.AddDays(180).ToString("yyyy-MM-dd")
                    Write-Host "  [Codeforces] Ближайший раунд: $dateStr ($($nextRound.name))" -ForegroundColor Green
                }
            }
        }
    }
} catch {
    Write-Host "  [Codeforces API] Офлайн-режим, сохранены текущие данные" -ForegroundColor DarkGray
}

# 5. Проверка работоспособности ссылок (HTTP 200 OK)
$urlCache = @{}
$verified = New-Object System.Collections.Generic.List[object]
foreach ($item in $kept) {
    $u = $item.url
    if (-not $urlCache.ContainsKey($u)) {
        $urlCache[$u] = Test-OlympUrl $u
    }
    if ($urlCache[$u]) {
        $verified.Add($item)
    } else {
        # Если нет интернета, сохраняем проверенные официальные домены из белого списка
        if ($u -match 'daryny\.kz|daryn\.kz|matol\.kz|fizmat\.kz|izho\.kz|smart-arena\.kz|usaco\.org|physicscup\.ee|turgor\.ru|bc-pf\.org|olimpiada\.ru|cdo\.kz|codeforces\.com') {
            $verified.Add($item)
        } else {
            Write-Host "  [Отклонена нерабочая ссылка] $u" -ForegroundColor Red
        }
    }
}

$olympObj.updated = $today.ToString("yyyy-MM-dd")
$olympObj.items = $verified.ToArray()

$OLYMPIADS_PRETTY = ($olympObj | ConvertTo-Json -Depth 10)
$OLYMPIADS_JSON = ($olympObj | ConvertTo-Json -Depth 10 -Compress)
Write-Utf8 $DATA_PATH $OLYMPIADS_PRETTY
Write-Utf8 (Join-Path $OUT "olympiads.json") $OLYMPIADS_PRETTY

Write-Host "Олимпиады сохранены: активно $($verified.Count), удалено устаревших (>3 дней): $removedCount" -ForegroundColor Green

# 6. Пересборка site/index.html и site/sw.js со свежими данными олимпиад
function Get-Rgba([string]$hexc, [string]$a) {
    $r = [Convert]::ToInt32($hexc.Substring(1, 2), 16)
    $g = [Convert]::ToInt32($hexc.Substring(3, 2), 16)
    $b = [Convert]::ToInt32($hexc.Substring(5, 2), 16)
    return "rgba($r,$g,$b,$a)"
}

function Get-Themed([string]$selPrefix, [string]$selSuffix, [string]$light, [string]$dark) {
    $s1 = "${selPrefix}:root${selSuffix}{$light}"
    $s2 = "@media (prefers-color-scheme:dark){${selPrefix}:root:not([data-theme=`"light`"])${selSuffix}{$dark}}"
    $s3 = "${selPrefix}:root[data-theme=`"dark`"]${selSuffix}{$dark}"
    return "$s1`n$s2`n$s3"
}

$ACCENTS = @(
    @("blue", "Синий", "#0062DA", "#FFFFFF", "#409CFF", "#0B0B0C"),
    @("indigo", "Индиго", "#3D3BB8", "#FFFFFF", "#8B89FF", "#0B0B0C"),
    @("purple", "Фиолетовый", "#8944AB", "#FFFFFF", "#DA8FFF", "#0B0B0C"),
    @("pink", "Розовый", "#C70F45", "#FFFFFF", "#FF6482", "#0B0B0C"),
    @("red", "Красный", "#D70015", "#FFFFFF", "#FF6961", "#0B0B0C"),
    @("orange", "Оранжевый", "#B83D00", "#FFFFFF", "#FFB340", "#0B0B0C"),
    @("green", "Зелёный", "#1F7A36", "#FFFFFF", "#30DB5B", "#0B0B0C"),
    @("mint", "Мятный", "#0B7570", "#FFFFFF", "#66D4CF", "#0B0B0C"),
    @("mono", "Без цвета", "#141414", "#FFFFFF", "#F3F3F1", "#0B0B0C")
)

$HUES = [ordered]@{
    "red"    = @("#FF3B30", "#C40013", "#FF453A", "#FF6961")
    "orange" = @("#FF9500", "#B83D00", "#FF9F0A", "#FFB340")
    "yellow" = @("#FFCC00", "#8F5A00", "#FFD60A", "#FFD426")
    "green"  = @("#34C759", "#1F7A36", "#30D158", "#30DB5B")
    "mint"   = @("#00C7BE", "#0B7570", "#63E6E2", "#66D4CF")
    "teal"   = @("#30B0C7", "#006F83", "#40CBE0", "#5DE6FF")
    "cyan"   = @("#32ADE6", "#006A9A", "#64D2FF", "#70D7FF")
    "blue"   = @("#007AFF", "#0058D6", "#0A84FF", "#409CFF")
    "indigo" = @("#5856D6", "#3D3BB8", "#5E5CE6", "#8B89FF")
    "purple" = @("#AF52DE", "#8944AB", "#BF5AF2", "#DA8FFF")
    "pink"   = @("#FF2D55", "#BA0E40", "#FF375F", "#FF6482")
    "brown"  = @("#A2845E", "#765C3E", "#AC8E68", "#C4A27A")
    "gray"   = @("#8E8E93", "#636366", "#8E8E93", "#AEAEB2")
}

function Get-AccentCss() {
    $out = New-Object System.Collections.Generic.List[string]
    for ($i = 1; $i -lt $ACCENTS.Count; $i++) {
        $a = $ACCENTS[$i]
        $key = $a[0]; $la = $a[2]; $li = $a[3]; $da = $a[4]; $di = $a[5]
        $lStr = "--accent:$la;--accent-ink:$li;--accent-soft:$(Get-Rgba $la '.1')"
        $dStr = "--accent:$da;--accent-ink:$di;--accent-soft:$(Get-Rgba $da '.16')"
        $out.Add((Get-Themed "" "[data-accent=`"$key`"]" $lStr $dStr))
    }
    foreach ($key in $HUES.Keys) {
        $v = $HUES[$key]
        $lv = $v[0]; $lt = $v[1]; $dv = $v[2]; $dt = $v[3]
        $lStr = "--sv:$lv;--st:$lt;--ss:$(Get-Rgba $lv '.2');--sf:$lt;--si:#FFFFFF"
        $dStr = "--sv:$dv;--st:$dt;--ss:$(Get-Rgba $dv '.24');--sf:$dt;--si:#0B0B0C"
        $out.Add((Get-Themed "" " .hue-$key" $lStr $dStr))
    }
    return ($out -join "`n")
}

function Min-Css([string]$css) {
    $pattern = '("(?:[^"\\]|\\.)*"|''(?:[^''\\]|\\.)*'')'
    $parts = [regex]::Split($css, $pattern)
    $sb = New-Object System.Text.StringBuilder
    for ($i = 0; $i -lt $parts.Length; $i++) {
        $p = $parts[$i]
        if ($i % 2 -eq 1) { [void]$sb.Append($p); continue }
        $p = [regex]::Replace($p, '/\*[\s\S]*?\*/', '')
        $p = [regex]::Replace($p, '\s+', ' ')
        $p = [regex]::Replace($p, '\s*([{};,>])\s*', '$1')
        $p = [regex]::Replace($p, ':\s+', ':')
        $p = $p.Replace(';}', '}')
        [void]$sb.Append($p)
    }
    return $sb.ToString().Trim()
}

function Bundle-Css([string[]]$names) {
    $chunks = foreach ($n in $names) { Read-Utf8 (Join-Path $SRC "css\$n.css") }
    $css = ($chunks -join "`n").Replace("/*@ACCENTS@*/", (Get-AccentCss))
    return (Min-Css $css)
}

$ACCENTS_JSON = '[{"id":"blue","name":"Синий","light":"#0062DA","dark":"#409CFF"},{"id":"indigo","name":"Индиго","light":"#3D3BB8","dark":"#8B89FF"},{"id":"purple","name":"Фиолетовый","light":"#8944AB","dark":"#DA8FFF"},{"id":"pink","name":"Розовый","light":"#C70F45","dark":"#FF6482"},{"id":"red","name":"Красный","light":"#D70015","dark":"#FF6961"},{"id":"orange","name":"Оранжевый","light":"#B83D00","dark":"#FFB340"},{"id":"green","name":"Зелёный","light":"#1F7A36","dark":"#30DB5B"},{"id":"mint","name":"Мятный","light":"#0B7570","dark":"#66D4CF"},{"id":"mono","name":"Без цвета","light":"#141414","dark":"#F3F3F1"}]'

function Bundle-Js([string[]]$names) {
    $chunks = foreach ($n in $names) { Read-Utf8 (Join-Path $SRC "js\$n.js") }
    return ($chunks -join "`n").Replace("/*@ACCENTS@*/null", $ACCENTS_JSON)
}

$ICONS = @{
    "now"      = '<circle cx="12" cy="12" r="8.6"/><path d="M12 7.6V12l2.9 1.9"/>'
    "week"     = '<rect x="3.6" y="5" width="16.8" height="15.4" rx="3"/><path d="M3.6 9.8h16.8M8.2 3v3.6M15.8 3v3.6"/>'
    "door"     = '<path d="M6 20.6V4.8a1.2 1.2 0 0 1 1.2-1.2h9.6A1.2 1.2 0 0 1 18 4.8v15.8M3.8 20.6h16.4"/><circle cx="14.6" cy="12.4" r=".7" fill="currentColor"/>'
    "more"     = '<circle cx="5.6" cy="12" r="1.1" fill="currentColor"/><circle cx="12" cy="12" r="1.1" fill="currentColor"/><circle cx="18.4" cy="12" r="1.1" fill="currentColor"/>'
    "search"   = '<circle cx="10.6" cy="10.6" r="6.2"/><path d="M15.2 15.2l4.8 4.8"/>'
    "down"     = '<path d="M6.5 9.5l5.5 5.5 5.5-5.5"/>'
    "right"    = '<path d="M9.5 6.5l5.5 5.5-5.5 5.5"/>'
    "close"    = '<path d="M6.5 6.5l11 11M17.5 6.5l-11 11"/>'
    "sun"      = '<circle cx="12" cy="12" r="3.8"/><path d="M12 2.8v2.1M12 19.1v2.1M4.9 4.9l1.5 1.5M17.6 17.6l1.5 1.5M2.8 12h2.1M19.1 12h2.1M4.9 19.1l1.5-1.5M17.6 6.4l1.5-1.5"/>'
    "moon"     = '<path d="M19.8 14.6A8.2 8.2 0 0 1 9.4 4.2a8.2 8.2 0 1 0 10.4 10.4z"/>'
    "auto"     = '<circle cx="12" cy="12" r="8.4"/><path d="M12 3.6v16.8a8.4 8.4 0 0 0 0-16.8z" fill="currentColor"/>'
    "share"    = '<path d="M12 3.4v11.2M8.2 7.2L12 3.4l3.8 3.8"/><path d="M8.4 10.6H6.6a1.6 1.6 0 0 0-1.6 1.6v6.8a1.6 1.6 0 0 0 1.6 1.6h10.8a1.6 1.6 0 0 0 1.6-1.6v-6.8a1.6 1.6 0 0 0-1.6-1.6h-1.8"/>'
    "addsq"    = '<rect x="4" y="4" width="16" height="16" rx="4.2"/><path d="M12 8.4v7.2M8.4 12h7.2"/>'
    "download" = '<path d="M12 4v11M7.6 10.8L12 15.2l4.4-4.4M5 19.8h14"/>'
    "copy"     = '<rect x="8.4" y="8.4" width="11.6" height="11.6" rx="2.6"/><path d="M15.6 8.4V6.6A2.6 2.6 0 0 0 13 4H6.6A2.6 2.6 0 0 0 4 6.6V13a2.6 2.6 0 0 0 2.6 2.6h1.8"/>'
    "check"    = '<path d="M5.2 12.6l4.3 4.3 9.3-9.6"/>'
    "out"      = '<path d="M13.6 4.2h6.2v6.2M19.6 4.4l-8.4 8.4M18 14v4.6a1.4 1.4 0 0 1-1.4 1.4H5.4A1.4 1.4 0 0 1 4 18.6V7.4A1.4 1.4 0 0 1 5.4 6H10"/>'
    "bell"     = '<path d="M6.2 16.4V11a5.8 5.8 0 0 1 11.6 0v5.4l1.6 1.8H4.6z"/><path d="M10 20.4a2.1 2.1 0 0 0 4 0"/>'
    "edit"     = '<path d="M4.4 19.6h4L19 9l-4-4L4.4 15.6z"/><path d="M13.4 6.6l4 4"/>'
    "phone"    = '<rect x="6.6" y="2.8" width="10.8" height="18.4" rx="2.6"/><path d="M10.6 18.2h2.8"/>'
    "siri"     = '<path d="M12 3.6a3 3 0 0 1 3 3v5a3 3 0 0 1-6 0v-5a3 3 0 0 1 3-3z"/><path d="M6.4 11.4a5.6 5.6 0 0 0 11.2 0M12 17v3.4"/>'
    "plus"     = '<path d="M12 5.4v13.2M5.4 12h13.2"/>'
    "help"     = '<circle cx="12" cy="12" r="8.6"/><path d="M9.6 9.4a2.5 2.5 0 0 1 4.8.9c0 1.7-2.4 2.2-2.4 3.7"/><circle cx="12" cy="17" r=".6" fill="currentColor"/>'
    "info"     = '<circle cx="12" cy="12" r="8.6"/><path d="M12 11v5.4"/><circle cx="12" cy="7.9" r=".6" fill="currentColor"/>'
    "lock"     = '<rect x="5" y="10.4" width="14" height="10" rx="2.4"/><path d="M8.2 10.4V8a3.8 3.8 0 0 1 7.6 0v2.4"/>'
    "trash"    = '<path d="M5 7h14M10 4.4h4M7 7l.8 12.2a1.4 1.4 0 0 0 1.4 1.3h5.6a1.4 1.4 0 0 0 1.4-1.3L17 7"/>'
    "cup"      = '<path d="M7 4h10v5a5 5 0 0 1-10 0V4zM7 6H4.5a1.5 1.5 0 0 0-1.5 1.5v.5a4 4 0 0 0 4 4M17 6h2.5A1.5 1.5 0 0 1 21 7.5v.5a4 4 0 0 1-4 4M12 14v5M8.5 19h7"/>'
}

function Get-IconSprite([string]$page) {
    $usedSet = New-Object System.Collections.Generic.HashSet[string]
    foreach ($m in [regex]::Matches($page, 'href="#i-([a-z]+)"')) { [void]$usedSet.Add($m.Groups[1].Value) }
    foreach ($m in [regex]::Matches($page, 'icon\("([a-z]+)"')) { [void]$usedSet.Add($m.Groups[1].Value) }
    $used = @($usedSet) | Sort-Object
    $sb = New-Object System.Text.StringBuilder
    [void]$sb.Append('<svg width="0" height="0" style="position:absolute" aria-hidden="true">')
    foreach ($n in $used) { [void]$sb.Append("<symbol id=`"i-$n`" viewBox=`"0 0 24 24`">$($ICONS[$n])</symbol>") }
    [void]$sb.Append('</svg>')
    return $sb.ToString()
}

function Get-Csp([string]$page, [string]$connect = "'self'") {
    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    $hashSet = New-Object System.Collections.Generic.HashSet[string]
    foreach ($m in [regex]::Matches($page, '(?s)<script[^>]*>(.*?)</script>')) {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($m.Groups[1].Value)
        $b64 = [Convert]::ToBase64String($sha256.ComputeHash($bytes))
        [void]$hashSet.Add("'sha256-$b64'")
    }
    $sorted = @($hashSet) | Sort-Object -CaseSensitive
    $scripts = $sorted -join " "
    return "default-src 'self'; script-src $scripts; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; connect-src $connect; manifest-src 'self' blob:; worker-src 'self'; object-src 'none'; base-uri 'none'; form-action 'none'"
}

$MARK = '<svg viewBox="25 25 58 58" aria-hidden="true"><circle class="mark-track" cx="54" cy="54" r="25" fill="none" stroke-width="5.5"/><g fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><path d="M54 29a25 25 0 1 1-25 25" stroke-width="5.5"/><path d="M54 41v13l7.5 4.3" stroke-width="5"/></g><circle cx="54" cy="54" r="3.4" fill="currentColor"/></svg>'
$HEAD_PC = '(function(){try{var L=location,S=sessionStorage;if(/^http/.test(L.protocol)&&!L.hash&&!new URLSearchParams(L.search).has("app")&&!(navigator.standalone||matchMedia("(display-mode: standalone)").matches)&&matchMedia("(hover: hover) and (pointer: fine) and (min-width: 1024px)").matches&&localStorage.getItem("zv.pcApp")!=="true"&&!S.getItem("zv.toAbout")){S.setItem("zv.toAbout","1");L.replace("about.html")}}catch(e){}})();'
$HEAD_JS = '(function(){try{if(top!==self)void top.location.href}catch(e){document.documentElement.style.display="none"}})();(function(){try{var d=document.documentElement,g=function(k){return JSON.parse(localStorage.getItem("zv."+k))},a=g("accent"),s=g("scheme");if(typeof a==="string"&&/^[a-z]+$/.test(a)&&a!=="blue")d.setAttribute("data-accent",a);if(g("plain")===true)d.setAttribute("data-plain","");if(s==="light"||s==="dark"){d.setAttribute("data-theme",s);var m=document.querySelectorAll(''meta[name="theme-color"]'');for(var i=0;i<m.length;i++)m[i].setAttribute("content",s==="dark"?"#0B0B0C":"#F4F4F1")}}catch(e){}})();'

$existingPoster = Read-Utf8 (Join-Path $OUT "poster.html")
$FAVICON = ([regex]::Match($existingPoster, '<link rel="icon" href="(data:image/png;base64,[^"]+)">')).Groups[1].Value

$SPLASH = @(
    @(375, 667, 2), @(414, 736, 3), @(375, 812, 3), @(414, 896, 2), @(414, 896, 3), @(390, 844, 3), @(428, 926, 3),
    @(393, 852, 3), @(430, 932, 3), @(402, 874, 3), @(420, 912, 3), @(440, 956, 3)
)
$startupLinks = New-Object System.Collections.Generic.List[string]
foreach ($s in $SPLASH) {
    $w = $s[0]; $h = $s[1]; $k = $s[2]
    foreach ($dark in @($false, $true)) {
        $W = $w * $k; $H = $h * $k
        $dSuffix = if ($dark) { "d" } else { "" }
        $scheme = if ($dark) { "dark" } else { "light" }
        $startupLinks.Add("<link rel=`"apple-touch-startup-image`" href=`"splash/${W}x${H}${dSuffix}.png`" media=`"screen and (device-width:${w}px) and (device-height:${h}px) and (-webkit-device-pixel-ratio:$k) and (orientation:portrait) and (prefers-color-scheme:$scheme)`">")
    }
}
$STARTUP = $startupLinks -join "`n"
$JSONLD = '{"@context":"https://schema.org","@type":"WebApplication","name":"Звонок","url":"https://danick-dack.github.io/Zvonok/","inLanguage":"ru","description":"Расписание лицея №165: сколько до звонка, какой урок следующий и в каком кабинете.","applicationCategory":"EducationalApplication","operatingSystem":"Android, iOS, Web","isAccessibleForFree":true,"offers":{"@type":"Offer","price":"0","priceCurrency":"KZT"}}'
$DATA_JSON = (Read-Utf8 (Join-Path $OUT "schedule.json")).Trim()
$CHANGES_JSON = '{"updated":"2026-11-01","days":{}}'
$HUES_JSON = '{"Алгебра":"blue","Геометрия":"blue","Математика":"blue","Физика":"purple","Практикум по физике":"purple","Казахский яз. и лит.":"yellow","Русский язык":"red","Русская литература":"red","Английский язык":"cyan","История Казахстана":"brown","Всемирная история":"brown","География":"mint","Биология":"green","Химия":"teal","Информатика":"indigo","Программирование":"indigo","Физкультура":"orange","Худ. труд":"pink","Классный час":"gray","Основы права":"brown","НВТП":"green","Автодело":"orange"}'
$QRS_JSON = (Read-Utf8 (Join-Path $ROOT "app\src\main\assets\qr.json")).Trim()

$existingIndex = Read-Utf8 (Join-Path $OUT "index.html")
$newsLogMatch = [regex]::Match($existingIndex, 'const NEWS = (\{.*?\}), LOG = (\[.*?\]);\n')
$NEWS_JSON = $newsLogMatch.Groups[1].Value
$LOG_JSON = $newsLogMatch.Groups[2].Value

$t = Read-Utf8 (Join-Path $SRC "index.html")
$jsText = Bundle-Js @("core", "embed", "schedule", "rules", "app", "now", "week", "rooms", "more", "olymp", "snake", "start")
$extra = @{
    "/*@DATA@*/null"      = $DATA_JSON
    "/*@CHANGES@*/null"   = $CHANGES_JSON
    "/*@HUES@*/null"      = $HUES_JSON
    "/*@QRS@*/null"       = $QRS_JSON
    "/*@NEWS@*/null"      = $NEWS_JSON
    "/*@LOG@*/[]"         = $LOG_JSON
    "/*@OLYMPIADS@*/null" = $OLYMPIADS_JSON
}
foreach ($k in $extra.Keys) {
    $jsText = $jsText.Replace($k, $extra[$k])
    $t = $t.Replace($k, $extra[$k])
}
$t = $t.Replace("@JS@", $jsText)
$t = $t.Replace("@CSS@", (Bundle-Css @("tokens", "base", "components", "app")))
$t = $t.Replace("@HEAD_JS@", ($HEAD_JS + $HEAD_PC))
$t = $t.Replace("@MARK@", $MARK)
$t = $t.Replace("@FAVICON@", $FAVICON)
$t = $t.Replace("@SITE_URL@", $SITE_URL)
$t = $t.Replace("@APK_URL@", $APK_URL)
$t = $t.Replace("@STARTUP@", $STARTUP)
$t = $t.Replace("@JSONLD@", $JSONLD)
$t = $t.Replace("@ICONS@", (Get-IconSprite $t))
$t = $t.Replace("@CSP@", (Get-Csp $t "'self' https://codeforces.com"))
Write-Utf8 (Join-Path $OUT "index.html") $t

$shell = @("./", "index.html", "about.html", "make.html", "iphone.html", "changelog.html", "feedback.html", "poster.html",
           "manifest.webmanifest", "icon-192.png", "icon-512.png", "icon-maskable.png", "apple-touch-icon.png", "changes.json", "olympiads.json")
$sha = [System.Security.Cryptography.SHA256]::Create()
$ms = New-Object System.IO.MemoryStream
foreach ($f in ($shell[1..($shell.Length - 1)] + @("schedule.json"))) {
    $bytes = [System.IO.File]::ReadAllBytes((Join-Path $OUT $f))
    $ms.Write($bytes, 0, $bytes.Length)
}
$hashBytes = $sha.ComputeHash($ms.ToArray())
$verHex = ([BitConverter]::ToString($hashBytes).Replace("-", "").ToLower()).Substring(0, 10)
$shellJson = '["./", "index.html", "about.html", "make.html", "iphone.html", "changelog.html", "feedback.html", "poster.html", "manifest.webmanifest", "icon-192.png", "icon-512.png", "icon-maskable.png", "apple-touch-icon.png", "changes.json", "olympiads.json"]'
$sw = (Read-Utf8 (Join-Path $SRC "sw.js")).Replace("@VERSION@", $verHex).Replace("/*@SHELL@*/[]", $shellJson)
Write-Utf8 (Join-Path $OUT "sw.js") $sw

Write-Host "Сайт успешно пересобран! (SW: $verHex)" -ForegroundColor Cyan
