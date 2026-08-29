"""Genera doc/mock/tavola-moduli.html infilando il catalogo nel modello.

La tavola e' una pagina sola e autosufficiente: il catalogo ci finisce
dentro, perche' aperta come artifact non puo' andare a pescarsi file.
Si rigenera ogni volta che catalogo.json cambia.
"""
import io, json, pathlib

radice = pathlib.Path(__file__).resolve().parent.parent
catalogo = radice / "content" / "moduli" / "catalogo.json"
modello  = radice / "strumenti" / "tavola-moduli.template.html"
uscita   = radice / "doc" / "mock" / "tavola-moduli.html"

dati = json.loads(io.open(catalogo, encoding="utf-8").read())
# </script> dentro i dati chiuderebbe il blocco prima del tempo
grezzo = json.dumps(dati, ensure_ascii=False, separators=(",", ":")).replace("</", r"<\/")

pagina = io.open(modello, encoding="utf-8").read().replace("__CATALOGO__", grezzo)
io.open(uscita, "w", encoding="utf-8").write(pagina)

n = sum(len(dati[k]) for k in ("iniziali", "corridoi", "stanze"))
print("scritta %s — %d moduli, %.0f KB" % (uscita.relative_to(radice), n, len(pagina)/1024))
