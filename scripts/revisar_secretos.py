import re
import subprocess
import sys
from pathlib import Path

PATRONES = {
    "clave de Anthropic": re.compile(r"sk-ant-[A-Za-z0-9_-]{20,}"),
    "clave de AWS": re.compile(r"AKIA[0-9A-Z]{16}"),
    "token de GitHub": re.compile(r"gh[pousr]_[A-Za-z0-9]{36,}"),
    "clave privada": re.compile(r"-----BEGIN [A-Z ]*PRIVATE KEY-----"),
    "contraseña en una URL": re.compile(
        r"postgres(?:ql)?://[^:\s/]+:(?!cambia-esta-clave@|prueba@)[^@\s$]+@"
    ),
}
OMITIDOS = {".git", ".venv", "node_modules", "build", ".gradle"}
BINARIOS = {".png", ".jpg", ".jpeg", ".webp", ".jar", ".lock", ".ico", ".onnx"}


def archivos(raiz: Path) -> list[Path]:
    try:
        salida = subprocess.run(
            ["git", "ls-files"],  # noqa: S607
            cwd=raiz,
            capture_output=True,
            text=True,
            check=True,
        ).stdout
        return [raiz / linea for linea in salida.splitlines()]
    except (OSError, subprocess.CalledProcessError):
        return [
            p
            for p in raiz.rglob("*")
            if p.is_file() and not OMITIDOS.intersection(p.relative_to(raiz).parts)
        ]


def main() -> int:
    raiz = Path(__file__).resolve().parent.parent
    hallazgos = 0
    for ruta in archivos(raiz):
        if not ruta.is_file() or ruta.suffix.lower() in BINARIOS or ruta.name == ".env":
            continue
        texto = ruta.read_text(encoding="utf-8", errors="ignore")
        for numero, linea in enumerate(texto.splitlines(), start=1):
            for nombre, patron in PATRONES.items():
                if patron.search(linea):
                    print(f"{ruta.relative_to(raiz)}:{numero}: posible {nombre}")
                    hallazgos += 1
    return 1 if hallazgos else 0


if __name__ == "__main__":
    sys.exit(main())
