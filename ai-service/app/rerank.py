import re

from app.config import Settings
from app.retrieval import RetrievedChunk

RERANK_SYSTEM_PROMPT = (
    "You rank search passages by relevance to a question. Reply with ONLY a JSON "
    "array of the passage numbers ordered from most to least relevant, for example "
    "[3, 1, 2]. Include every passage number exactly once. No explanation."
)

_NUMBER_PATTERN = re.compile(r"\d+")


def build_rerank_messages(question: str, chunks: list[RetrievedChunk]) -> list[dict]:
    lines = [f"Question: {question}", "", "Passages:"]
    for index, chunk in enumerate(chunks, start=1):
        lines.append(f"{index}. ({chunk.filename}, page {chunk.page}) {chunk.text}")
    lines.append("")
    lines.append("JSON array of passage numbers, most to least relevant:")
    return [
        {"role": "system", "content": RERANK_SYSTEM_PROMPT},
        {"role": "user", "content": "\n".join(lines)},
    ]


def parse_rerank_order(raw: str, total: int) -> list[int]:
    """Parse a 1-based order from the model reply into 0-based indexes.

    Invalid or out-of-range numbers are dropped; missing positions are
    appended in their original order so no chunk is ever lost.
    """
    order: list[int] = []
    seen: set[int] = set()
    for value in _NUMBER_PATTERN.findall(raw or ""):
        position = int(value) - 1
        if 0 <= position < total and position not in seen:
            seen.add(position)
            order.append(position)
    order.extend(position for position in range(total) if position not in seen)
    return order


def rerank_chunks(
    openai_client,
    settings: Settings,
    question: str,
    chunks: list[RetrievedChunk],
) -> list[RetrievedChunk]:
    """LLM reranking of hybrid candidates; falls back to input order on any failure."""
    if not settings.rerank_enabled or openai_client is None or len(chunks) <= 1:
        return chunks

    try:
        response = openai_client.chat.completions.create(
            model=settings.chat_model,
            messages=build_rerank_messages(question, chunks),
            temperature=0.0,
            max_tokens=settings.rerank_max_tokens,
        )
        raw = (response.choices[0].message.content or "").strip()
        order = parse_rerank_order(raw, len(chunks))
        return [chunks[position] for position in order]
    except Exception:
        return chunks
