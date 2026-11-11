from __future__ import annotations

from dataclasses import replace
from datetime import datetime, timedelta
from decimal import Decimal
from uuid import UUID, uuid4

import pytest
from fakes_aplicacion import (
    AHORA,
    AnalizadorFijo,
    ExtractorFijo,
    PagosEnMemoria,
    RelojFijo,
    VerificacionesEnMemoria,
    nueva_verificacion,
)
from hypothesis import given
from hypothesis import strategies as st

from cobroverificado.aplicacion.errores import (
    IdempotenciaConflictoError,
    PagoInvalidoError,
    VerificacionNoEncontradaError,
    VerificacionNoRevisableError,
)
from cobroverificado.aplicacion.registrar_pago import RegistrarPagoRecibido
from cobroverificado.aplicacion.revisar_verificacion import DecisionRevision, RevisarVerificacion
from cobroverificado.aplicacion.solicitar_verificacion import SolicitarVerificacion
from cobroverificado.aplicacion.verificar_captura import VerificarCaptura
from cobroverificado.dominio.modelos import (
    Billetera,
    Comercio,
    DatosExtraidos,
    Estado,
    Fuente,
    OrigenPago,
    Senal,
    Severidad,
    SolicitudVerificacion,
    Veredicto,
    Verificacion,
)
from cobroverificado.puertos import SolicitudRegistrada

COMERCIO = Comercio(
    id=UUID("00000000-0000-0000-0000-000000000001"),
    nombre="Bodega",
    destinatarios=("Juan Perez",),
    celulares=("987654321",),
)
IMAGEN = b"png-sintetico"


def datos(**cambios: object) -> DatosExtraidos:
    base = DatosExtraidos(
        billetera=Billetera.YAPE,
        monto=Decimal("50.00"),
        fecha_hora=AHORA - timedelta(minutes=5),
        numero_operacion="000111",
        destinatario="Juan P.",
        remitente=None,
        celular_destino=None,
        confianza=0.9,
        fuente=Fuente.OCR,
    )
    return replace(base, **cambios)  # type: ignore[arg-type]


class Escenario:
    def __init__(self, extraidos: DatosExtraidos, senales: list[Senal] | None = None) -> None:
        self.extractor = ExtractorFijo(extraidos)
        self.pagos = PagosEnMemoria()
        self.verificaciones = VerificacionesEnMemoria()
        self.reloj = RelojFijo()
        self.registrar = RegistrarPagoRecibido(self.pagos, self.reloj)
        self.verificar = VerificarCaptura(
            self.extractor,
            AnalizadorFijo(senales or []),
            self.pagos,
            self.verificaciones,
            self.reloj,
        )

    def verificacion(self, monto: Decimal | None = Decimal("50.00")) -> UUID:
        id_ = uuid4()
        self.verificaciones.crear(nueva_verificacion(COMERCIO.id, id_, monto))
        return id_

    def pago(self, minutos: int = -4, billetera: Billetera = Billetera.YAPE) -> None:
        self.registrar.ejecutar(
            COMERCIO.id,
            billetera,
            Decimal("50.00"),
            AHORA + timedelta(minutes=minutos),
            huella=str(uuid4()),
            numero_operacion="000111",
        )


def test_verificar_confirma_y_guarda() -> None:
    e = Escenario(datos())
    e.pago()
    id_ = e.verificacion()
    veredicto = e.verificar.ejecutar(COMERCIO, id_, IMAGEN)

    assert veredicto.estado is Estado.CONFIRMADO
    guardada = e.verificaciones.verificaciones[id_]
    assert guardada.estado is Estado.CONFIRMADO
    assert guardada.datos == e.extractor.datos
    assert e.extractor.recibidas == [IMAGEN]


def test_verificar_sin_pago_queda_pendiente() -> None:
    e = Escenario(datos())
    assert e.verificar.ejecutar(COMERCIO, e.verificacion(), IMAGEN).estado is Estado.PENDIENTE


def test_misma_captura_en_dos_verificaciones_se_rechaza() -> None:
    e = Escenario(datos())
    e.pago()
    assert e.verificar.ejecutar(COMERCIO, e.verificacion(), IMAGEN).estado is Estado.CONFIRMADO
    segunda = e.verificar.ejecutar(COMERCIO, e.verificacion(), IMAGEN)
    assert segunda.estado is Estado.RECHAZADO
    assert "operacion_repetida" in {s.codigo for s in segunda.senales}


