package dev.michelelops.arcaniaquest.desktop

import dev.michelelops.arcaniaquest.regole.Catalogo
import dev.michelelops.arcaniaquest.regole.Generatore
import dev.michelelops.arcaniaquest.regole.Sorte
import java.io.File

/** Oltre questo non si cerca: se non e' saltato fuori prima, non salta fuori. */
private const val SEMI_DA_PROVARE = 200_000L

/**
 * Cerca semi che producano un sotterraneo contenente certi moduli.
 *
 * Va a forza bruta sui semi in ordine crescente. In ordine e non a caso
 * perche' i semi bassi si scrivono corti: `D` si batte meglio di
 * `K7X2M9`, e questi semi finiscono nelle segnalazioni.
 *
 * Non apre nessuna finestra e legge il catalogo dal disco, quindi va
 * lanciato con la cartella di lavoro su `content/` come fa `:desktop:run`.
 */
fun cercaSemi(voluti: List<String>, quantiPezzi: Int, quantiSemi: Int) {
    if (voluti.isEmpty()) {
        println("--cerca vuole almeno un id, per esempio --cerca=S34")
        return
    }
    val generatore = Generatore(Catalogo.daJson(File("moduli/catalogo.json").readText()))
    println("cerco semi con ${voluti.joinToString(" + ")} in $quantiPezzi pezzi")

    var trovati = 0
    var seme = 1L
    while (trovati < quantiSemi && seme < SEMI_DA_PROVARE) {
        val dungeon = generatore.genera(Sorte(seme), quantiPezzi)
        val dentro = dungeon.pezzi.map { it.modulo.id }.toSet()
        if (voluti.all { it in dentro }) {
            println(
                "  %-8s %d pezzi, %d caselle   %s".format(
                    Sorte.scrivi(seme),
                    dungeon.pezzi.size,
                    dungeon.caselleInTutto,
                    dungeon.pezzi.joinToString(" ") { it.modulo.id }
                )
            )
            trovati++
        }
        seme++
    }
    if (trovati == 0) println("  nessuno nei primi $SEMI_DA_PROVARE semi")
}
