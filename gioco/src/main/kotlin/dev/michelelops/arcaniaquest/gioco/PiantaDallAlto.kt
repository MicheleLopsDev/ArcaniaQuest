package dev.michelelops.arcaniaquest.gioco

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.utils.Disposable
import kotlin.math.tan
import dev.michelelops.arcaniaquest.regole.Cella
import dev.michelelops.arcaniaquest.regole.Dungeon
import dev.michelelops.arcaniaquest.regole.Esplorazione

/**
 * La mappa dipinta, e dove ci e' finito sopra il gruppo.
 *
 * [dove] va da 0 a 1 sui due lati del quadro, con lo zero in basso a
 * sinistra come vuole libGDX. Chi disegna il triangolo del gruppo non
 * deve rifare i conti dell'inquadratura: gli si dice il punto e basta.
 */
class Quadro(
    val quadro: TextureRegion,
    val doveX: Float,
    val doveY: Float,
    /** Quanti pixel del pannello vale una casella: ci si misura il triangolo del gruppo. */
    val perCasella: Float
)

/**
 * La mappa del sotterraneo: la scena vera guardata da sopra, non un
 * disegnino a parte.
 *
 * Si dipinge dentro un quadro fuori schermo e poi si appiccica nel
 * pannello come una figura qualsiasi. Costa un secondo disegno della
 * scena, ma il guadagno e' che la mappa **non puo' mentire**: sale tonde,
 * porte, muri e torce sono quelli, perche' sono gli stessi modelli della
 * vista in prima persona. Una mappa disegnata a parte prima o poi
 * racconta un sotterraneo diverso da quello che si cammina.
 */
class PiantaDallAlto : Disposable {

    private var quadro: FrameBuffer? = null
    private var largo = 0
    private var alto = 0

    private val batch = ModelBatch()
    /**
     * La telecamera della mappa guarda a picco ma **in prospettiva**, non
     * ortogonale. A rigore una mappa vorrebbe la seconda; il guaio e' che
     * dritti a picco un muro e' una superficie verticale, cioe' non si
     * vede: resterebbero le macchie del pavimento senza niente attorno. La
     * prospettiva apre le pareti verso i bordi e ridisegna il sotterraneo
     * come una pianta con lo spessore.
     */
    private val camera = PerspectiveCamera(APERTURA, 1f, 1f)

    /**
     * Sulla mappa non c'e' notte: si guarda una pianta, non una stanza.
     * Luce piena e nessuna nebbia, se no il buio del gioco si mangerebbe
     * proprio quello che la mappa deve far vedere.
     */
    private val ambiente = Environment().apply {
        set(ColorAttribute(ColorAttribute.AmbientLight, 1.05f, 1.02f, 0.98f, 1f))
        add(DirectionalLight().set(0.55f, 0.55f, 0.58f, -0.3f, -0.95f, -0.2f))
    }

    /**
     * Dipinge la mappa nella misura del pannello che la ospitera'.
     *
     * Null quando non c'e' ancora niente da far vedere: al primo
     * fotogramma di una partita nuova il gruppo non ha visto nulla.
     */
    fun dipingi(
        misura: Riq,
        dungeon: Dungeon,
        istanze: Map<String, ModelInstance>,
        esplorazione: Esplorazione,
        gruppoX: Float,
        gruppoZ: Float,
        scala: Scala
    ): Quadro? {
        val viste = esplorazione.celleViste()
        if (viste.isEmpty()) return null

        val telo = teloDa(misura) ?: return null
        val vista = inquadra(misura, viste, gruppoX, gruppoZ, scala)

        telo.begin()
        Gdx.gl.glClearColor(0.03f, 0.033f, 0.036f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)

        // Nebbia di guerra, un pezzo alla volta: un modulo compare appena
        // il gruppo ne ha visto una casella. A caselle sarebbe piu' avaro,
        // ma lascerebbe mezze stanze tagliate nel vuoto, e una stanza
        // tagliata a meta' non e' una mappa: e' un errore di disegno.
        batch.begin(camera)
        for (p in dungeon.pezzi) {
            if (p.celleMondo().none { it in viste }) continue
            istanze[p.chiave]?.let { senzaSoffitto(it) { i -> batch.render(i, ambiente) } }
        }
        batch.end()

        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        telo.end()

        // La texture di un quadro fuori schermo nasce con la y all'ingiu':
        // senza il ribaltamento la mappa esce a testa in giu' e il nord
        // finisce in basso.
        val figura = TextureRegion(telo.colorBufferTexture)
        figura.flip(false, true)

        // Dove cade il gruppo si chiede alla telecamera invece di rifare i
        // conti a mano: cosi' vale comunque sia inquadrata la scena, e non
        // c'e' una seconda formula da tenere allineata alla prima.
        // con le misure del quadro, non della finestra: project() senza
        // argomenti si arrangia con lo schermo intero e la freccia
        // finirebbe fuori dal sotterraneo
        val dove = camera.project(Vector3(gruppoX, 0f, gruppoZ), 0f, 0f, misura.w, misura.h)
        return Quadro(
            figura,
            doveX = dove.x / misura.w,
            doveY = dove.y / misura.h,
            perCasella = misura.h / vista.alto * Misure.CASELLA
        )
    }

