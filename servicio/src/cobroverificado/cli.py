from __future__ import annotations

import argparse
import secrets
import sys
from collections.abc import Sequence
from typing import Protocol

from cobroverificado.adaptadores.http.seguridad import sha256
from cobroverificado.config import Configuracion
from cobroverificado.dominio.modelos import Comercio

PREFIJO_CLAVE = "yc_"


class RegistroComercios(Protocol):
    def crear(
        self,
        nombre: str,
        destinatarios: tuple[str, ...],
        celulares: tuple[str, ...],
        hash_clave: str,
    ) -> Comercio: ...


def registro_postgres(config: Configuracion) -> RegistroComercios:
    from cobroverificado.composicion import registro_comercios

    return registro_comercios(config)


def nueva_clave() -> str:
    return PREFIJO_CLAVE + secrets.token_urlsafe(32)


def main(argv: Sequence[str] | None = None, registro: RegistroComercios | None = None) -> int:
    parser = argparse.ArgumentParser(prog="cobroverificado")
    sub = parser.add_subparsers(dest="comando", required=True)
    crear = sub.add_parser("crear-comercio", help="crea un comercio y muestra su clave API una vez")
    crear.add_argument("nombre")
    crear.add_argument(
        "--destinatario", action="append", default=[], help="nombre que muestra la billetera"
    )
    crear.add_argument("--celular", action="append", default=[])
    args = parser.parse_args(argv)

    registro = registro or registro_postgres(Configuracion())
    clave = nueva_clave()
    comercio = registro.crear(
        args.nombre, tuple(args.destinatario), tuple(args.celular), sha256(clave)
    )
    print(f"comercio: {comercio.id}")
    print(f"clave API (no se volverá a mostrar): {clave}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
