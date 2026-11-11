from __future__ import annotations

import hashlib
import threading
import time
from collections.abc import Callable


def sha256(texto: str) -> str:
    return hashlib.sha256(texto.encode()).hexdigest()


class LimitadorPeticiones:
    # cubeta de fichas en memoria por proceso; con varias réplicas el límite es por réplica
    def __init__(self, por_minuto: int, reloj: Callable[[], float] = time.monotonic) -> None:
        self._capacidad = float(por_minuto)
        self._ritmo = por_minuto / 60.0
        self._reloj = reloj
        self._cubetas: dict[str, tuple[float, float]] = {}
        self._candado = threading.Lock()

    def permitir(self, clave: str) -> tuple[bool, int]:
        with self._candado:
            ahora = self._reloj()
            fichas, ultimo = self._cubetas.get(clave, (self._capacidad, ahora))
            fichas = min(self._capacidad, fichas + (ahora - ultimo) * self._ritmo)
            if fichas < 1:
                self._cubetas[clave] = (fichas, ahora)
                return False, max(1, int((1 - fichas) / self._ritmo) + 1)
            self._cubetas[clave] = (fichas - 1, ahora)
            return True, 0


FIRMAS_IMAGEN: tuple[tuple[str, Callable[[bytes], bool]], ...] = (
    ("image/png", lambda b: b.startswith(b"\x89PNG\r\n\x1a\n")),
    ("image/jpeg", lambda b: b.startswith(b"\xff\xd8\xff")),
    ("image/webp", lambda b: len(b) >= 12 and b[:4] == b"RIFF" and b[8:12] == b"WEBP"),
)


def tipo_imagen(contenido: bytes) -> str | None:
    return next((tipo for tipo, coincide in FIRMAS_IMAGEN if coincide(contenido)), None)
