package dev.michelelops.arcaniaquest.regole

import kotlin.math.roundToInt

/**
 * Quanto del sotterraneo il gruppo ha gia' calpestato.
 *
 * Si conta a caselle e non a stanze, perche' una sala grande vista per
 * meta' non e' una sala vista. La chiave porta anche il modulo, cosi'
 * quando i pezzi saranno piu' d'uno il conto continuera' a tornare senza
 * cambiare niente qui.
 */
class Esplorazione(caselleInTutto: Int = 0) {

    private val viste = LinkedHashSet<String>()
    private var totale = caselleInTutto

    /** Quante caselle esistono in tutto. Cresce quando entra un modulo nuovo. */
    val inTutto: Int get() = totale

    val quante: Int get() = viste.size

    val frazione: Float get() = if (totale <= 0) 0f else quante.toFloat() / totale

    val percento: Int get() = (frazione * 100f).roundToInt()

    /** Ritorna true se la casella non era mai stata calpestata prima. */
    fun visita(modulo: String, x: Int, z: Int): Boolean = viste.add("$modulo:$x,$z")

    fun conosce(modulo: String, x: Int, z: Int): Boolean = viste.contains("$modulo:$x,$z")

    /** Aggiunge al totale le caselle di un modulo appena entrato in gioco. */
    fun aggiungiModulo(m: Modulo) {
        totale += m.celle().size
    }

    override fun toString(): String = "$quante / $inTutto ($percento%)"
}
