from __future__ import annotations

from dataclasses import replace
from datetime import datetime, timedelta
from decimal import Decimal
from uuid import UUID, uuid4

from hypothesis import given
from hypothesis import strategies as st

from cobroverificado.dominio.decision import ZONA_LIMA, decidir, pagos_coincidentes
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
    SolicitudVerificacion,
    Veredicto,
)

AHORA = datetime(2026, 11, 9, 15, 0, tzinfo=ZONA_LIMA)
COMERCIO = Comercio(
    id=UUID("00000000-0000-0000-0000-000000000001"),
    nombre="Bodega",
    destinatarios=("Juan Perez Gomez",),
    celulares=("987654321",),
)


def datos(**cambios: object) -> DatosExtraidos:
    base = DatosExtraidos(
        billetera=Billetera.YAPE,
        monto=Decimal("50.00"),
        fecha_hora=AHORA - timedelta(minutes=5),
        numero_operacion="12345678",
        destinatario="Juan P.",
        remitente="Ana Ruiz",
        celular_destino="*** *** 321",
        confianza=0.95,
        fuente=Fuente.OCR,
    )
    return replace(base, **cambios)  # type: ignore[arg-type]


def pago(**cambios: object) -> PagoRecibido:
    base = PagoRecibido(
        id=uuid4(),
        comercio_id=COMERCIO.id,
        billetera=Billetera.YAPE,
        monto=Decimal("50.00"),
        fecha_hora=AHORA - timedelta(minutes=4),
        numero_operacion="12345678",
        remitente="Ana Ruiz",
        huella="h",
        origen=OrigenPago.NOTIFICACION,
        recibido_en=AHORA,
    )
    return replace(base, **cambios)  # type: ignore[arg-type]


def solicitud(monto: Decimal | None = Decimal("50.00")) -> SolicitudVerificacion:
    return SolicitudVerificacion(comercio_id=COMERCIO.id, monto_esperado=monto, referencia=None)


def ejecutar(
    d: DatosExtraidos | None = None,
    *,
    s: SolicitudVerificacion | None = None,
    candidatos: list[PagoRecibido] | None = None,
    senales: list[Senal] | None = None,
    comercio: Comercio = COMERCIO,
    operacion_libre: bool = True,
) -> Veredicto:
    return decidir(
        d or datos(),
        s or solicitud(),
        candidatos if candidatos is not None else [pago()],
        senales or [],
        comercio,
        operacion_libre,
        ahora=AHORA,
    )


def codigos(v: Veredicto) -> set[str]:
    return {s.codigo for s in v.senales}


def test_confirma_con_un_pago_coincidente() -> None:
    p = pago()
    v = ejecutar(candidatos=[p])
    assert v.estado is Estado.CONFIRMADO
    assert v.pago_id == p.id
    assert not v.requiere_revision


def test_sin_monto_es_ilegible() -> None:
    v = ejecutar(datos(monto=None))
    assert v.estado is Estado.RECHAZADO
    assert codigos(v) == {"imagen_ilegible"}


def test_sin_fecha_es_ilegible() -> None:
    assert ejecutar(datos(fecha_hora=None)).estado is Estado.RECHAZADO


def test_ilegible_conserva_senales_del_analizador() -> None:
    previa = Senal("metadatos_editor", Severidad.ADVERTENCIA, "x")
    v = ejecutar(datos(monto=None), senales=[previa])
    assert previa in v.senales


def test_operacion_repetida_rechaza() -> None:
    v = ejecutar(operacion_libre=False)
    assert v.estado is Estado.RECHAZADO
    assert "operacion_repetida" in codigos(v)


def test_monto_distinto_rechaza() -> None:
    v = ejecutar(s=solicitud(Decimal("60.00")))
    assert v.estado is Estado.RECHAZADO
    assert "monto_distinto" in codigos(v)


def test_monto_igual_con_otra_escala_no_rechaza() -> None:
    assert ejecutar(s=solicitud(Decimal("50"))).estado is Estado.CONFIRMADO


