from __future__ import annotations

import hashlib
import secrets
import threading
from collections import OrderedDict
from collections.abc import Callable
from datetime import UTC, datetime, timedelta
from functools import cache
from uuid import UUID, uuid4

import psycopg
from psycopg_pool import ConnectionPool

from cobroverificado.adaptadores.almacen import AlmacenImagenesLocal
from cobroverificado.adaptadores.cifrado import CifradorSecretos
from cobroverificado.adaptadores.http.servicios import (
    CodigoVinculacion,
    ConflictoIdempotenciaError,
    NuevoPago,
    Servicios,
    Solicitada,
)
from cobroverificado.adaptadores.imagen import AnalizadorEdicion
from cobroverificado.adaptadores.ocr import ExtractorOcr, Linea
from cobroverificado.adaptadores.postgres import (
    ColaTrabajosPg,
    Pool,
    RegistroEntregasPg,
    RegistroSolicitudesPg,
    RepositorioComerciosPg,
    RepositorioDispositivosPg,
    RepositorioPagosPg,
    RepositorioVerificacionesPg,
    Trabajo,
    limpiar_filas,
    migrar,
)
from cobroverificado.adaptadores.webhooks import DespachadorWebhooks
from cobroverificado.aplicacion.errores import (
    IdempotenciaConflictoError,
    VerificacionNoEncontradaError,
    VerificacionNoRevisableError,
)
from cobroverificado.aplicacion.registrar_pago import RegistrarPagoRecibido
from cobroverificado.aplicacion.revisar_verificacion import DecisionRevision, RevisarVerificacion
from cobroverificado.aplicacion.solicitar_verificacion import SolicitarVerificacion
from cobroverificado.aplicacion.verificar_captura import VerificarCaptura
from cobroverificado.config import Configuracion
from cobroverificado.dominio.modelos import (
    Comercio,
    DatosExtraidos,
    Estado,
    Fuente,
    PagoRecibido,
    Senal,
    Severidad,
    SolicitudVerificacion,
    Veredicto,
    Verificacion,
)
from cobroverificado.trabajador import Trabajador

VIGENCIA_CODIGO = timedelta(minutes=10)


class RelojSistema:
    def ahora(self) -> datetime:
        return datetime.now(UTC)


@cache
def abrir_pool(url: str) -> Pool:
    with psycopg.connect(url) as conexion:
        migrar(conexion)
    return ConnectionPool(url, min_size=1, max_size=10, open=True)


def _pool(config: Configuracion) -> Pool:
    return abrir_pool(config.database_url.get_secret_value())


def _cifrador(config: Configuracion) -> CifradorSecretos:
    if config.clave_cifrado is None:
        raise RuntimeError("falta COBROVERIFICADO_CLAVE_CIFRADO (32 bytes en hexadecimal)")
    return CifradorSecretos(config.clave_cifrado.get_secret_value())


class Autenticacion:
    def __init__(self, pool: Pool) -> None:
        self._comercios = RepositorioComerciosPg(pool)
        self._dispositivos = RepositorioDispositivosPg(pool)

    def comercio_por_clave(self, hash_clave: str) -> Comercio | None:
        return self._comercios.por_hash_clave(hash_clave)

    def comercio_por_dispositivo(self, hash_token: str) -> UUID | None:
        return self._dispositivos.comercio_por_hash_token(hash_token)


class CasosVerificaciones:
    def __init__(self, pool: Pool, almacen: AlmacenImagenesLocal) -> None:
        self._verificaciones = RepositorioVerificacionesPg(pool)
        self._solicitar = SolicitarVerificacion(
            RegistroSolicitudesPg(pool), almacen, RelojSistema()
        )
        self._revisar = RevisarVerificacion(self._verificaciones)

    def solicitar(
        self, solicitud: SolicitudVerificacion, imagen: bytes, clave_idempotencia: str | None
    ) -> Solicitada:
        try:
            registrada = self._solicitar.ejecutar(solicitud, imagen, clave_idempotencia)
        except IdempotenciaConflictoError as error:
            raise ConflictoIdempotenciaError(str(error)) from error
        return Solicitada(registrada.verificacion, repetida=not registrada.creada)

    def obtener(self, comercio_id: UUID, verificacion_id: UUID) -> Verificacion | None:
        return self._verificaciones.obtener(comercio_id, verificacion_id)

    def revisar(
        self, comercio_id: UUID, verificacion_id: UUID, aprobar: bool, nota: str | None
    ) -> Verificacion | None:
        decision = DecisionRevision.APROBAR if aprobar else DecisionRevision.RECHAZAR
        try:
            self._revisar.ejecutar(comercio_id, verificacion_id, decision, nota)
        except VerificacionNoEncontradaError:
            return None
        except VerificacionNoRevisableError as error:
            raise ValueError(str(error)) from error
        return self._verificaciones.obtener(comercio_id, verificacion_id)


class CasosDispositivos:
    def __init__(self, pool: Pool) -> None:
        self._repo = RepositorioDispositivosPg(pool)

    def generar_codigo(self, comercio_id: UUID) -> CodigoVinculacion:
        codigo, expira_en = self._repo.crear_codigo(comercio_id, VIGENCIA_CODIGO)
        return CodigoVinculacion(codigo, expira_en)

    def vincular(self, codigo: str) -> str | None:
        return self._repo.vincular(codigo)


