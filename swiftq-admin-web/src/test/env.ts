// Dummy settings so the API modules can load. No real network call is made.
// This file is imported first, because the API client reads these values when it is created.
process.env.NEXT_PUBLIC_API_BASE_URL = "https://api.test";
