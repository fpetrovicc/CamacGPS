# CLAUDE.md — Čamac GPS

Kontekst za Claude Code. Pročitaj celo pre rada. Privatni detalji (tracker, SIM, server, korisnik)
su u `CLAUDE.local.md` (nije na git-u).

## Ukratko

Android aplikacija (Kotlin + Jetpack Compose, Material 3) koja upravlja **TKSTAR TK915** GPS trackerom
na pecaroškom čamcu **isključivo preko SMS-a**: šalje komande (`G123456#`, `upload123456 60`…) i parsira
odgovore iz sistemskog SMS sandučeta. Nema backend-a. Open source, MIT.

- Repo: https://github.com/fpetrovicc/CamacGPS (javan, grana `main`)
- Lokalno: `D:\Projects\Github\CamacGPS` (Windows 11, PowerShell)
- Paket: `com.camac.tracker`, debug: `com.camac.tracker.debug` („Čamac GPS (test)”)
- Verzija: `versionCode 2`, `versionName "1.1.0"`, minSdk 26, targetSdk/compileSdk 35
- Ciljni telefon korisnika: Samsung Galaxy A56 (Android 15+, One UI)
- **Jezik:** korisnik (Filip) piše na srpskom, latinicom — odgovaraj tako. Sav tekst u aplikaciji je
  srpski latinica. Kod i komentari: komentari na srpskom, identifikatori na engleskom.

## ⚠️ Trenutno stanje (2026-10-24)

1. **Kod nikad nije uspešno kompajliran.** Pisan je u okruženju bez Android SDK-a. Data sloj
   (`TrackerParser`, `CommandCatalog`, `Weather`, poređenje verzija) je proveren `kotlinc 2.0.21`
   na stvarnim porukama trackera. UI/ViewModel/Android delovi nisu.
2. **GitHub Actions build pada** u koraku *Build debug APK* (svi run-ovi, uključujući commit `7ec1586`).
   Log se ne vidi bez autentikacije — dobij ga sa:
   ```powershell
   gh run list --limit 5
   gh run view <RUN_ID> --log-failed
   ```
   ili lokalno: `.\gradlew.bat assembleDebug --stacktrace` (vidi JDK napomenu ispod).
3. **Nekomitovane izmene** koje je napravio Android Studio (treba ih zadržati i commit-ovati):
   - `build.gradle.kts`: AGP `8.7.3` → `8.13.2`
   - `gradle/wrapper/gradle-wrapper.properties`: Gradle `8.9` → `8.13`
   - `settings.gradle.kts`: dodat plugin `org.gradle.toolchains.foojay-resolver-convention` `0.10.0`
4. **Lokalni Gradle keš je bio korumpiran** (`Could not read workspace metadata … metadata.bin`
   u `C:\Users\fpetr\.gradle\caches\8.13\…`). Rešenje: zatvori Android Studio, `.\gradlew.bat --stop`,
   obriši `$HOME\.gradle\caches\8.13` i `.gradle` u projektu, dodaj Defender izuzetak za
   `$HOME\.gradle` i folder projekta, pa sync. Ako se ponovi — Defender/antivirus.
5. **Dependabot je otvorio 8 PR-ova** (#1–#8). Plan:
   - **Zatvoriti #7 (AGP 9.4.1) i #8 (Gradle 9.8.2)** — major verzije, ne sada (AGP 9 menja DSL,
     traži Gradle 9). Dodati u `.github/dependabot.yml` `ignore` za `version-update:semver-major`
     na `com.android.application` i `gradle-wrapper`.
   - #1 checkout→v7, #2 upload-artifact→v7, #3 setup-java→v6, #5 gradle/actions→v6: primeniti
     (ili merge-ovati) **posle** prvog zelenog build-a. Uklanjaju upozorenja o Node 20.
   - #4 androidx grupa, #6 kotlin grupa: tek kad build prođe; Kotlin plugin i compose plugin
     moraju ostati iste verzije.
6. Repo podešavanja su primenjena skriptom `scripts/setup-repo.ps1` (opis, teme, Wiki/Projects/
   Discussions isključeni, Dependabot, secret scanning, push protection, ruleset na `main`).
   **Ruleset zabranjuje force push na `main`.** Ručno ostaje: Social preview
   (`docs/images/social-preview.png`) i u *About* isključiti Packages/Deployments.
