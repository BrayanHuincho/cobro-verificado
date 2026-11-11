from __future__ import annotations

import ast
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent / "src" / "cobroverificado"
ADAPTADORES = RAIZ / "adaptadores"
RAIZ_COMPOSICION = {"composicion", "principal", "trabajador", "cli"}


def _importados(archivo: Path) -> set[str]:
    arbol = ast.parse(archivo.read_text(encoding="utf-8"))
    modulos: set[str] = set()
    for nodo in ast.walk(arbol):
        if isinstance(nodo, ast.Import):
            modulos.update(alias.name for alias in nodo.names)
        elif isinstance(nodo, ast.ImportFrom) and nodo.level == 0 and nodo.module:
            modulos.add(nodo.module)
        elif isinstance(nodo, ast.Call) and getattr(nodo.func, "attr", "") == "import_module":
            modulos.update(
                arg.value
                for arg in nodo.args
                if isinstance(arg, ast.Constant) and isinstance(arg.value, str)
            )
    return modulos


def test_ningun_adaptador_importa_otro_adaptador() -> None:
    violaciones = []
    for archivo in ADAPTADORES.rglob("*.py"):
        propio = archivo.relative_to(ADAPTADORES).parts[0]
        for modulo in _importados(archivo):
            partes = modulo.split(".")
            if partes[0] != "cobroverificado" or len(partes) < 2:
                continue
            ajeno = partes[1] == "adaptadores" and len(partes) > 2 and partes[2] != propio
            if ajeno or partes[1] in RAIZ_COMPOSICION:
                violaciones.append(f"{archivo.relative_to(RAIZ)} -> {modulo}")
    assert violaciones == []


def test_nucleo_no_importa_adaptadores() -> None:
    violaciones = [
        f"{archivo.relative_to(RAIZ)} -> {modulo}"
        for capa in ("dominio", "aplicacion", "puertos")
        for archivo in (RAIZ / capa).rglob("*.py")
        for modulo in _importados(archivo)
        if modulo.startswith("cobroverificado.adaptadores")
    ]
    assert violaciones == []
