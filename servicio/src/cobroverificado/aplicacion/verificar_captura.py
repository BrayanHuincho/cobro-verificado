from __future__ import annotations

from datetime import timedelta
from uuid import UUID

from cobroverificado.aplicacion.errores import VerificacionNoEncontradaError
from cobroverificado.dominio.decision import decidir, en_lima
from cobroverificado.dominio.modelos import (
    Billetera,
    Comercio,
    DatosExtraidos,
    PagoRecibido,
    SolicitudVerificacion,
    Veredicto,
)
from cobroverificado.puertos import (
    AnalizadorImagen,
    Extractor,
    Reloj,
    RepositorioPagos,
    RepositorioVerificaciones,
)


class VerificarCaptura:
    def __init__(
        self,
        extractor: Extractor,
        analizador: AnalizadorImagen,
        pagos: RepositorioPagos,
        verificaciones: RepositorioVerificaciones,
        reloj: Reloj,
    ) -> None:
        self._extractor = extractor
        self._analizador = analizador
        self._pagos = pagos
        self._verificaciones = verificaciones
        self._reloj = reloj

    def ejecutar(self, comercio: Comercio, verificacion_id: UUID, imagen: bytes) -> Veredicto:
        verificacion = self._verificaciones.obtener(comercio.id, verificacion_id)
        if verificacion is None:
            raise VerificacionNoEncontradaError(str(verificacion_id))

        datos = self._extractor.extraer(imagen)
        senales = self._analizador.analizar(imagen, datos)
        solicitud = SolicitudVerificacion(
            comercio_id=comercio.id,
            monto_esperado=verificacion.monto_esperado,
            referencia=verificacion.referencia,
        )
        veredicto = decidir(
            datos,
            solicitud,
            self._candidatos(comercio, datos),
            senales,
            comercio,
            self._reservar(comercio, verificacion_id, datos),
            ahora=self._reloj.ahora(),
        )
        self._verificaciones.guardar_resultado(comercio.id, verificacion_id, datos, veredicto)
        return veredicto

    def _reservar(self, comercio: Comercio, verificacion_id: UUID, datos: DatosExtraidos) -> bool:
        legible = datos.monto is not None and datos.fecha_hora is not None
        if not legible or datos.billetera is None or not datos.numero_operacion:
            return True
        return self._verificaciones.reservar_operacion(
            comercio.id, datos.billetera, datos.numero_operacion.strip(), verificacion_id
        )

    def _candidatos(self, comercio: Comercio, datos: DatosExtraidos) -> list[PagoRecibido]:
        if datos.monto is None or datos.fecha_hora is None:
            return []
        fecha = en_lima(datos.fecha_hora)
        tolerancia = timedelta(minutes=comercio.tolerancia_minutos)
        billeteras = [datos.billetera] if datos.billetera else list(Billetera)
        return [
            pago
            for billetera in billeteras
            for pago in self._pagos.candidatos(
                comercio.id, billetera, datos.monto, fecha - tolerancia, fecha + tolerancia
            )
        ]
