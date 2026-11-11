from __future__ import annotations

import re
import unicodedata
from collections.abc import Callable
from dataclasses import dataclass
from datetime import datetime
from decimal import Decimal, InvalidOperation
from zoneinfo import ZoneInfo

from cobroverificado.dominio.modelos import Billetera, DatosExtraidos, Fuente

from .patrones import PATRONES_POR_DEFECTO, Patrones

LIMA = ZoneInfo("America/Lima")

MESES = {
    "ene": 1, "enero": 1, "feb": 2, "febrero": 2, "mar": 3, "marzo": 3,
    "abr": 4, "abril": 4, "may": 5, "mayo": 5, "jun": 6, "junio": 6,
    "jul": 7, "julio": 7, "ago": 8, "agosto": 8, "set": 9, "sep": 9,
    "sept": 9, "setiembre": 9, "septiembre": 9, "oct": 10, "octubre": 10,
    "nov": 11, "noviembre": 11, "dic": 12, "diciembre": 12,
}  # fmt: skip

PESOS = {
    "billetera": 0.1,
    "monto": 0.3,
    "fecha_hora": 0.25,
    "numero_operacion": 0.2,
    "destinatario": 0.15,
}

_FECHA_NUMERICA = re.compile(r"\b(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{4}|\d{2})\b")
_FECHA_TEXTO = re.compile(
    r"\b(\d{1,2})\s*(?:de\s+)?([a-z][a-z0]{2,9})\.?\s*(?:de\s+|del\s+)?(\d{4})\b"
)
_HORA = re.compile(r"\b(\d{1,2}):(\d{2})(?::(\d{2}))?\s*(a\.?\s*m\.?|p\.?\s*m\.?)?(?![\w])")
_NUMERO = re.compile(r"\d[\d.,\s]*\d|\d")
_ENMASCARADO = re.compile(r"(?:\*[\s*]*)+(\d{3,4})\b")
_CERO_EN_PALABRA = re.compile(r"(?<=[a-z])0|0(?=[a-z]{2})")
_NOMBRE = re.compile(r"^[a-zñ][a-zñ.\s]{2,}$")

Caja = tuple[float, float, float, float]


@dataclass(frozen=True, slots=True)
class Linea:
    texto: str
    caja: Caja
    puntaje: float = 1.0

    @property
    def alto(self) -> float:
        return self.caja[3] - self.caja[1]

    @property
    def centro_y(self) -> float:
        return (self.caja[1] + self.caja[3]) / 2


def normalizar(texto: str) -> str:
    sin_tildes = unicodedata.normalize("NFKD", texto)
    plano = "".join(c for c in sin_tildes if not unicodedata.combining(c) or c == "̃")
    return unicodedata.normalize("NFC", plano).lower().replace("¡", "").replace("!", "").strip()


def parsear_monto(texto: str) -> Decimal | None:
    coincidencia = _NUMERO.search(texto)
    if coincidencia is None:
        return None
    crudo = re.sub(r"\s", "", coincidencia.group())
    punto, coma = crudo.rfind("."), crudo.rfind(",")
    if punto >= 0 and coma >= 0:
        decimal_sep = "." if punto > coma else ","
    elif punto >= 0 or coma >= 0:
        sep = "." if punto >= 0 else ","
        partes = crudo.split(sep)
        decimal_sep = sep if len(partes) == 2 and len(partes[1]) in (1, 2) else ""
    else:
        decimal_sep = ""
    miles = {".", ","} - {decimal_sep}
    limpio = "".join(c for c in crudo if c not in miles).replace(",", ".")
    try:
        valor = Decimal(limpio)
    except InvalidOperation:
        return None
    return valor.quantize(Decimal("0.01")) if valor > 0 else None


def parsear_fecha_hora(texto: str) -> datetime | None:
    plano = normalizar(texto)
    fecha = _buscar_fecha(plano)
    hora = _HORA.search(plano)
    if fecha is None or hora is None:
        return None
    anio, mes, dia = fecha
    h, m, s = int(hora.group(1)), int(hora.group(2)), int(hora.group(3) or 0)
    meridiano = (hora.group(4) or "").replace(".", "").replace(" ", "")
    if meridiano == "pm" and h < 12:
        h += 12
    elif meridiano == "am" and h == 12:
        h = 0
    try:
        return datetime(anio, mes, dia, h, m, s, tzinfo=LIMA)
    except ValueError:
        return None


