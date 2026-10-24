<p align="center">
  <img src="docs/images/banner.png" alt="Čamac GPS" width="100%">
</p>

<p align="center">
  <a href="https://github.com/fpetrovicc/CamacGPS/actions/workflows/build.yml"><img src="https://github.com/fpetrovicc/CamacGPS/actions/workflows/build.yml/badge.svg" alt="Build"></a>
  <a href="https://github.com/fpetrovicc/CamacGPS/releases/latest"><img src="https://img.shields.io/github/v/release/fpetrovicc/CamacGPS?label=verzija&color=2E5E4E" alt="Release"></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white" alt="Android 8+">
  <a href="LICENSE"><img src="https://img.shields.io/github/license/fpetrovicc/CamacGPS?color=5F7D6B" alt="MIT"></a>
</p>

<p align="center">
  <b>Srpski</b> · <a href="#english">English</a>
</p>

---

Mnogi GPS trackeri za čamce, vozila i opremu (TKSTAR TK915 i slični) podešavaju se SMS porukama
tipa `upload123456 60` ili `move123456 0100`. Te komande je teško pamtiti, a odgovor trackera
izgleda ovako:

```
lat:44.80000
lon:20.46000
Spd:000
T:26/10/23 18:20
bat:31%
http://maps.google.com/maps?q=N44.80000,E20.46000
```

Čamac GPS sve to pretvara u par velikih dugmadi i jedan pregledan ekran. Nema servera, naloga ni pretplate.
Aplikacija šalje običan SMS sa telefona i čita odgovor koji stigne.

## Šta može

**Početni ekran** pokazuje sve bitno odjednom: da li je čamac na vezu, bateriju, kad se tracker poslednji put
javio, poziciju na mapi i jačinu signala.

**Idem na pecanje / Završio sam.** Pre pecanja jedan dodir prebacuje tracker na često slanje pozicije, da ruta
bude detaljna. Posle pecanja drugi dodir ga vraća na ređe slanje, da čuva bateriju. Aplikacija usput beleži
kad je pecanje počelo i koliko je trajalo.

**Alarm pomeranja.** Kad je čamac na vezu, uključiš alarm i tracker šalje SMS ako se pomeri više od 100 m.

**Sve komande trackera** na jednom mestu, po kategorijama i sa objašnjenjem šta koja radi. PIN i admin broj se
upisuju jednom u podešavanjima i aplikacija ih sama ubacuje u komande.

**Uslovi na vodi:** temperatura, vetar i udari, pritisak i da li pada, izlazak i zalazak sunca i faza meseca,
za mesto gde je čamac.

**Istorija** cele prepiske sa trackerom, grafik baterije i spisak pecanja. Sve može da se izveze u tabelu (CSV)
ili kao tačke za mapu (GPX), a jednom nedeljno i automatski na Google Drive.

**Obaveštenja** kad tracker odgovori ili pošalje alarm, i kad je aplikacija zatvorena.

**Ažuriranja** stižu sama: kad izađe nova verzija, u aplikaciji se pojavi dugme „Ažuriraj”.

## Instalacija

