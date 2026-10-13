from typing import Optional

from pydantic import BaseModel, Field


class ChunkIn(BaseModel):
    chunkIndex: int
    page: int
    text: str


class IndexRequest(BaseModel):
    documentId: int
    filename: str
    contentType: str
    chunks: list[ChunkIn]


class IndexResponse(BaseModel):
    documentId: int
    indexedChunks: int


class DeleteIndexResponse(BaseModel):
    documentId: int
    deleted: bool


class HistoryTurn(BaseModel):
    question: str
    answer: str


class QueryRequest(BaseModel):
    question: str = Field(min_length=1, max_length=2000)
    documentIds: Optional[list[int]] = None
    history: Optional[list[HistoryTurn]] = None


class SourceOut(BaseModel):
    documentId: int
    filename: str
    page: int


class QueryResponse(BaseModel):
    answer: str
    sources: list[SourceOut]


class SearchRequest(BaseModel):
    query: str = Field(min_length=1, max_length=2000)
    documentIds: Optional[list[int]] = None
    limit: int = Field(default=8, ge=1, le=20)


class SearchResult(BaseModel):
    documentId: int
    filename: str
    page: int
    chunkIndex: int
    text: str
    score: float


class SearchResponse(BaseModel):
    results: list[SearchResult]


class GenerateRequest(BaseModel):
    question: str = Field(min_length=1, max_length=2000)
    context: str = Field(min_length=1, max_length=30000)
    history: Optional[list[HistoryTurn]] = None


class GenerateResponse(BaseModel):
    answer: str


class HealthResponse(BaseModel):
    status: str