def _buscar_fecha(plano: str) -> tuple[int, int, int] | None:
    for c in _FECHA_TEXTO.finditer(plano):
        mes = MESES.get(c.group(2).replace("0", "o"))
        if mes is not None:
            return int(c.group(3)), mes, int(c.group(1))
    numerica = _FECHA_NUMERICA.search(plano)
    if numerica is None:
        return None
    anio = int(numerica.group(3))
    return (anio + 2000 if anio < 100 else anio), int(numerica.group(2)), int(numerica.group(1))


def leer_comprobante(
    lineas: list[Linea], patrones: Patrones = PATRONES_POR_DEFECTO
) -> DatosExtraidos:
    ordenadas = sorted(lineas, key=lambda linea: (linea.caja[1], linea.caja[0]))
    usadas: list[Linea] = []

    billetera = _billetera(ordenadas, patrones)
    monto, linea_monto = _monto(ordenadas, patrones)
    if linea_monto is not None:
        usadas.append(linea_monto)

    fecha_hora = _fecha(ordenadas, patrones, usadas)
    operacion = _valor(ordenadas, patrones.etiquetas_operacion, usadas, _parece_operacion)
    numero_operacion = re.sub(r"\D", "", operacion) if operacion else None

    marcas = {p for _, palabras in patrones.marcas for p in palabras}
    destinatario = _valor(
        ordenadas, patrones.etiquetas_destinatario, usadas, lambda v: normalizar(v) not in marcas
    )
    if destinatario is None and linea_monto is not None:
        destinatario = _nombre_tras(ordenadas, linea_monto, patrones)
    remitente = _valor(ordenadas, patrones.etiquetas_remitente, usadas)
    celular = _celular(ordenadas, patrones, usadas)

    encontrados = {
        "billetera": billetera, "monto": monto, "fecha_hora": fecha_hora,
        "numero_operacion": numero_operacion, "destinatario": destinatario,
    }  # fmt: skip
    cobertura = sum(peso for campo, peso in PESOS.items() if encontrados[campo] is not None)
    base = usadas or ordenadas
    calidad = sum(linea.puntaje for linea in base) / len(base) if base else 0.0

    return DatosExtraidos(
        billetera=billetera,
        monto=monto,
        fecha_hora=fecha_hora,
        numero_operacion=numero_operacion,
        destinatario=_limpiar_nombre(destinatario),
        remitente=_limpiar_nombre(remitente),
        celular_destino=celular,
        confianza=round(cobertura * calidad, 4),
        fuente=Fuente.OCR,
    )


def _billetera(lineas: list[Linea], patrones: Patrones) -> Billetera | None:
    texto = " ".join(normalizar(linea.texto) for linea in lineas)
    for billetera, palabras in patrones.marcas:
        if any(re.search(rf"\b{re.escape(p)}\b", texto) for p in palabras):
            return billetera
    return None


def _monto(lineas: list[Linea], patrones: Patrones) -> tuple[Decimal | None, Linea | None]:
    simbolo = "|".join(
        re.escape(s).replace(r"\ ", r"\s*")
        for s in sorted(patrones.simbolos_moneda, key=len, reverse=True)
    )
    con_simbolo = re.compile(rf"(?:{simbolo})\s*(\d[\d.,\s]*)?")
    candidatos: list[tuple[Decimal, Linea]] = []
    for i, linea in enumerate(lineas):
        c = con_simbolo.search(normalizar(linea.texto))
        if c is None:
            continue
        if c.group(1):
            valor = parsear_monto(c.group(1))
            if valor is not None:
                candidatos.append((valor, linea))
            continue
        vecina = _derecha(lineas, linea) or (lineas[i + 1] if i + 1 < len(lineas) else None)
        if vecina is not None and re.fullmatch(r"[\d.,\s]+", vecina.texto.strip()):
            valor = parsear_monto(vecina.texto)
            if valor is not None:
                candidatos.append((valor, vecina))
    if not candidatos:
        return None, None
    # el monto principal se muestra con la letra más grande del comprobante
    valor, linea = max(candidatos, key=lambda par: par[1].alto)
    return valor, linea


