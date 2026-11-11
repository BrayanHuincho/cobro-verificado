from __future__ import annotations

from datetime import UTC, datetime, timedelta
from decimal import Decimal
from typing import Annotated, Any
from uuid import UUID

from fastapi import APIRouter, Depends, File, Form, Header, Query, Response, UploadFile, status

from cobroverificado.adaptadores.http.dependencias import (
    ComercioActual,
    ComercioDispositivo,
    Estado,
    limitar_por_ip,
)
from cobroverificado.adaptadores.http.errores import ApiError, RespuestaError
from cobroverificado.adaptadores.http.esquemas import (
    CodigoSalida,
    ListaPagos,
    PagoEntrada,
    PagoSalida,
    RevisionEntrada,
    Salud,
    SecretoSalida,
    VerificacionSalida,
    VincularEntrada,
    VincularSalida,
    WebhookEntrada,
    WebhookSalida,
)
from cobroverificado.adaptadores.http.seguridad import tipo_imagen
from cobroverificado.adaptadores.http.servicios import ConflictoIdempotenciaError, NuevoPago
from cobroverificado.dominio.modelos import SolicitudVerificacion

RANGO_MAXIMO = timedelta(days=31)
_BLOQUE = 64 * 1024


def _errores(*codigos: int) -> dict[int | str, dict[str, Any]]:
    return {c: {"model": RespuestaError} for c in codigos}


salud = APIRouter(tags=["salud"])
v1 = APIRouter(prefix="/v1")


@salud.get("/salud", response_model=Salud, responses=_errores(503))
def obtener_salud(estado: Estado, response: Response) -> Salud:
    if estado.servicios.salud.disponible():
        return Salud(estado="ok")
    response.status_code = status.HTTP_503_SERVICE_UNAVAILABLE
    return Salud(estado="degradado")


async def _leer_imagen(imagen: UploadFile, maximo: int) -> bytes:
    partes: list[bytes] = []
    total = 0
    while bloque := await imagen.read(_BLOQUE):
        total += len(bloque)
        if total > maximo:
            raise ApiError(
                413,
                "imagen_demasiado_grande",
                "La imagen supera el tamaño permitido",
                {"maximo_bytes": maximo},
            )
        partes.append(bloque)
    contenido = b"".join(partes)
    if not contenido:
        raise ApiError(422, "imagen_vacia", "La imagen está vacía")
    if tipo_imagen(contenido) is None:
        raise ApiError(
            415,
            "tipo_no_soportado",
            "La imagen debe ser PNG, JPEG o WEBP",
            {"permitidos": ["image/png", "image/jpeg", "image/webp"]},
        )
    return contenido


@v1.post(
    "/verificaciones",
    tags=["verificaciones"],
    status_code=status.HTTP_202_ACCEPTED,
    response_model=VerificacionSalida,
    responses=_errores(401, 409, 413, 415, 422, 429),
    summary="Enviar una captura para verificar",
    description="Acepta la captura y la procesa en segundo plano. Reenviar con la misma "
    "`Idempotency-Key` devuelve la verificación ya creada, en su estado actual.",
)
async def crear_verificacion(
    estado: Estado,
    comercio: ComercioActual,
    imagen: Annotated[UploadFile, File(description="Captura PNG, JPEG o WEBP de hasta 8 MB")],
    monto_esperado: Annotated[
        Decimal | None, Form(gt=0, max_digits=12, decimal_places=2, examples=["45.50"])
    ] = None,
    referencia: Annotated[str | None, Form(max_length=120, examples=["pedido-1024"])] = None,
    clave_idempotencia: Annotated[
        str | None, Header(alias="Idempotency-Key", min_length=8, max_length=128)
    ] = None,
) -> Response:
    contenido = await _leer_imagen(imagen, estado.config.tamano_maximo_imagen)
    solicitud = SolicitudVerificacion(comercio.id, monto_esperado, referencia)
    try:
        solicitada = estado.servicios.verificaciones.solicitar(
            solicitud, contenido, clave_idempotencia
        )
    except ConflictoIdempotenciaError as exc:
        raise ApiError(
            409, "idempotencia_conflicto", "La Idempotency-Key ya se usó con otra solicitud"
        ) from exc
    verificacion = solicitada.verificacion
    cabeceras = {"Location": f"/v1/verificaciones/{verificacion.id}"}
    if solicitada.repetida:
        cabeceras["Idempotent-Replayed"] = "true"
    return Response(
        VerificacionSalida.desde(verificacion).model_dump_json(),
        status_code=status.HTTP_202_ACCEPTED,
        media_type="application/json",
        headers=cabeceras,
    )


@v1.get(
    "/verificaciones/{verificacion_id}",
    tags=["verificaciones"],
    response_model=VerificacionSalida,
    responses=_errores(401, 404, 429),
)
def obtener_verificacion(
    estado: Estado, comercio: ComercioActual, verificacion_id: UUID
) -> VerificacionSalida:
    verificacion = estado.servicios.verificaciones.obtener(comercio.id, verificacion_id)
    if verificacion is None:
        raise ApiError(404, "no_encontrado", "Verificación no encontrada")
    return VerificacionSalida.desde(verificacion)


