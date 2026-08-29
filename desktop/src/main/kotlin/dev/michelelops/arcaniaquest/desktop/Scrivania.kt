package dev.michelelops.arcaniaquest.desktop

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import dev.michelelops.arcaniaquest.gioco.SchermoDungeon

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
    val schermo = if (scatto != null) SchermoDungeon(modulo, scattaDopo = 12, fileScatto = scatto)
                  else SchermoDungeon(modulo)
    Lwjgl3Application(schermo, config)
}
