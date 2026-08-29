package dev.michelelops.arcaniaquest.gioco

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.environment.PointLight
import com.badlogic.gdx.graphics.PixmapIO
import dev.michelelops.arcaniaquest.regole.Catalogo
import dev.michelelops.arcaniaquest.regole.Modulo
import kotlin.math.cos
import kotlin.math.sin

/**
 * Il primo schermo: un modulo, il gruppo dentro, e i comandi a caselle.
 *
 * Non e' ancora il gioco — non c'e' l'interfaccia, non c'e' il dungeon
 * generato, c'e' un pezzo solo. Serve a provare la cosa che tutto il
 * resto da' per scontata: che il dato diventi geometria e che muoversi a
 * caselle sia davvero cosi' com'era nel prototipo.
 */
class SchermoDungeon(
    private val moduloIniziale: String = "S25",
    /**
     * Se maggiore di zero, dopo tanti fotogrammi salva uno scatto e
     * chiude. Serve a vedere cosa disegna il motore senza doverlo
     * guardare a occhio: e' la prova che si puo' rifare uguale domani.
     */
    private val scattaDopo: Int = 0,
    private val fileScatto: String = "scatto.png",
    /**
     * Telecamera a picco sul modulo, senza buio ne' nebbia. Non e' una
     * modalita' di gioco: e' il modo per vedere se alla mesh manca un
     * pezzo, cosa che da dentro non si nota mai.
     */
    private val dallAlto: Boolean = false,
    /** Casella e verso da cui guardare, per rifare uno scatto uguale. */
    private val posa: Triple<Int, Int, dev.michelelops.arcaniaquest.regole.Lato>? = null
) : ApplicationAdapter() {

    private var fotogrammi = 0

    private lateinit var catalogo: Catalogo
    private lateinit var modulo: Modulo
    private lateinit var gruppo: Gruppo

    private lateinit var camera: PerspectiveCamera
    private lateinit var batch: ModelBatch
    private lateinit var ambiente: Environment
    private var modello: Model? = null
    private var istanza: ModelInstance? = null

    private val torciaDelGruppo = PointLight()
    private var ultimoRifiuto: Rifiuto = Rifiuto.NIENTE

    override fun create() {
        catalogo = Catalogo.daJson(Gdx.files.internal("moduli/catalogo.json").readString("UTF-8"))
        modulo = catalogo[moduloIniziale]
        gruppo = posa?.let { Gruppo(modulo, it.first, it.second, it.third) }
            ?: Gruppo.dallaPartenza(modulo)

        camera = PerspectiveCamera(64f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat()).apply {
            near = 0.15f
            far = if (dallAlto) 400f else Misure.FONDO_BUIO
        }
        batch = ModelBatch()

        ambiente = Environment().apply {
            if (dallAlto) {
                set(ColorAttribute(ColorAttribute.AmbientLight, 0.55f, 0.56f, 0.58f, 1f))
                add(DirectionalLight().set(0.5f, 0.5f, 0.5f, -0.4f, -0.9f, -0.25f))
            } else {
                set(ColorAttribute(ColorAttribute.AmbientLight, 0.17f, 0.18f, 0.22f, 1f))
                set(ColorAttribute(ColorAttribute.Fog, 0.015f, 0.018f, 0.024f, 1f))
                add(DirectionalLight().set(0.13f, 0.14f, 0.17f, -0.4f, -0.9f, -0.25f))
                add(torciaDelGruppo)
            }
        }
        // Le torce a muro dichiarate nel modulo.
        for (a in if (dallAlto) emptyList() else modulo.arredi.filter { it.tipo == "torcia" }) {
            ambiente.add(PointLight().set(
                Color(1f, 0.62f, 0.28f, 1f),
                (a.x + 0.5f) * Misure.CASELLA, Misure.ALTEZZA_MURO * 0.62f, (a.z + 0.5f) * Misure.CASELLA,
                Misure.FORZA_TORCIA_A_MURO
            ))
        }

        rigenera()
    }

    private fun rigenera() {
        modello?.dispose()
        modello = CostruttoreMesh.costruisci(modulo).also { istanza = ModelInstance(it) }
    }

    override fun resize(width: Int, height: Int) {
        camera.viewportWidth = width.toFloat()
        camera.viewportHeight = height.toFloat()
        camera.update()
    }

    override fun render() {
        if (dallAlto) inquadraDallAlto() else inquadraDalGruppo()

        Gdx.gl.glClearColor(0.012f, 0.014f, 0.018f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)

        istanza?.let {
            batch.begin(camera)
            batch.render(it, ambiente)
            batch.end()
        }

        fotogrammi++
        if (scattaDopo > 0 && fotogrammi >= scattaDopo) {
            val pixmap = com.badlogic.gdx.graphics.Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.backBufferWidth, Gdx.graphics.backBufferHeight)
            // OpenGL legge il fotogramma dal basso: senza il ribaltamento
            // il PNG esce a testa in giu' e sembra che il pavimento sia
            // il soffitto.
            PixmapIO.writePNG(Gdx.files.absolute(fileScatto), pixmap, java.util.zip.Deflater.DEFAULT_COMPRESSION, true)
            pixmap.dispose()
            Gdx.app.log("arcania", "scatto salvato in " + fileScatto)
            Gdx.app.exit()
        }
    }

    /** A picco sul modulo, tutto dentro l'inquadratura. */
    private fun inquadraDallAlto() {
        val c = Misure.CASELLA
        val cx = modulo.larghezza * c / 2f
        val cz = modulo.profondita * c / 2f
        val quanto = maxOf(modulo.larghezza, modulo.profondita) * c
        camera.position.set(cx, quanto * 1.25f, cz + 0.001f)
        camera.direction.set(0f, -1f, -0.0001f).nor()
        camera.up.set(0f, 0f, -1f)
        camera.update()
    }

    private fun inquadraDalGruppo() {
        leggiComandi()
        gruppo.avanza(Gdx.graphics.deltaTime)

        val c = Misure.CASELLA
        val px = (gruppo.mostraX + 0.5f) * c
        val pz = (gruppo.mostraZ + 0.5f) * c
        val a = gruppo.mostraAngolo

        camera.position.set(px, Misure.ALTEZZA_OCCHI, pz)
        camera.direction.set(sin(a), 0f, cos(a))
        camera.up.set(0f, 1f, 0f)
        camera.update()

        // Il gruppo si porta dietro la torcia: senza, il buio sarebbe totale.
        torciaDelGruppo.set(Color(1f, 0.68f, 0.36f, 1f), px, Misure.ALTEZZA_OCCHI + 0.35f, pz, Misure.FORZA_TORCIA_GRUPPO)
    }

    /**
     * Si legge lo stato dei tasti a ogni fotogramma invece degli eventi:
     * tenendo premuto si cammina, e il ritmo lo detta la durata del passo,
     * perche' finche' il gruppo si muove ogni altra mossa viene rifiutata.
     */
    private fun leggiComandi() {
        val giu = { k: Int -> Gdx.input.isKeyPressed(k) }
        val mossa = when {
            giu(Input.Keys.UP) || giu(Input.Keys.W) -> Mossa.AVANTI
            giu(Input.Keys.DOWN) || giu(Input.Keys.S) -> Mossa.INDIETRO
            giu(Input.Keys.LEFT) || giu(Input.Keys.Q) -> Mossa.VOLTA_SINISTRA
            giu(Input.Keys.RIGHT) || giu(Input.Keys.E) -> Mossa.VOLTA_DESTRA
            giu(Input.Keys.A) -> Mossa.PASSO_SINISTRO
            giu(Input.Keys.D) -> Mossa.PASSO_DESTRO
            else -> null
        } ?: return

        if (!gruppo.esegui(mossa) && gruppo.rifiuto != ultimoRifiuto) {
            ultimoRifiuto = gruppo.rifiuto
            when (gruppo.rifiuto) {
                Rifiuto.PORTA_CHIUSA ->
                    Gdx.app.log("arcania", "La porta e' chiusa: di la' comincia il modulo successivo.")
                Rifiuto.ROCCIA -> Gdx.app.log("arcania", "Pietra viva.")
                else -> {}
            }
        }
        if (gruppo.rifiuto == Rifiuto.NIENTE) ultimoRifiuto = Rifiuto.NIENTE
    }

    override fun dispose() {
        batch.dispose()
        modello?.dispose()
    }
}
