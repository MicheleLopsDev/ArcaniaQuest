package dev.michelelops.arcaniaquest.desktop

import dev.michelelops.arcaniaquest.regole.Catalogo
import dev.michelelops.arcaniaquest.regole.Generatore
import dev.michelelops.arcaniaquest.regole.Perlustratore
import dev.michelelops.arcaniaquest.regole.Sorte
import java.io.File
import kotlin.system.exitProcess

/**
 * Cammina un mucchio di sotterranei e dice se si tengono tutti.
 *
 * Non apre nessuna finestra e non disegna niente: monta il dungeon, lo
 * percorre un passo alla volta come farebbe il gruppo, e controlla di
 * essere arrivato dappertutto. Le porte chiuse non fermano, si contano e
 * si passa.
 *
 * Si lancia col compito Gradle:
 *
 *   ./gradlew perlustra
 *   ./gradlew perlustra -Psemi=1-500 -Ppezzi=16
 *   ./gradlew perlustra -Psemi=8BD -Pdiario=si
 *
 * Esce con codice diverso da zero se anche un solo sotterraneo si
 * interrompe, cosi' puo' stare in una verifica automatica.
 */
fun main(args: Array<String>) {
    val arg = Argomenti(args)
    val quantiPezzi = arg.intero("pezzi", 12)
    val semi = leggiSemi(arg.opzione("semi") ?: "1-200")
    val conDiario = arg.opzione("diario") != null
    val cartella = File(arg.opzione("dove") ?: "../build/perlustrazioni")

    val catalogo = Catalogo.daJson(File("moduli/catalogo.json").readText())
    val generatore = Generatore(catalogo)

    println("perlustro ${semi.size} sotterranei da $quantiPezzi pezzi")

    var interrotti = 0
    var passiInTutto = 0L
    var caselleInTutto = 0L
    val guai = mutableListOf<String>()

    for (seme in semi) {
        val dungeon = generatore.genera(Sorte(seme), quantiPezzi)
        val esito = Perlustratore.percorri(dungeon)
        passiInTutto += esito.passi.size
        caselleInTutto += esito.quanteInTutto

        if (!esito.percorribile) {
            interrotti++
            guai += esito.riassunto()
            // il diario di un sotterraneo rotto si tiene sempre: e' l'unico
            // modo di capire dove ci si e' fermati
            scrivi(cartella, seme, esito.diario() + "\n" + dungeon.disegno())
        } else if (conDiario) {
            scrivi(cartella, seme, esito.diario() + "\n" + dungeon.disegno())
        }
    }

    println("passi: $passiInTutto     caselle: $caselleInTutto")
    if (interrotti == 0) {
        println("OK  tutti e ${semi.size} i sotterranei si percorrono per intero")
        if (conDiario) println("diari in ${cartella.absolutePath}")
        return
    }

    println()
    println("INTERROTTI: $interrotti su ${semi.size}")
    for (g in guai.take(20)) println("  $g")
    if (guai.size > 20) println("  ... e altri ${guai.size - 20}")
    println("i diari stanno in ${cartella.absolutePath}")
    exitProcess(1)
}

/** Accetta `1-200`, `8BD`, `1,7,42` e qualunque miscuglio dei tre. */
private fun leggiSemi(testo: String): List<Long> {
    val fuori = mutableListOf<Long>()
    for (pezzo in testo.split(",").map { it.trim() }.filter { it.isNotEmpty() }) {
        val trattino = pezzo.indexOf('-', startIndex = 1)
        if (trattino > 0) {
            val da = Sorte.leggi(pezzo.substring(0, trattino)).seme
            val a = Sorte.leggi(pezzo.substring(trattino + 1)).seme
            for (s in minOf(da, a)..maxOf(da, a)) fuori += s
        } else {
            fuori += Sorte.leggi(pezzo).seme
        }
    }
    return fuori
}

private fun scrivi(cartella: File, seme: Long, testo: String) {
    cartella.mkdirs()
    File(cartella, "seme-${Sorte.scrivi(seme)}.txt").writeText(testo)
}
