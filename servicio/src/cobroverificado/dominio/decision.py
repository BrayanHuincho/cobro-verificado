from __future__ import annotations

from collections.abc import Iterable, Sequence
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

from cobroverificado.dominio.coincidencias import coincide_celular, coincide_nombre
from cobroverificado.dominio.modelos import (
    Comercio,
    DatosExtraidos,
    Estado,
    PagoRecibido,
    Senal,
    Severidad,
    SolicitudVerificacion,
    Veredicto,
)

ZONA_LIMA = ZoneInfo("America/Lima")
# las capturas muestran la hora al minuto y los relojes no van sincronizados
MARGEN_FUTURO = timedelta(minutes=2)


def en_lima(momento: datetime) -> datetime:
    if momento.tzinfo is None:
        return momento.replace(tzinfo=ZONA_LIMA)
    return momento


def _senal(codigo: str, severidad: Severidad, mensaje: str) -> Senal:
    return Senal(codigo=codigo, severidad=severidad, mensaje=mensaje)


def _senales_de_rechazo(
    datos: DatosExtraidos, solicitud: SolicitudVerificacion, operacion_libre: bool
) -> list[Senal]:
    senales: list[Senal] = []
    if not operacion_libre:
        senales.append(
            _senal(
                "operacion_repetida",
                Severidad.CRITICA,
                f"El número de operación {datos.numero_operacion} ya se usó en otra verificación.",
            )
        )
    if solicitud.monto_esperado is not None and datos.monto != solicitud.monto_esperado:
        senales.append(
            _senal(
                "monto_distinto",
                Severidad.CRITICA,
                f"La captura muestra S/ {datos.monto} y se esperaba S/ {solicitud.monto_esperado}.",
            )
        )
    return senales


def _senales_de_destino(datos: DatosExtraidos, comercio: Comercio) -> list[Senal]:
    senales: list[Senal] = []
    if datos.destinatario and comercio.destinatarios:
        if not any(coincide_nombre(datos.destinatario, d) for d in comercio.destinatarios):
            senales.append(
                _senal(
                    "destinatario_ajeno",
                    Severidad.CRITICA,
                    f"El destinatario «{datos.destinatario}» no corresponde al comercio.",
                )
            )
    elif comercio.destinatarios:
        senales.append(
            _senal(
                "destinatario_no_legible",
                Severidad.ADVERTENCIA,
                "No se pudo leer el destinatario en la captura.",
            )
        )
    celular = datos.celular_destino
    if (
        celular
        and comercio.celulares
        and not any(coincide_celular(celular, c) for c in comercio.celulares)
    ):
        senales.append(
            _senal(
                "celular_ajeno",
                Severidad.CRITICA,
                f"El celular de destino {celular} no es del comercio.",
            )
        )
    return senales


def _senales_de_tiempo(fecha_hora: datetime, comercio: Comercio, ahora: datetime) -> list[Senal]:
    if fecha_hora > ahora + MARGEN_FUTURO:
        return [
            _senal(
                "fecha_futura",
                Severidad.CRITICA,
                "La fecha de la captura es posterior a la hora actual.",
            )
        ]
    if ahora - fecha_hora > timedelta(minutes=comercio.ventana_minutos):
        return [
            _senal(
                "fecha_antigua",
                Severidad.CRITICA,
                f"La captura tiene más de {comercio.ventana_minutos} minutos de antigüedad.",
            )
        ]
    return []


def pagos_coincidentes(
    datos: DatosExtraidos, candidatos: Iterable[PagoRecibido], comercio: Comercio
) -> list[PagoRecibido]:
    if datos.monto is None or datos.fecha_hora is None:
        return []
    fecha = en_lima(datos.fecha_hora)
    tolerancia = timedelta(minutes=comercio.tolerancia_minutos)
    coincidentes: list[PagoRecibido] = []
    for pago in candidatos:
        if pago.comercio_id != comercio.id or pago.monto != datos.monto:
            continue
        if datos.billetera is not None and pago.billetera != datos.billetera:
            continue
        if abs(en_lima(pago.fecha_hora) - fecha) > tolerancia:
            continue
        if (
            datos.numero_operacion
            and pago.numero_operacion
            and datos.numero_operacion.strip() != pago.numero_operacion.strip()
        ):
            continue
        coincidentes.append(pago)
    return coincidentes


def decidir(
    datos: DatosExtraidos,
    solicitud: SolicitudVerificacion,
    candidatos: Sequence[PagoRecibido],
    senales: Sequence[Senal],
    comercio: Comercio,
    operacion_libre: bool,
    *,
    ahora: datetime,
) -> Veredicto:
    previas = tuple(senales)
    if datos.monto is None or datos.fecha_hora is None:
        ilegible = _senal(
            "imagen_ilegible",
            Severidad.CRITICA,
            "No se pudo leer el monto o la fecha de la captura.",
        )
        return Veredicto(estado=Estado.RECHAZADO, senales=(*previas, ilegible))

    rechazo = _senales_de_rechazo(datos, solicitud, operacion_libre)
    alertas = _senales_de_destino(datos, comercio) + _senales_de_tiempo(
        en_lima(datos.fecha_hora), comercio, en_lima(ahora)
    )
    if not datos.numero_operacion:
        alertas.append(
            _senal(
                "sin_numero_operacion",
                Severidad.ADVERTENCIA,
                "La captura no muestra número de operación.",
            )
        )
    acumuladas = (*previas, *rechazo, *alertas)

    if rechazo:
        return Veredicto(estado=Estado.RECHAZADO, senales=acumuladas)
    if any(s.severidad is Severidad.CRITICA for s in acumuladas):
        return Veredicto(estado=Estado.SOSPECHOSO, senales=acumuladas)

    coincidentes = pagos_coincidentes(datos, candidatos, comercio)
    if not coincidentes:
        sin_pago = _senal(
            "sin_pago_recibido",
            Severidad.INFO,
            "Aún no hay un pago recibido que respalde la captura.",
        )
        return Veredicto(estado=Estado.PENDIENTE, senales=(*acumuladas, sin_pago))
    if len(coincidentes) > 1:
        ambiguo = _senal(
            "pago_ambiguo",
            Severidad.ADVERTENCIA,
            f"Hay {len(coincidentes)} pagos recibidos que podrían corresponder a la captura.",
        )
        return Veredicto(estado=Estado.PENDIENTE, senales=(*acumuladas, ambiguo))

    pago = coincidentes[0]
    umbral = comercio.umbral_revision_humana
    if umbral is not None and datos.monto >= umbral:
        revision = _senal(
            "requiere_revision",
            Severidad.INFO,
            f"El monto supera el umbral de revisión de S/ {umbral}.",
        )
        return Veredicto(
            estado=Estado.CONFIRMADO,
            senales=(*acumuladas, revision),
            pago_id=pago.id,
            requiere_revision=True,
        )
    return Veredicto(estado=Estado.CONFIRMADO, senales=acumuladas, pago_id=pago.id)
