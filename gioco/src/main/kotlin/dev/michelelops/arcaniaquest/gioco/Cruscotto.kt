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
import dev.michelelops.arcaniaquest.regole.Cella
import dev.michelelops.arcaniaquest.regole.Dungeon
import dev.michelelops.arcaniaquest.regole.Esplorazione
import dev.michelelops.arcaniaquest.regole.Famiglia
import dev.michelelops.arcaniaquest.regole.Lato

/** Una riga di dati: a sinistra cosa e', a destra quanto vale. */
data class Voce(val etichetta: String, val valore: String)

/** Tutto quello che al cruscotto serve sapere per disegnare un fotogramma. */
class StatoHud(
    val dungeon: Dungeon,
    val esplorazione: Esplorazione,
    val x: Int,
    val z: Int,
    val verso: Lato,
    val seme: String,
    val stanza: String,
    val famiglia: String,
    val mappaGrande: Boolean,
    val messaggio: String
)

/**
 * L'interfaccia: le cornici attorno alla vista, la mappa che si scopre
 * camminando, e i pannelli ancora finti.
 *
 * Disegna tutto con un carattere e un pixel bianco tinto: nessun file da
 * caricare, quindi niente da tenere allineato fra desktop e Android.
 * Quando arrivera' la grafica vera si sostituiscono le tinte, non la
 * struttura.
 */
class Cruscotto : Disposable {

    private val batch = SpriteBatch()
    private val font = BitmapFont()
    private val misura = GlyphLayout()
    private val proiezione = Matrix4()
    private val tinta: Texture

    var visibile = true

    // Una tavolozza sola: cambiarla qui cambia tutta l'interfaccia.
    private val pece = Color(0.043f, 0.047f, 0.051f, 1f)
    private val fondo = Color(0.07f, 0.075f, 0.08f, 0.95f)
    private val incisione = Color(0.145f, 0.15f, 0.145f, 1f)
    private val rilievo = Color(0.26f, 0.25f, 0.21f, 1f)
    private val ambra = Color(0.88f, 0.60f, 0.28f, 1f)
    private val pergamena = Color(0.86f, 0.84f, 0.78f, 1f)
    private val spenta = Color(0.50f, 0.49f, 0.45f, 1f)
    private val verde = Color(0.42f, 0.68f, 0.38f, 1f)
    private val ruggine = Color(0.72f, 0.35f, 0.22f, 1f)
    private val sala = Color(0.42f, 0.40f, 0.36f, 1f)
    private val andito = Color(0.28f, 0.30f, 0.33f, 1f)
    private val sangue = Color(0.66f, 0.22f, 0.20f, 1f)

    private var s = 1f

    init {
        val p = Pixmap(1, 1, Pixmap.Format.RGBA8888)
        p.setColor(Color.WHITE); p.fill()
        tinta = Texture(p); p.dispose()
        font.setUseIntegerPositions(false)
        // il carattere di serie e' da 15 pixel: ingrandito senza filtro
        // viene a scaletta, e un pannello si legge di sfuggita
        font.region.texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
    }

    fun disegna(t: Telaio, stato: StatoHud) {
        if (!visibile) return
        s = (t.altezza / 760f).coerceIn(0.85f, 2.4f)
        font.data.setScale(s)

        proiezione.setToOrtho2D(0f, 0f, t.larghezza, t.altezza)
        batch.projectionMatrix = proiezione
        batch.begin()

        // Niente fondo a tutto schermo: la scena e' gia' stata disegnata
        // nel suo riquadro, e coprirla sarebbe il modo piu' rapido di
        // ritrovarsi con una vista nera senza capire perche'.
        // Nella barra della vista, che e' quella che si guarda sempre,
        // finiscono le tre cose che servono a raccontare dove si e': la
        // stanza, la casella col verso, e il seme per rifare la partita.
        cornice(t.vista, "VISTA IN PRIMA PERSONA",
            "${stato.stanza}     ${stato.x}, ${stato.z}  ${stato.verso.name.lowercase()}     seme ${stato.seme}")
        zaino(t.zaino)
        mappa(t.mappa, stato, false)
        gruppo(t.gruppo)
        diario(t.diario, stato)
        comandi(t.comandi, stato)

        if (stato.mappaGrande) {
            rett(Riq(0f, 0f, t.larghezza, t.altezza), Color(0f, 0f, 0f, 0.72f))
            val grande = Riq(t.larghezza * 0.08f, t.altezza * 0.08f, t.larghezza * 0.84f, t.altezza * 0.84f)
            mappa(grande, stato, true)
        }

        batch.end()
    }

    // ---------- pannelli ----------

