"""HTTP client for calling Java data APIs."""
import logging
from typing import List, Optional, Dict, Any
import httpx
from config import JAVA_API_BASE

logger = logging.getLogger(__name__)

TIMEOUT = httpx.Timeout(30.0)


async def _get(path: str, params: Optional[Dict[str, Any]] = None) -> list | dict:
    """Call Java API and extract 'data' field from Result wrapper."""
    url = f"{JAVA_API_BASE}{path}"
    async with httpx.AsyncClient(timeout=TIMEOUT) as client:
        resp = await client.get(url, params=params)
        resp.raise_for_status()
        body = resp.json()
        if not body.get("success", False):
            logger.warning("Java API %s failed: %s", path, body.get("errorMsg"))
            return [] if path.endswith(("/tags", "/shops/by-tags", "/blogs/by-tags",
                                        "/intent-cases", "/strategy-rules",
                                        "/datasources")) else {}
        return body.get("data", body)


async def fetch_intent_cases() -> list:
    data = await _get("/intent-cases")
    return data if isinstance(data, list) else []


async def fetch_strategy_rules(scene: Optional[str] = None) -> list:
    params = {"scene": scene} if scene else None
    data = await _get("/strategy-rules", params)
    return data if isinstance(data, list) else []


async def fetch_tags(keyword: Optional[str] = None, category: Optional[str] = None) -> list:
    params = {}
    if keyword:
        params["keyword"] = keyword
    if category:
        params["category"] = category
    data = await _get("/tags", params if params else None)
    return data if isinstance(data, list) else []


async def fetch_shops_by_tags(tag_ids: List[int]) -> list:
    if not tag_ids:
        return []
    data = await _get("/shops/by-tags", {"tagIds": ",".join(str(t) for t in tag_ids)})
    return data if isinstance(data, list) else []


async def fetch_blogs_by_tags(tag_ids: List[int]) -> list:
    if not tag_ids:
        return []
    data = await _get("/blogs/by-tags", {"tagIds": ",".join(str(t) for t in tag_ids)})
    return data if isinstance(data, list) else []
