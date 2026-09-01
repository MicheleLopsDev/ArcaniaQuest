package dev.michelelops.arcaniaquest.gioco

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
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
import dev.michelelops.arcaniaquest.regole.Cella
import dev.michelelops.arcaniaquest.regole.Ostacolo
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
    val porteSpalancate: Boolean = false,
    /** Parte con la mappa grande gia' aperta. */
    val mappaAperta: Boolean = false,
    /** Segna come gia' visto tutto il sotterraneo: serve solo a fotografarlo. */
    val tuttoScoperto: Boolean = false,
    /** Parte col riquadro del seme gia' aperto, per fotografarlo. */
    val chiediIlSeme: String? = null,
    /**
     * Prima persona ma a giorno: niente buio e niente nebbia.
     * Serve a distinguere «e' buio» da «e' rotto», che al lume di torcia
     * sono la stessa cosa.
     */
    val pienaLuce: Boolean = false
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
    private lateinit var materiali: Materiali
    private var telaio = Telaio(1f, 1f)
    private var mappaGrande = false
    private var messaggio = ""
    /** Il seme che si sta battendo. null vuol dire che non si sta scrivendo. */
    private var semeScritto: String? = null

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

        camera = PerspectiveCamera(Misure.CAMPO_VISIVO, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat()).apply {
            near = 0.15f
            far = if (avvio.dallAlto) 1200f else if (avvio.pienaLuce) 120f else Misure.FONDO_BUIO
        }
        batch = ModelBatch()
        materiali = Materiali()
        if (!avvio.dallAlto) cruscotto = Cruscotto()
        mappaGrande = avvio.mappaAperta
        semeScritto = avvio.chiediIlSeme
        Gdx.input.inputProcessor = tastiera()

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

        esplorazione = Esplorazione(dungeon.caselleInTutto)
        if (avvio.tuttoScoperto) {
            for (pezzo in dungeon.pezzi) for (c in pezzo.celleMondo()) esplorazione.vedi(c)
        }
        guardatiAttorno()
        messaggio = ""

        if (avvio.dallAlto) Gdx.app.log("arcania", "\n" + dungeon.disegno())

        // il titolo segue il seme: cambiandolo dentro il gioco, prima
        // restava quello di partenza e non tornava piu' con il pannello
        Gdx.graphics.setTitle("ArcaniaQuest - seme ${sorte.semeScritto()}")

        rifaiAmbiente()
        for (m in modelli.values) m.dispose()
        modelli.clear(); istanze.clear()
        for (p in dungeon.pezzi) rifaiPezzo(p)

        if (avvio.dallAlto || avvio.pienaLuce) elencaBattenti()
    }

    /** Dove finisce ogni battente, in metri di mondo. Solo diagnostica. */
    private fun elencaBattenti() {
        Gdx.app.log("arcania", "battenti disegnati:")
        for (p in dungeon.pezzi) {
            for (i in dungeon.battentiChiusiDi(p.chiave)) {
                val k = p.modulo.connettori[i]
                val v = Pianta.varco(k, true)
                val (lx, lz) = CostruttoreMesh.centroPorta(k, v, Misure.CASELLA)
                val mx = lx + p.ox * Misure.CASELLA
                val mz = lz + p.oz * Misure.CASELLA
                Gdx.app.log(
                    "arcania",
                    "  %-9s conn %d %-5s   mondo %7.2f , %7.2f"
                        .format(p.chiave, i, k.lato.name.lowercase(), mx, mz)
                )
            }
        }
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
                // Senza passaggio il modulo e' isolato — e' la modalita'
                // guarda-un-pezzo — e allora vale quello che dice il
                // catalogo, se no le porte sparirebbero proprio dove le si
                // sta andando a controllare.
                battente = if (porta != null)
                    !porta.aperta && porta.proprietario == (p.chiave to i)
                else k.porta
            )
        }
        val modello = CostruttoreMesh.costruisci(p.modulo, materiali, aperture) { lx, lz ->
            // di la' dal muro c'e' un altro modulo? allora li' non ci va
            // pietra: il confine e' condiviso
            val altro = dungeon.pezzoIn(lx + p.ox, lz + p.oz)
            altro != null && altro.chiave != p.chiave
        }
        modelli[p.chiave] = modello
        istanze[p.chiave] = ModelInstance(modello).apply {
            transform.setToTranslation(p.ox * Misure.CASELLA, 0f, p.oz * Misure.CASELLA)
        }
    }

    private fun rifaiAmbiente() {
        ambiente = Environment().apply {
            if (avvio.dallAlto || avvio.pienaLuce) {
                set(ColorAttribute(ColorAttribute.AmbientLight, 0.55f, 0.56f, 0.58f, 1f))
                add(DirectionalLight().set(0.5f, 0.5f, 0.5f, -0.4f, -0.9f, -0.25f))
            } else {
                set(ColorAttribute(ColorAttribute.AmbientLight, 0.34f, 0.35f, 0.40f, 1f))
                set(ColorAttribute(ColorAttribute.Fog, 0.015f, 0.018f, 0.024f, 1f))
                add(DirectionalLight().set(0.24f, 0.25f, 0.29f, -0.4f, -0.9f, -0.25f))
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
        telaio = Telaio(width.toFloat(), height.toFloat())
    }

    /**
     * Quello che il gruppo vede da dove sta: la sua casella, quelle a
     * fianco, e il corridoio davanti finche' qualcosa non lo ferma. E'
     * questo che scopre la mappa, non il solo camminarci sopra: se no una
     * sala grande resterebbe nera fino all'ultimo angolo.
     */
    private fun guardatiAttorno() {
        val qui = Cella(gruppo.x, gruppo.z)
        esplorazione.calpesta(qui)
        for (l in Lato.entries) {
            val n = Cella(qui.x + l.dx, qui.z + l.dz)
            if (dungeon.calpestabile(n.x, n.z)) esplorazione.vedi(n)
        }
        var c = qui
        repeat(14) {
            if (dungeon.ostacolo(c.x, c.z, gruppo.verso) != Ostacolo.NIENTE) return
            c = Cella(c.x + gruppo.verso.dx, c.z + gruppo.verso.dz)
            esplorazione.vedi(c)
            for (l in listOf(gruppo.verso.ruotato(1), gruppo.verso.ruotato(-1))) {
                val fianco = Cella(c.x + l.dx, c.z + l.dz)
                if (dungeon.calpestabile(fianco.x, fianco.z)) esplorazione.vedi(fianco)
            }
        }
    }

    override fun render() {
        val l = Gdx.graphics.width.toFloat()
        val a = Gdx.graphics.height.toFloat()
        if (telaio.larghezza != l || telaio.altezza != a) telaio = Telaio(l, a)

        if (avvio.dallAlto) inquadraDallAlto() else inquadraDalGruppo()

        Gdx.gl.glClearColor(0.02f, 0.022f, 0.026f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        // La scena non prende piu' tutto lo schermo: sta nel suo riquadro,
        // e le cornici le stanno attorno invece che sopra.
        val r = if (cruscotto?.visibile == true) dentroLaVista() else Riq(0f, 0f, l, a)
        camera.viewportWidth = r.w
        camera.viewportHeight = r.h
        camera.update()

        val sx = Gdx.graphics.backBufferWidth.toFloat() / l
        val sy = Gdx.graphics.backBufferHeight.toFloat() / a
        Gdx.gl.glViewport((r.x * sx).toInt(), (r.y * sy).toInt(), (r.w * sx).toInt(), (r.h * sy).toInt())
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)

        batch.begin(camera)
        for (p in dungeon.pezzi) {
            if (!avvio.dallAlto && lontano(p)) continue
            istanze[p.chiave]?.let { batch.render(it, ambiente) }
        }
        batch.end()

        Gdx.gl.glViewport(0, 0, Gdx.graphics.backBufferWidth, Gdx.graphics.backBufferHeight)
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        cruscotto?.disegna(telaio, statoHud())

        fotogrammi++
        if (avvio.scattaDopo > 0 && fotogrammi >= avvio.scattaDopo) scatta()
    }

    /**
     * La tastiera per scrivere un seme.
     *
     * Si legge dai codici dei tasti e non dai caratteri: un seme sta in
     * base 36, cioe' lettere e cifre, e cosi' non c'e' da litigare con
     * accenti e disposizioni di tastiera diverse. Mentre si scrive, la
     * tastiera si mangia tutto: il gruppo non deve camminare perche' hai
     * battuto una D.
     */
    private fun tastiera() = object : InputAdapter() {
        override fun keyDown(keycode: Int): Boolean {
            val ora = semeScritto
            if (ora == null) {
                if (keycode != Input.Keys.ENTER) return false
                semeScritto = ""
                messaggio = "Scrivi un seme e premi INVIO. ESC annulla."
                return true
            }
            when (keycode) {
                Input.Keys.ENTER -> {
                    val testo = ora.trim()
                    semeScritto = null
                    if (testo.isEmpty()) {
                        messaggio = ""
                    } else {
                        nuovaPartita(Sorte.leggi(testo))
                        messaggio = "Sotterraneo del seme ${sorte.semeScritto()}."
                    }
                }
                Input.Keys.ESCAPE -> {
                    semeScritto = null
                    messaggio = ""
                }
                Input.Keys.BACKSPACE -> semeScritto = ora.dropLast(1)
                Input.Keys.SPACE -> if (ora.length < 24) semeScritto = "$ora "
                in Input.Keys.A..Input.Keys.Z ->
                    if (ora.length < 24) semeScritto = ora + ('A' + (keycode - Input.Keys.A))
                in Input.Keys.NUM_0..Input.Keys.NUM_9 ->
                    if (ora.length < 24) semeScritto = ora + ('0' + (keycode - Input.Keys.NUM_0))
                else -> {}
            }
            return true
        }
    }

    /** Il buco della cornice, dove va disegnata la scena. */
    private fun dentroLaVista(): Riq {
        val v = telaio.vista
        val barra = 20f + telaio.altezza * 0.012f
        return Riq(v.x + 1f, v.y + 1f, v.w - 2f, v.h - barra - 2f)
    }

    private fun statoHud(): StatoHud {
        val p = pezzoCorrente()
        return StatoHud(
            dungeon = dungeon,
            esplorazione = esplorazione,
            x = gruppo.x, z = gruppo.z, verso = gruppo.verso,
            seme = sorte.semeScritto(),
            stanza = p?.let { "${it.modulo.id} ${it.modulo.nome}" } ?: "fuori dal sotterraneo",
            famiglia = p?.modulo?.famiglia?.name?.lowercase() ?: "-",
            mappaGrande = mappaGrande,
            messaggio = messaggio,
            semeInScrittura = semeScritto,
            unSoloModulo = avvio.modulo != null
        )
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
        // Mentre si scrive un seme il gruppo non si muove: le lettere sono
        // lettere, non comandi.
        if (semeScritto != null) return

        if (Gdx.input.isKeyJustPressed(Input.Keys.F1)) {
            cruscotto?.let { it.visibile = !it.visibile }
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.M)) mappaGrande = !mappaGrande
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
            nuovaPartita(Sorte.nuova())
            messaggio = "Sotterraneo nuovo: seme ${sorte.semeScritto()}."
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
            guardatiAttorno()
            ultimoRifiuto = Rifiuto.NIENTE
            messaggio = ""
            return
        }
        if (gruppo.rifiuto != ultimoRifiuto) {
            ultimoRifiuto = gruppo.rifiuto
            messaggio = when (gruppo.rifiuto) {
                Rifiuto.PORTA_CHIUSA -> "La porta e' chiusa. SPAZIO per aprirla."
                Rifiuto.MURO -> "Di la' c'e' un altro pezzo, ma non ci si passa."
                Rifiuto.ROCCIA -> "Pietra viva."
                else -> ""
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
        guardatiAttorno()
        messaggio = "La porta si apre."
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
        materiali.dispose()
        for (m in modelli.values) m.dispose()
        cruscotto?.dispose()
    }
}
