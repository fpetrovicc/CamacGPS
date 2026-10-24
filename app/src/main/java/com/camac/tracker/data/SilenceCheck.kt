package com.camac.tracker.data

/**
 * "Tracker se dugo nije javio" — samo UPOZORAVA. Aplikacija nikad sama ne šalje SMS;
 * proveru (G#) šalje tek kad korisnik pritisne "Pošalji proveru".
 *
 * Napomena: aplikacija vidi samo SMS poruke. Tracker sam šalje SMS samo kod alarma, pa
 * "nema poruka X dana" znači i "niko nije proveravao X dana" — zato tekst ne tvrdi da je kvar.
 */
object SilenceCheck {
    const val DAY_MS = 24 * 60 * 60 * 1000L

    /**
     * Da li treba prikazati upozorenje.
     * @param lastContact vreme poslednje poruke OD trackera (null = nikad nije stigla nijedna)
     */
    fun isDue(lastContact: Long?, s: Settings, now: Long): Boolean {
        if (lastContact == null || s.staleAlertDays <= 0) return false
        val limit = s.staleAlertDays * DAY_MS
        // posle "U redu" ćuti još jedan isti period, pa podseti ponovo
        return now - lastContact >= limit && now - s.staleDismissedAt >= limit
    }

    /** Da li je vreme za (novu) notifikaciju — najviše jednom po periodu. */
    fun shouldNotify(lastContact: Long?, s: Settings, now: Long): Boolean =
        isDue(lastContact, s, now) && now - s.staleNotifiedAt >= s.staleAlertDays * DAY_MS

    fun daysSince(lastContact: Long, now: Long): Long = (now - lastContact) / DAY_MS
}
