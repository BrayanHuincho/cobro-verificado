from __future__ import annotations

import base64
import os
from uuid import UUID

from cryptography.exceptions import InvalidTag
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

_NONCE = 12


class SecretoIlegibleError(Exception):
    pass


class CifradorSecretos:
    # el id del comercio va como dato asociado: un secreto copiado a otra fila no descifra
    def __init__(self, clave_hex: str) -> None:
        try:
            clave = bytes.fromhex(clave_hex)
        except ValueError as error:
            raise ValueError("la clave de cifrado debe estar en hexadecimal") from error
        if len(clave) != 32:
            raise ValueError("la clave de cifrado debe tener 32 bytes")
        self._aes = AESGCM(clave)

    def cifrar(self, secreto: str, comercio_id: UUID) -> str:
        nonce = os.urandom(_NONCE)
        cifrado = self._aes.encrypt(nonce, secreto.encode("utf-8"), comercio_id.bytes)
        return base64.urlsafe_b64encode(nonce + cifrado).decode("ascii")

    def descifrar(self, texto: str, comercio_id: UUID) -> str:
        try:
            crudo = base64.urlsafe_b64decode(texto.encode("ascii"))
            plano = self._aes.decrypt(crudo[:_NONCE], crudo[_NONCE:], comercio_id.bytes)
        except (InvalidTag, ValueError) as error:
            raise SecretoIlegibleError("no se pudo descifrar el secreto del webhook") from error
        return plano.decode("utf-8")