class CasosPagos:
    def __init__(self, pool: Pool) -> None:
        self._repo = RepositorioPagosPg(pool)
        self._registrar = RegistrarPagoRecibido(self._repo, RelojSistema())

    def registrar(self, pago: NuevoPago) -> PagoRecibido:
        return self._registrar.ejecutar(
            pago.comercio_id,
            pago.billetera,
            pago.monto,
            pago.fecha_hora,
            pago.huella,
            remitente=pago.remitente,
            numero_operacion=pago.numero_operacion,
        )

    def listar(self, comercio_id: UUID, desde: datetime, hasta: datetime) -> list[PagoRecibido]:
        return self._repo.listar(comercio_id, desde, hasta)


class Salud:
    def __init__(self, pool: Pool) -> None:
        self._pool = pool

    def disponible(self) -> bool:
        try:
            with self._pool.connection(timeout=2) as conexion:
                conexion.execute("select 1")
        except Exception:
            return False
        return True


class CasosWebhook:
    def __init__(self, pool: Pool, cifrador: CifradorSecretos) -> None:
        self._comercios = RepositorioComerciosPg(pool)
        self._cifrador = cifrador

    def configurar(self, comercio_id: UUID, url: str) -> str | None:
        if self._comercios.secreto_cifrado(comercio_id) is not None:
            self._comercios.configurar_webhook(comercio_id, url, None)
            return None
        secreto = nuevo_secreto()
        cifrado = self._cifrador.cifrar(secreto, comercio_id)
        self._comercios.configurar_webhook(comercio_id, url, cifrado)
        return secreto

    def rotar(self, comercio_id: UUID) -> str:
        secreto = nuevo_secreto()
        self._comercios.reemplazar_secreto(comercio_id, self._cifrador.cifrar(secreto, comercio_id))
        return secreto


def nuevo_secreto() -> str:
    return "whsec_" + secrets.token_urlsafe(32)


def construir_servicios(config: Configuracion) -> Servicios:
    pool = _pool(config)
    cifrador = _cifrador(config)
    return Servicios(
        autenticacion=Autenticacion(pool),
        verificaciones=CasosVerificaciones(pool, AlmacenImagenesLocal(config.directorio_imagenes)),
        dispositivos=CasosDispositivos(pool),
        pagos=CasosPagos(pool),
        salud=Salud(pool),
        webhook=CasosWebhook(pool, cifrador),
    )


class RegistroComercios:
    def __init__(self, pool: Pool) -> None:
        self._repo = RepositorioComerciosPg(pool)

    def crear(
        self,
        nombre: str,
        destinatarios: tuple[str, ...],
        celulares: tuple[str, ...],
        hash_clave: str,
    ) -> Comercio:
        comercio = self._repo.crear(
            Comercio(id=uuid4(), nombre=nombre, destinatarios=destinatarios, celulares=celulares)
        )
        self._repo.agregar_hash_clave(comercio.id, hash_clave)
        return comercio


def registro_comercios(config: Configuracion) -> RegistroComercios:
    return RegistroComercios(_pool(config))


class LineasEnCache:
    # el extractor y el analizador leen la misma imagen; así rapidocr corre una sola vez
    def __init__(self, leer: Callable[[bytes], list[Linea]], tamano: int = 4) -> None:
        self._leer = leer
        self._tamano = tamano
        self._guardadas: OrderedDict[str, list[Linea]] = OrderedDict()
        self._candado = threading.Lock()

    def __call__(self, imagen: bytes) -> list[Linea]:
        huella = hashlib.sha256(imagen).hexdigest()
        with self._candado:
            if huella in self._guardadas:
                self._guardadas.move_to_end(huella)
                return self._guardadas[huella]
        lineas = self._leer(imagen)
        with self._candado:
            self._guardadas[huella] = lineas
            while len(self._guardadas) > self._tamano:
                self._guardadas.popitem(last=False)
        return lineas


def construir_trabajador(config: Configuracion) -> Trabajador[Trabajo]:
    from cobroverificado.principal import construir_extractor

    pool = _pool(config)
    cifrador = _cifrador(config)
    verificaciones = RepositorioVerificacionesPg(pool)
    almacen = AlmacenImagenesLocal(config.directorio_imagenes)
    ocr = ExtractorOcr()
    lineas = LineasEnCache(ocr.lineas)
    setattr(ocr, "lineas", lineas)  # noqa: B010
    caso = VerificarCaptura(
        construir_extractor(config, ocr),
        AnalizadorEdicion(detector=lineas),
        RepositorioPagosPg(pool),
        verificaciones,
        RelojSistema(),
    )
    retencion = timedelta(hours=config.retencion_horas)

    def limpiar() -> None:
        almacen.limpiar_antiguas(retencion)
        limpiar_filas(pool, retencion)

    def al_fallar(comercio_id: UUID, verificacion_id: UUID, _error: str) -> None:
        senal = Senal("procesamiento_fallido", Severidad.CRITICA, "No se pudo procesar la imagen")
        verificaciones.guardar_resultado(
            comercio_id,
            verificacion_id,
            DatosExtraidos(None, None, None, None, None, None, None, 0.0, Fuente.OCR),
            Veredicto(Estado.RECHAZADO, (senal,)),
        )

    return Trabajador(
        ColaTrabajosPg(pool),
        almacen,
        RepositorioComerciosPg(pool),
        caso,
        verificaciones=verificaciones,
        despachador=DespachadorWebhooks(RegistroEntregasPg(pool), cifrador.descifrar),
        limpiar=limpiar,
        al_fallar=al_fallar,
    )
