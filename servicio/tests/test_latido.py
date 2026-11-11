import time
from pathlib import Path

from cobroverificado.trabajador import latido_reciente, latir


def test_latir_escribe_marca_actual(tmp_path: Path) -> None:
    archivo = tmp_path / "latido"
    latir(archivo)
    assert abs(float(archivo.read_text(encoding="ascii")) - time.time()) < 5
    assert latido_reciente(archivo)


def test_latido_viejo_ausente_o_corrupto_no_es_reciente(tmp_path: Path) -> None:
    archivo = tmp_path / "latido"
    assert not latido_reciente(archivo)
    archivo.write_text(f"{time.time() - 61:.0f}", encoding="ascii")
    assert not latido_reciente(archivo)
    archivo.write_text("basura", encoding="ascii")
    assert not latido_reciente(archivo)


def test_latir_en_ruta_invalida_no_falla(tmp_path: Path) -> None:
    latir(tmp_path / "no-existe" / "latido")
