from __future__ import annotations

from enum import StrEnum
from uuid import UUID

from cobroverificado.aplicacion.errores import (
    VerificacionNoEncontradaError,
    VerificacionNoRevisableError,
)
from cobroverificado.dominio.modelos import Estado, Senal, Severidad, Veredicto
from cobroverificado.puertos import RepositorioVerificaciones


class DecisionRevision(StrEnum):
    APROBAR = "aprobar"
    RECHAZAR = "rechazar"


class RevisarVerificacion:
    def __init__(self, verificaciones: RepositorioVerificaciones) -> None:
        self._verificaciones = verificaciones

    def ejecutar(
        self,
        comercio_id: UUID,
        verificacion_id: UUID,
        decision: DecisionRevision,
        nota: str | None = None,
    ) -> Veredicto:
        verificacion = self._verificaciones.obtener(comercio_id, verificacion_id)
        if verificacion is None:
            raise VerificacionNoEncontradaError(str(verificacion_id))
        if verificacion.revisada:
            raise VerificacionNoRevisableError("la verificación ya fue revisada")
        if verificacion.datos is None or verificacion.veredicto is None:
            raise VerificacionNoRevisableError("la verificación aún se está procesando")

        anterior = verificacion.veredicto
        aprobada = decision is DecisionRevision.APROBAR
        detalle = f": {nota.strip()}" if nota and nota.strip() else ""
        senal = Senal(
            codigo="revision_aprobada" if aprobada else "revision_rechazada",
            severidad=Severidad.INFO,
            mensaje=(
                f"Aprobada por revisión humana{detalle}"
                if aprobada
                else f"Rechazada por revisión humana{detalle}"
            ),
        )
        veredicto = Veredicto(
            estado=Estado.CONFIRMADO if aprobada else Estado.RECHAZADO,
            senales=(*anterior.senales, senal),
            pago_id=anterior.pago_id,
            requiere_revision=False,
        )
        nota_limpia = nota.strip() if nota and nota.strip() else None
        self._verificaciones.registrar_revision(
            comercio_id, verificacion_id, veredicto, nota_limpia
        )
        return veredicto
