package com.camac.tracker.data

/** Kakav unos komanda traži pre slanja. */
enum class InputKind { NONE, NUMBER, PHONE, TEXT }

enum class CommandCategory(val title: String) {
    STATUS("Lokacija i status"),
    FISHING("Pecanje i učestalost slanja"),
    ALARMS("Alarmi"),
    BATTERY("Baterija i spavanje"),
    ADMIN("Admin i bezbednost"),
    NETWORK("Server i mreža"),
    ADVANCED("Napredno"),
}

/**
 * Jedna SMS komanda za TK915/TK905.
 *
 * U [template] i [description] se zamenjuje:
 *  {pwd} PIN trackera · {admin} admin broj · {fish} / {normal} intervali u sekundama ·
 *  {radius} radijus alarma kao 4 cifre (0100) · {radiusM} radijus u metrima · {input} unos korisnika
 */
data class TrackerCommand(
    val id: String,
    val category: CommandCategory,
    val title: String,
    val template: String,
    val description: String,
    val input: InputKind = InputKind.NONE,
    val inputHint: String = "",
    val confirm: Boolean = false,
    val danger: Boolean = false,
    val needsAdmin: Boolean = false,
) {
    fun resolve(s: Settings, value: String = ""): String = fill(template, s, value)
    fun label(s: Settings): String = fill(title, s, "")
    fun describe(s: Settings): String = fill(description, s, "")

    /** Šta fali u podešavanjima da bi komanda mogla da se pošalje (ili null). */
    fun missing(s: Settings): String? = when {
        s.trackerNumber.isBlank() -> "Upiši broj trackera u Podešavanjima"
        template.contains("{pwd}") && s.pin.isBlank() -> "Upiši PIN trackera u Podešavanjima"
        needsAdmin && s.adminNumber.isBlank() -> "Upiši admin broj u Podešavanjima"
        else -> null
    }

    private fun fill(t: String, s: Settings, value: String) = t
        .replace("{pwd}", s.pin)
        .replace("{admin}", s.adminNumber.ifBlank { "(admin broj)" })
        .replace("{fish}", s.fishInterval.toString())
        .replace("{normal}", s.normalInterval.toString())
        .replace("{radiusM}", s.moveRadius.toString())
        .replace("{radius}", s.moveRadius.coerceIn(0, 9999).toString().padStart(4, '0'))
        .replace("{input}", value.trim())
}

object CommandCatalog {
    const val FISH_START = "fish_start"
    const val FISH_END = "fish_end"
    const val LOCATION = "location"
    const val CHECK = "check"
    const val MOVE = "move"
    const val NOMOVE = "nomove"
    const val PASSWORD = "password"

    /** Komande na početnom ekranu, ovim redom. */
    val homeIds = listOf(FISH_START, FISH_END, LOCATION, CHECK, MOVE, NOMOVE)

