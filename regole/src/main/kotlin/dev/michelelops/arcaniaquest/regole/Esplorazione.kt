package dev.michelelops.arcaniaquest.regole

import kotlin.math.roundToInt

/**
 * Quanto del sotterraneo il gruppo conosce.
 *
 * Sono due cose diverse e servono a due cose diverse. Le caselle
 * **calpestate** dicono quanto e' stato esplorato davvero, e si contano
 * per la percentuale: una sala grande vista per meta' non e' una sala
 * vista. Le caselle **viste** sono quelle che finiscono sulla mappa —
 * anche quelle intraviste in fondo a un corridoio, che sulla mappa ci
 * vanno perche' il gruppo le ha guardate, ma non contano come esplorate
 * perche' non ci ha ancora messo piede.
 *
 * Le caselle sono in coordinate di mondo, quindi gia' uniche: non serve
 * portarsi dietro anche il modulo.
 */
class Esplorazione(caselleInTutto: Int = 0) {

    private val calpestate = LinkedHashSet<Cella>()
    private val viste = LinkedHashSet<Cella>()
    private var totale = caselleInTutto

    val inTutto: Int get() = totale
    val quante: Int get() = calpestate.size
    val quanteViste: Int get() = viste.size

    val frazione: Float get() = if (totale <= 0) 0f else quante.toFloat() / totale
    val percento: Int get() = (frazione * 100f).roundToInt()

    /** Ritorna true se la casella non era mai stata calpestata prima. */
    fun calpesta(c: Cella): Boolean {
        viste.add(c)
        return calpestate.add(c)
    }

    fun vedi(c: Cella): Boolean = viste.add(c)

    fun calpestata(c: Cella): Boolean = c in calpestate
    fun conosciuta(c: Cella): Boolean = c in viste

    /** Le caselle da disegnare sulla mappa. */
    fun celleViste(): Set<Cella> = viste

    /** Aggiunge al totale le caselle di un modulo appena entrato in gioco. */
    fun aggiungiModulo(m: Modulo) {
        totale += m.celle().size
    }

    override fun toString(): String = "$quante / $inTutto ($percento%)"
}
