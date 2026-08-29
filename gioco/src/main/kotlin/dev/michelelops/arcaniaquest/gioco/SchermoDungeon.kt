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
import dev.michelelops.arcaniaquest.regole.Dungeon
import dev.michelelops.arcaniaquest.regole.Esplorazione
import dev.michelelops.arcaniaquest.regole.Generatore
import dev.michelelops.arcaniaquest.regole.Lato
import dev.michelelops.arcaniaquest.regole.Piazzato
import dev.michelelops.arcaniaquest.regole.Sorte
import kotlin.math.cos
import kotlin.math.sin

/**
 * Come si avvia una partita. Sta tutto qui invece che in sette parametri
 * in fila, cosi' aggiungerne uno non tocca ogni chiamata.
 */
data class Avvio(
    /** Un solo modulo, per guardarlo da vicino. Se manca, si genera un sotterraneo. */
    val modulo: String? = null,
    val sorte: Sorte = Sorte.nuova(),
    val quantiPezzi: Int = 12,
    /** Se maggiore di zero: disegna tanti fotogrammi, salva uno scatto e chiude. */
    val scattaDopo: Int = 0,
    val fileScatto: String = "scatto.png",
    /** Telecamera a picco, senza buio: per vedere se alla mesh manca un pezzo. */
    val dallAlto: Boolean = false,
    /** Casella e verso da cui guardare, per rifare due volte lo stesso scatto. */
    val posa: Triple<Int, Int, Lato>? = null,
    /** Apre tutte le porte all'avvio: serve a fotografare il prima e il dopo. */
    val porteSpalancate: Boolean = false
)

/**
 * Il gioco: un sotterraneo generato, il gruppo dentro, i comandi a
 * caselle e il pannello di servizio.
 */
class SchermoDungeon(private val avvio: Avvio = Avvio()) : ApplicationAdapter() {

    private lateinit var catalogo: Catalogo
    private lateinit var generatore: Generatore

    private lateinit var sorte: Sorte
    private lateinit var dungeon: Dungeon
    private lateinit var gruppo: Gruppo
    private lateinit var esplorazione: Esplorazione

    private lateinit var camera: PerspectiveCamera
    private lateinit var batch: ModelBatch
    private var cruscotto: Cruscotto? = null

    /** Un modello e la sua istanza per ogni pezzo, cosi' si rifa' solo quello che cambia. */
    private val modelli = LinkedHashMap<String, Model>()
    private val istanze = LinkedHashMap<String, ModelInstance>()
    private var ambiente = Environment()

    private val torciaDelGruppo = PointLight()
    private var ultimoRifiuto: Rifiuto = Rifiuto.NIENTE
    private var fotogrammi = 0

    override fun create() {
        catalogo = Catalogo.daJson(Gdx.files.internal("moduli/catalogo.json").readString("UTF-8"))
        generatore = Generatore(catalogo)

        camera = PerspectiveCamera(64f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat()).apply {
            near = 0.15f
            far = if (avvio.dallAlto) 1200f else Misure.FONDO_BUIO
        }
        batch = ModelBatch()
        if (!avvio.dallAlto) cruscotto = Cruscotto()

        nuovaPartita(avvio.sorte)
    }

    /**
     * Butta il sotterraneo e ne monta un altro. E' quello che fa il tasto
     * R: cambia il seme, quindi cambia tutto — pezzi, porte, esplorato.
     */
    private fun nuovaPartita(conSorte: Sorte) {
        sorte = conSorte
        dungeon = avvio.modulo?.let { Dungeon.unoSolo(catalogo[it], sorte.seme) }
            ?: generatore.genera(sorte, avvio.quantiPezzi)

        if (avvio.porteSpalancate) dungeon.passaggi.forEach { it.apri() }

        gruppo = avvio.posa?.let { Gruppo(dungeon, it.first, it.second, it.third) }
            ?: Gruppo.dallaPartenza(dungeon)

        esplorazione = Esplorazione(dungeon.caselleInTutto).apply {
            visita(pezzoCorrente()?.chiave ?: "", gruppo.x, gruppo.z)
        }

        if (avvio.dallAlto) Gdx.app.log("arcania", "\n" + dungeon.disegno())

        rifaiAmbiente()
        for (m in modelli.values) m.dispose()
        modelli.clear(); istanze.clear()
        for (p in dungeon.pezzi) rifaiPezzo(p)
    }

