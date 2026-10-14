from functools import lru_cache
from typing import Literal

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    APP_NAME: str = "ITDMA3-44 API"
    APP_VERSION: str = "1.0.0"
    ENVIRONMENT: Literal["dev", "prod"] = "prod"

    REDIS_URL: str = "redis://localhost:6379/0"

    FIREBASE_PROJECT_ID: str = ""
    FIREBASE_WEB_API_KEY: str = ""
    FIREBASE_SERVICE_ACCOUNT_PATH: str = "./secrets/firebase-service-account.json"

    JWT_SECRET_KEY: str = ""
    JWT_ALGORITHM: str = "HS256"
    JWT_ACCESS_TOKEN_EXPIRE_MINUTES: int = 10080

    # Email to SMS, the carrier gateway domain and the SMTP account that sends through it
    CARRIER_GATEWAY: str = ""
    SMTP_HOST: str = ""
    SMTP_PORT: int = 587
    SMTP_USER: str = ""
    SMTP_PASS: str = ""

    RESEND_API_KEY: str = ""
    RESEND_FROM_EMAIL: str = ""
    RESEND_FROM_NAME: str = ""

    # Fernet key for stored phone numbers and emails. When empty one is derived from JWT_SECRET_KEY.
    PHONE_ENCRYPTION_KEY: str = ""
    # A waiting patient gets the almost your turn alert once their position is this or lower.
    ALMOST_TURN_POSITION: int = 3

    RATE_LIMIT_ENABLED: bool = True
    # How many proxies sit in front of the API, so the client address is read from X-Forwarded-For correctly.
    TRUSTED_PROXY_HOPS: int = 1

    # Comma-separated list of allowed origins, or * for all
    CORS_ORIGINS: str = "*"

    @property
    def cors_origins(self) -> list[str]:
        return [o.strip() for o in self.CORS_ORIGINS.split(",") if o.strip()]

    @property
    def is_dev(self) -> bool:
        return self.ENVIRONMENT == "dev"


@lru_cache
def get_settings() -> Settings:
    return Settings()


settings = get_settings()