def test_reintento_de_la_misma_verificacion_no_se_bloquea() -> None:
    e = Escenario(datos())
    e.pago()
    id_ = e.verificacion()
    e.verificar.ejecutar(COMERCIO, id_, IMAGEN)
    assert e.verificar.ejecutar(COMERCIO, id_, IMAGEN).estado is Estado.CONFIRMADO


def test_captura_ilegible_no_reserva_operacion() -> None:
    e = Escenario(datos(monto=None))
    assert e.verificar.ejecutar(COMERCIO, e.verificacion(), IMAGEN).estado is Estado.RECHAZADO
    assert e.verificaciones.operaciones == {}


def test_sin_numero_operacion_no_reserva() -> None:
    e = Escenario(datos(numero_operacion=None))
    e.verificar.ejecutar(COMERCIO, e.verificacion(), IMAGEN)
    assert e.verificaciones.operaciones == {}


def test_billetera_desconocida_consulta_ambas() -> None:
    e = Escenario(datos(billetera=None))
    e.pago(billetera=Billetera.PLIN)
    veredicto = e.verificar.ejecutar(COMERCIO, e.verificacion(), IMAGEN)
    assert veredicto.estado is Estado.CONFIRMADO
    assert {c[0] for c in e.pagos.consultas} == set(Billetera)


def test_consulta_candidatos_en_ventana_de_tolerancia() -> None:
    e = Escenario(datos())
    e.verificar.ejecutar(COMERCIO, e.verificacion(), IMAGEN)
    fecha = AHORA - timedelta(minutes=5)
    tolerancia = timedelta(minutes=COMERCIO.tolerancia_minutos)
    assert e.pagos.consultas == [(Billetera.YAPE, fecha - tolerancia, fecha + tolerancia)]


def test_senal_critica_del_analizador_llega_al_veredicto() -> None:
    critica = Senal("edicion_detectada", Severidad.CRITICA, "Se detectó edición.")
    e = Escenario(datos(), [critica])
    e.pago()
    veredicto = e.verificar.ejecutar(COMERCIO, e.verificacion(), IMAGEN)
    assert veredicto.estado is Estado.SOSPECHOSO
    assert critica in veredicto.senales


def test_monto_esperado_viene_de_la_verificacion() -> None:
    e = Escenario(datos())
    e.pago()
    veredicto = e.verificar.ejecutar(COMERCIO, e.verificacion(Decimal("80")), IMAGEN)
    assert veredicto.estado is Estado.RECHAZADO


def test_verificacion_inexistente() -> None:
    e = Escenario(datos())
    with pytest.raises(VerificacionNoEncontradaError):
        e.verificar.ejecutar(COMERCIO, uuid4(), IMAGEN)


def test_verificacion_de_otro_comercio_no_se_encuentra() -> None:
    e = Escenario(datos())
    id_ = e.verificacion()
    with pytest.raises(VerificacionNoEncontradaError):
        e.verificar.ejecutar(replace(COMERCIO, id=uuid4()), id_, IMAGEN)


def test_registrar_pago_completa_campos() -> None:
    pagos = PagosEnMemoria()
    fijo = UUID("00000000-0000-0000-0000-0000000000aa")
    caso = RegistrarPagoRecibido(pagos, RelojFijo(), generar_id=lambda: fijo)
    pago = caso.ejecutar(
        COMERCIO.id,
        Billetera.PLIN,
        Decimal("12.5"),
        AHORA,
        huella=" abc ",
        remitente="  ",
        numero_operacion=" 77 ",
        origen=OrigenPago.MANUAL,
    )
    assert pago.id == fijo
    assert pago.recibido_en == AHORA
    assert pago.huella == "abc"
    assert pago.remitente is None
    assert pago.numero_operacion == "77"
    assert pago.origen is OrigenPago.MANUAL
    assert pagos.pagos == [pago]