1. Sa stranice [Releases](https://github.com/fpetrovicc/CamacGPS/releases/latest) preuzmi `camac-gps-vX.Y.Z.apk`.
2. Otvori fajl na telefonu i dozvoli instalaciju iz tog izvora.
3. Dozvoli SMS i obaveštenja. Ako je dozvola siva sa natpisom *Ograničeno podešavanje*, idi u
   Podešavanja → Aplikacije → Čamac GPS → ⋮ → *Dozvoli ograničena podešavanja*.
4. U aplikaciji otvori Podešavanja i upiši broj SIM kartice u trackeru, svoj broj (admin) i PIN.
5. Kad je čamac na vezu, pritisni *Status lokacije*, pa u podešavanjima *Obeleži vez*.

Telefon sa kog šalješ komande mora biti admin broj trackera. Tracker ne odgovara na komande sa drugih brojeva.

## Glavne komande

| Dugme | SMS koji se šalje | Šta radi |
|---|---|---|
| Status lokacije | `G123456#` | Pozicija, brzina i baterija |
| GSM status | `check123456` | Signal, GPS sateliti, server, APN |
| Idem na pecanje | `upload123456 60` | Pozicija na server svakog minuta |
| Završio sam | `upload123456 300` | Na 5 minuta, štedi bateriju |
| Alarm pomeranja | `move123456 0100` | SMS ako se čamac pomeri 100 m |
| Isključi alarm | `nomove123456` | Gasi alarm pomeranja |

Ostalih 16 komandi (alarmi brzine i vibracije, spavanje, admin broj, PIN, APN, server, reset) je u tabu *Komande*.
Intervali i radijus se menjaju u podešavanjima.

## Privatnost

Aplikacija ništa ne šalje na svoj server, jer ga nema. Poruke čita samo iz SMS sandučeta na telefonu.
Na internet ide samo u dva slučaja: kad traži prognozu od [Open-Meteo](https://open-meteo.com), kome šalje
približne koordinate čamca, i kad na GitHub-u proverava da li postoji nova verzija.

## Ograničenja

- Testirano sa TKSTAR TK915 (firmver `XM_TK915_SW_6.2`). TK905 i slični klonovi koriste iste komande,
  ali nisu posebno testirani.
- Svaka komanda je jedan SMS sa tvog kredita, a svaki odgovor jedan SMS sa kredita SIM kartice u trackeru.
- U istoriji su samo pozicije koje je tracker poslao SMS-om. Detaljna ruta pecanja je na serveru
  na koji tracker šalje podatke (Ruhavik, TKSTAR…).
- Na telefonu sa dve SIM kartice SMS ide sa one koja je podrazumevana za poruke.

## Build

Treba ti [Android Studio](https://developer.android.com/studio), u kome su već JDK i Android SDK.

```bash
git clone https://github.com/fpetrovicc/CamacGPS.git
cd CamacGPS
./gradlew assembleDebug        # Windows: .\gradlew.bat assembleDebug
```

Test verzija se instalira kao zasebna aplikacija „Čamac GPS (test)”, pored prave.
Potpisivanje, objavljivanje verzija i podešavanje repoa su u [docs/SETUP.md](docs/SETUP.md).

Napravljeno u Kotlinu sa Jetpack Compose i Material 3. Bez dodatnih biblioteka za mrežu, jer je dovoljan
`HttpURLConnection`. Pozadinski izvoz radi preko WorkManager-a, a ažuriranja preko `PackageInstaller`-a.

## Doprinos

Greške i predlozi idu u [Issues](https://github.com/fpetrovicc/CamacGPS/issues). Ako tvoj tracker odgovara
u drugačijem formatu, pošalji primer poruke, ali sakrij PIN i brojeve telefona. Tako se najlakše proširuje
podrška za druge modele. Više u [CONTRIBUTING.md](CONTRIBUTING.md).

## Licenca

[MIT](LICENSE). Slobodno koristi, menjaj i deli. Font Manrope je pod
[SIL Open Font License](https://openfontlicense.org). Projekat nije povezan sa proizvođačem TKSTAR.

<br>

---


## English

Many GPS trackers for boats, vehicles and equipment (TKSTAR TK915 and similar) are configured through
SMS commands like `upload123456 60`, which are hard to remember, and their replies are a wall of
coordinates. Čamac GPS (*čamac* means boat) turns that into a few big buttons and one clear screen.
There's no server, no account and no subscription. The app sends plain SMS from the phone and reads the reply.

The app UI is in Serbian.

### Features

- **Home screen** with everything at a glance: moored or away, battery, last check-in, position on the map,
  signal strength.
- **Going fishing / Done fishing.** One tap switches the tracker to frequent position updates for a detailed
  route, another switches it back to save battery. Each fishing trip is logged with start time and duration.
- **Movement alarm.** While moored, the tracker texts you if the boat moves more than 100 m.
- **All tracker commands** grouped by category, each with a plain explanation. PIN and admin number are entered
  once and filled into the commands automatically.
- **Conditions on the water:** temperature, wind and gusts, pressure trend, sunrise and sunset, moon phase.
- **History** of every message, battery chart, list of fishing trips. Export to CSV or GPX, plus an automatic
  weekly export to Google Drive.
- **Notifications** for replies and alarms, even when the app is closed.
- **Self-updating** from GitHub Releases.

### Install

Download `camac-gps-vX.Y.Z.apk` from [Releases](https://github.com/fpetrovicc/CamacGPS/releases/latest),
install it and allow SMS and notification permissions. On newer Android versions sideloaded apps may show
SMS permissions as *Restricted setting*. Allow it under App info → ⋮ → *Allow restricted settings*.
Then enter the tracker's number, your admin number and the PIN in Settings.

### Compatibility

Tested with the TKSTAR TK915 (firmware `XM_TK915_SW_6.2`). Other TKSTAR models and clones that understand
the same commands (`G123456#`, `check123456`, `upload123456 …`) should work too. If yours replies in a
different format, open an issue with a sample message (hide the PIN and phone numbers).

### Privacy

There is no backend. Messages are read from the phone's SMS inbox only. The app goes online for two things:
the weather from [Open-Meteo](https://open-meteo.com), which receives the boat's approximate coordinates,
and checking GitHub for a new version.

### Building

```bash
git clone https://github.com/fpetrovicc/CamacGPS.git
cd CamacGPS
./gradlew assembleDebug
```

Requires Android Studio (bundled JDK and SDK). Kotlin, Jetpack Compose, Material 3, WorkManager.
Signing and release instructions are in [docs/SETUP.md](docs/SETUP.md) (Serbian).

### License

[MIT](LICENSE). The Manrope font is licensed under the SIL Open Font License.
Not affiliated with TKSTAR.
