from __future__ import annotations

from uuid import uuid4

import pytest

from cobroverificado.adaptadores.cifrado import CifradorSecretos, SecretoIlegibleError
from cobroverificado.adaptadores.postgres import Pool, RepositorioComerciosPg
from cobroverificado.dominio.modelos import Comercio


def test_cifrado_ida_y_vuelta_ligado_al_comercio() -> None:
    cifrador = CifradorSecretos("22" * 32)
    dueno, otro = uuid4(), uuid4()
    texto = cifrador.cifrar("whsec_abc", dueno)
    assert "whsec_abc" not in texto
    assert cifrador.descifrar(texto, dueno) == "whsec_abc"
    with pytest.raises(SecretoIlegibleError):
        cifrador.descifrar(texto, otro)
    with pytest.raises(SecretoIlegibleError):
        CifradorSecretos("33" * 32).descifrar(texto, dueno)


@pytest.mark.parametrize("clave", ["ab" * 16, "zz" * 32])
def test_clave_de_cifrado_invalida(clave: str) -> None:
    with pytest.raises(ValueError, match="clave de cifrado"):
        CifradorSecretos(clave)


def test_configurar_webhook_conserva_secreto(pool: Pool) -> None:
    repo = RepositorioComerciosPg(pool)
    comercio = repo.crear(Comercio(id=uuid4(), nombre="Sin hook", destinatarios=(), celulares=()))
    assert repo.secreto_cifrado(comercio.id) is None
    assert repo.configurar_webhook(comercio.id, "https://a.example/h", "cifrado-1")
    assert repo.configurar_webhook(comercio.id, "https://b.example/h", None)
    leido = repo.obtener(comercio.id)
    assert leido is not None
    assert leido.url_webhook == "https://b.example/h"
    assert repo.secreto_cifrado(comercio.id) == "cifrado-1"
    assert repo.reemplazar_secreto(comercio.id, "cifrado-2")
    assert repo.secreto_cifrado(comercio.id) == "cifrado-2"
