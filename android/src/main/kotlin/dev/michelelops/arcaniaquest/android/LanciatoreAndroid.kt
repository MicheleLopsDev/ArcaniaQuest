package dev.michelelops.arcaniaquest.android

import android.os.Bundle
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import dev.michelelops.arcaniaquest.gioco.SchermoDungeon

/**
 * Il lanciatore Android. Cambia solo lui: la scena, il ciclo di gioco e
 * le regole sono le stesse che girano su desktop.
 */
class LanciatoreAndroid : AndroidApplication() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = AndroidApplicationConfiguration().apply {
            useGL30 = true
            useAccelerometer = false
            useCompass = false
            numSamples = 2
        }
        initialize(SchermoDungeon(), config)
    }
}
