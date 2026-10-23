from pydantic import BaseModel, EmailStr, Field
from .common import StaffRole


class LoginRequest(BaseModel):
    email: EmailStr
    password: str = Field(min_length=1)


class RegisterRequest(BaseModel):
    email: EmailStr
    password: str = Field(min_length=8)
    full_name: str = Field(min_length=1, max_length=100)
    role: StaffRole
    clinic_id: str | None = None


class AuthUser(BaseModel):
    id: str
    email: str
    role: StaffRole
    full_name: str
    clinic_id: str | None = None


class AuthResponse(BaseModel):
    access_token: str
    token_type: str = "Bearer"
    expires_in: int
    user: AuthUser