def test_sin_monto_esperado_no_compara() -> None:
    assert ejecutar(s=solicitud(None)).estado is Estado.CONFIRMADO


def test_rechazo_gana_sobre_sospecha() -> None:
    v = ejecutar(datos(destinatario="Pedro Lopez"), operacion_libre=False)
    assert v.estado is Estado.RECHAZADO


def test_destinatario_ajeno_es_sospechoso() -> None:
    v = ejecutar(datos(destinatario="Pedro Lopez"))
    assert v.estado is Estado.SOSPECHOSO
    assert "destinatario_ajeno" in codigos(v)
    assert v.pago_id is None


def test_destinatario_con_tildes_y_recortado_es_valido() -> None:
    assert ejecutar(datos(destinatario="JUAN PÉREZ G.")).estado is Estado.CONFIRMADO


def test_destinatario_no_legible_solo_advierte() -> None:
    v = ejecutar(datos(destinatario=None))
    assert v.estado is Estado.CONFIRMADO
    assert "destinatario_no_legible" in codigos(v)


def test_comercio_sin_destinatarios_no_valida_nombre() -> None:
    comercio = replace(COMERCIO, destinatarios=())
    v = ejecutar(datos(destinatario="Cualquiera"), comercio=comercio)
    assert v.estado is Estado.CONFIRMADO


def test_celular_ajeno_es_sospechoso() -> None:
    v = ejecutar(datos(celular_destino="*** *** 999"))
    assert v.estado is Estado.SOSPECHOSO
    assert "celular_ajeno" in codigos(v)


def test_fecha_futura_es_sospechosa() -> None:
    v = ejecutar(datos(fecha_hora=AHORA + timedelta(minutes=10)))
    assert v.estado is Estado.SOSPECHOSO
    assert "fecha_futura" in codigos(v)


def test_fecha_un_minuto_adelantada_se_tolera() -> None:
    d = datos(fecha_hora=AHORA + timedelta(minutes=1))
    assert ejecutar(d, candidatos=[pago(fecha_hora=AHORA)]).estado is Estado.CONFIRMADO


def test_fecha_antigua_es_sospechosa() -> None:
    v = ejecutar(datos(fecha_hora=AHORA - timedelta(minutes=1441)))
    assert v.estado is Estado.SOSPECHOSO
    assert "fecha_antigua" in codigos(v)


def test_fecha_sin_zona_se_interpreta_en_lima() -> None:
    d = datos(fecha_hora=(AHORA - timedelta(minutes=5)).replace(tzinfo=None))
    assert ejecutar(d).estado is Estado.CONFIRMADO


def test_senal_critica_del_analizador_es_sospechosa() -> None:
    critica = Senal("edicion_detectada", Severidad.CRITICA, "x")
    v = ejecutar(senales=[critica])
    assert v.estado is Estado.SOSPECHOSO
    assert critica in v.senales


def test_senal_advertencia_no_bloquea() -> None:
    v = ejecutar(senales=[Senal("compresion", Severidad.ADVERTENCIA, "x")])
    assert v.estado is Estado.CONFIRMADO


def test_sin_candidatos_queda_pendiente() -> None:
    v = ejecutar(candidatos=[])
    assert v.estado is Estado.PENDIENTE
    assert "sin_pago_recibido" in codigos(v)


def test_dos_candidatos_es_ambiguo() -> None:
    v = ejecutar(d=datos(numero_operacion=None), candidatos=[pago(), pago()])
    assert v.estado is Estado.PENDIENTE
    assert "pago_ambiguo" in codigos(v)
    assert "sin_numero_operacion" in codigos(v)


def test_numero_operacion_desambigua() -> None:
    correcto = pago()
    v = ejecutar(candidatos=[pago(numero_operacion="999"), correcto])
    assert v.estado is Estado.CONFIRMADO
    assert v.pago_id == correcto.id


def test_candidato_sin_numero_operacion_vale() -> None:
    assert ejecutar(candidatos=[pago(numero_operacion=None)]).estado is Estado.CONFIRMADO


