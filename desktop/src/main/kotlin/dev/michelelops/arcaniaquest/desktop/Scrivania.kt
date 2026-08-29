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
 *   :desktop:run --args="--seme=K7X2M --pezzi=16"
 *   :desktop:run --args="S25"   (un modulo solo, per guardarlo)
 *   :desktop:run --args="S25 --posa=1,2,ovest --scatto=porta.png"
 *   :desktop:run --args="S25 --alto --scatto=pianta.png"
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
        tuttoScoperto = args.any { it == "--tuttoscoperto" }
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
