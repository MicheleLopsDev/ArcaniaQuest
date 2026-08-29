package dev.michelelops.arcaniaquest.desktop

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import dev.michelelops.arcaniaquest.gioco.Avvio
import dev.michelelops.arcaniaquest.gioco.SchermoDungeon
import dev.michelelops.arcaniaquest.regole.Lato
import dev.michelelops.arcaniaquest.regole.Catalogo
import dev.michelelops.arcaniaquest.regole.Generatore
import dev.michelelops.arcaniaquest.regole.Sorte
import java.io.File

/**
 * Il lanciatore per Windows e Linux. Lo stesso backend copre entrambi:
 * non c'e' un ramo di codice per sistema operativo.
 *
 *   :desktop:run --args="--seme=K7X2M --pezzi=16"
 *   :desktop:run --args="S25"   (un modulo solo, per guardarlo)
 *   :desktop:run --args="S25 --posa=1,2,ovest --scatto=porta.png"
 *   :desktop:run --args="S25 --alto --scatto=pianta.png"
 *   :desktop:run --args="--cerca=S34,S36 --pezzi=14"
 */
fun main(args: Array<String>) {
    val opzione = { nome: String ->
        args.firstOrNull { it.startsWith("--$nome=") }?.substringAfter('=')
    }

    val modulo = args.firstOrNull { !it.startsWith("--") }
    val sorte = opzione("seme")?.let { Sorte.leggi(it) } ?: Sorte.nuova()
    val scatto = args.firstOrNull { it.startsWith("--scatto") }
        ?.substringAfter("=", "scatto.png")
    val posa = opzione("posa")?.split(",")?.let { p ->
        Triple(
            p[0].trim().toInt(),
            p[1].trim().toInt(),
            Lato.valueOf(p.getOrElse(2) { "nord" }.trim().uppercase())
        )
    }

    // --cerca=S34,S36 non apre niente: gira i semi finche' non ne trova
    // uno che contiene tutti i moduli chiesti, e li stampa. Serve a
    // ritrovare una partita in cui c'e' quello che si vuole guardare.
    opzione("cerca")?.let {
        cercaSemi(it.split(",").map { id -> id.trim().uppercase() }.filter { id -> id.isNotEmpty() },
            opzione("pezzi")?.toIntOrNull() ?: 12,
            opzione("quanti")?.toIntOrNull() ?: 5)
        return
    }

    val avvio = Avvio(
        modulo = modulo,
        sorte = sorte,
        quantiPezzi = opzione("pezzi")?.toIntOrNull() ?: 12,
        scattaDopo = if (scatto != null) 12 else 0,
        fileScatto = scatto ?: "scatto.png",
        dallAlto = args.any { it == "--alto" },
        posa = posa,
        porteSpalancate = args.any { it == "--porteaperte" },
        mappaAperta = args.any { it == "--mappa" },
        tuttoScoperto = args.any { it == "--tuttoscoperto" },
        chiediIlSeme = opzione("chiediseme")
    )

    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle("ArcaniaQuest — seme ${sorte.semeScritto()}")
        // --finestra=480x900 serve a provare la disposizione da telefono
        // senza tirare fuori il telefono
        val misura = opzione("finestra")?.split("x")?.mapNotNull { it.trim().toIntOrNull() }
        if (misura != null && misura.size == 2) setWindowedMode(misura[0], misura[1])
        else setWindowedMode(1280, 800)
        setForegroundFPS(60)
        useVsync(true)
        setBackBufferConfig(8, 8, 8, 8, 16, 0, 0)
    }
    Lwjgl3Application(SchermoDungeon(avvio), config)
}

/**
 * Cerca semi che producano un sotterraneo contenente certi moduli.
 *
 * Va a forza bruta sui semi in ordine, perche' e' semplice e i semi
 * bassi vengono corti da scrivere. Non apre nessuna finestra: legge il
 * catalogo dal disco, quindi va lanciato con la cartella di lavoro su
 * content/ come fa il compito :desktop:run.
 */
private fun cercaSemi(voluti: List<String>, quantiPezzi: Int, quantiSemi: Int) {
    if (voluti.isEmpty()) {
        println("--cerca vuole almeno un id, per esempio --cerca=S34")
        return
    }
    val catalogo = Catalogo.daJson(File("moduli/catalogo.json").readText())
    val generatore = Generatore(catalogo)
    println("cerco semi con ${voluti.joinToString(" + ")} in $quantiPezzi pezzi")

    var trovati = 0
    var seme = 1L
    val limite = 200_000L
    while (trovati < quantiSemi && seme < limite) {
        val d = generatore.genera(Sorte(seme), quantiPezzi)
        val dentro = d.pezzi.map { it.modulo.id }.toSet()
        if (voluti.all { it in dentro }) {
            val elenco = d.pezzi.joinToString(" ") { it.modulo.id }
            println("  ${Sorte.scrivi(seme).padEnd(8)}  ${d.pezzi.size} pezzi, ${d.caselleInTutto} caselle   $elenco")
            trovati++
        }
        seme++
    }
    if (trovati == 0) println("  nessuno nei primi $limite semi")
}
