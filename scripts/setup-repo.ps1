# Podešava GitHub repo fpetrovicc/CamacGPS. Pokreće se jednom, iz foldera projekta:
#   powershell -ExecutionPolicy Bypass -File .\scripts\setup-repo.ps1
# Može da se pokrene i ponovo, ništa se neće duplirati.

$ErrorActionPreference = "Stop"
$repo = "fpetrovicc/CamacGPS"

function Step($text) { Write-Host "`n== $text" -ForegroundColor Green }
function ApiJson($method, $path, $json) {
    $tmp = New-TemporaryFile
    [IO.File]::WriteAllText($tmp.FullName, $json, (New-Object Text.UTF8Encoding $false))
    gh api -X $method $path --input $tmp.FullName --silent
    Remove-Item $tmp
}

gh auth status *> $null
if ($LASTEXITCODE -ne 0) { Write-Host "Prvo pokreni: gh auth login" -ForegroundColor Yellow; exit 1 }

Step "Opis, sajt i teme"
gh repo edit $repo `
    --description "Android aplikacija za upravljanje GPS trackerom (TKSTAR TK915) na pecaroškom čamcu preko SMS-a. Android app to control a boat GPS tracker via SMS." `
    --homepage "https://github.com/$repo/releases/latest" `
    --add-topic "android,kotlin,jetpack-compose,material3,gps-tracker,sms,fishing,boat,tk915,tkstar"

Step "Iskljucujem nepotrebno: Wiki, Projects, Discussions"
gh repo edit $repo --enable-wiki=false --enable-projects=false --enable-discussions=false

Step "Ukljucujem korisno: Issues, samo squash merge, brisanje grane posle merge-a"
gh repo edit $repo `
    --enable-issues `
    --enable-squash-merge --enable-merge-commit=false --enable-rebase-merge=false `
    --delete-branch-on-merge --allow-update-branch --enable-auto-merge

Step "Bezbednost: Dependabot upozorenja i ispravke, privatne prijave ranjivosti"
gh api -X PUT "repos/$repo/vulnerability-alerts" --silent
gh api -X PUT "repos/$repo/automated-security-fixes" --silent
gh api -X PUT "repos/$repo/private-vulnerability-reporting" --silent

Step "Bezbednost: secret scanning i push protection (blokira push ako u kodu ostane lozinka ili kljuc)"
ApiJson PATCH "repos/$repo" '{"security_and_analysis":{"secret_scanning":{"status":"enabled"},"secret_scanning_push_protection":{"status":"enabled"}}}'

Step "Actions: podrazumevano samo citanje (release workflow sam trazi pravo upisa)"
ApiJson PUT "repos/$repo/actions/permissions/workflow" '{"default_workflow_permissions":"read","can_approve_pull_request_reviews":false}'

Step "Zastita main grane: zabranjeno brisanje i force push"
$existing = gh api "repos/$repo/rulesets" --jq ".[].name"
if ($existing -notcontains "main") {
    ApiJson POST "repos/$repo/rulesets" '{"name":"main","target":"branch","enforcement":"active","conditions":{"ref_name":{"include":["~DEFAULT_BRANCH"],"exclude":[]}},"rules":[{"type":"deletion"},{"type":"non_fast_forward"}]}'
} else { Write-Host "vec postoji" }

Step "Labela za druge modele trackera"
gh label create tracker --repo $repo --color 2E5E4E --description "Podrška za drugi model trackera" --force

Write-Host "`nGotovo. Jos dve stvari se rade rucno na sajtu (GitHub ih ne nudi preko API-ja):" -ForegroundColor Cyan
Write-Host "  1. Settings > General > Social preview > Edit > Upload: docs\images\social-preview.png"
Write-Host "  2. Na glavnoj strani repoa, zupcanik pored 'About': iskljuci 'Packages' i 'Deployments', ostavi 'Releases'"
