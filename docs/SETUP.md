# Podešavanje od nule (Windows)

Korak po korak: alati, projekat, build, ključ za potpisivanje, GitHub i prvo izdanje.
Komande su za **PowerShell** (Start → "PowerShell").

---

## 1. Alati

Preskoči ono što već imaš.

```powershell
winget install --id Git.Git -e
winget install --id GitHub.cli -e
winget install --id Google.AndroidStudio -e
```

Posle instalacije **zatvori i ponovo otvori PowerShell**, pa proveri:

```powershell
git --version
gh --version
```

Git se podešava jednom:

```powershell
git config --global user.name "Filip Petrović"
git config --global user.email "tvoj@mejl.com"
git config --global init.defaultBranch main
```

Android Studio pri prvom pokretanju sam skida Android SDK (izaberi *Standard*). JDK je već u njemu,
pa Javu ne treba posebno instalirati.

---

## 2. Projekat u repo

Repo `fpetrovicc/CamacGPS` već postoji na GitHub-u sa početnim README-om, licencom i .gitignore-om.
Kloniraš ga i preko njega iskopiraš projekat:

```powershell
gh auth login                      # GitHub.com → HTTPS → Login with a web browser
cd $HOME\source
gh repo clone fpetrovicc/CamacGPS
cd CamacGPS

# raspakuj camac-gps.zip (iz Downloads) preko kloniranog repoa
Expand-Archive "$HOME\Downloads\camac-gps.zip" -DestinationPath "$env:TEMP\cg" -Force
Copy-Item "$env:TEMP\cg\camac-gps\*" -Destination . -Recurse -Force
```

README, LICENSE i .gitignore iz zip-a zamenjuju početne fajlove sa GitHub-a. Tako i treba.

---

## 3. Prvi build

1. Android Studio → **Open** → folder `CamacGPS`.
2. Sačekaj da Gradle sync završi (traka na dnu). Prvi put skida biblioteke, oko 5–10 min.
   Ako traži *Android SDK Platform 35*, klikni **Install**.
3. **Build → Build App Bundle(s) / APK(s) → Build APK(s)**, pa **locate** kad završi.

Iz terminala:

```powershell
.\gradlew.bat assembleDebug
```

Debug verzija se na telefonu zove **„Čamac GPS (test)”** i instalira se pored prave, pa ne smeta tatinoj.

### Direktno na telefon (najbrže za probu)

1. Telefon: Podešavanja → O telefonu → Informacije o softveru → 7× dodirni **Broj verzije**.
2. Podešavanja → Opcije za programere → **USB otklanjanje grešaka**.
3. Poveži USB kablom, prihvati "Dozvoli otklanjanje grešaka".
4. U Android Studiju izaberi telefon gore i klikni ▶ **Run**.

> Ako build prijavi grešku, kopiraj ceo tekst iz prozora *Build* i pošalji ga.

---

## 4. Ključ za potpisivanje (jednom, za sva vremena)

Svaka verzija mora biti potpisana **istim ključem**, inače telefon ne prihvata ažuriranje.
U folderu `CamacGPS`:

```powershell
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v `
  -keystore camac.jks -alias camac -keyalg RSA -keysize 2048 -validity 10000
```

Pita za lozinku i podatke (dovoljno je ime, ostalo Enter). Zatim napravi `keystore.properties`
u istom folderu:

```properties
storeFile=camac.jks
storePassword=TVOJA_LOZINKA
keyAlias=camac
keyPassword=TVOJA_LOZINKA
```

Oba fajla su u `.gitignore` i **nikad ne idu na GitHub**. Push protection na GitHub-u dodatno blokira
push ako se lozinka slučajno nađe u kodu.

> ⚠️ **Napravi rezervnu kopiju** `camac.jks` i lozinke (USB, menadžer lozinki).
> Bez njih ne možeš da izdaš ažuriranje koje se instalira preko postojeće aplikacije.

---

## 5. Prvi push i podešavanje repoa

```powershell
git add .
git update-index --chmod=+x gradlew
git status          # camac.jks i keystore.properties NE smeju biti na listi
git commit -m "Čamac GPS 1.1.0"
git push
```

Zatim podešavanja repoa, jednom komandom:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\setup-repo.ps1
```

Skripta:

