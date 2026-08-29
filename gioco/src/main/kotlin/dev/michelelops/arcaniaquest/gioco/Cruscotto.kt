package dev.michelelops.arcaniaquest.gioco

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.utils.Disposable

/** Una riga del pannello: a sinistra cosa e', a destra quanto vale. */
data class Voce(val etichetta: String, val valore: String)

/**
 * Il pannello di servizio: dove sei, con che seme, quanto hai visto e
 * che tasti si premono.
 *
 * Non e' l'interfaccia del gioco — quella arrivera' disegnata. Questo
 * serve a chi il gioco lo sta costruendo: senza il seme a schermo un
 * guaio non si riproduce, e senza le coordinate non si sa nemmeno da
 * dove raccontarlo.
 *
 * Usa il carattere di serie di libGDX, che sta gia' dentro la libreria:
 * cosi' non c'e' nessun file da caricare e nessun asset da tenere
 * allineato fra desktop e Android.
 */
class Cruscotto : Disposable {

    private val batch = SpriteBatch()
    private val font = BitmapFont()
    private val misura = GlyphLayout()
    private val proiezione = Matrix4()
    private val tinta: Texture

    var visibile = true

    private val fondo = Color(0.03f, 0.033f, 0.036f, 0.86f)
    private val bordo = Color(0.24f, 0.22f, 0.18f, 0.9f)
    private val ambra = Color(0.88f, 0.60f, 0.28f, 1f)
    private val pergamena = Color(0.85f, 0.83f, 0.78f, 1f)
    private val spenta = Color(0.55f, 0.53f, 0.48f, 1f)
    private val avanzata = Color(0.42f, 0.62f, 0.38f, 1f)

    init {
        val p = Pixmap(1, 1, Pixmap.Format.RGBA8888)
        p.setColor(Color.WHITE)
        p.fill()
        tinta = Texture(p)
        p.dispose()
        font.setUseIntegerPositions(false)
        // il carattere di serie e' da 15 pixel: ingrandito senza filtro
        // viene a scaletta, e un pannello di servizio va letto in fretta
        font.region.texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
    }

    fun disegna(voci: List<Voce>, frazione: Float, tasti: List<String>) {
        if (!visibile) return

        val w = Gdx.graphics.width.toFloat()
        val h = Gdx.graphics.height.toFloat()
        val s = (h / 700f).coerceIn(1.1f, 2.6f)
        font.data.setScale(s)

        val margine = 14f * s
        val riga = font.lineHeight * 1.08f
        val colonna = larghezzaMassima(voci.map { it.etichetta }) + 12f * s

        var largo = 0f
        for (v in voci) largo = maxOf(largo, colonna + larghezza(v.valore))
        val larghezzaPannello = largo + margine * 2f
        val altezzaPannello = riga * voci.size + margine * 2.4f + 7f * s

        proiezione.setToOrtho2D(0f, 0f, w, h)
        batch.projectionMatrix = proiezione
        batch.begin()

        // pannello in alto a sinistra
        val px = margine
        val py = h - margine - altezzaPannello
        riquadro(px, py, larghezzaPannello, altezzaPannello)

        var y = h - margine * 2f
        for (v in voci) {
            font.color = spenta
            font.draw(batch, v.etichetta, px + margine, y)
            font.color = if (v.etichetta == "SEME") ambra else pergamena
            font.draw(batch, v.valore, px + margine + colonna, y)
            y -= riga
        }

        // barra dell'esplorato, in fondo al pannello
        val bx = px + margine
        val bl = larghezzaPannello - margine * 2f
        val by = py + margine * 0.8f
        batch.color = Color(0.16f, 0.16f, 0.15f, 0.9f)
        batch.draw(tinta, bx, by, bl, 4f * s)
        batch.color = avanzata
        batch.draw(tinta, bx, by, bl * frazione.coerceIn(0f, 1f), 4f * s)
        batch.color = Color.WHITE

        // striscia dei tasti, in basso
        val testo = tasti.joinToString("     ")
        misura.setText(font, testo)
        val ty = margine + misura.height + 8f * s
        riquadro(margine, margine, misura.width + margine * 2f, misura.height + 16f * s)
        font.color = spenta
        font.draw(batch, testo, margine * 2f, ty)

        batch.end()
    }

    private fun riquadro(x: Float, y: Float, w: Float, h: Float) {
        batch.color = fondo
        batch.draw(tinta, x, y, w, h)
        batch.color = bordo
        batch.draw(tinta, x, y + h - 1f, w, 1f)
        batch.draw(tinta, x, y, w, 1f)
        batch.draw(tinta, x, y, 1f, h)
        batch.draw(tinta, x + w - 1f, y, 1f, h)
        batch.color = Color.WHITE
    }

    private fun larghezza(testo: String): Float {
        misura.setText(font, testo)
        return misura.width
    }

    private fun larghezzaMassima(testi: List<String>): Float {
        var m = 0f
        for (t in testi) m = maxOf(m, larghezza(t))
        return m
    }

    override fun dispose() {
        batch.dispose()
        font.dispose()
        tinta.dispose()
    }
}