def test_registrar_pago_duplicado_devuelve_el_existente() -> None:
    pagos = PagosEnMemoria()
    caso = RegistrarPagoRecibido(pagos, RelojFijo())
    primero = caso.ejecutar(COMERCIO.id, Billetera.YAPE, Decimal("1"), AHORA, huella="h")
    segundo = caso.ejecutar(COMERCIO.id, Billetera.YAPE, Decimal("1"), AHORA, huella="h")
    assert segundo == primero
    assert len(pagos.pagos) == 1


@pytest.mark.parametrize("monto", [Decimal("0"), Decimal("-1"), Decimal("NaN"), Decimal("Inf")])
def test_registrar_pago_monto_invalido(monto: Decimal) -> None:
    caso = RegistrarPagoRecibido(PagosEnMemoria(), RelojFijo())
    with pytest.raises(PagoInvalidoError):
        caso.ejecutar(COMERCIO.id, Billetera.YAPE, monto, AHORA, huella="h")


def test_registrar_pago_fecha_sin_zona() -> None:
    caso = RegistrarPagoRecibido(PagosEnMemoria(), RelojFijo())
    with pytest.raises(PagoInvalidoError):
        caso.ejecutar(COMERCIO.id, Billetera.YAPE, Decimal("1"), datetime(2026, 1, 1), huella="h")


def test_registrar_pago_sin_huella() -> None:
    caso = RegistrarPagoRecibido(PagosEnMemoria(), RelojFijo())
    with pytest.raises(PagoInvalidoError):
        caso.ejecutar(COMERCIO.id, Billetera.YAPE, Decimal("1"), AHORA, huella="  ")


@given(st.integers(min_value=-10, max_value=10))
def test_pago_registrado_dentro_de_tolerancia_confirma(minutos: int) -> None:
    e = Escenario(datos())
    e.pago(minutos=-5 + minutos)
    assert e.verificar.ejecutar(COMERCIO, e.verificacion(), IMAGEN).estado is Estado.CONFIRMADO


@given(st.integers(min_value=11, max_value=500), st.booleans())
def test_pago_fuera_de_tolerancia_no_confirma(minutos: int, antes: bool) -> None:
    e = Escenario(datos())
    e.pago(minutos=-5 + (-minutos if antes else minutos))
    assert e.verificar.ejecutar(COMERCIO, e.verificacion(), IMAGEN).estado is Estado.PENDIENTE


def _verificada(e: Escenario) -> UUID:
    e.pago()
    id_ = e.verificacion()
    e.verificar.ejecutar(COMERCIO, id_, IMAGEN)
    return id_


@pytest.mark.parametrize(
    ("decision", "estado", "codigo"),
    [
        (DecisionRevision.APROBAR, Estado.CONFIRMADO, "revision_aprobada"),
        (DecisionRevision.RECHAZAR, Estado.RECHAZADO, "revision_rechazada"),
    ],
)
def test_revisar(decision: DecisionRevision, estado: Estado, codigo: str) -> None:
    e = Escenario(datos(destinatario="Otra Persona"))
    id_ = _verificada(e)
    veredicto = RevisarVerificacion(e.verificaciones).ejecutar(
        COMERCIO.id, id_, decision, nota=" llamé al cliente "
    )
    assert veredicto.estado is estado
    assert not veredicto.requiere_revision
    assert veredicto.senales[-1].codigo == codigo
    assert veredicto.senales[-1].mensaje.endswith("llamé al cliente")
    assert "destinatario_ajeno" in {s.codigo for s in veredicto.senales}
    assert e.verificaciones.verificaciones[id_].estado is estado
    assert e.verificaciones.verificaciones[id_].revisada
    assert e.verificaciones.notas[id_] == "llamé al cliente"


def test_revisar_sin_nota() -> None:
    e = Escenario(datos())
    id_ = _verificada(e)
    veredicto = RevisarVerificacion(e.verificaciones).ejecutar(
        COMERCIO.id, id_, DecisionRevision.APROBAR
    )
    assert veredicto.senales[-1].mensaje == "Aprobada por revisión humana"


def test_revisar_inexistente() -> None:
    with pytest.raises(VerificacionNoEncontradaError):
        RevisarVerificacion(VerificacionesEnMemoria()).ejecutar(
            COMERCIO.id, uuid4(), DecisionRevision.APROBAR
        )


