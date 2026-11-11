from __future__ import annotations

from datetime import datetime
from decimal import Decimal
from typing import Any
from uuid import UUID

from cobroverificado.dominio.modelos import (
    Billetera,
    DatosExtraidos,
    Estado,
    Fuente,
    Senal,
    Severidad,
    Veredicto,
)


def _texto(valor: Any) -> str | None:
    return None if valor is None else str(valor)


def datos_a_json(datos: DatosExtraidos) -> dict[str, Any]:
    return {
        "billetera": _texto(datos.billetera),
        "monto": _texto(datos.monto),
        "fecha_hora": None if datos.fecha_hora is None else datos.fecha_hora.isoformat(),
        "numero_operacion": datos.numero_operacion,
        "destinatario": datos.destinatario,
        "remitente": datos.remitente,
        "celular_destino": datos.celular_destino,
        "confianza": datos.confianza,
        "fuente": str(datos.fuente),
    }


def datos_desde_json(crudo: dict[str, Any]) -> DatosExtraidos:
    return DatosExtraidos(
        billetera=None if crudo["billetera"] is None else Billetera(crudo["billetera"]),
        monto=None if crudo["monto"] is None else Decimal(crudo["monto"]),
        fecha_hora=None
        if crudo["fecha_hora"] is None
        else datetime.fromisoformat(crudo["fecha_hora"]),
        numero_operacion=crudo["numero_operacion"],
        destinatario=crudo["destinatario"],
        remitente=crudo["remitente"],
        celular_destino=crudo["celular_destino"],
        confianza=float(crudo["confianza"]),
        fuente=Fuente(crudo["fuente"]),
    )


def senal_a_json(senal: Senal) -> dict[str, str]:
    return {"codigo": senal.codigo, "severidad": str(senal.severidad), "mensaje": senal.mensaje}


def senal_desde_json(crudo: dict[str, Any]) -> Senal:
    return Senal(crudo["codigo"], Severidad(crudo["severidad"]), crudo["mensaje"])


def veredicto_a_json(veredicto: Veredicto) -> dict[str, Any]:
    return {
        "estado": str(veredicto.estado),
        "senales": [senal_a_json(s) for s in veredicto.senales],
        "pago_id": _texto(veredicto.pago_id),
        "requiere_revision": veredicto.requiere_revision,
    }


def veredicto_desde_json(crudo: dict[str, Any]) -> Veredicto:
    return Veredicto(
        estado=Estado(crudo["estado"]),
        senales=tuple(senal_desde_json(s) for s in crudo["senales"]),
        pago_id=None if crudo["pago_id"] is None else UUID(crudo["pago_id"]),
        requiere_revision=bool(crudo["requiere_revision"]),
    )