@v1.post(
    "/verificaciones/{verificacion_id}/revision",
    tags=["verificaciones"],
    response_model=VerificacionSalida,
    responses=_errores(401, 404, 409, 422, 429),
    summary="Registrar la revisión humana",
)
def revisar_verificacion(
    estado: Estado, comercio: ComercioActual, verificacion_id: UUID, entrada: RevisionEntrada
) -> VerificacionSalida:
    try:
        verificacion = estado.servicios.verificaciones.revisar(
            comercio.id, verificacion_id, entrada.decision == "aprobar", entrada.nota
        )
    except ValueError as exc:
        raise ApiError(409, "revision_no_permitida", str(exc)) from exc
    if verificacion is None:
        raise ApiError(404, "no_encontrado", "Verificación no encontrada")
    return VerificacionSalida.desde(verificacion)


@v1.post(
    "/dispositivos/codigos",
    tags=["dispositivos"],
    status_code=status.HTTP_201_CREATED,
    response_model=CodigoSalida,
    responses=_errores(401, 429),
    summary="Generar un código para vincular la app",
)
def generar_codigo(estado: Estado, comercio: ComercioActual) -> CodigoSalida:
    codigo = estado.servicios.dispositivos.generar_codigo(comercio.id)
    return CodigoSalida(codigo=codigo.codigo, expira_en=codigo.expira_en)


@v1.post(
    "/dispositivos/vincular",
    tags=["dispositivos"],
    response_model=VincularSalida,
    responses=_errores(400, 422, 429),
    dependencies=[Depends(limitar_por_ip)],
    summary="Canjear un código por el token del dispositivo",
)
def vincular_dispositivo(estado: Estado, entrada: VincularEntrada) -> VincularSalida:
    token = estado.servicios.dispositivos.vincular(entrada.codigo)
    if token is None:
        raise ApiError(400, "codigo_invalido", "Código inválido o expirado")
    return VincularSalida(token=token)


@v1.post(
    "/pagos-recibidos",
    tags=["pagos"],
    status_code=status.HTTP_201_CREATED,
    response_model=PagoSalida,
    responses=_errores(401, 422, 429),
    summary="Registrar un pago leído de una notificación",
    description="Idempotente por `huella`: repetir la notificación devuelve el pago ya registrado.",
)
def registrar_pago(
    estado: Estado, comercio_id: ComercioDispositivo, entrada: PagoEntrada
) -> PagoSalida:
    pago = estado.servicios.pagos.registrar(
        NuevoPago(
            comercio_id=comercio_id,
            billetera=entrada.billetera,
            monto=entrada.monto,
            fecha_hora=entrada.fecha_hora,
            remitente=entrada.remitente,
            numero_operacion=entrada.numero_operacion,
            huella=entrada.huella,
        )
    )
    return PagoSalida.desde(pago)


@v1.get(
    "/pagos-recibidos",
    tags=["pagos"],
    response_model=ListaPagos,
    responses=_errores(401, 422, 429),
    description="Sin parámetros devuelve las últimas 24 horas. Rango máximo de 31 días.",
)
def listar_pagos(
    estado: Estado,
    comercio: ComercioActual,
    desde: Annotated[datetime | None, Query(examples=["2026-11-09T00:00:00-05:00"])] = None,
    hasta: Annotated[datetime | None, Query(examples=["2026-11-09T23:59:59-05:00"])] = None,
) -> ListaPagos:
    for nombre, valor in (("desde", desde), ("hasta", hasta)):
        if valor is not None and valor.tzinfo is None:
            raise ApiError(
                422, "validacion", "Las fechas deben incluir zona horaria", {"campo": nombre}
            )
    fin = hasta or datetime.now(UTC)
    inicio = desde or fin - timedelta(hours=24)
    if inicio > fin or fin - inicio > RANGO_MAXIMO:
        raise ApiError(422, "rango_invalido", "Rango de fechas inválido", {"maximo_dias": 31})
    pagos = estado.servicios.pagos.listar(comercio.id, inicio, fin)
    return ListaPagos(pagos=[PagoSalida.desde(p) for p in pagos])


@v1.put(
    "/comercio/webhook",
    tags=["comercio"],
    response_model=WebhookSalida,
    responses=_errores(401, 422, 429),
    summary="Configurar la url del webhook",
    description="La primera vez genera el secreto de firma y lo devuelve una sola vez.",
)
def configurar_webhook(
    estado: Estado, comercio: ComercioActual, entrada: WebhookEntrada
) -> WebhookSalida:
    secreto = estado.servicios.webhook.configurar(comercio.id, entrada.url)
    return WebhookSalida(url=entrada.url, secreto=secreto)


@v1.post(
    "/comercio/webhook/rotar",
    tags=["comercio"],
    response_model=SecretoSalida,
    responses=_errores(401, 429),
    summary="Rotar el secreto de firma del webhook",
)
def rotar_secreto_webhook(estado: Estado, comercio: ComercioActual) -> SecretoSalida:
    return SecretoSalida(secreto=estado.servicios.webhook.rotar(comercio.id))