7. Secrets za potpisivanje su postavljeni (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`,
   `KEY_PASSWORD`) — proveri `gh secret list`. `camac.jks` i `keystore.properties` su u korenu
   projekta, ignorisani. **Nikad ih ne čitaj naglas, ne štampaj i ne commit-uj.** Proveri da
   `keystore.properties` nema placeholder `TVOJA_LOZINKA` (korisnik ga je možda tako ostavio) —
   samo pitaj korisnika, ne ispisuj sadržaj.
8. **Release `v1.1.0` još nije objavljen.** Prvo zeleni build, pa tag.

### Prioriteti
1. Popraviti lokalni Gradle (tačka 4) ili samo čitati CI log.
2. Ispraviti greške kompajliranja dok `assembleDebug` ne prođe lokalno i na CI-ju.
3. Commit izmena iz tačke 3 + popravki; push (bez force).
4. Srediti Dependabot (tačka 5).
5. `git tag v1.1.0 && git push origin v1.1.0` → Release sa potpisanim APK-om → instalacija na tatin telefon.

### Gde je najverovatnije da kompajliranje pukne
- **Ikonice iz `material-icons-extended`** koje nisu sigurno potvrđene: `Phishing`, `Air`, `NightsStay`,
  `WbTwilight`, `Thermostat`, `AdminPanelSettings`, `BatteryChargingFull`, `CloudUpload`,
  `DeleteOutline`, `TableChart`, `Timeline`, `Outlined.Phishing`, `Outlined.Speed`. Ako neka ne postoji,
  zameni sličnom (npr. `Phishing`→`SetMeal`/`DirectionsBoat`, `Air`→`Toys`, `WbTwilight`→`WbSunny`).
- Compose API potpisi (BOM `2024.10.01`, material3 1.3.x): `CircularProgressIndicator(progress = { … })`,
  `LinearProgressIndicator(progress = { … })`, `Path.relativeQuadraticBezierTo` (deprecated, ok).
- Smart cast-ovi na `SmsMessage.reply` (to je `by lazy` — **ne može** smart cast; uvek u lokalni `val`).
- `return@InfoCard` / `return@Column` / `return@LazyColumn` lambda labele.
- Lint je namerno neblokirajući (`abortOnError = false`, `checkReleaseBuilds = false`).

## Alati na Filipovom računaru

- Android Studio (nije u `C:\Program Files\Android`, pa tamošnji `jbr\bin\keytool.exe` ne postoji).
- **Gradle JDK: `C:\Users\fpetr\.jdks\jbr-21.0.11`** (JetBrains Runtime 21).
- Na sistemu je i **Java 25 — Gradle 8.x ne radi sa njom.** Za terminal uvek:
  ```powershell
  $env:JAVA_HOME = "C:\Users\fpetr\.jdks\jbr-21.0.11"
  ```
- keytool: `C:\Users\fpetr\.jdks\jbr-21.0.11\bin\keytool.exe`
- `git`, `gh` (ulogovan kao `fpetrovicc`), PowerShell (verovatno 7; poruke grešaka u PS7 formatu).
- Kraj reda: `.gitattributes` forsira LF (osim `.bat/.cmd/.ps1`). `gradlew` mora imati +x
  (`git update-index --chmod=+x gradlew`); CI ga i sam `chmod`-uje.

## Komande

```powershell
# build
$env:JAVA_HOME = "C:\Users\fpetr\.jdks\jbr-21.0.11"
.\gradlew.bat assembleDebug            # app\build\outputs\apk\debug\app-debug.apk
.\gradlew.bat assembleRelease          # potpisuje sa keystore.properties (ako postoji), inače debug ključem
.\gradlew.bat lintDebug

# instalacija na povezan telefon (USB debugging)
adb install -r app\build\outputs\apk\debug\app-debug.apk

# CI
gh run list --limit 5
gh run watch
gh run view <id> --log-failed

# izdanje (tag MORA = versionName, workflow to proverava)
git tag v1.1.0; git push origin v1.1.0
```

## Arhitektura

```
app/src/main/java/com/camac/tracker/
├── MainActivity.kt          splash (core-splashscreen, drži dok state.loading), edge-to-edge,
│                            traženje dozvola, refresh na ON_RESUME
├── data/
│   ├── TrackerParser.kt     regex parser odgovora → TrackerReply(kind, lat, lon, approximate(LBS),
│   │                        battery, speed, deviceTime, gps sat., gsm CSQ, server, apn, firmware, alarm)
│   ├── CommandCatalog.kt    22 komande u 7 kategorija, opisi na srpskom, placeholderi, homeIds
│   ├── Prefs.kt             Settings (SharedPreferences) + FishingSession lista (JSON)
│   ├── SmsRepository.kt     čita content://sms za broj trackera (poslednjih 8 cifara), najnovije prvo
│   ├── Exporter.kt          CSV (";" + BOM, za Excel sr) i GPX; FileProvider share; upis u SAF Uri
│   └── Weather.kt           Open-Meteo (bez ključa) + lokalni proračun faze meseca, vetar/kodovi na srpskom
├── sms/
│   ├── SmsSender.kt         SmsManager + PendingIntent ACTION_SENT (rezultat slanja)
│   ├── SmsReceiver.kt       manifest receiver SMS_RECEIVED → notifikacija ako je od trackera
│   │                        (i gasi podsetnik "nema podataka")
│   ├── SilenceActionReceiver.kt  dugmad iz notifikacije: "Pošalji proveru" (G#) / "U redu"
│   └── Notifier.kt          kanali: replies, alarms (HIGH), export (LOW), updates, status
├── work/ExportWorker.kt     nedeljni izvoz (WorkManager periodic 7d, KEEP)
├── work/SilenceWorker.kt    dnevna provera kad je stigla poslednja poruka → notifikacija (BEZ SMS-a)
├── update/
│   ├── UpdateManager.kt     GitHub Releases API → poredi versionName → skida .apk → PackageInstaller
│   └── InstallResultReceiver.kt  STATUS_PENDING_USER_ACTION → pokreće sistemski dijalog
└── ui/
    ├── MainViewModel.kt     UiState (StateFlow), ContentObserver na content://sms, slanje, sesije
    │                        pecanja, potvrda PIN-a, vreme (keš 30 min), izvoz, update
    ├── CamacApp.kt          Scaffold + NavigationBar (Početna, Komande, Istorija, Podešavanja),
    │                        AnimatedContent, BackHandler (predictive back), snackbar
    ├── HomeScreen.kt        hero kartica (status, BatteryRing), Idem na pecanje/Završio sam,
    │                        brze komande 2×2, Lokacija, Uređaj, Uslovi na vodi, poslednja poruka, baneri
    ├── CommandsScreen.kt    sve komande po kategorijama + pretraga, opasne crvene
    ├── HistoryScreen.kt     izvoz, filteri, pecanja + statistika, grafik baterije (Canvas), bubble-i
    ├── SettingsScreen.kt    sva podešavanja, lokalno stanje u TextField-ovima (kursor ne skače)
    ├── Common.kt            formatiranje (Locale sr-Latn-RS!), openMap, komponente, rememberCommandRunner
    └── Theme.kt             paleta (Pine #2E5E4E, Sage, Reed, FloatOrange), Manrope, Shapes
```

Resursi: `res/font/manrope_{regular,medium,semibold,bold}.ttf` (OFL), `drawable/ic_launcher_foreground.xml`
(čamac sa štapom i plovkom), `ic_stat_boat.xml` (monohrom za notifikacije), `xml/file_paths.xml`,
`src/debug/res/values/strings.xml` (naziv test verzije).

## Ključne odluke (i zašto)

- **Aplikacija NIKAD ne šalje SMS automatski.** Svaki SMS košta korisnika; šalje se samo na dodir.
  Podsetnik „Nema podataka od trackera X dana” (`SilenceCheck`, podrazumevano 7 dana, 0 = isključeno)
  samo pita: „Pošalji proveru” (jedan `G#`) ili „U redu”. Posle oba ćuti još isti broj dana.
  Prikazuje se kao kartica na Početnoj i kao notifikacija (najviše jednom po periodu).
  Aplikacija vidi samo SMS, pa „nema poruka” može značiti i da niko nije proveravao — tekst to kaže.

- **Bez baze**: istorija = sistemsko SMS sanduče. Kad aplikacija koja nije default SMS app pošalje
  preko `SmsManager`-a, sistem sam upisuje poslatu poruku u provider, pa se vide i komande iz Poruka.
- **Bez mrežnih biblioteka**: `HttpURLConnection` + `org.json` (dovoljno za Open-Meteo i GitHub API).
- **Bez Room/DataStore/Navigation**: namerno malo zavisnosti; state-based navigacija enum-om.
- **Placeholderi u komandama**: `{pwd}` PIN, `{admin}` admin broj, `{fish}`/`{normal}` intervali (s),
  `{radius}` radijus kao 4 cifre (`0100`), `{radiusM}` u metrima, `{input}` unos pre slanja.
  `{radiusM}` se menja PRE `{radius}` (redosled bitan). Opisi i nazivi se takođe popunjavaju.
- **Sesije pecanja**: `FISH_START` postavlja `fishingSince`, `FISH_END` upisuje `FishingSession`.
- **Promena PIN-a**: `pendingPin` se čuva; kad stigne ACK sa „password” → novi PIN se primenjuje.
- **Izvoz na Drive**: korisnik jednom bira fajl (SAF `CreateDocument`) → `takePersistableUriPermission`
  → worker prepisuje (`"wt"`). Ako upis ne uspe → notifikacija sa share sheet-om. Pravi Drive API
  (OAuth) je moguća nadogradnja ako SAF ne radi pouzdano.
- **Auto-update**: `BuildConfig.UPDATE_REPO = "fpetrovicc/CamacGPS"`, isključen u debug build-u.
  Release workflow na tag `v*`: proverava tag = versionName, dekodira keystore iz secrets, `assembleRelease`,
  objavljuje `camac-gps-vX.Y.Z.apk`. Sve verzije MORAJU biti potpisane istim `camac.jks`.
- **Debug pored release-a**: `applicationIdSuffix ".debug"`, da test ne pregazi tatinu verziju.
- **Lokalizacija datuma**: `Locale.Builder().setLanguage("sr").setScript("Latn")` — obični `sr` daje ćirilicu.

## TK915 — format i komande

Stvarni odgovor na `G123456#`:
```
lat:44.xxxxx
lon:19.xxxxx
Spd:000
T:26/10/23 18:20        ← UTC, yy/MM/dd
bat:31%
ID:<10 cifara>
http://maps.google.com/maps?q=N44.xxxxx,E19.xxxxx
```
LBS (bez GPS-a) počinje sa `lac:xxxx yyyy` i ima samo link. Odgovor na `check123456`:
`XM_TK915_SW_6.2 … ID:… IP:<host:port> APN:<apn>,, GPS:<sat> GSM:<0-31> <admin>,,,`.
Potvrde: `upload ok`, `move ok`, `adminip ok`, `password ok`. Alarmi: `Move!`, `low battery!`, `help me!`.

Komande (PIN fabrički `123456`): `G{pwd}#`, `check{pwd}`, `upload{pwd} 10–300`, `move{pwd} 0100`,
`nomove{pwd}`, `speed{pwd} 030`, `nospeed{pwd}`, `shock{pwd}`, `noshock{pwd}`, `sleep{pwd} shock|off`,
`admin{pwd} <broj>`, `noadmin{pwd} <broj>`, `password{pwd} <novi>`, `apn{pwd} <apn>`,
`adminip{pwd} <host> <port>`, `timezone{pwd} <z>`, `begin{pwd}` (fabrički reset!).
Samo admin broj sme da šalje komande. Neke komande (speed, shock, sleep, timezone, noadmin) su iz
TK905/TK915 dokumentacije i **nisu potvrđene na ovom uređaju** — proveravaj po „… ok” odgovoru.

## Proveravanje pre završetka zadatka

- `.\gradlew.bat assembleDebug` prolazi (sa JDK 21).
- Ako menjaš parser: pusti ga na primerima iznad (TK915 format, LBS, check, ACK, alarmi).
- Ne menjaj `applicationId`, ključ za potpisivanje ni `UPDATE_REPO` bez dogovora.
- Pre izdanja: `versionCode` +1, `versionName`, `CHANGELOG.md`, pa tag.
- Ne radi force push (ruleset ga blokira). Ne commit-uj `camac.jks`, `keystore.properties`,
  `local.properties`, `CLAUDE.local.md`.

## Ideje za kasnije (dogovoreno kao predlog, nije počelo)

1. Widget na početnom ekranu (Glance): baterija, status, „Gde je čamac?”.
2. Podsetnik za obnovu paketa na SIM-u trackera (dan pre isteka).
3. Dnevnik ulova uz pecanje (riba, mamac, slika) + mapa dobrih mesta.
4. Vodostaj Save (RHMZ) — prvo proveriti izvor podataka.
5. ~~Automatska dnevna provera~~ — odbijeno (troši SMS). Umesto toga urađen podsetnik bez SMS-a (vidi Ključne odluke).
6. Prikaz rute iz Ruhavik/GPS-Trace (ako postoji API).
7. Drive API (OAuth) umesto SAF-a ako nedeljni upis ne bude pouzdan.
8. Kasnije: AGP 9 / Gradle 9 / novi Kotlin kad sve bude stabilno.
