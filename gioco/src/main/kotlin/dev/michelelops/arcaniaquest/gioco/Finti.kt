package dev.michelelops.arcaniaquest.gioco

/**
 * I dati finti dell'interfaccia.
 *
 * Tutto quello che sta qui dentro e' **inventato**: il gruppo, lo zaino,
 * il diario. Serve a vedere l'interfaccia intera mentre si costruisce il
 * resto, e sta in un file solo apposta — quando arrivera' la roba vera
 * questo file si cancella, e il compilatore dira' subito chi lo usava.
 *
 * Regola: nessun pezzo di gioco vero legge da qui.
 */
object Finti {

    data class Personaggio(
        val ruolo: String,
        val nome: String,
        val vita: Int,
        val vitaMassima: Int,
        val inMano: String
    )

    val gruppo = listOf(
        Personaggio("GUERRIERO", "Grik", 22, 22, "spada  scudo"),
        Personaggio("CHIERICO", "Elara", 16, 18, "mazza  simbolo"),
        Personaggio("LADRO", "Mira", 15, 15, "pugnale  grimaldelli"),
        Personaggio("MAGO", "Borin", 7, 10, "bastone  grimorio")
    )

    val zaino = listOf(
        "spada corta", "pozione", "lanterna", "corda",
        "grimaldelli", "razioni", "olio", "chiave",
        "", "", "", ""
    )

    val diario = listOf(
        "Il gruppo scende nella cripta.",
        "GRIK forza la porta a est.",
        "MIRA sente qualcosa muoversi",
        "oltre il muro.",
        "",
        "TIRO  d6+3 = 8   riuscito"
    )
}