    /** Rifa' la mesh di un pezzo solo: serve quando una porta si apre. */
    private fun rifaiPezzo(p: Piazzato) {
        modelli.remove(p.chiave)?.dispose()
        val aperture = dungeon.varchiDi(p.chiave).sorted().map { i ->
            val k = p.modulo.connettori[i]
            val cella = p.cellaDi(i)
            val porta = dungeon.portaFra(cella.x, cella.z, k.lato)
            Apertura(
                indice = i,
                stretta = porta?.conBattente ?: k.porta,
                battente = porta != null && !porta.aperta && porta.proprietario == (p.chiave to i)
            )
        }
        val modello = CostruttoreMesh.costruisci(p.modulo, aperture)
        modelli[p.chiave] = modello
        istanze[p.chiave] = ModelInstance(modello).apply {
            transform.setToTranslation(p.ox * Misure.CASELLA, 0f, p.oz * Misure.CASELLA)
        }
    }

    private fun rifaiAmbiente() {
        ambiente = Environment().apply {
            if (avvio.dallAlto) {
                set(ColorAttribute(ColorAttribute.AmbientLight, 0.55f, 0.56f, 0.58f, 1f))
                add(DirectionalLight().set(0.5f, 0.5f, 0.5f, -0.4f, -0.9f, -0.25f))
            } else {
                set(ColorAttribute(ColorAttribute.AmbientLight, 0.17f, 0.18f, 0.22f, 1f))
                set(ColorAttribute(ColorAttribute.Fog, 0.015f, 0.018f, 0.024f, 1f))
                add(DirectionalLight().set(0.13f, 0.14f, 0.17f, -0.4f, -0.9f, -0.25f))
                add(torciaDelGruppo)
                // Le torce a muro dei pezzi che le hanno. Il numero di luci
                // che il motore accetta e' piccolo, quindi si tengono solo
                // quelle vicine al gruppo: le altre non si vedrebbero.
                for (p in dungeon.pezzi) for (a in p.modulo.arredi.filter { it.tipo == "torcia" }) {
                    add(PointLight().set(
                        Color(1f, 0.62f, 0.28f, 1f),
                        (a.x + p.ox + 0.5f) * Misure.CASELLA,
                        Misure.ALTEZZA_MURO * 0.62f,
                        (a.z + p.oz + 0.5f) * Misure.CASELLA,
                        Misure.FORZA_TORCIA_A_MURO
                    ))
                }
            }
        }
    }

    private fun pezzoCorrente(): Piazzato? = dungeon.pezzoIn(gruppo.x, gruppo.z)

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

        batch.begin(camera)
        for (p in dungeon.pezzi) {
            if (!avvio.dallAlto && lontano(p)) continue
            istanze[p.chiave]?.let { batch.render(it, ambiente) }
        }
        batch.end()

        // Il pannello va dopo la scena e senza prova di profondita',
        // altrimenti i muri se lo mangiano.
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        cruscotto?.disegna(voci(), esplorazione.frazione, TASTI_IN_CHIARO)

