from __future__ import annotations

import os
from dataclasses import replace
from datetime import UTC, datetime, timedelta
from decimal import Decimal
from uuid import UUID, uuid4

import psycopg

from cobroverificado.adaptadores.postgres import (
    Pool,
    RepositorioComerciosPg,
    RepositorioDispositivosPg,
    RepositorioPagosPg,
    RepositorioVerificacionesPg,
    migrar,
    sha256,
)
from cobroverificado.dominio.modelos import (
    Billetera,
    Comercio,
    DatosExtraidos,
    Estado,
    Fuente,
    OrigenPago,
    PagoRecibido,
    Senal,
    Severidad,
    Veredicto,
    Verificacion,
)

URL = os.environ.get(
    "COBROVERIFICADO_TEST_DATABASE_URL", "postgresql://postgres:prueba@localhost:55432/postgres"
)
AHORA = datetime(2026, 11, 9, 15, 0, tzinfo=UTC)


def _pago(comercio_id: UUID, huella: str, monto: str = "25.50", minutos: int = 0) -> PagoRecibido:
    return PagoRecibido(
        id=uuid4(),
        comercio_id=comercio_id,
        billetera=Billetera.YAPE,
        monto=Decimal(monto),
        fecha_hora=AHORA + timedelta(minutes=minutos),
        numero_operacion="123456",
        remitente="Juan",
        huella=sha256(huella),
        origen=OrigenPago.NOTIFICACION,
        recibido_en=AHORA,
    )


def _verificacion(comercio_id: UUID) -> Verificacion:
    return Verificacion(
        id=uuid4(),
        comercio_id=comercio_id,
        estado=Estado.PROCESANDO,
        creada_en=AHORA,
        monto_esperado=Decimal("25.50"),
        referencia="pedido-1",
    )


def test_migrar_es_idempotente() -> None:
    with psycopg.connect(URL) as conexion:
        assert migrar(conexion) == []


def test_pago_duplicado_devuelve_el_existente(pool: Pool, comercio: Comercio) -> None:
    repo = RepositorioPagosPg(pool)
    primero = repo.registrar(_pago(comercio.id, "n1"))
    segundo = repo.registrar(_pago(comercio.id, "n1"))
    assert segundo.id == primero.id
    assert primero.monto == Decimal("25.50")
    assert primero.fecha_hora == AHORA


def test_candidatos_filtra_por_comercio_monto_y_ventana(pool: Pool, comercio: Comercio) -> None:
    otro = RepositorioComerciosPg(pool).crear(
        Comercio(id=uuid4(), nombre="Otra", destinatarios=(), celulares=())
    )
    repo = RepositorioPagosPg(pool)
    esperado = repo.registrar(_pago(comercio.id, "a"))
    repo.registrar(replace(_pago(comercio.id, "b", monto="30.00"), numero_operacion="2"))
    repo.registrar(replace(_pago(comercio.id, "c", minutos=60), numero_operacion="3"))
    repo.registrar(_pago(otro.id, "a"))
    encontrados = repo.candidatos(
        comercio.id,
        Billetera.YAPE,
        Decimal("25.5"),
        AHORA - timedelta(minutes=10),
        AHORA + timedelta(minutes=10),
    )
    assert [p.id for p in encontrados] == [esperado.id]
    assert (
        len(repo.listar(comercio.id, AHORA - timedelta(hours=1), AHORA + timedelta(hours=2))) == 3
    )


def test_verificacion_guarda_resultado_y_esta_aislada(pool: Pool, comercio: Comercio) -> None:
    repo = RepositorioVerificacionesPg(pool)
    creada = repo.crear(_verificacion(comercio.id))
    assert repo.obtener(uuid4(), creada.id) is None
    datos = DatosExtraidos(
        Billetera.PLIN, Decimal("25.50"), AHORA, "999", "Ana P.", "Juan", None, 0.93, Fuente.OCR
    )
    senal = Senal("hora_futura", Severidad.CRITICA, "la hora es futura")
    veredicto = Veredicto(Estado.SOSPECHOSO, (senal,), None, True)
    repo.guardar_resultado(uuid4(), creada.id, datos, veredicto)
    sin_tocar = repo.obtener(comercio.id, creada.id)
    assert sin_tocar is not None
    assert sin_tocar.estado is Estado.PROCESANDO
    repo.guardar_resultado(comercio.id, creada.id, datos, veredicto)
    leida = repo.obtener(comercio.id, creada.id)
    assert leida is not None
    assert leida.estado is Estado.SOSPECHOSO
    assert leida.datos == datos
    assert leida.veredicto == veredicto
    assert leida.senales == (senal,)


