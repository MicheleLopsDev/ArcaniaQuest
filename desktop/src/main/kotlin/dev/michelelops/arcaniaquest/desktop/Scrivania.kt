package dev.michelelops.arcaniaquest.desktop

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import dev.michelelops.arcaniaquest.gioco.SchermoDungeon
import dev.michelelops.arcaniaquest.regole.Lato

/**
 * Il lanciatore per Windows e Linux. Lo stesso backend copre entrambi:
 * non c'e' un ramo di codice per sistema operativo.
 */
fun main(args: Array<String>) {
    val liberi = args.filterNot { it.startsWith("--") }
    val modulo = liberi.firstOrNull() ?: "S25"
    // --scatto=percorso.png disegna qualche fotogramma, salva e chiude:
    // serve a controllare la resa senza stare li' a guardare.
    val scatto = args.firstOrNull { it.startsWith("--scatto") }
        ?.substringAfter("=", "scatto.png")
    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle("ArcaniaQuest — $modulo")
        setWindowedMode(1280, 800)
        setForegroundFPS(60)
        useVsync(true)
        setBackBufferConfig(8, 8, 8, 8, 16, 0, 0)
    }
    val dallAlto = args.any { it == "--alto" }
    // --posa=x,z,verso mette il gruppo in una casella precisa: serve a
    // rifare due volte lo stesso scatto e confrontarli.
    val posa = args.firstOrNull { it.startsWith("--posa=") }
        ?.removePrefix("--posa=")?.split(",")
        ?.let { p ->
            Triple(
                p[0].trim().toInt(),
                p[1].trim().toInt(),
                Lato.valueOf(p.getOrElse(2) { "nord" }.trim().uppercase())
            )
        }
    val schermo = if (scatto != null) SchermoDungeon(modulo, 12, scatto, dallAlto, posa)
                  else SchermoDungeon(modulo, dallAlto = dallAlto, posa = posa)
    Lwjgl3Application(schermo, config)
}