def test_revisar_en_proceso() -> None:
    e = Escenario(datos())
    with pytest.raises(VerificacionNoRevisableError):
        RevisarVerificacion(e.verificaciones).ejecutar(
            COMERCIO.id, e.verificacion(), DecisionRevision.APROBAR
        )


def test_revisar_ya_revisada() -> None:
    e = Escenario(datos())
    id_ = _verificada(e)
    revisar = RevisarVerificacion(e.verificaciones)
    revisar.ejecutar(COMERCIO.id, id_, DecisionRevision.APROBAR)
    with pytest.raises(VerificacionNoRevisableError):
        revisar.ejecutar(COMERCIO.id, id_, DecisionRevision.RECHAZAR)


def test_revisar_conserva_datos_extraidos() -> None:
    e = Escenario(datos())
    id_ = _verificada(e)
    RevisarVerificacion(e.verificaciones).ejecutar(
        COMERCIO.id, id_, DecisionRevision.RECHAZAR, "  "
    )
    assert e.verificaciones.verificaciones[id_].datos == e.extractor.datos
    assert e.verificaciones.notas[id_] is None


class _AlmacenEnMemoria:
    def __init__(self) -> None:
        self.imagenes: dict[str, bytes] = {}

    def guardar(self, imagen: bytes) -> str:
        nombre = uuid4().hex
        self.imagenes[nombre] = imagen
        return nombre

    def borrar(self, nombre: str) -> None:
        self.imagenes.pop(nombre, None)


class _RegistroEnMemoria:
    def __init__(self, falla: bool = False) -> None:
        self.falla = falla
        self.por_clave: dict[str, SolicitudRegistrada] = {}
        self.trabajos: list[str] = []

    def crear_y_encolar(
        self,
        verificacion: Verificacion,
        ruta_imagen: str,
        clave_idempotencia: str | None,
        huella_peticion: str,
    ) -> SolicitudRegistrada:
        if self.falla:
            raise RuntimeError("base caída")
        if clave_idempotencia and clave_idempotencia in self.por_clave:
            return replace(self.por_clave[clave_idempotencia], creada=False)
        registrada = SolicitudRegistrada(verificacion, True, huella_peticion)
        if clave_idempotencia:
            self.por_clave[clave_idempotencia] = registrada
        self.trabajos.append(ruta_imagen)
        return registrada


def test_solicitar_repetida_devuelve_la_misma_y_borra_la_copia() -> None:
    almacen, registro = _AlmacenEnMemoria(), _RegistroEnMemoria()
    caso = SolicitarVerificacion(registro, almacen, RelojFijo())
    solicitud = SolicitudVerificacion(COMERCIO.id, Decimal("5.00"), None)
    a = caso.ejecutar(solicitud, b"img", "clave-1")
    b = caso.ejecutar(solicitud, b"img", "clave-1")
    assert a.creada
    assert not b.creada
    assert b.verificacion.id == a.verificacion.id
    assert a.verificacion.estado is Estado.PROCESANDO
    assert list(almacen.imagenes) == registro.trabajos
    with pytest.raises(IdempotenciaConflictoError):
        caso.ejecutar(solicitud, b"otra", "clave-1")
    assert len(almacen.imagenes) == 1


def test_solicitar_borra_la_imagen_si_falla_la_transaccion() -> None:
    almacen = _AlmacenEnMemoria()
    caso = SolicitarVerificacion(_RegistroEnMemoria(falla=True), almacen, RelojFijo())
    with pytest.raises(RuntimeError):
        caso.ejecutar(SolicitudVerificacion(COMERCIO.id, None, None), b"img")
    assert almacen.imagenes == {}


def test_guardar_resultado_de_otro_comercio_no_toca_nada() -> None:
    repo = VerificacionesEnMemoria()
    v = repo.crear(nueva_verificacion(COMERCIO.id, uuid4()))
    datos = DatosExtraidos(None, None, None, None, None, None, None, 0.0, Fuente.OCR)
    repo.guardar_resultado(uuid4(), v.id, datos, Veredicto(Estado.RECHAZADO))
    assert repo.verificaciones[v.id].estado is Estado.PROCESANDO
