"""Agent configuration — override via environment variables."""
import os

JAVA_API_BASE = os.getenv("JAVA_API_BASE", "http://localhost:8081/api/agent")

# LLM config (OpenAI-compatible)
LLM_BASE_URL = os.getenv("LLM_BASE_URL", "https://api.openai.com")
LLM_API_KEY = os.getenv("LLM_API_KEY", "your-api-key")
LLM_MODEL = os.getenv("LLM_MODEL", "gpt-3.5-turbo")

# Rate limit
RATE_LIMIT_MAX = int(os.getenv("RATE_LIMIT_MAX", "20"))
RATE_LIMIT_WINDOW = int(os.getenv("RATE_LIMIT_WINDOW", "60"))

# Server
SERVER_HOST = os.getenv("SERVER_HOST", "0.0.0.0")
SERVER_PORT = int(os.getenv("SERVER_PORT", "8090"))
