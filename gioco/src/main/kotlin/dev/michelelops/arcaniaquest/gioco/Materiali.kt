package dev.michelelops.arcaniaquest.gioco

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.utils.Disposable

/**
 * Le pietre, il legno e il ferro del sotterraneo.
 *
 * Le immagini stanno in `content/texture/` e sono le stesse su desktop e
 * su Android, perche' quella cartella e' la cartella degli asset di tutti
 * e due. Si caricano una volta sola e si spartiscono fra tutti i moduli:
 * un dungeon da dodici pezzi usa quattro texture, non quarantotto.
 *
 * Il ripiego a tinta unita non e' un vezzo: se un file manca, il gioco
 * deve partire lo stesso e farsi guardare, non piantarsi.
 */
class Materiali : Disposable {

    private val caricate = mutableListOf<Texture>()

    val pavimento: Material
    val volta: Material
    val muro: Material
    val cima: Material
    val legno: Material
    val ferro: Material

    init {
        // I muri si ripetono lungo la loro lunghezza, il pavimento in
        // tutte e due le direzioni. Specchiata invece che ripetuta: le
        // texture combaciano ai bordi ma non alla perfezione, e la
        // specchiatura toglie di mezzo la cucitura senza chiedere niente
        // a chi le ha disegnate.
        val pietraMuro = carica("muri", Texture.TextureWrap.Repeat, Texture.TextureWrap.ClampToEdge)
        val pietraSuolo = carica("pavimento", Texture.TextureWrap.MirroredRepeat, Texture.TextureWrap.MirroredRepeat)
        val assi = carica("legno", Texture.TextureWrap.Repeat, Texture.TextureWrap.ClampToEdge)
        val battuto = carica("metallo", Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat)

        pavimento = fatto(pietraSuolo, Color(0.95f, 0.94f, 0.92f, 1f))
        // il soffitto e' la stessa pietra del pavimento, ma piu' spenta:
        // di la' non arriva quasi mai la luce e non deve rubare l'occhio
        volta = fatto(pietraSuolo, Color(0.62f, 0.61f, 0.60f, 1f))
        muro = fatto(pietraMuro, Color(1f, 1f, 1f, 1f))
        cima = fatto(pietraMuro, Color(0.55f, 0.55f, 0.54f, 1f))
        legno = fatto(assi, Color(1f, 1f, 1f, 1f))
        ferro = fatto(battuto, Color(0.85f, 0.85f, 0.88f, 1f))
    }

    /**
     * Niente scarto delle facce di dietro: il gruppo sta sempre dentro il
     * dungeon, quindi il risparmio sarebbe minimo, e in cambio si toglie
     * di mezzo l'errore piu' insidioso di tutti — un triangolo avvolto al
     * contrario che sparisce senza dire niente.
     */
    private fun fatto(t: Texture?, tinta: Color): Material {
        val m = Material(
            ColorAttribute.createDiffuse(tinta),
            IntAttribute.createCullFace(GL20.GL_NONE)
        )
        if (t != null) m.set(TextureAttribute.createDiffuse(t))
        return m
    }

    private fun carica(nome: String, u: Texture.TextureWrap, v: Texture.TextureWrap): Texture? {
        val file = Gdx.files.internal("texture/$nome.jpg")
        if (!file.exists()) {
            Gdx.app.log("arcania", "texture mancante: ${file.path()}, si va a tinta unita")
            return null
        }
        val t = Texture(file, true)
        t.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear)
        t.setWrap(u, v)
        caricate += t
        return t
    }

    override fun dispose() {
        for (t in caricate) t.dispose()
        caricate.clear()
    }
}
