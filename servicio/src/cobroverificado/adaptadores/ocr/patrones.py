from __future__ import annotations

from dataclasses import dataclass

from cobroverificado.dominio.modelos import Billetera


@dataclass(frozen=True, slots=True)
class Patrones:
    marcas: tuple[tuple[Billetera, tuple[str, ...]], ...] = (
        (Billetera.YAPE, ("yapeaste", "yapear", "yape")),
        (Billetera.PLIN, ("plineaste", "plin")),
    )
    etiquetas_operacion: tuple[str, ...] = (
        "nro. de operacion",
        "nro de operacion",
        "n° de operacion",
        "numero de operacion",
        "codigo de operacion",
        "cod. de operacion",
        "operacion",
    )
    etiquetas_destinatario: tuple[str, ...] = (
        "destino",
        "destinatario",
        "enviado a",
        "para",
        "a nombre de",
    )
    etiquetas_remitente: tuple[str, ...] = ("origen", "enviado por", "remitente", "desde", "de")
    etiquetas_celular: tuple[str, ...] = ("nro. de celular", "celular", "telefono")
    etiquetas_fecha: tuple[str, ...] = ("fecha y hora", "fecha", "hora")
    simbolos_moneda: tuple[str, ...] = ("s/.", "s/", "s/ .", "s /")


PATRONES_POR_DEFECTO = Patrones()