def test_idempotencia_de_verificaciones(pool: Pool, comercio: Comercio) -> None:
    repo = RepositorioVerificacionesPg(pool)
    primera = repo.crear_idempotente(_verificacion(comercio.id), "clave-1", sha256("a"))
    segunda = repo.crear_idempotente(_verificacion(comercio.id), "clave-1", sha256("b"))
    assert primera.creada
    assert not segunda.creada
    assert segunda.verificacion.id == primera.verificacion.id
    assert segunda.huella_peticion == sha256("a")


def test_reservar_operacion(pool: Pool, comercio: Comercio) -> None:
    repo = RepositorioVerificacionesPg(pool)
    a = repo.crear(_verificacion(comercio.id))
    b = repo.crear(_verificacion(comercio.id))
    assert repo.reservar_operacion(comercio.id, Billetera.YAPE, "777", a.id)
    assert repo.reservar_operacion(comercio.id, Billetera.YAPE, "777", a.id)
    assert not repo.reservar_operacion(comercio.id, Billetera.YAPE, "777", b.id)
    assert repo.reservar_operacion(comercio.id, Billetera.PLIN, "777", b.id)


def test_registrar_revision_guarda_nota(pool: Pool, comercio: Comercio) -> None:
    repo = RepositorioVerificacionesPg(pool)
    v = repo.crear(_verificacion(comercio.id))
    veredicto = Veredicto(Estado.CONFIRMADO, (Senal("revision_aprobada", Severidad.INFO, "ok"),))
    repo.registrar_revision(uuid4(), v.id, veredicto, "ajeno")
    intacta = repo.obtener(comercio.id, v.id)
    assert intacta is not None
    assert not intacta.revisada
    repo.registrar_revision(comercio.id, v.id, veredicto, "visto en banco")
    leida = repo.obtener(comercio.id, v.id)
    assert leida is not None
    assert leida.revisada
    assert leida.veredicto == veredicto
    with pool.connection() as conexion:
        fila = conexion.execute(
            "select nota_revision from verificaciones where id = %s", (v.id,)
        ).fetchone()
    assert fila == ("visto en banco",)


def test_clave_api_se_guarda_como_hash(pool: Pool, comercio: Comercio) -> None:
    repo = RepositorioComerciosPg(pool)
    clave = repo.emitir_clave(comercio.id)
    encontrado = repo.por_clave(clave)
    assert encontrado is not None
    assert encontrado.id == comercio.id
    assert repo.por_clave(clave + "x") is None
    with pool.connection() as conexion:
        filas = conexion.execute(
            "select hash_clave from claves_api where comercio_id = %s", (comercio.id,)
        ).fetchall()
    assert [f[0] for f in filas] == [sha256(clave)]


def test_vincular_dispositivo_una_sola_vez(pool: Pool, comercio: Comercio) -> None:
    repo = RepositorioDispositivosPg(pool)
    codigo, expira = repo.crear_codigo(comercio.id, timedelta(minutes=10))
    assert expira > datetime.now(UTC)
    token = repo.vincular(codigo)
    assert token is not None
    assert repo.vincular(codigo) is None
    assert repo.comercio_por_token(token) == comercio.id
    with pool.connection() as conexion:
        fila = conexion.execute(
            "select count(*) from dispositivos where hash_token = %s", (sha256(token),)
        ).fetchone()
    assert fila == (1,)


def test_codigo_vencido_no_vincula(pool: Pool, comercio: Comercio) -> None:
    repo = RepositorioDispositivosPg(pool)
    codigo, _ = repo.crear_codigo(comercio.id, timedelta(seconds=-1))
    assert repo.vincular(codigo) is None


def test_notificacion_repetida_con_otra_huella_no_duplica(pool: Pool, comercio: Comercio) -> None:
    repo = RepositorioPagosPg(pool)
    primero = repo.registrar(_pago(comercio.id, "post-1"))
    repetido = repo.registrar(_pago(comercio.id, "post-2", minutos=1))
    assert repetido.id == primero.id
    plin = repo.registrar(replace(_pago(comercio.id, "post-3"), billetera=Billetera.PLIN))
    assert plin.id != primero.id
    sin_operacion = [
        repo.registrar(replace(_pago(comercio.id, h), numero_operacion=None)) for h in ("s1", "s2")
    ]
    assert sin_operacion[0].id != sin_operacion[1].id
    with pool.connection() as conexion:
        fila = conexion.execute(
            "select count(*) from pagos_recibidos where comercio_id = %s"
            " and numero_operacion = '123456' and billetera = 'yape'",
            (comercio.id,),
        ).fetchone()
    assert fila == (1,)
