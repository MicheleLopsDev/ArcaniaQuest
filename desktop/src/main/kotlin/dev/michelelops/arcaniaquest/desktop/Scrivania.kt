package dev.michelelops.arcaniaquest.desktop

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import dev.michelelops.arcaniaquest.gioco.Avvio
import dev.michelelops.arcaniaquest.gioco.SchermoDungeon
import dev.michelelops.arcaniaquest.regole.Lato
import dev.michelelops.arcaniaquest.regole.Sorte

/**
 * Il lanciatore per Windows e Linux. Lo stesso backend copre entrambi:
 * non c'e' un ramo di codice per sistema operativo.
 *
 *     :desktop:run --args="--seme=K7X2M --pezzi=16"
 *     :desktop:run --args="S25"                        un modulo solo, per guardarlo
 *     :desktop:run --args="S25 --posa=1,2,ovest --scatto=porta.png"
 *     :desktop:run --args="S25 --alto --scatto=pianta.png"
 *     :desktop:run --args="--cerca=S34,S36 --pezzi=14" non apre niente, stampa i semi
 *     :desktop:run --args="--finestra=460x900"         la disposizione da telefono
 */
fun main(args: Array<String>) {
    val arg = Argomenti(args)

    if (arg.opzione("cerca") != null) {
        cercaSemi(
            voluti = arg.elenco("cerca").map { it.uppercase() },
            quantiPezzi = arg.intero("pezzi", 12),
            quantiSemi = arg.intero("quanti", 5)
        )
        return
    }

    val sorte = arg.opzione("seme")?.let { Sorte.leggi(it) } ?: Sorte.nuova()
    Lwjgl3Application(SchermoDungeon(avvioDa(arg, sorte)), configurazione(arg, sorte))
}

private fun avvioDa(arg: Argomenti, sorte: Sorte): Avvio {
    val scatto = arg.opzione("scatto", senzaValore = "scatto.png")
    return Avvio(
        modulo = arg.primoLibero(),
        sorte = sorte,
        quantiPezzi = arg.intero("pezzi", 12),
        scattaDopo = if (scatto != null) FOTOGRAMMI_PRIMA_DELLO_SCATTO else 0,
        fileScatto = scatto ?: "scatto.png",
        dallAlto = arg.bandiera("alto"),
        posa = posaDa(arg),
        porteSpalancate = arg.bandiera("porteaperte"),
        mappaAperta = arg.bandiera("mappa"),
        tuttoScoperto = arg.bandiera("tuttoscoperto"),
        chiediIlSeme = arg.opzione("chiediseme"),
        pienaLuce = arg.bandiera("pienaluce")
    )
}

/** `--posa=4,0,est`: casella e verso da cui guardare. Il verso puo' mancare. */
private fun posaDa(arg: Argomenti): Triple<Int, Int, Lato>? {
    val pezzi = arg.elenco("posa")
    if (pezzi.size < 2) return null
    return Triple(
        pezzi[0].toInt(),
        pezzi[1].toInt(),
        Lato.valueOf(pezzi.getOrElse(2) { "nord" }.uppercase())
    )
}

private fun configurazione(arg: Argomenti, sorte: Sorte) =
    Lwjgl3ApplicationConfiguration().apply {
        setTitle("ArcaniaQuest - seme ${sorte.semeScritto()}")
        val misura = arg.elenco("finestra").let {
            if (it.size == 1) it[0].split("x").mapNotNull { n -> n.trim().toIntOrNull() } else emptyList()
        }
        if (misura.size == 2) setWindowedMode(misura[0], misura[1])
        else setWindowedMode(1280, 800)
        setForegroundFPS(60)
        useVsync(true)
        setBackBufferConfig(8, 8, 8, 8, 16, 0, 0)
    }

/**
 * Quanti fotogrammi si disegnano prima di salvare uno scatto. Il primo
 * non basta: la scena si assesta in un paio di giri.
 */
private const val FOTOGRAMMI_PRIMA_DELLO_SCATTO = 12
