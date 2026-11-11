from __future__ import annotations

from collections.abc import Callable
from datetime import datetime
from decimal import Decimal
from uuid import UUID, uuid4

from cobroverificado.aplicacion.errores import PagoInvalidoError
from cobroverificado.dominio.modelos import Billetera, OrigenPago, PagoRecibido
from cobroverificado.puertos import Reloj, RepositorioPagos


class RegistrarPagoRecibido:
    def __init__(
        self,
        pagos: RepositorioPagos,
        reloj: Reloj,
        generar_id: Callable[[], UUID] = uuid4,
    ) -> None:
        self._pagos = pagos
        self._reloj = reloj
        self._generar_id = generar_id

    def ejecutar(
        self,
        comercio_id: UUID,
        billetera: Billetera,
        monto: Decimal,
        fecha_hora: datetime,
        huella: str,
        remitente: str | None = None,
        numero_operacion: str | None = None,
        origen: OrigenPago = OrigenPago.NOTIFICACION,
    ) -> PagoRecibido:
        if not monto.is_finite() or monto <= 0:
            raise PagoInvalidoError("el monto debe ser positivo")
        if fecha_hora.tzinfo is None:
            raise PagoInvalidoError("la fecha debe tener zona horaria")
        if not huella.strip():
            raise PagoInvalidoError("la huella es obligatoria")
        pago = PagoRecibido(
            id=self._generar_id(),
            comercio_id=comercio_id,
            billetera=billetera,
            monto=monto,
            fecha_hora=fecha_hora,
            numero_operacion=(numero_operacion or "").strip() or None,
            remitente=(remitente or "").strip() or None,
            huella=huella.strip(),
            origen=origen,
            recibido_en=self._reloj.ahora(),
        )
        return self._pagos.registrar(pago)
