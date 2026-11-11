from __future__ import annotations


class AplicacionError(Exception):
    codigo = "error_aplicacion"


class VerificacionNoEncontradaError(AplicacionError):
    codigo = "verificacion_no_encontrada"


class VerificacionNoRevisableError(AplicacionError):
    codigo = "verificacion_no_revisable"


class PagoInvalidoError(AplicacionError):
    codigo = "pago_invalido"


class IdempotenciaConflictoError(AplicacionError):
    codigo = "idempotencia_conflicto"
