package dev.michelelops.arcaniaquest.gioco

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.environment.PointLight
import dev.michelelops.arcaniaquest.regole.Catalogo
import dev.michelelops.arcaniaquest.regole.Esplorazione
import dev.michelelops.arcaniaquest.regole.Lato
import dev.michelelops.arcaniaquest.regole.Modulo
import dev.michelelops.arcaniaquest.regole.Sorte
import kotlin.math.cos
import kotlin.math.sin

/**
 * Come si avvia una partita. Sta tutto qui invece che in sei parametri
 * in fila, cosi' aggiungerne uno non tocca ogni chiamata.
 */
data class Avvio(
    /** Id del modulo. Se manca, lo pesca il seme col d66. */
    val modulo: String? = null,
    val sorte: Sorte = Sorte.nuova(),
    /** Se maggiore di zero: disegna tanti fotogrammi, salva uno scatto e chiude. */
    val scattaDopo: Int = 0,
    val fileScatto: String = "scatto.png",
    /** Telecamera a picco, senza buio: per vedere se alla mesh manca un pezzo. */
    val dallAlto: Boolean = false,
    /** Casella e verso da cui guardare, per rifare due volte lo stesso scatto. */
    val posa: Triple<Int, Int, Lato>? = null
)

/**
 * Il primo schermo: un modulo, il gruppo dentro, i comandi a caselle e
 * il pannello di servizio.
 *
 * Non e' ancora il gioco — c'e' un pezzo solo e non c'e' l'interfaccia.
 * Serve a provare la cosa che tutto il resto da' per scontata: che il
 * dato diventi geometria e che muoversi a caselle sia come nel prototipo.
 */
class SchermoDungeon(private val avvio: Avvio = Avvio()) : ApplicationAdapter() {

    private lateinit var catalogo: Catalogo
    private lateinit var modulo: Modulo
    private lateinit var gruppo: Gruppo
    private lateinit var esplorazione: Esplorazione

    private lateinit var camera: PerspectiveCamera
    private lateinit var batch: ModelBatch
    private lateinit var ambiente: Environment
    private var modello: Model? = null
    private var istanza: ModelInstance? = null
    private var cruscotto: Cruscotto? = null

    private val torciaDelGruppo = PointLight()
    private var ultimoRifiuto: Rifiuto = Rifiuto.NIENTE
    private var fotogrammi = 0

    override fun create() {
        catalogo = Catalogo.daJson(Gdx.files.internal("moduli/catalogo.json").readString("UTF-8"))
        // Senza un modulo scelto a mano, e' il seme a pescarlo: cosi' anche
        // adesso che il pezzo e' uno solo, il seme conta gia' qualcosa.
        modulo = avvio.modulo?.let { catalogo[it] } ?: catalogo.d66(avvio.sorte.d66())

        gruppo = avvio.posa?.let { Gruppo(modulo, it.first, it.second, it.third) }
            ?: Gruppo.dallaPartenza(modulo)

        esplorazione = Esplorazione().apply {
            aggiungiModulo(modulo)
            visita(modulo.id, gruppo.x, gruppo.z)
        }

        camera = PerspectiveCamera(64f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat()).apply {
            near = 0.15f
            far = if (avvio.dallAlto) 400f else Misure.FONDO_BUIO
        }
        batch = ModelBatch()
        if (!avvio.dallAlto) cruscotto = Cruscotto()

        ambiente = Environment().apply {
            if (avvio.dallAlto) {
                set(ColorAttribute(ColorAttribute.AmbientLight, 0.55f, 0.56f, 0.58f, 1f))
                add(DirectionalLight().set(0.5f, 0.5f, 0.5f, -0.4f, -0.9f, -0.25f))
            } else {
                set(ColorAttribute(ColorAttribute.AmbientLight, 0.17f, 0.18f, 0.22f, 1f))
                set(ColorAttribute(ColorAttribute.Fog, 0.015f, 0.018f, 0.024f, 1f))
                add(DirectionalLight().set(0.13f, 0.14f, 0.17f, -0.4f, -0.9f, -0.25f))
                add(torciaDelGruppo)
                for (a in modulo.arredi.filter { it.tipo == "torcia" }) {
                    add(PointLight().set(
                        Color(1f, 0.62f, 0.28f, 1f),
                        (a.x + 0.5f) * Misure.CASELLA,
                        Misure.ALTEZZA_MURO * 0.62f,
                        (a.z + 0.5f) * Misure.CASELLA,
                        Misure.FORZA_TORCIA_A_MURO
                    ))
                }
            }
        }

        modello = CostruttoreMesh.costruisci(modulo).also { istanza = ModelInstance(it) }
    }

