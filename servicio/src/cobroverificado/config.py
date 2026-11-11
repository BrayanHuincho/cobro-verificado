from __future__ import annotations

from pathlib import Path

from pydantic import Field, SecretStr, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Configuracion(BaseSettings):
    model_config = SettingsConfigDict(
        env_prefix="COBROVERIFICADO_", env_file=".env", extra="ignore"
    )

    database_url: SecretStr = SecretStr("postgresql://localhost/cobroverificado")
    anthropic_api_key: SecretStr | None = None
    modelo_vision: str | None = None
    umbral_confianza_ocr: float = Field(default=0.85, ge=0, le=1)
    directorio_imagenes: Path = Path("imagenes")
    retencion_horas: int = Field(default=24, ge=1)
    log_level: str = "INFO"
    tamano_maximo_imagen: int = Field(default=8 * 1024 * 1024, ge=1)
    peticiones_por_minuto: int = Field(default=60, ge=1)
    clave_cifrado: SecretStr | None = None

    @field_validator("anthropic_api_key", "clave_cifrado", mode="before")
    @classmethod
    def vacio_es_ausente(cls, valor: object) -> object:
        # en .env una variable sin valor llega como texto vacío
        return None if isinstance(valor, str) and not valor.strip() else valor
