"""
MCP data client — calls Spring AI MCP Server's REST wrapper.

The MCP server (:8020) is the universal data gateway. It reads
data-sources.json to know all available endpoints, and can fetch
from any of them by ID.

MCP path (MCP_ENABLED=true): Python -> MCP Server -> Java REST APIs -> DB
Direct path (MCP_ENABLED=false): Python -> Java REST APIs -> DB
"""
import json
import logging
import os
import re
from typing import List, Dict, Any, Optional
import httpx

logger = logging.getLogger(__name__)

MCP_BASE = os.getenv("MCP_BASE_URL", "http://localhost:8020")
MCP_ENABLED = os.getenv("MCP_ENABLED", "true").lower() == "true"

TIMEOUT = httpx.Timeout(30.0)


async def list_data_sources() -> str:
    """Get all available data sources from MCP server."""
    async with httpx.AsyncClient(timeout=TIMEOUT) as client:
        resp = await client.get(f"{MCP_BASE}/api/mcp/datasources")
        resp.raise_for_status()
        return resp.text


async def fetch_by_source_ids(
    source_ids: List[int],
    params: Optional[Dict[int, Dict[str, Any]]] = None,
) -> str:
    """
    Fetch data from MCP server by source IDs.

    Args:
        source_ids: [1, 3, 4, 5] etc.
        params: {3: {"keyword": "川菜"}, 4: {"tagIds": "1,2,3"}}

    Returns:
        Raw response text from MCP server containing all fetched data.
    """
    body = {
        "sourceIds": source_ids,
        "params": {str(k): v for k, v in (params or {}).items()},
    }
    async with httpx.AsyncClient(timeout=TIMEOUT) as client:
        resp = await client.post(f"{MCP_BASE}/api/mcp/fetch", json=body)
        resp.raise_for_status()
        return resp.text


def parse_mcp_response(text: str) -> List[dict]:
    """
    Parse MCP response text into a list of dicts.

    MCP returns format like:
      数据源 ID: 3 请求成功
      返回内容:
      {"success":true,"data":[{...},{...}]}

    This extracts the JSON list from each source's response.
    """
    if not text:
        return []

    results = []
    # Try to find all JSON objects/arrays embedded in the text
    # First, extract per-source sections
    sections = re.split(r'数据源 ID: \d+ 请求成功\n返回内容:\n', text)
    for section in sections[1:]:  # skip preamble
        section = section.strip()
        # Try parsing as-is
        try:
            parsed = json.loads(section)
            if isinstance(parsed, list):
                results.extend(parsed)
            elif isinstance(parsed, dict) and "data" in parsed:
                d = parsed["data"]
                if isinstance(d, list):
                    results.extend(d)
                elif isinstance(d, dict):
                    results.append(d)
            elif isinstance(parsed, dict):
                results.append(parsed)
            continue
        except (json.JSONDecodeError, TypeError):
            pass

        # Try finding JSON array
        arrays = re.findall(r'\[[\s\S]*?\]', section)
        for arr_str in arrays:
            try:
                arr = json.loads(arr_str)
                if isinstance(arr, list):
                    results.extend(arr)
            except (json.JSONDecodeError, TypeError):
                continue

    return results


def parse_mcp_response_by_source(text: str) -> Dict[int, List[dict]]:
    """
    Parse MCP response, grouping results by source ID.

    Returns: {1: [{...}], 3: [{...}, {...}], ...}
    """
    if not text:
        return {}

    result = {}
    # Split by source sections
    pattern = r'数据源 ID: (\d+) 请求成功\n返回内容:\n([\s\S]*?)(?=\n数据源 ID:|\Z)'
    for match in re.finditer(pattern, text):
        source_id = int(match.group(1))
        content = match.group(2).strip()

        try:
            parsed = json.loads(content)
            if isinstance(parsed, list):
                result[source_id] = parsed
            elif isinstance(parsed, dict) and "data" in parsed:
                d = parsed["data"]
                result[source_id] = d if isinstance(d, list) else [d] if d else []
            elif isinstance(parsed, dict):
                result[source_id] = [parsed]
        except (json.JSONDecodeError, TypeError):
            # Try finding JSON array
            arrays = re.findall(r'\[[\s\S]*?\]', content)
            for arr_str in arrays:
                try:
                    arr = json.loads(arr_str)
                    if isinstance(arr, list) and len(arr) > 0:
                        result[source_id] = arr
                        break
                except (json.JSONDecodeError, TypeError):
                    continue
            if source_id not in result:
                result[source_id] = []

    return result