    private fun cornice(r: Riq, titolo: String, aDestra: String = "") {
        rett(Riq(r.x, r.y1 - barra(), r.w, barra()), incisione)
        bordo(r)
        val yTitolo = r.y1 - barra() + barra() * 0.72f
        testo(titolo, r.x + pad(), yTitolo, spenta, 0.72f)
        // Se le due scritte non ci stanno affiancate, quella di destra si
        // toglie: due testi sovrapposti sono peggio di uno mancante.
        if (aDestra.isNotEmpty()) {
            val l = larghezza(aDestra, 0.72f)
            if (larghezza(titolo, 0.72f) + l + pad() * 4f <= r.w) {
                testo(aDestra, r.x1 - pad() - l, yTitolo, ambra, 0.72f)
            }
        }
    }

    /** Lo spazio dentro una cornice, sotto la barra del titolo. */
    private fun dentro(r: Riq) = Riq(r.x + pad(), r.y + pad(), r.w - pad() * 2f, r.h - barra() - pad() * 1.5f)

    private fun zaino(r: Riq) {
        cornice(r, "ZAINO", "finto")
        val d = dentro(r)
        // Quante caselle ci stanno lo dice lo spazio, non un numero deciso
        // a tavolino: in verticale il pannello e' stretto e quattro colonne
        // finirebbero una sopra l'altra.
        val lato = (minOf(d.w / 4f, d.h / 3f)).coerceIn(20f * s, 74f * s)
        val colonne = maxOf(1, (d.w / lato).toInt())
        val righe = maxOf(1, (d.h / lato).toInt())
        val x0 = d.x + (d.w - lato * colonne) / 2f
        val y0 = d.y1 - lato * righe
        for (i in 0 until minOf(colonne * righe, Finti.zaino.size)) {
            val cx = x0 + (i % colonne) * lato
            val cy = y0 + (righe - 1 - i / colonne) * lato
            val cella = Riq(cx + 2f * s, cy + 2f * s, lato - 4f * s, lato - 4f * s)
            rett(cella, fondo)
            bordo(cella)
            val nome = Finti.zaino.getOrElse(i) { "" }
            if (nome.isEmpty()) continue
            // niente icone finche' non c'e' la grafica: le iniziali dicono
            // gia' quello che serve a provare la disposizione
            val sigla = nome.take(2).uppercase()
            val l = larghezza(sigla, 0.8f)
            testo(sigla, cella.cx - l / 2f, cella.cy + font.lineHeight * 0.8f * 0.32f, pergamena, 0.8f)
        }
    }

    private fun gruppo(r: Riq) {
        cornice(r, "GRUPPO", "finto")
        val d = dentro(r)
        val riga = d.h / Finti.gruppo.size
        // Sotto una certa altezza il nome sopra e la barra sotto si
        // pestano i piedi: si passa a una riga sola, nome e barra affiancati.
        val stretta = riga < font.lineHeight * 1.9f
        for ((i, p) in Finti.gruppo.withIndex()) {
            val y = d.y1 - riga * (i + 1)
            val lato = riga * 0.82f
            val ritratto = Riq(d.x, y + riga * 0.09f, lato, lato)
            rett(ritratto, fondo); bordo(ritratto)
            val sigla = p.ruolo.take(1)
            testo(sigla, ritratto.cx - larghezza(sigla, 0.8f) / 2f,
                ritratto.cy + font.lineHeight * 0.8f * 0.3f, ambra, 0.8f)

            val tx = ritratto.x1 + pad()
            val quota = p.vita.toFloat() / p.vitaMassima
            val cifre = "${p.vita}/${p.vitaMassima}"
            val colore = if (quota < 0.34f) sangue else ruggine

            if (stretta) {
                testo(p.nome, tx, y + riga * 0.74f, pergamena, 0.7f)
                val larghezzaNome = larghezza(p.nome, 0.7f) + pad()
                val bx = tx + larghezzaNome
                val bl = d.x1 - bx - larghezza(cifre, 0.62f) - pad() * 2f
                if (bl > 10f * s) {
                    val barraVita = Riq(bx, y + riga * 0.36f, bl, 4f * s)
                    rett(barraVita, incisione)
                    rett(Riq(barraVita.x, barraVita.y, barraVita.w * quota, barraVita.h), colore)
                }
                testo(cifre, d.x1 - larghezza(cifre, 0.62f), y + riga * 0.74f, spenta, 0.62f)
            } else {
                testo(p.nome, tx, y + riga * 0.86f, pergamena, 0.78f)
                testo(cifre, d.x1 - larghezza(cifre, 0.66f), y + riga * 0.86f, spenta, 0.66f)
                val barraVita = Riq(tx, y + riga * 0.3f, d.x1 - tx, 5f * s)
                rett(barraVita, incisione)
                rett(Riq(barraVita.x, barraVita.y, barraVita.w * quota, barraVita.h), colore)
            }
        }
    }

