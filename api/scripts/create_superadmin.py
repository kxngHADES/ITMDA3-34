# Creates the first it_admin account, run once from the api folder:
#   uv run python -m scripts.create_superadmin --email <email> --password <password> --name "<full name>"
import argparse
import asyncio

from core.firebase import init_firebase
from schemas import AppError, ErrorCode, StaffRole
from services import firebase_auth_service, user_service


async def main(email: str, password: str, full_name: str) -> None:
    init_firebase()
    try:
        firebase_user = await firebase_auth_service.sign_up_with_password(email, password)
    except AppError as exc:
        if exc.code is not ErrorCode.EMAIL_IN_USE:
            raise
        # The Firebase user exists already, so only the profile and role are (re)applied.
        firebase_user = await firebase_auth_service.sign_in_with_password(email, password)
    await user_service.create_user(firebase_user.uid, firebase_user.email, full_name, StaffRole.it_admin)
    print(f"it_admin ready: {firebase_user.email} ({firebase_user.uid})")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--email", required=True)
    parser.add_argument("--password", required=True) # locla dev will use "SuperSecret@1"
    parser.add_argument("--name", default="Super Admin")
    args = parser.parse_args()
    asyncio.run(main(args.email, args.password, args.name))
