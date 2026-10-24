# Doprinos

Hvala što hoćeš da pomogneš. Projekat je mali, pa su pravila jednostavna.

## Prijava greške ili predlog

Otvori [Issue](https://github.com/fpetrovicc/CamacGPS/issues/new/choose) i izaberi šablon.
Za greške je najkorisnije: model telefona, verzija Androida, verzija aplikacije i šta si tačno uradio.

## Drugi model trackera

Ako tvoj tracker odgovara drugačije od TK915, izaberi šablon *Drugi model trackera* i nalepi
primer odgovora na `G123456#` i `check123456`. **Pre slanja obriši PIN i brojeve telefona.**

## Izmene u kodu

1. Napravi fork i granu (`git checkout -b ispravka-parsera`).
2. Proveri da build prolazi: `./gradlew assembleDebug`.
3. Pošalji pull request sa kratkim opisom šta i zašto.

Stil koda je standardni Kotlin (`kotlin.code.style=official`). Tekst u aplikaciji je na srpskom (latinica).

---

**English:** Issues and pull requests are welcome in English too. For tracker models other than the TK915,
please share sample replies with the PIN and phone numbers removed.