| | |
|---|---|
| Upisuje | opis (srpski + engleski), link ka Releases, teme |
| Isključuje | Wiki, Projects, Discussions, merge commit i rebase merge |
| Uključuje | Issues, squash merge, brisanje grane posle merge-a, auto-merge |
| Bezbednost | Dependabot upozorenja i automatske ispravke, privatne prijave ranjivosti, secret scanning, push protection |
| Actions | podrazumevano samo čitanje (release workflow sam traži pravo upisa) |
| Zaštita | `main` ne može da se obriše ni da se prepiše force push-om |

Dve stvari GitHub ne nudi preko komandne linije, pa ih radiš na sajtu:

1. **Settings → General → Social preview → Edit → Upload an image** → `docs\images\social-preview.png`.
   Ta slika se prikazuje kad link na repo pošalješ na Viber, WhatsApp ili Discord.
2. Glavna strana repoa → ⚙ pored **About** → odštikliraj **Packages** i **Deployments**, ostavi **Releases**.

---

## 6. Secrets za potpisivanje u CI-u

GitHub Actions pravi potpisan APK pri svakom izdanju, pa mu treba ključ. Čuva se šifrovan i niko ga ne vidi,
pa ni ti posle unosa.

```powershell
gh secret set KEYSTORE_BASE64 --body ([Convert]::ToBase64String([IO.File]::ReadAllBytes("$PWD\camac.jks")))
gh secret set KEYSTORE_PASSWORD     # pita za vrednost, upiši lozinku
gh secret set KEY_ALIAS --body "camac"
gh secret set KEY_PASSWORD          # pita za vrednost, upiši lozinku ključa
```

---

## 7. Da li je build passing?

```powershell
gh run watch
```

Ili na sajtu: kartica **Actions**. Zelena kvačica znači da je build prošao, i bedž u README-u postaje
**build passing**. Ako je crveno, otvori run, kopiraj grešku iz koraka *Build debug APK* i pošalji je.

Svaki build ostavlja i debug APK (Actions → run → *Artifacts* → `camac-gps-debug`).

---

## 8. Prvo izdanje

```powershell
git tag v1.1.0
git push origin v1.1.0
```

Za par minuta se na **Releases** pojavljuje `camac-gps-v1.1.0.apk`, potpisan tvojim ključem,
sa automatskim beleškama o izmenama.

**Tati instaliraj baš taj APK sa Releases**, ne debug iz Android Studija. Posle toga svako novo izdanje
stiže kao "Nova verzija → Ažuriraj" u aplikaciji.

> Workflow proverava da tag (`v1.1.0`) odgovara `versionName` u `app/build.gradle.kts` i staje ako se razlikuju.

---

## 9. Svako sledeće izdanje

1. U `app/build.gradle.kts`: `versionCode` +1, `versionName` npr. `"1.2.0"`.
2. U `CHANGELOG.md` dopiši šta je novo.
3. Objavi:

```powershell
git add .
git commit -m "Verzija 1.2.0"
git push
git tag v1.2.0
git push origin v1.2.0
```

Tatin telefon vidi novu verziju pri sledećem otvaranju aplikacije (proverava najviše na 12 h),
ili odmah preko Podešavanja → *Proveri ažuriranje*.

---

## Česti problemi

| Problem | Rešenje |
|---|---|
| `gh` / `git` nije prepoznat | Zatvori i ponovo otvori PowerShell |
| Skripta: "running scripts is disabled" | Pokreni je tačno kao gore, sa `-ExecutionPolicy Bypass` |
| CI: `./gradlew: Permission denied` | `git update-index --chmod=+x gradlew`, commit, push |
| CI: `bad interpreter` | `git add --renormalize .`, commit, push |
| Release pao (npr. fale secrets) | Ispravi, pa ponovo pošalji isti tag: `git tag -d v1.1.0`, `git push origin :refs/tags/v1.1.0`, pa `git tag v1.1.0` i `git push origin v1.1.0` |
| Telefon: "Aplikacija nije instalirana" pri ažuriranju | APK je potpisan drugim ključem. Instaliraj verziju sa Releases i uvek koristi isti `camac.jks` |
| Gradle sync traje večno | Prvi put je normalno. Ako zapne: File → Invalidate Caches |
