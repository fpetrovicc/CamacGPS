# Izmene

Format prati [Keep a Changelog](https://keepachangelog.com/), verzije prate [SemVer](https://semver.org/).

## [Unreleased]

### Dodato
- Podsetnik „Nema podataka od trackera X dana” na Početnoj i kao notifikacija, sa dugmadima
  „Pošalji proveru” i „U redu”. Aplikacija ne šalje SMS sama; broj dana se menja u Podešavanjima.

## [1.1.0] — 2026-10-24

### Dodato
- Pecaroška tema: mirna zelena paleta, Manrope font, splash ekran, donja navigacija.
- Početni ekran: status čamca, baterija, "Idem na pecanje" / "Završio sam", brze komande,
  lokacija, stanje uređaja, uslovi na vodi (Open-Meteo) i faza meseca.
- Tab Komande: sve TK915 komande sa opisima na srpskom i dinamičnim PIN-om i admin brojem.
- Istorija: filteri, grafik baterije, beleženje pecanja, izvoz CSV/GPX, nedeljni izvoz na Google Drive.
- Automatsko ažuriranje sa GitHub Releases.
- GitHub Actions: build na svaki push, potpisan release na tag.
- Test (debug) verzija se instalira pored prave kao „Čamac GPS (test)”.

## [1.0.0] — 2026-10-24

### Dodato
- Prva verzija: slanje SMS komandi, čitanje odgovora, istorija, notifikacije.