    /** Quanto si vede della mappa: piccola nel pannello, larga a tutto schermo. */
    enum class Scala(val minimaPerCasella: Float, val massimaPerCasella: Float) {
        NEL_PANNELLO(5f, 22f),
        A_TUTTO_SCHERMO(7f, 40f)
    }

    private class Inquadratura(
        val centroX: Float, val centroZ: Float,
        val largo: Float, val alto: Float
    )

    /**
     * Quanto mondo entra nel quadro e attorno a cosa sta centrato.
     *
     * Sono le stesse due regole della vecchia mappa a quadretti, che
     * funzionavano: la casella non si rimpicciolisce oltre un minimo — sotto
     * quello la mappa smette di stringersi e comincia a scorrere dietro al
     * gruppo — e non si ingrandisce oltre un massimo, se no due caselle
     * appena viste diventano due francobolli giganti in mezzo al vuoto.
     */
    private fun inquadra(
        misura: Riq,
        viste: Collection<Cella>,
        gruppoX: Float,
        gruppoZ: Float,
        scala: Scala
    ): Inquadratura {
        val x0 = viste.minOf { it.x }
        val z0 = viste.minOf { it.z }
        val colonne = (viste.maxOf { it.x } - x0 + 1).coerceAtLeast(1)
        val righe = (viste.maxOf { it.z } - z0 + 1).coerceAtLeast(1)

        // Il calzante esatto non basta: la prospettiva allarga i muri, che
        // stanno tre metri piu' vicini alla telecamera del pavimento, e
        // sui bordi sborderebbero fuori dal quadro. Si sta un po' larghi.
        val perCasella = (minOf(misura.w / colonne, misura.h / righe) * RESPIRO)
            .coerceIn(scala.minimaPerCasella, scala.massimaPerCasella)
        val largo = misura.w / perCasella * Misure.CASELLA
        val alto = misura.h / perCasella * Misure.CASELLA

        val ciSta = colonne * perCasella <= misura.w
        val ciStaInAltezza = righe * perCasella <= misura.h
        val vista = Inquadratura(
            centroX = if (ciSta) (x0 + colonne / 2f) * Misure.CASELLA else gruppoX,
            centroZ = if (ciStaInAltezza) (z0 + righe / 2f) * Misure.CASELLA else gruppoZ,
            largo = largo,
            alto = alto
        )

        camera.viewportWidth = misura.w
        camera.viewportHeight = misura.h
        camera.fieldOfView = APERTURA
        // da che quota si vede alto metri di sotterraneo, con questa apertura
        val quota = alto / 2f / tan(Math.toRadians(APERTURA / 2.0)).toFloat()
        camera.position.set(vista.centroX, quota, vista.centroZ)
        // non proprio a piombo: a piombo la direzione e l'alto sarebbero
        // paralleli e la matrice della telecamera non starebbe in piedi
        camera.direction.set(0f, -1f, -0.0001f).nor()
        // il nord in cima: la z del sotterraneo cresce verso sud
        camera.up.set(0f, 0f, -1f)
        camera.near = 1f
        camera.far = quota + 10f
        camera.update()
        return vista
    }

    /**
     * Disegna un pezzo senza il suo soffitto.
     *
     * Guardando da sopra, la volta sta fra la telecamera e tutto il resto:
     * lasciandola si mapperebbe il tetto del sotterraneo, che e' una
     * macchia scura uguale dappertutto. Tagliarla col piano di taglio non
     * si puo', perche' la volta e le cime dei muri stanno alla stessa
     * quota e sparirebbero anche quelle, lasciando i muri invisibili —
     * visti dall'alto sono superfici verticali, cioe' niente.
     */
    private fun senzaSoffitto(istanza: ModelInstance, disegna: (ModelInstance) -> Unit) {
        val volte = istanza.nodes.flatMap { it.parts }.filter { it.meshPart.id == "soffitto" }
        volte.forEach { it.enabled = false }
        try {
            disegna(istanza)
        } finally {
            volte.forEach { it.enabled = true }
        }
    }

    /** Il quadro fuori schermo, rifatto solo quando cambia la misura del pannello. */
    private fun teloDa(misura: Riq): FrameBuffer? {
        val l = misura.w.toInt().coerceIn(16, 2048)
        val a = misura.h.toInt().coerceIn(16, 2048)
        if (quadro == null || l != largo || a != alto) {
            quadro?.dispose()
            quadro = FrameBuffer(Pixmap.Format.RGBA8888, l, a, true)
            largo = l
            alto = a
        }
        return quadro
    }

    override fun dispose() {
        quadro?.dispose()
        batch.dispose()
    }

    private companion object {
        /**
         * Quanto e' aperta la telecamera della mappa, in gradi.
         *
         * Stretta apposta: piu' si apre, piu' le pareti si sventagliano
         * verso i bordi e piu' la pianta si deforma. A quaranta i muri
         * hanno lo spessore che serve a vederli e il sotterraneo somiglia
         * ancora a se stesso.
         */
        const val APERTURA = 40f

        /** Quanto si sta larghi rispetto al calzante esatto. */
        const val RESPIRO = 0.9f
    }
}
