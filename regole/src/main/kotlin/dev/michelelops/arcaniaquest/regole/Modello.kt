package dev.michelelops.arcaniaquest.regole

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * I quattro versi del mondo. Non ne esistono altri: il gruppo si gira
 * solo di 90 gradi, e un connettore si apre solo su uno di questi lati.
 */
@Serializable
enum class Lato {
    @SerialName("nord") NORD,
    @SerialName("est") EST,
    @SerialName("sud") SUD,
    @SerialName("ovest") OVEST;

    /** Spostamento sulla griglia: x cresce verso est, z verso sud. */
    val dx: Int get() = when (this) { EST -> 1; OVEST -> -1; else -> 0 }
    val dz: Int get() = when (this) { SUD -> 1; NORD -> -1; else -> 0 }

    /** Il lato che deve trovarsi di fronte perche' due moduli si incastrino. */
    val opposto: Lato get() = ruotato(2)

    /** Ruotato di [quarti] scatti di 90 gradi in senso orario. */
    fun ruotato(quarti: Int): Lato =
        entries[((ordinal + quarti) % 4 + 4) % 4]
}

@Serializable
enum class Famiglia {
    @SerialName("iniziale") INIZIALE,
    @SerialName("corridoio") CORRIDOIO,
    @SerialName("stanza") STANZA
}

@Serializable
enum class Incontri {
    @SerialName("nessuno") NESSUNO,
    @SerialName("vaganti") VAGANTI,
    @SerialName("fissi") FISSI
}

@Serializable
enum class Formazione {
    @SerialName("fila") FILA,
    @SerialName("libera") LIBERA
}

/**
 * Cosa comporta appartenere a una famiglia. E' qui, e non nella mesh,
 * perche' la differenza fra una stanza e un corridoio e' una differenza
 * di regole prima che di forma.
 */
@Serializable
data class RegoleFamiglia(
    val incontri: Incontri,
    val formazione: Formazione,
    val agguato: Boolean,
    val tesoro: Boolean,
    val arredi: List<String>,
    val luceTorceAMuro: Boolean,
    val riposo: Boolean
)

@Serializable
data class Ingombro(val w: Int, val d: Int)

@Serializable
data class Pesca(val tabella: String, val valore: Int)

/** Una casella della griglia del modulo. */
@Serializable
data class Cella(val x: Int, val z: Int)

/**
 * Il punto in cui un modulo si apre verso l'esterno: la casella interna
 * e il suo lato. Non sta per forza sul bordo del rettangolo d'ingombro —
 * in un pezzo a L cade in mezzo.
 */
@Serializable
data class Connettore(
    val lato: Lato,
    val cella: List<Int>,
    val porta: Boolean = false
) {
    val x: Int get() = cella[0]
    val z: Int get() = cella[1]
    fun a(x: Int, z: Int): Connettore = copy(cella = listOf(x, z))
}

/** Dove entra il gruppo, e in che verso guarda al primo fotogramma. */
@Serializable
data class Partenza(
    val cella: List<Int>,
    val verso: Lato = Lato.NORD
) {
    val x: Int get() = cella[0]
    val z: Int get() = cella[1]
}

/**
 * Una forma della pianta, da cui si genera la mesh. Facoltativa: se un
 * modulo non ce l'ha, la pianta si ricava dalle caselle. Si scrive a mano
 * solo dove la stanza non e' squadrata.
 */
@Serializable
data class Forma(
    val forma: String,
    val x: Double,
    val z: Double,
    val w: Double,
    val d: Double,
    val raggio: Double = 0.0
)

@Serializable
data class Arredo(val tipo: String, val cella: List<Int>) {
    val x: Int get() = cella[0]
    val z: Int get() = cella[1]
}