    private fun diario(r: Riq, stato: StatoHud) {
        cornice(r, "DIARIO", "finto")
        val d = dentro(r)
        var y = d.y1
        // in cima quello che succede davvero, sotto il finto
        if (stato.messaggio.isNotEmpty()) {
            testo(stato.messaggio, d.x, y, ambra, 0.74f)
            y -= font.lineHeight * 0.74f * 1.15f
        }
        for (riga in Finti.diario) {
            if (y < d.y) break
            testo(riga, d.x, y, spenta, 0.74f)
            y -= font.lineHeight * 0.74f * 1.15f
        }
    }

    private fun comandi(r: Riq, stato: StatoHud) {
        cornice(r, "COMANDI")
        val d = dentro(r)
        // La bussola prima di tutto: con le svolte di novanta gradi sapere
        // da che parte si guarda conta piu' di qualunque altra cosa.
        val versi = listOf(Lato.NORD to "N", Lato.EST to "E", Lato.SUD to "S", Lato.OVEST to "O")
        val altezzaBussola = font.lineHeight * 0.9f
        var bx = d.x
        for ((l, sigla) in versi) {
            val acceso = l == stato.verso
            testo(sigla, bx, d.y1, if (acceso) ambra else incisione, if (acceso) 1.0f else 0.8f)
            bx += larghezza("N", 1.0f) * 1.9f
        }

        val griglia = Riq(d.x, d.y, d.w, d.h - altezzaBussola * 1.4f)
        val tasti = listOf(
            Triple("VOLTA", "<", 0), Triple("AVANTI", "^", 1), Triple("VOLTA", ">", 2),
            Triple("PASSO", "[", 3), Triple("INDIETRO", "v", 4), Triple("PASSO", "]", 5)
        )
        val lw = griglia.w / 3f
        val lh = griglia.h / 3f
        for ((etichetta, segno, i) in tasti) {
            val cx = griglia.x + (i % 3) * lw
            val cy = griglia.y1 - lh * (i / 3 + 1)
            val b = Riq(cx + 2f * s, cy + 2f * s, lw - 4f * s, lh - 4f * s)
            rett(b, fondo); bordo(b)
            testo(segno, b.cx - larghezza(segno, 0.95f) / 2f, b.cy + font.lineHeight * 0.4f, pergamena, 0.95f)
            testo(etichetta, b.cx - larghezza(etichetta, 0.6f) / 2f, b.y + lh * 0.26f, spenta, 0.6f)
        }
        // la riga in fondo: le due azioni che non stanno nella crociera
        val fondoRiga = griglia.y1 - lh * 3f - 1f * s
        val azioni = "SPAZIO apri     M mappa     R nuovo     F1 nascondi"
        testo(azioni, d.x, maxOf(d.y + font.lineHeight * 0.62f, fondoRiga), spenta, 0.62f)
    }

    // ---------- la mappa ----------

