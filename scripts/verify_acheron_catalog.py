"""Comprueba que la copia del catálogo de Acheron sigue al original.

El catálogo de tipos de la bóveda vive en ``schema/schema.json`` del
repositorio ``AcheronCore``. Esta app no puede leerlo de ahí durante los tests,
así que lleva una copia en ``app/src/test/resources/acheron-schema.json``.

Y ahí estaba el agujero. ``StorableSchemaContractTest`` compara
``StorableSchema.kt`` **contra la copia**, pero nadie comprobaba que la copia
siguiera al original: una copia congelada y un código congelado coinciden
perfectamente entre sí, así que el test da verde mientras la copia envejece.

Esta copia llegó a ir **dos marcas por detrás** —``matchKey`` e
``identityKey``—, la primera durante semanas, con la suite en verde todo el
tiempo. Que esas marcas le sean inertes a esta app es lo que permitió que
durara: una divergencia que no rompe nada no se descubre, se acumula. La
siguiente podría no ser inerte, porque un campo renombrado sí viaja al JSON de
la bóveda.

**Qué versión se compara.** La del motor que fija ``app/build.gradle.kts``, y no
un fichero aparte. Se puede porque el motor y el catálogo salen del MISMO tag de
``AcheronCore``: ``schema/schema.json`` en ``v2.4.0`` es el catálogo que hace
pareja con ``acheron-core`` 2.4.0, por construcción.

Nació declarándose a mano, cuando la app iba por el motor 1.0.0 y el catálogo
por el 2.4.0. Al alinear las dos, el fichero pasó a ser una segunda fuente para
lo mismo —es decir, algo que puede quedarse atrás—, así que se quitó.

    python3 scripts/verify_acheron_catalog.py
"""

from __future__ import annotations

import json
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[1]
RECURSOS = RAIZ / "app" / "src" / "test" / "resources"
COPIA = RECURSOS / "acheron-schema.json"
GRADLE = RAIZ / "app" / "build.gradle.kts"
MOTOR = re.compile(r'com\.ellysia:acheron-core:([0-9]+\.[0-9]+\.[0-9]+)')
ORIGEN = "https://raw.githubusercontent.com/ProjectEllysia/AcheronCore/{tag}/schema/schema.json"


def tag_declarado() -> str:
    """El tag de AcheronCore que sigue esta app, leido de su dependencia Gradle."""
    encontrado = MOTOR.search(GRADLE.read_text(encoding="utf-8"))
    if not encontrado:
        raise SystemExit(
            f"{GRADLE.relative_to(RAIZ).as_posix()} no fija com.ellysia:acheron-core a una "
            "version exacta. El catalogo se compara contra un tag, y un rango no senala ninguno."
        )
    return f"v{encontrado.group(1)}"


def catalogo_original(tag: str) -> dict:
    url = ORIGEN.format(tag=tag)
    try:
        with urllib.request.urlopen(url, timeout=30) as respuesta:
            return json.loads(respuesta.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        if error.code == 404:
            raise SystemExit(f"No existe el tag {tag} en AcheronCore.\n  {url}") from error
        raise


def diferencias(copia: dict, original: dict) -> list[str]:
    """Resume en que se separan, tipo a tipo, en vez de volcar dos JSON enteros."""
    lineas: list[str] = []

    if copia.get("schemaVersion") != original.get("schemaVersion"):
        lineas.append(
            f"schemaVersion: copia={copia.get('schemaVersion')} "
            f"original={original.get('schemaVersion')}"
        )

    en_copia = {t.get("kind"): t for t in copia.get("types", [])}
    en_original = {t.get("kind"): t for t in original.get("types", [])}

    for kind in sorted(set(en_original) - set(en_copia)):
        lineas.append(f"falta el tipo '{kind}', que el original si trae")
    for kind in sorted(set(en_copia) - set(en_original)):
        lineas.append(f"sobra el tipo '{kind}', que el original ya no trae")
    for kind in sorted(set(en_copia) & set(en_original)):
        if en_copia[kind] != en_original[kind]:
            lineas.append(f"el tipo '{kind}' difiere:")
            lineas.append(f"    copia:    {json.dumps(en_copia[kind], sort_keys=True)}")
            lineas.append(f"    original: {json.dumps(en_original[kind], sort_keys=True)}")

    return lineas


def main() -> int:
    tag = tag_declarado()
    original = catalogo_original(tag)
    copia = json.loads(COPIA.read_text(encoding="utf-8"))

    if copia == original:
        tipos = len(original.get("types", []))
        print(f"OK: la copia coincide con AcheronCore {tag} ({tipos} tipos).")
        return 0

    # Quien lea esto estara mirando un job rojo en un repositorio cuyo catalogo
    # no ha tocado nadie, asi que el mensaje tiene que decir QUE hacer.
    print(f"La copia del catalogo NO coincide con AcheronCore {tag}.\n", file=sys.stderr)
    for linea in diferencias(copia, original):
        print(f"  {linea}", file=sys.stderr)
    print(
        f"\nSi el catalogo cambio y esta app debe seguirlo:\n"
        f"  curl -sL {ORIGEN.format(tag=tag)} -o {COPIA.relative_to(RAIZ).as_posix()}\n"
        f"  ./gradlew :app:testDebugUnitTest\n\n"
        f"Si esta app debe quedarse atras, baja la version de com.ellysia:acheron-core en "
        f"{GRADLE.relative_to(RAIZ).as_posix()} — pero eso desempareja los motores, "
        f"y es una decision, no un arreglo.",
        file=sys.stderr,
    )
    return 1


if __name__ == "__main__":
    sys.exit(main())
