import argparse
import importlib
import json
import sys
from collections.abc import Callable
from dataclasses import dataclass, field
from datetime import datetime
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Any
from zoneinfo import ZoneInfo

from cobroverificado.dominio.modelos import DatosExtraidos
from cobroverificado.puertos import Extractor

CAMPOS = (
    "billetera",
    "monto",
    "fecha_hora",
    "numero_operacion",
    "destinatario",
    "remitente",
    "celular_destino",
)
EXTENSIONES = (".png", ".jpg", ".jpeg", ".webp")
LIMA = ZoneInfo("America/Lima")


@dataclass
class Conteo:
    aciertos: int = 0
    total: int = 0
    fallos: list[str] = field(default_factory=list)


def normalizar(campo: str, valor: Any) -> str | None:
    if valor is None or valor == "":
        return None
    if campo == "monto":
        try:
            return str(Decimal(str(valor)).quantize(Decimal("0.01")))
        except InvalidOperation:
            return str(valor)
    if campo == "fecha_hora":
        fecha = valor if isinstance(valor, datetime) else datetime.fromisoformat(str(valor))
        if fecha.tzinfo is None:
            fecha = fecha.replace(tzinfo=LIMA)
        return fecha.astimezone(LIMA).strftime("%Y-%m-%dT%H:%M")
    texto = str(getattr(valor, "value", valor))
    return " ".join(texto.split()).casefold()


def cargar_extractor(ruta: str) -> Extractor:
    modulo, _, nombre = ruta.partition(":")
    fabrica: Callable[[], Extractor] = getattr(importlib.import_module(modulo), nombre)
    return fabrica()


def evaluar(carpeta: Path, extractor: Extractor) -> dict[str, Conteo]:
    conteos = {campo: Conteo() for campo in CAMPOS}
    imagenes = sorted(p for p in carpeta.iterdir() if p.suffix.lower() in EXTENSIONES)
    for imagen in imagenes:
        etiqueta = imagen.with_suffix(".json")
        if not etiqueta.exists():
            print(f"sin etiqueta: {imagen.name}", file=sys.stderr)
            continue
        esperado: dict[str, Any] = json.loads(etiqueta.read_text(encoding="utf-8"))
        datos: DatosExtraidos = extractor.extraer(imagen.read_bytes())
        for campo in CAMPOS:
            # solo se mide lo que la etiqueta declara, así una captura parcial sigue sirviendo
            if campo not in esperado:
                continue
            conteo = conteos[campo]
            conteo.total += 1
            obtenido = normalizar(campo, getattr(datos, campo))
            if obtenido == normalizar(campo, esperado[campo]):
                conteo.aciertos += 1
            else:
                conteo.fallos.append(f"{imagen.name}: {obtenido!r} != {esperado[campo]!r}")
    return conteos


def main() -> int:
    parser = argparse.ArgumentParser(description="precisión por campo del extractor")
    parser.add_argument("carpeta", type=Path, nargs="?", default=Path(__file__).parent / "capturas")
    parser.add_argument("--extractor", default="cobroverificado.principal:extractor_ocr")
    parser.add_argument("--detalle", action="store_true")
    args = parser.parse_args()

    conteos = evaluar(args.carpeta, cargar_extractor(args.extractor))
    for campo, conteo in conteos.items():
        if not conteo.total:
            continue
        precision = conteo.aciertos / conteo.total
        print(f"{campo:<18} {conteo.aciertos:>4}/{conteo.total:<4} {precision:6.1%}")
        if args.detalle:
            for fallo in conteo.fallos:
                print(f"    {fallo}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
