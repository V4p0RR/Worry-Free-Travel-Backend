"""Pydantic models for request / response."""
from typing import List, Optional
from pydantic import BaseModel, Field


class ChatRequest(BaseModel):
    user_id: Optional[int] = Field(None, alias="userId")
    query: str
    conversation_id: Optional[str] = Field(None, alias="conversationId")


class TagItem(BaseModel):
    id: int
    name: str
    category: str


class ShopItem(BaseModel):
    id: int
    name: str
    link: str = ""               # 前端跳转路径 /shop/{id}
    area: Optional[str] = None
    address: Optional[str] = None
    avg_price: Optional[int] = Field(None, alias="avgPrice")
    score: Optional[int] = None
    images: Optional[str] = None


class BlogItem(BaseModel):
    id: int
    title: str
    link: str = ""               # 前端跳转路径 /blog/{id}
    content: Optional[str] = None
    liked: Optional[int] = None
    images: Optional[str] = None


class ChatResponse(BaseModel):
    conversation_id: str
    intention: str
    optimized_query: str
    matched_tags: List[TagItem] = Field(default_factory=list)
    recommended_shops: List[ShopItem] = Field(default_factory=list)
    recommended_blogs: List[BlogItem] = Field(default_factory=list)
    summary: str = ""


class AgentState(BaseModel):
    """State that flows through the LangGraph (6 nodes = 18 steps)."""
    user_id: Optional[int] = None
    query: str = ""
    conversation_id: str = ""
    intention: str = ""
    intention_key: str = "other"
    optimized_query: str = ""
    matched_tag_ids: List[int] = Field(default_factory=list)
    matched_tags: List[dict] = Field(default_factory=list)
    source_ids: List[int] = Field(default_factory=list)
    source_params: dict = Field(default_factory=dict)
    shops: List[dict] = Field(default_factory=list)
    blogs: List[dict] = Field(default_factory=list)
    rules: List[dict] = Field(default_factory=list)
    summary: str = ""
    error: str = ""
