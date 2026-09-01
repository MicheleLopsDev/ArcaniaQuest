package dev.michelelops.arcaniaquest.desktop

/**
 * Gli argomenti della riga di comando, letti una volta sola.
 *
 * Due sole forme, e nessuna libreria di mezzo: `--nome=valore` per le
 * opzioni, `--nome` per le bandiere. Quello che non comincia per due
 * trattini e' un argomento libero — nel gioco e' l'id di un modulo.
 */
class Argomenti(private val args: Array<String>) {

    /** Il valore di `--nome=valore`, o null se l'opzione non c'e'. */
    fun opzione(nome: String): String? =
        args.firstOrNull { it.startsWith("--$nome=") }?.substringAfter('=')

    /**
     * Come [opzione], ma accetta anche `--nome` senza valore e in quel
     * caso restituisce [senzaValore]. Serve a `--scatto`, che si usa sia
     * da solo sia con un percorso.
     */
    fun opzione(nome: String, senzaValore: String): String? =
        args.firstOrNull { it == "--$nome" || it.startsWith("--$nome=") }
            ?.substringAfter("=", senzaValore)

    fun intero(nome: String, altrimenti: Int): Int =
        opzione(nome)?.toIntOrNull() ?: altrimenti

    /** Se c'e' `--nome`. */
    fun bandiera(nome: String): Boolean = args.any { it == "--$nome" }

    /** Il primo argomento che non e' un'opzione. */
    fun primoLibero(): String? = args.firstOrNull { !it.startsWith("--") }

    /** I valori di `--nome=a,b,c`, ripuliti e senza vuoti. */
    fun elenco(nome: String): List<String> =
        opzione(nome)?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
}
