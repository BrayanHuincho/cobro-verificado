from __future__ import annotations

import hashlib
import secrets
from collections.abc import Iterator
from contextlib import contextmanager
from datetime import datetime, timedelta
from decimal import Decimal
from typing import Any
from uuid import UUID, uuid4

from psycopg import Connection
from psycopg.rows import TupleRow
from psycopg.types.json import Jsonb
from psycopg_pool import ConnectionPool

from cobroverificado.dominio.modelos import (
    Billetera,
    Comercio,
    DatosExtraidos,
    Estado,
    OrigenPago,
    PagoRecibido,
    Veredicto,
    Verificacion,
)
from cobroverificado.puertos import SolicitudRegistrada

from .serializacion import (
    datos_a_json,
    datos_desde_json,
    veredicto_a_json,
    veredicto_desde_json,
)

Conexion = Connection[TupleRow]
Pool = ConnectionPool[Conexion]


@contextmanager
def usar_conexion(pool: Pool, conexion: Conexion | None) -> Iterator[Conexion]:
    if conexion is not None:
        yield conexion
        return
    with pool.connection() as nueva:
        yield nueva


def sha256(texto: str) -> str:
    return hashlib.sha256(texto.encode("utf-8")).hexdigest()


_COLUMNAS_PAGO = (
    "id, comercio_id, billetera, monto, fecha_hora, numero_operacion,"
    " remitente, huella, origen, recibido_en"
)


def _pago(fila: tuple[Any, ...]) -> PagoRecibido:
    return PagoRecibido(
        id=fila[0],
        comercio_id=fila[1],
        billetera=Billetera(fila[2]),
        monto=fila[3],
        fecha_hora=fila[4],
        numero_operacion=fila[5],
        remitente=fila[6],
        huella=fila[7].strip(),
        origen=OrigenPago(fila[8]),
        recibido_en=fila[9],
    )


class RepositorioPagosPg:
    def __init__(self, pool: Pool) -> None:
        self._pool = pool

    def registrar(self, pago: PagoRecibido) -> PagoRecibido:
        with self._pool.connection() as conexion:
            fila = conexion.execute(
                f"insert into pagos_recibidos ({_COLUMNAS_PAGO})"  # noqa: S608
                " values (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)"
                " on conflict do nothing"
                f" returning {_COLUMNAS_PAGO}",
                (
                    pago.id,
                    pago.comercio_id,
                    str(pago.billetera),
                    pago.monto,
                    pago.fecha_hora,
                    pago.numero_operacion,
                    pago.remitente,
                    pago.huella,
                    str(pago.origen),
                    pago.recibido_en,
                ),
            ).fetchone()
            if fila is None:
                fila = conexion.execute(
                    f"select {_COLUMNAS_PAGO} from pagos_recibidos"  # noqa: S608
                    " where comercio_id = %s and (huella = %s"
                    " or (billetera = %s and numero_operacion = %s))"
                    " order by huella = %s desc limit 1",
                    (
                        pago.comercio_id,
                        pago.huella,
                        str(pago.billetera),
                        pago.numero_operacion,
                        pago.huella,
                    ),
                ).fetchone()
            if fila is None:
                raise RuntimeError("la base no devolvió la fila esperada")
            return _pago(fila)

    def candidatos(
        self,
        comercio_id: UUID,
        billetera: Billetera,
        monto: Decimal,
        desde: datetime,
        hasta: datetime,
    ) -> list[PagoRecibido]:
        with self._pool.connection() as conexion:
            filas = conexion.execute(
                f"select {_COLUMNAS_PAGO} from pagos_recibidos"  # noqa: S608
                " where comercio_id = %s and billetera = %s and monto = %s"
                " and fecha_hora between %s and %s order by fecha_hora",
                (comercio_id, str(billetera), monto, desde, hasta),
            ).fetchall()
        return [_pago(f) for f in filas]

    def listar(self, comercio_id: UUID, desde: datetime, hasta: datetime) -> list[PagoRecibido]:
        with self._pool.connection() as conexion:
            filas = conexion.execute(
                f"select {_COLUMNAS_PAGO} from pagos_recibidos"  # noqa: S608
                " where comercio_id = %s and fecha_hora between %s and %s"
                " order by fecha_hora desc limit 500",
                (comercio_id, desde, hasta),
            ).fetchall()
        return [_pago(f) for f in filas]


_COLUMNAS_VERIFICACION = (
    "id, comercio_id, estado, creada_en, monto_esperado, referencia, datos, veredicto, revisada"
)


def _verificacion(fila: tuple[Any, ...]) -> Verificacion:
    veredicto = None if fila[7] is None else veredicto_desde_json(fila[7])
    return Verificacion(
        id=fila[0],
        comercio_id=fila[1],
        estado=Estado(fila[2]),
        creada_en=fila[3],
        monto_esperado=fila[4],
        referencia=fila[5],
        datos=None if fila[6] is None else datos_desde_json(fila[6]),
        veredicto=veredicto,
        revisada=fila[8],
        senales=() if veredicto is None else veredicto.senales,
    )