def _fecha(lineas: list[Linea], patrones: Patrones, usadas: list[Linea]) -> datetime | None:
    for linea in lineas:
        resultado = parsear_fecha_hora(linea.texto)
        if resultado is not None:
            usadas.append(linea)
            return resultado
    etiquetada = _valor(lineas, patrones.etiquetas_fecha, [])
    texto = (
        etiquetada
        if etiquetada and parsear_fecha_hora(etiquetada)
        else " ".join(linea.texto for linea in lineas)
    )
    return parsear_fecha_hora(texto)


def _separar_etiqueta(texto: str, etiquetas: tuple[str, ...]) -> str | None:
    plano = _CERO_EN_PALABRA.sub("o", normalizar(texto))
    for etiqueta in sorted(etiquetas, key=len, reverse=True):
        if not plano.startswith(etiqueta):
            continue
        resto = plano[len(etiqueta) :]
        if resto.strip() == "":
            return ""
        if resto.lstrip().startswith(":"):
            return texto.split(":", 1)[1].strip()
        if len(etiqueta) >= 5 and resto.startswith(" "):
            return " ".join(texto.split()[len(etiqueta.split()) :])
    return None


def _valor(
    lineas: list[Linea],
    etiquetas: tuple[str, ...],
    usadas: list[Linea],
    valido: Callable[[str], bool] = bool,
) -> str | None:
    for i, linea in enumerate(lineas):
        resto = _separar_etiqueta(linea.texto, etiquetas)
        if resto is None:
            continue
        if resto:
            if valido(resto):
                usadas.append(linea)
                return resto
            continue
        vecina = _derecha(lineas, linea) or _debajo(lineas, i)
        if vecina is not None and valido(vecina.texto.strip()):
            usadas.append(vecina)
            return vecina.texto.strip()
    return None


def _parece_operacion(valor: str) -> bool:
    return len(re.sub(r"\D", "", valor)) >= 4 and not re.search(r"[a-z]{3,}", normalizar(valor))


def _tiene_digitos(valor: str) -> bool:
    return len(re.sub(r"\D", "", valor)) >= 3


def _derecha(lineas: list[Linea], ancla: Linea) -> Linea | None:
    misma_fila = [
        linea
        for linea in lineas
        if linea is not ancla
        and linea.caja[0] >= ancla.caja[2] - 2
        and abs(linea.centro_y - ancla.centro_y) < max(linea.alto, ancla.alto) / 2
    ]
    return min(misma_fila, key=lambda linea: linea.caja[0], default=None)


def _debajo(lineas: list[Linea], indice: int) -> Linea | None:
    ancla = lineas[indice]
    for linea in lineas[indice + 1 :]:
        if linea.caja[1] >= ancla.centro_y and linea.caja[1] - ancla.caja[3] < 2.5 * ancla.alto:
            return linea
    return None


def _nombre_tras(lineas: list[Linea], ancla: Linea, patrones: Patrones) -> str | None:
    marcas = {p for _, palabras in patrones.marcas for p in palabras}
    for linea in lineas:
        if linea.caja[1] < ancla.caja[3] - 1 or linea is ancla:
            continue
        plano = normalizar(linea.texto)
        if (
            _NOMBRE.match(plano)
            and not any(m in plano for m in marcas)
            and not _FECHA_TEXTO.search(plano)
        ):
            return linea.texto.strip()
    return None


def _celular(lineas: list[Linea], patrones: Patrones, usadas: list[Linea]) -> str | None:
    for linea in lineas:
        c = _ENMASCARADO.search(linea.texto)
        if c is not None:
            return c.group(1)
    valor = _valor(lineas, patrones.etiquetas_celular, usadas, _tiene_digitos)
    digitos = re.sub(r"\D", "", valor or "")
    return digitos[-9:] if len(digitos) >= 3 else None


def _limpiar_nombre(valor: str | None) -> str | None:
    if valor is None:
        return None
    limpio = re.sub(r"\s+", " ", valor).strip(" .:-")
    return limpio or None
