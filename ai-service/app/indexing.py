from app.config import Settings
from app.models import IndexRequest


def _embed_texts(openai_client, settings: Settings, texts: list[str]) -> list[list[float]]:
    embeddings: list[list[float]] = []
    batch_size = settings.embedding_batch_size
    for start in range(0, len(texts), batch_size):
        batch = texts[start : start + batch_size]
        response = openai_client.embeddings.create(model=settings.embedding_model, input=batch)
        embeddings.extend(item.embedding for item in response.data)
    return embeddings


def index_document(collection, openai_client, settings: Settings, request: IndexRequest) -> int:
    if not request.chunks:
        return 0

    texts = [chunk.text for chunk in request.chunks]
    embeddings = _embed_texts(openai_client, settings, texts)

    ids = [f"{request.documentId}:{chunk.chunkIndex}" for chunk in request.chunks]
    metadatas = [
        {
            "documentId": request.documentId,
            "filename": request.filename,
            "contentType": request.contentType,
            "chunkIndex": chunk.chunkIndex,
            "page": chunk.page,
        }
        for chunk in request.chunks
    ]

    collection.upsert(
        ids=ids,
        embeddings=embeddings,
        documents=texts,
        metadatas=metadatas,
    )
    return len(request.chunks)


def delete_document(collection, document_id: int) -> bool:
    existing = collection.get(where={"documentId": document_id}, include=[])
    if not existing or not existing.get("ids"):
        return False
    collection.delete(where={"documentId": document_id})
    return True
