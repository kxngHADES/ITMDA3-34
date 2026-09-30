from functools import lru_cache
from typing import Literal

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    APP_NAME: str = "ITDMA3-44 API"
    ENVIRONMENT: Literal["dev", "prod"] = "prod"

    REDIS_URL: str = "redis://localhost:6379/0"

    @property
    def is_dev(self) -> bool:
        return self.ENVIRONMENT == "dev"


@lru_cache
def get_settings() -> Settings:
    return Settings()


settings = get_settings()