class RepositorioVerificacionesPg:
    def __init__(self, pool: Pool) -> None:
        self._pool = pool

    def crear(self, verificacion: Verificacion) -> Verificacion:
        return self.crear_idempotente(verificacion, None).verificacion

    def crear_idempotente(
        self,
        verificacion: Verificacion,
        clave: str | None,
        huella_peticion: str | None = None,
        conexion: Conexion | None = None,
    ) -> SolicitudRegistrada:
        with usar_conexion(self._pool, conexion) as activa:
            fila = activa.execute(
                "insert into verificaciones"  # noqa: S608
                " (id, comercio_id, estado, creada_en, monto_esperado, referencia,"
                " clave_idempotencia, huella_peticion)"
                " values (%s, %s, %s, %s, %s, %s, %s, %s)"
                " on conflict (comercio_id, clave_idempotencia) do nothing"
                f" returning {_COLUMNAS_VERIFICACION}, huella_peticion",
                (
                    verificacion.id,
                    verificacion.comercio_id,
                    str(verificacion.estado),
                    verificacion.creada_en,
                    verificacion.monto_esperado,
                    verificacion.referencia,
                    clave,
                    huella_peticion,
                ),
            ).fetchone()
            creada = fila is not None
            if fila is None:
                # en read committed esta lectura ve la fila que confirmó la petición concurrente
                fila = activa.execute(
                    f"select {_COLUMNAS_VERIFICACION}, huella_peticion"  # noqa: S608
                    " from verificaciones where comercio_id = %s and clave_idempotencia = %s",
                    (verificacion.comercio_id, clave),
                ).fetchone()
            if fila is None:
                raise RuntimeError("la base no devolvió la fila esperada")
        huella = None if fila[-1] is None else str(fila[-1]).strip()
        return SolicitudRegistrada(_verificacion(fila), creada, huella)

    def obtener(self, comercio_id: UUID, verificacion_id: UUID) -> Verificacion | None:
        with self._pool.connection() as conexion:
            fila = conexion.execute(
                f"select {_COLUMNAS_VERIFICACION} from verificaciones"  # noqa: S608
                " where comercio_id = %s and id = %s",
                (comercio_id, verificacion_id),
            ).fetchone()
        return None if fila is None else _verificacion(fila)

    def guardar_resultado(
        self,
        comercio_id: UUID,
        verificacion_id: UUID,
        datos: DatosExtraidos,
        veredicto: Veredicto,
    ) -> None:
        with self._pool.connection() as conexion:
            conexion.execute(
                "update verificaciones set estado = %s, datos = %s, veredicto = %s,"
                " completada_en = now() where id = %s and comercio_id = %s",
                (
                    str(veredicto.estado),
                    Jsonb(datos_a_json(datos)),
                    Jsonb(veredicto_a_json(veredicto)),
                    verificacion_id,
                    comercio_id,
                ),
            )

    def reservar_operacion(
        self,
        comercio_id: UUID,
        billetera: Billetera,
        numero_operacion: str,
        verificacion_id: UUID,
    ) -> bool:
        with self._pool.connection() as conexion:
            conexion.execute(
                "insert into operaciones_usadas"
                " (comercio_id, billetera, numero_operacion, verificacion_id)"
                " values (%s, %s, %s, %s) on conflict do nothing",
                (comercio_id, str(billetera), numero_operacion, verificacion_id),
            )
            # si el trabajo se reintenta, la reserva previa de la misma verificación sigue valiendo
            dueno = conexion.execute(
                "select verificacion_id from operaciones_usadas"
                " where comercio_id = %s and billetera = %s and numero_operacion = %s",
                (comercio_id, str(billetera), numero_operacion),
            ).fetchone()
        return dueno is not None and dueno[0] == verificacion_id

    def registrar_revision(
        self, comercio_id: UUID, verificacion_id: UUID, veredicto: Veredicto, nota: str | None
    ) -> None:
        with self._pool.connection() as conexion:
            conexion.execute(
                "update verificaciones set estado = %s, veredicto = %s, revisada = true,"
                " nota_revision = %s where id = %s and comercio_id = %s",
                (
                    str(veredicto.estado),
                    Jsonb(veredicto_a_json(veredicto)),
                    nota,
                    verificacion_id,
                    comercio_id,
                ),
            )


_COLUMNAS_COMERCIO = (
    "id, nombre, destinatarios, celulares, ventana_minutos, tolerancia_minutos,"
    " umbral_revision_humana, url_webhook"
)


def _comercio(fila: tuple[Any, ...]) -> Comercio:
    return Comercio(
        id=fila[0],
        nombre=fila[1],
        destinatarios=tuple(fila[2]),
        celulares=tuple(fila[3]),
        ventana_minutos=fila[4],
        tolerancia_minutos=fila[5],
        umbral_revision_humana=fila[6],
        url_webhook=fila[7],
    )


