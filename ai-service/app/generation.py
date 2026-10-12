from app.config import Settings
from app.models import HistoryTurn
from app.retrieval import RetrievedChunk

SYSTEM_PROMPT = (
    "You are a helpful document assistant. Answer the user's question using ONLY "
    "the provided context passages from their documents. Rules:\n"
    "1. Base every factual statement on the context. If the context does not "
    "contain the answer, say so clearly instead of guessing.\n"
    "2. Be concise and direct.\n"
    "3. You may refer to passages by their [n] markers and page numbers."
)

NO_CONTEXT_ANSWER = (
    "I could not find a matching answer in your indexed documents. "
    "Upload and index a document or ask using related terms."
)


def build_context(chunks: list[RetrievedChunk], max_chars: int) -> str:
    blocks = []
    used = 0
    for index, chunk in enumerate(chunks, start=1):
        block = f"[{index}] ({chunk.filename}, page {chunk.page})\n{chunk.text}"
        if used + len(block) > max_chars and blocks:
            break
        blocks.append(block)
        used += len(block)
    return "\n\n".join(blocks)


def build_messages(
    settings: Settings,
    question: str,
    chunks: list[RetrievedChunk],
    history: list[HistoryTurn] | None,
) -> list[dict]:
    messages = [{"role": "system", "content": SYSTEM_PROMPT}]

    for turn in (history or [])[-settings.history_max_turns :]:
        messages.append({"role": "user", "content": turn.question})
        messages.append({"role": "assistant", "content": turn.answer})

    user_content = f"Context:\n{build_context(chunks, settings.max_context_chars)}\n\nQuestion: {question}"
    messages.append({"role": "user", "content": user_content})
    return messages


def generate_answer(openai_client, settings: Settings, question: str, chunks: list[RetrievedChunk], history: list[HistoryTurn] | None) -> str:
    if not chunks:
        return NO_CONTEXT_ANSWER

    response = openai_client.chat.completions.create(
        model=settings.chat_model,
        messages=build_messages(settings, question, chunks, history),
        temperature=0.2,
        max_tokens=settings.max_answer_tokens,
    )
    return (response.choices[0].message.content or "").strip() or NO_CONTEXT_ANSWER