        fotogrammi++
        if (avvio.scattaDopo > 0 && fotogrammi >= avvio.scattaDopo) scatta()
    }

    /**
     * Un pezzo oltre il fondo del buio non si vede comunque: non vale la
     * pena mandarlo alla scheda video. E' il culling piu' rozzo che ci
     * sia, ma con quindici pezzi basta e avanza.
     */
    private fun lontano(p: Piazzato): Boolean {
        val cx = (p.ox + p.modulo.larghezza / 2f) * Misure.CASELLA
        val cz = (p.oz + p.modulo.profondita / 2f) * Misure.CASELLA
        val raggio = maxOf(p.modulo.larghezza, p.modulo.profondita) * Misure.CASELLA
        val dx = cx - camera.position.x
        val dz = cz - camera.position.z
        return dx * dx + dz * dz > (Misure.FONDO_BUIO + raggio) * (Misure.FONDO_BUIO + raggio)
    }

    private fun voci(): List<Voce> {
        val p = pezzoCorrente()
        return listOf(
            Voce("CASELLA", "${gruppo.x}, ${gruppo.z}"),
            Voce("METRI", "%.1f, %.1f".format(
                (gruppo.x + 0.5f) * Misure.CASELLA, (gruppo.z + 0.5f) * Misure.CASELLA)),
            Voce("VERSO", gruppo.verso.name.lowercase()),
            Voce("STANZA", p?.let { "${it.modulo.id}  ${it.modulo.nome}" } ?: "-"),
            Voce("FAMIGLIA", p?.modulo?.famiglia?.name?.lowercase() ?: "-"),
            Voce("SEME", sorte.semeScritto()),
            Voce("PEZZI", "${dungeon.pezzi.size}   porte ${dungeon.porteAperte}/${dungeon.porteInTutto} aperte"),
            Voce("ESPLORATO", "${esplorazione.quante} / ${esplorazione.inTutto} caselle   ${esplorazione.percento}%")
        )
    }

    /** A picco su tutto il sotterraneo, tutto dentro l'inquadratura. */
    private fun inquadraDallAlto() {
        val c = Misure.CASELLA
        val celle = dungeon.pezzi.flatMap { it.celleMondo() }
        val x0 = celle.minOf { it.x }; val x1 = celle.maxOf { it.x } + 1
        val z0 = celle.minOf { it.z }; val z1 = celle.maxOf { it.z } + 1
        val quanto = maxOf(x1 - x0, z1 - z0) * c
        camera.position.set((x0 + x1) / 2f * c, quanto * 1.2f, (z0 + z1) / 2f * c)
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
     * Aprire e rigenerare invece vanno alla pressione, non alla tenuta.
     */
    private fun leggiComandi() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.F1)) {
            cruscotto?.let { it.visibile = !it.visibile }
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
            nuovaPartita(Sorte.nuova())
            Gdx.app.log("arcania", "sotterraneo nuovo, seme ${sorte.semeScritto()}")
            return
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) apri()

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
            esplorazione.visita(pezzoCorrente()?.chiave ?: "", gruppo.x, gruppo.z)
            ultimoRifiuto = Rifiuto.NIENTE
            return
        }
        if (gruppo.rifiuto != ultimoRifiuto) {
            ultimoRifiuto = gruppo.rifiuto
            when (gruppo.rifiuto) {
                Rifiuto.PORTA_CHIUSA -> Gdx.app.log("arcania", "La porta e' chiusa. SPAZIO per aprirla.")
                Rifiuto.MURO -> Gdx.app.log("arcania", "Di la' c'e' un altro pezzo, ma non ci si passa.")
                Rifiuto.ROCCIA -> Gdx.app.log("arcania", "Pietra viva.")
                else -> {}
            }
        }
    }

    /**
     * Apre la porta davanti. Una volta aperta resta aperta, quindi basta
     * rifare la mesh del pezzo che disegnava il battente: quello e' l'unico
     * a cui e' cambiato qualcosa.
     */
    private fun apri() {
        val porta = dungeon.apri(gruppo.x, gruppo.z, gruppo.verso) ?: return
        porta.proprietario?.let { (chiave, _) ->
            dungeon.pezzi.firstOrNull { it.chiave == chiave }?.let { rifaiPezzo(it) }
        }
        Gdx.app.log("arcania", "La porta si apre.")
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
        for (m in modelli.values) m.dispose()
        cruscotto?.dispose()
    }

    companion object {
        private val TASTI_IN_CHIARO = listOf(
            "SU / GIU  avanti e indietro",
            "SX / DX  volta di 90 gradi",
            "A D  passo laterale",
            "SPAZIO  apri",
            "R  sotterraneo nuovo",
            "F1  nasconde il pannello"
        )
    }
}