class RepositorioComerciosPg:
    def __init__(self, pool: Pool) -> None:
        self._pool = pool

    def crear(self, comercio: Comercio, secreto_cifrado: str | None = None) -> Comercio:
        with self._pool.connection() as conexion:
            conexion.execute(
                f"insert into comercios ({_COLUMNAS_COMERCIO}, secreto_webhook_cifrado)"  # noqa: S608
                " values (%s, %s, %s, %s, %s, %s, %s, %s, %s)",
                (
                    comercio.id,
                    comercio.nombre,
                    list(comercio.destinatarios),
                    list(comercio.celulares),
                    comercio.ventana_minutos,
                    comercio.tolerancia_minutos,
                    comercio.umbral_revision_humana,
                    comercio.url_webhook,
                    secreto_cifrado,
                ),
            )
        return comercio

    def obtener(self, comercio_id: UUID) -> Comercio | None:
        with self._pool.connection() as conexion:
            fila = conexion.execute(
                f"select {_COLUMNAS_COMERCIO} from comercios where id = %s",  # noqa: S608
                (comercio_id,),
            ).fetchone()
        return None if fila is None else _comercio(fila)

    def secreto_cifrado(self, comercio_id: UUID) -> str | None:
        with self._pool.connection() as conexion:
            fila = conexion.execute(
                "select secreto_webhook_cifrado from comercios where id = %s", (comercio_id,)
            ).fetchone()
        return None if fila is None else fila[0]

    def configurar_webhook(self, comercio_id: UUID, url: str, secreto_cifrado: str | None) -> bool:
        with self._pool.connection() as conexion:
            cursor = conexion.execute(
                "update comercios set url_webhook = %s,"
                " secreto_webhook_cifrado = coalesce(%s, secreto_webhook_cifrado)"
                " where id = %s",
                (url, secreto_cifrado, comercio_id),
            )
            return cursor.rowcount == 1

    def reemplazar_secreto(self, comercio_id: UUID, secreto_cifrado: str) -> bool:
        with self._pool.connection() as conexion:
            cursor = conexion.execute(
                "update comercios set secreto_webhook_cifrado = %s where id = %s",
                (secreto_cifrado, comercio_id),
            )
            return cursor.rowcount == 1

    def agregar_hash_clave(self, comercio_id: UUID, hash_clave: str) -> None:
        with self._pool.connection() as conexion:
            conexion.execute(
                "insert into claves_api (id, comercio_id, hash_clave) values (%s, %s, %s)",
                (uuid4(), comercio_id, hash_clave),
            )

    def emitir_clave(self, comercio_id: UUID) -> str:
        clave = "yc_" + secrets.token_urlsafe(32)
        self.agregar_hash_clave(comercio_id, sha256(clave))
        return clave

    def por_clave(self, clave: str) -> Comercio | None:
        return self.por_hash_clave(sha256(clave))

    def por_hash_clave(self, hash_clave: str) -> Comercio | None:
        with self._pool.connection() as conexion:
            fila = conexion.execute(
                "select c.id, c.nombre, c.destinatarios, c.celulares, c.ventana_minutos,"
                " c.tolerancia_minutos, c.umbral_revision_humana, c.url_webhook"
                " from claves_api k join comercios c on c.id = k.comercio_id"
                " where k.hash_clave = %s and k.revocada_en is null",
                (hash_clave,),
            ).fetchone()
        return None if fila is None else _comercio(fila)


class RepositorioDispositivosPg:
    def __init__(self, pool: Pool) -> None:
        self._pool = pool

    def crear_codigo(self, comercio_id: UUID, vigencia: timedelta) -> tuple[str, datetime]:
        codigo = f"{secrets.randbelow(10**8):08d}"
        with self._pool.connection() as conexion:
            fila = conexion.execute(
                "insert into codigos_vinculacion (hash_codigo, comercio_id, expira_en)"
                " values (%s, %s, now() + %s) returning expira_en",
                (sha256(codigo), comercio_id, vigencia),
            ).fetchone()
        if fila is None:
            raise RuntimeError("la base no devolvió la fila esperada")
        return codigo, fila[0]

    def vincular(self, codigo: str, nombre: str | None = None) -> str | None:
        token = "yd_" + secrets.token_urlsafe(32)
        with self._pool.connection() as conexion, conexion.transaction():
            fila = conexion.execute(
                "update codigos_vinculacion set usado_en = now()"
                " where hash_codigo = %s and usado_en is null and expira_en > now()"
                " returning comercio_id",
                (sha256(codigo),),
            ).fetchone()
            if fila is None:
                return None
            conexion.execute(
                "insert into dispositivos (id, comercio_id, hash_token, nombre)"
                " values (%s, %s, %s, %s)",
                (uuid4(), fila[0], sha256(token), nombre),
            )
        return token

    def comercio_por_token(self, token: str) -> UUID | None:
        return self.comercio_por_hash_token(sha256(token))

    def comercio_por_hash_token(self, hash_token: str) -> UUID | None:
        with self._pool.connection() as conexion:
            fila = conexion.execute(
                "select comercio_id from dispositivos"
                " where hash_token = %s and revocado_en is null",
                (hash_token,),
            ).fetchone()
        return None if fila is None else fila[0]