def test_candidato_fuera_de_tolerancia_no_cuenta() -> None:
    v = ejecutar(candidatos=[pago(fecha_hora=AHORA - timedelta(minutes=30))])
    assert v.estado is Estado.PENDIENTE


def test_candidato_de_otra_billetera_no_cuenta() -> None:
    assert ejecutar(candidatos=[pago(billetera=Billetera.PLIN)]).estado is Estado.PENDIENTE


def test_candidato_de_otro_monto_no_cuenta() -> None:
    assert ejecutar(candidatos=[pago(monto=Decimal("50.01"))]).estado is Estado.PENDIENTE


def test_candidato_de_otro_comercio_no_cuenta() -> None:
    assert ejecutar(candidatos=[pago(comercio_id=uuid4())]).estado is Estado.PENDIENTE


def test_billetera_desconocida_acepta_cualquier_candidato() -> None:
    v = ejecutar(datos(billetera=None), candidatos=[pago(billetera=Billetera.PLIN)])
    assert v.estado is Estado.CONFIRMADO


def test_umbral_alcanzado_requiere_revision() -> None:
    comercio = replace(COMERCIO, umbral_revision_humana=Decimal("50.00"))
    v = ejecutar(comercio=comercio)
    assert v.estado is Estado.CONFIRMADO
    assert v.requiere_revision
    assert "requiere_revision" in codigos(v)


def test_umbral_no_alcanzado_no_requiere_revision() -> None:
    comercio = replace(COMERCIO, umbral_revision_humana=Decimal("50.01"))
    assert not ejecutar(comercio=comercio).requiere_revision


def test_umbral_no_afecta_pendientes() -> None:
    comercio = replace(COMERCIO, umbral_revision_humana=Decimal("1"))
    assert not ejecutar(comercio=comercio, candidatos=[]).requiere_revision


def test_pagos_coincidentes_sin_monto_devuelve_vacio() -> None:
    assert pagos_coincidentes(datos(monto=None), [pago()], COMERCIO) == []


def test_codigos_y_mensajes_siempre_presentes() -> None:
    v = ejecutar(
        datos(destinatario="Otro Nombre", celular_destino="111", numero_operacion=None),
        operacion_libre=False,
        s=solicitud(Decimal("1")),
    )
    assert all(s.codigo and s.mensaje for s in v.senales)


montos = st.decimals(min_value=Decimal("0.01"), max_value=Decimal("99999"), places=2)
desfases = st.integers(min_value=-3000, max_value=3000)


@given(montos, desfases, st.booleans(), st.booleans())
def test_propiedades_del_veredicto(
    monto: Decimal, minutos: int, libre: bool, con_pago: bool
) -> None:
    fecha = AHORA + timedelta(minutes=minutos)
    d = datos(monto=monto, fecha_hora=fecha)
    candidatos = [pago(monto=monto, fecha_hora=fecha)] if con_pago else []
    v = ejecutar(d, s=solicitud(monto), candidatos=candidatos, operacion_libre=libre)

    if not libre:
        assert v.estado is Estado.RECHAZADO
    elif minutos > 2 or minutos < -COMERCIO.ventana_minutos:
        assert v.estado is Estado.SOSPECHOSO
    elif con_pago:
        assert v.estado is Estado.CONFIRMADO
    else:
        assert v.estado is Estado.PENDIENTE
    assert (v.pago_id is not None) == (v.estado is Estado.CONFIRMADO)


@given(montos, montos)
def test_monto_distinto_siempre_rechaza(capturado: Decimal, esperado: Decimal) -> None:
    v = ejecutar(datos(monto=capturado), s=solicitud(esperado), candidatos=[])
    assert (v.estado is Estado.RECHAZADO) == (capturado != esperado)


@given(st.integers(min_value=-10, max_value=10))
def test_tolerancia_es_inclusiva(minutos: int) -> None:
    d = datos()
    assert d.fecha_hora is not None
    p = pago(fecha_hora=d.fecha_hora + timedelta(minutes=minutos))
    assert ejecutar(d, candidatos=[p]).estado is Estado.CONFIRMADO