    override fun resize(width: Int, height: Int) {
        camera.viewportWidth = width.toFloat()
        camera.viewportHeight = height.toFloat()
        camera.update()
    }

    override fun render() {
        if (avvio.dallAlto) inquadraDallAlto() else inquadraDalGruppo()

        Gdx.gl.glClearColor(0.012f, 0.014f, 0.018f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)

        istanza?.let {
            batch.begin(camera)
            batch.render(it, ambiente)
            batch.end()
        }

        // Il pannello va dopo la scena e senza prova di profondita',
        // altrimenti i muri se lo mangiano.
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        cruscotto?.disegna(voci(), esplorazione.frazione, TASTI_IN_CHIARO)

        fotogrammi++
        if (avvio.scattaDopo > 0 && fotogrammi >= avvio.scattaDopo) scatta()
    }

    private fun voci(): List<Voce> = listOf(
        Voce("CASELLA", "${gruppo.x}, ${gruppo.z}"),
        Voce("METRI", "%.1f, %.1f".format(
            (gruppo.x + 0.5f) * Misure.CASELLA, (gruppo.z + 0.5f) * Misure.CASELLA)),
        Voce("VERSO", gruppo.verso.name.lowercase()),
        Voce("STANZA", "${modulo.id}  ${modulo.nome}"),
        Voce("FAMIGLIA", modulo.famiglia.name.lowercase()),
        Voce("SEME", avvio.sorte.semeScritto()),
        Voce("ESPLORATO", "${esplorazione.quante} / ${esplorazione.inTutto} caselle   ${esplorazione.percento}%")
    )

    /** A picco sul modulo, tutto dentro l'inquadratura. */
    private fun inquadraDallAlto() {
        val c = Misure.CASELLA
        val quanto = maxOf(modulo.larghezza, modulo.profondita) * c
        camera.position.set(modulo.larghezza * c / 2f, quanto * 1.25f, modulo.profondita * c / 2f)
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
        torciaDelGruppo.set(
            Color(1f, 0.68f, 0.36f, 1f),
            px, Misure.ALTEZZA_OCCHI + 0.35f, pz,
            Misure.FORZA_TORCIA_GRUPPO
        )
    }

    /**
     * Si legge lo stato dei tasti a ogni fotogramma invece degli eventi:
     * tenendo premuto si cammina, e il ritmo lo detta la durata del passo,
     * perche' finche' il gruppo si muove ogni altra mossa viene rifiutata.
     */
    private fun leggiComandi() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.F1)) {
            cruscotto?.let { it.visibile = !it.visibile }
        }

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

        if (gruppo.esegui(mossa)) {
            esplorazione.visita(modulo.id, gruppo.x, gruppo.z)
            ultimoRifiuto = Rifiuto.NIENTE
            return
        }
        if (gruppo.rifiuto != ultimoRifiuto) {
            ultimoRifiuto = gruppo.rifiuto
            when (gruppo.rifiuto) {
                Rifiuto.PORTA_CHIUSA ->
                    Gdx.app.log("arcania", "La porta e' chiusa: di la' comincia il modulo successivo.")
                Rifiuto.ROCCIA -> Gdx.app.log("arcania", "Pietra viva.")
                else -> {}
            }
        }
    }

    private fun scatta() {
        val pixmap = Pixmap.createFromFrameBuffer(
            0, 0, Gdx.graphics.backBufferWidth, Gdx.graphics.backBufferHeight
        )
        // OpenGL legge il fotogramma dal basso: senza il ribaltamento il
        // PNG esce a testa in giu' e sembra che il pavimento sia il soffitto.
        PixmapIO.writePNG(
            Gdx.files.absolute(avvio.fileScatto), pixmap,
            java.util.zip.Deflater.DEFAULT_COMPRESSION, true
        )
        pixmap.dispose()
        Gdx.app.log("arcania", "scatto salvato in ${avvio.fileScatto}")
        Gdx.app.exit()
    }

    override fun dispose() {
        batch.dispose()
        modello?.dispose()
        cruscotto?.dispose()
    }

    companion object {
        private val TASTI_IN_CHIARO = listOf(
            "SU / GIU  avanti e indietro",
            "SX / DX  volta di 90 gradi",
            "A D  passo laterale",
            "F1  nasconde il pannello"
        )
    }
}