    val all: List<TrackerCommand> = listOf(
        // ----- Lokacija i status -----
        TrackerCommand(
            LOCATION, CommandCategory.STATUS, "Status lokacije", "G{pwd}#",
            "Tracker odgovara SMS-om sa trenutnom lokacijom, brzinom, baterijom i linkom za Google mape. " +
                "Ako nema GPS signala (zatvoren prostor, ispod platna), šalje približnu lokaciju preko baznih stanica."
        ),
        TrackerCommand(
            CHECK, CommandCategory.STATUS, "GSM status i podešavanja", "check{pwd}",
            "Vraća verziju uređaja, server na koji šalje podatke, APN, broj vidljivih GPS satelita " +
                "i jačinu mobilnog signala (0–31). Ispod 10 je slab signal, iznad 20 odličan."
        ),

        // ----- Pecanje -----
        TrackerCommand(
            FISH_START, CommandCategory.FISHING, "Idem na pecanje", "upload{pwd} {fish}",
            "Tracker šalje poziciju na server na svakih {fish} sekundi, pa je ruta pecanja detaljna. " +
                "Troši više baterije — posle pecanja pritisni \"Završio sam\"."
        ),
        TrackerCommand(
            FISH_END, CommandCategory.FISHING, "Završio sam sa pecanjem", "upload{pwd} {normal}",
            "Vraća ređe slanje (na svakih {normal} sekundi) dok čamac stoji na vezu. " +
                "Štedi bateriju i prostor za istoriju."
        ),
        TrackerCommand(
            "upload", CommandCategory.FISHING, "Podesi učestalost slanja", "upload{pwd} {input}",
            "Koliko često tracker šalje poziciju na server. 10 = najčešće, 60 = svaki minut, " +
                "300 = svakih 5 minuta (najređe).",
            input = InputKind.NUMBER, inputHint = "Sekunde (10–300)"
        ),

        // ----- Alarmi -----
        TrackerCommand(
            MOVE, CommandCategory.ALARMS, "Alarm pomeranja ({radiusM} m)", "move{pwd} {radius}",
            "Ako se čamac pomeri više od {radiusM} m od mesta gde je sada, tracker šalje SMS alarm na admin broj. " +
                "Uključuj kad je čamac na vezu i tracker ima GPS signal.",
            confirm = true
        ),
        TrackerCommand(
            NOMOVE, CommandCategory.ALARMS, "Isključi alarm pomeranja", "nomove{pwd}",
            "Gasi alarm pomeranja. Isključi ga pre nego što kreneš čamcem, da ne stižu lažni alarmi."
        ),
        TrackerCommand(
            "speed", CommandCategory.ALARMS, "Alarm brzine", "speed{pwd} {input}",
            "SMS alarm ako čamac ide brže od zadate brzine. Korisno da saznaš ako ga neko odveze. " +
                "Brzina se piše sa tri cifre, npr. 030 za 30 km/h.",
            input = InputKind.NUMBER, inputHint = "Brzina u km/h, tri cifre (npr. 030)"
        ),
        TrackerCommand(
            "nospeed", CommandCategory.ALARMS, "Isključi alarm brzine", "nospeed{pwd}",
            "Gasi alarm prekoračenja brzine."
        ),
        TrackerCommand(
            "shock", CommandCategory.ALARMS, "Alarm vibracije", "shock{pwd}",
            "SMS kad tracker oseti udarac ili trešnju — npr. ako neko dira čamac. " +
                "Na talasima može da se okida i bez razloga."
        ),
        TrackerCommand(
            "noshock", CommandCategory.ALARMS, "Isključi alarm vibracije", "noshock{pwd}",
            "Gasi alarm vibracije."
        ),

        // ----- Baterija -----
        TrackerCommand(
            "sleep_shock", CommandCategory.BATTERY, "Spavanje do pomeranja", "sleep{pwd} shock",
            "Tracker spava dok miruje i budi se kad oseti pomeranje. Najveća ušteda baterije, " +
                "ali dok spava ne šalje redovno poziciju, a talasi ga mogu često buditi.",
            confirm = true
        ),
        TrackerCommand(
            "sleep_off", CommandCategory.BATTERY, "Isključi spavanje", "sleep{pwd} off",
            "Tracker stalno radi i šalje poziciju po podešenoj učestalosti."
        ),

        // ----- Admin -----
        TrackerCommand(
            "admin", CommandCategory.ADMIN, "Postavi admin broj", "admin{pwd} {admin}",
            "Upisuje {admin} kao admin broj trackera. Samo admin dobija SMS alarme i može da šalje komande. " +
                "Broj se menja u Podešavanjima aplikacije.",
            confirm = true, needsAdmin = true
        ),
        TrackerCommand(
            "noadmin", CommandCategory.ADMIN, "Ukloni admin broj", "noadmin{pwd} {input}",
            "Briše broj sa liste admin brojeva. Pažnja: ako obrišeš svoj broj, tracker možda više neće " +
                "primati komande sa ovog telefona.",
            input = InputKind.PHONE, inputHint = "Broj koji se briše", confirm = true, danger = true
        ),
        TrackerCommand(
            PASSWORD, CommandCategory.ADMIN, "Promeni PIN", "password{pwd} {input}",
            "Menja PIN (lozinku) trackera. Kad tracker potvrdi, aplikacija sama upisuje novi PIN u podešavanja.",
            input = InputKind.NUMBER, inputHint = "Novi PIN (6 cifara)", confirm = true, danger = true
        ),

        // ----- Mreža -----
        TrackerCommand(
            "apn", CommandCategory.NETWORK, "Postavi APN", "apn{pwd} {input}",
            "Naziv pristupne tačke za mobilni internet na SIM kartici trackera. Za Globaltel je \"globaltel\", " +
                "za mts \"gprsinternet\", za Yettel i A1 \"internet\".",
            input = InputKind.TEXT, inputHint = "APN (npr. globaltel)", confirm = true
        ),
        TrackerCommand(
            "server_ruhavik", CommandCategory.NETWORK, "Server: Ruhavik", "adminip{pwd} 57981.flespi.gw 31120",
            "Tracker šalje podatke u Ruhavik aplikaciju (GPS-Trace). Trenutno podešeno.",
            confirm = true
        ),
        TrackerCommand(
            "server_tkstar", CommandCategory.NETWORK, "Server: TKSTAR", "adminip{pwd} mytkstar.net 7700",
            "Vraća slanje podataka na staru TKSTAR aplikaciju. Ruhavik tada prestaje da dobija podatke.",
            confirm = true
        ),
        TrackerCommand(
            "timezone", CommandCategory.NETWORK, "Vremenska zona", "timezone{pwd} {input}",
            "Vremenska zona u kojoj tracker šalje vreme. Ruhavik traži 0 (UTC) — ne menjaj bez potrebe.",
            input = InputKind.NUMBER, inputHint = "Zona (0 = UTC)", confirm = true
        ),

        // ----- Napredno -----
        TrackerCommand(
            "begin", CommandCategory.ADVANCED, "Fabrički reset", "begin{pwd}",
            "Vraća SVA podešavanja na fabrička: briše admin broj, server, APN i interval. " +
                "Posle toga tracker mora ponovo da se podesi od početka!",
            confirm = true, danger = true
        ),
        TrackerCommand(
            "custom", CommandCategory.ADVANCED, "Proizvoljna komanda", "{input}",
            "Šalje tačno ono što upišeš. Za komande kojih nema na listi.",
            input = InputKind.TEXT, inputHint = "Tekst komande", confirm = true
        ),
    )

    fun byId(id: String): TrackerCommand = all.first { it.id == id }
}
