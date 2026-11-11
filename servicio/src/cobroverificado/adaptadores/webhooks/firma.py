from __future__ import annotations

import hashlib
import hmac


def firmar(secreto: str, cuerpo: bytes, marca_tiempo: int) -> str:
    mensaje = str(marca_tiempo).encode("ascii") + b"." + cuerpo
    digesto = hmac.new(secreto.encode("utf-8"), mensaje, hashlib.sha256).hexdigest()
    return f"t={marca_tiempo},v1={digesto}"


def verificar(
    secreto: str, cuerpo: bytes, cabecera: str, ahora: int, tolerancia_segundos: int = 300
) -> bool:
    partes = dict(p.split("=", 1) for p in cabecera.split(",") if "=" in p)
    try:
        marca = int(partes["t"])
    except (KeyError, ValueError):
        return False
    if abs(ahora - marca) > tolerancia_segundos:
        return False
    esperada = firmar(secreto, cuerpo, marca).split("v1=", 1)[1]
    return hmac.compare_digest(esperada, partes.get("v1", ""))