    /**
     * La mappa si scopre camminando: si disegnano solo le caselle che il
     * gruppo ha visto. Le stanze e i corridoi hanno tinte diverse perche'
     * nel gioco valgono cose diverse, e la differenza deve saltare
     * all'occhio anche sulla mappa.
     */
    private fun mappa(r: Riq, stato: StatoHud, grande: Boolean) {
        val titolo = if (grande) "MAPPA DEL SOTTERRANEO" else "MAPPA"
        val e = stato.esplorazione
        cornice(r, titolo, "${e.percento}%  esplorato")
        val d = dentro(r)
        rett(d, Color(0.03f, 0.033f, 0.036f, 1f))

        val viste = e.celleViste()
        if (viste.isEmpty()) return

        val x0 = viste.minOf { it.x }; val x1 = viste.maxOf { it.x }
        val z0 = viste.minOf { it.z }; val z1 = viste.maxOf { it.z }
        val colonne = (x1 - x0 + 1).coerceAtLeast(1)
        val righe = (z1 - z0 + 1).coerceAtLeast(1)

        // La casella non si rimpicciolisce all'infinito, se no non si
        // capisce piu' niente: sotto la misura minima la mappa scorre
        // dietro al gruppo invece di stringersi. E nemmeno si ingrandisce
        // all'infinito: due caselle scoperte non devono diventare due
        // francobolli giganti in mezzo al pannello.
        val minima = if (grande) 7f * s else 5f * s
        val massima = if (grande) 40f * s else 22f * s
        val lato = minOf(massima, maxOf(minima, minOf(d.w / colonne, d.h / righe)))
        val larghi = colonne * lato <= d.w
        val alti = righe * lato <= d.h
        val ox = if (larghi) d.cx - colonne * lato / 2f - x0 * lato
                 else d.cx - (stato.x + 0.5f) * lato
        val oz = if (alti) d.cy + righe * lato / 2f + z0 * lato
                 else d.cy + (stato.z + 0.5f) * lato

        fun sx(x: Int) = ox + x * lato
        fun sy(z: Int) = oz - (z + 1) * lato

        forbici(d)
        for (c in viste) {
            val p = stato.dungeon.pezzoIn(c.x, c.z)
            val colore = when (p?.modulo?.famiglia) {
                Famiglia.CORRIDOIO -> andito
                else -> sala
            }
            val cella = Riq(sx(c.x), sy(c.z), lato - 1f, lato - 1f)
            rett(cella, if (e.calpestata(c)) colore else Color(colore.r, colore.g, colore.b, 0.45f))
        }
        // le porte: chiuse in ruggine, aperte in verde, e solo dove il
        // gruppo e' gia' passato a vederle
        for (porta in stato.dungeon.passaggi.filter { it.conBattente }) {
            if (porta.a !in viste && porta.b !in viste) continue
            val mx = (sx(porta.a.x) + sx(porta.b.x)) / 2f
            val my = (sy(porta.a.z) + sy(porta.b.z)) / 2f
            val spesso = maxOf(2f, lato * 0.22f)
            val orizzontale = porta.a.z == porta.b.z
            rett(
                if (orizzontale) Riq(mx + lato / 2f - spesso / 2f, my + lato * 0.2f, spesso, lato * 0.6f)
                else Riq(mx + lato * 0.2f, my + lato / 2f - spesso / 2f, lato * 0.6f, spesso),
                if (porta.aperta) verde else ruggine
            )
        }
        freccia(sx(stato.x) + lato / 2f, sy(stato.z) + lato / 2f, lato * 0.34f, stato.verso)
        niForbici()
    }

    /** Il gruppo sulla mappa: un triangolo che guarda dove guarda lui. */
    private fun freccia(cx: Float, cy: Float, raggio: Float, verso: Lato) {
        val (dx, dy) = when (verso) {
            Lato.NORD -> 0f to 1f
            Lato.SUD -> 0f to -1f
            Lato.EST -> 1f to 0f
            Lato.OVEST -> -1f to 0f
        }
        val sp = maxOf(3f, raggio * 1.1f)
        rett(Riq(cx - sp / 2f, cy - sp / 2f, sp, sp), ambra)
        // la punta, che dice da che parte si guarda
        val punta = maxOf(2f, raggio * 0.55f)
        rett(Riq(cx + dx * raggio * 1.15f - punta / 2f, cy + dy * raggio * 1.15f - punta / 2f,
            punta, punta), ambra)
    }

    // ---------- pennelli ----------

    private fun barra() = font.lineHeight * 0.78f + 4f * s
    private fun pad() = 6f * s

    private fun rett(r: Riq, c: Color) {
        batch.color = c
        batch.draw(tinta, r.x, r.y, r.w, r.h)
        batch.color = Color.WHITE
    }

    private fun bordo(r: Riq) {
        batch.color = rilievo
        batch.draw(tinta, r.x, r.y1 - 1f, r.w, 1f)
        batch.draw(tinta, r.x, r.y, r.w, 1f)
        batch.draw(tinta, r.x, r.y, 1f, r.h)
        batch.draw(tinta, r.x1 - 1f, r.y, 1f, r.h)
        batch.color = Color.WHITE
    }

    private fun testo(t: String, x: Float, y: Float, c: Color, scala: Float = 1f) {
        font.data.setScale(s * scala)
        font.color = c
        font.draw(batch, t, x, y)
        font.data.setScale(s)
    }

    private fun larghezza(t: String, scala: Float = 1f): Float {
        font.data.setScale(s * scala)
        misura.setText(font, t)
        font.data.setScale(s)
        return misura.width
    }

    /** Ritaglia il disegno dentro un riquadro: la mappa non deve sbordare. */
    private fun forbici(r: Riq) {
        batch.flush()
        Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_SCISSOR_TEST)
        val sx = Gdx.graphics.backBufferWidth.toFloat() / Gdx.graphics.width
        val sy = Gdx.graphics.backBufferHeight.toFloat() / Gdx.graphics.height
        Gdx.gl.glScissor((r.x * sx).toInt(), (r.y * sy).toInt(), (r.w * sx).toInt(), (r.h * sy).toInt())
    }

    private fun niForbici() {
        batch.flush()
        Gdx.gl.glDisable(com.badlogic.gdx.graphics.GL20.GL_SCISSOR_TEST)
    }

    override fun dispose() {
        batch.dispose()
        font.dispose()
        tinta.dispose()
    }
}
