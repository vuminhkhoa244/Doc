Project Specification
AI Document Assistant (RAG + Agent)

Version: 1.0

1. Mục tiêu

Xây dựng một hệ thống AI có khả năng:

Upload tài liệu
Phân tích nội dung
Lập chỉ mục (Indexing)
Tìm kiếm theo ngữ nghĩa
Trả lời câu hỏi dựa trên tài liệu
Trích dẫn nguồn
Hoạt động như một AI Agent có thể mở rộng thêm nhiều công cụ (tools)
2. Kiến trúc tổng thể
                    User
                     │
         React / React Native
                     │
             Spring Boot API
                     │
      ┌──────────────┴──────────────┐
      │                             │
      ▼                             ▼
 PostgreSQL                   Python AI Service
(User, Metadata)                    │
                                    │
              ┌─────────────────────┴─────────────────────┐
              │                                           │
              ▼                                           ▼
        Embedding Model                             LLM Provider
              │
              ▼
         ChromaDB / pgvector
3. Tech Stack
Frontend
React

Backend
Spring Boot
Spring Security JWT
PostgreSQL
Docker

AI Service
Python
FastAPI

AI Libraries
LangChain (giai đoạn đầu để học nhanh, sau đó có thể tự triển khai)
ChromaDB
FAISS (để thử nghiệm)
OpenAI SDK hoặc mô hình cục bộ
DevOps

Docker Compose
Nginx
GitHub Actions
Render hoặc VPS
MinIO (lưu file)

4. Chức năng
Authentication
Register
Login
JWT
Refresh Token
Document Management

Upload
PDF
DOCX
TXT
Markdown

Các thao tác:

Upload
Delete
Rename
Download
AI Indexing

Sau khi upload

Upload

↓

Extract Text

↓

Split

↓

Embedding

↓

Store Vector

↓

Done

AI Chat

Ví dụ

User

"What is annual leave?"

↓

Retriever

↓

Top 5 Chunks

↓

LLM

↓

Answer

Citation

Ví dụ

Employees receive 15 days annual leave.

(Source:
Employee Handbook
Page 12)
Search

Semantic Search

Ví dụ

vacation

vẫn tìm được

annual leave
5. Database
PostgreSQL
users
id

email

password

role

documents
id

owner_id

filename

size

upload_time

chunks

id

document_id

chunk_index

text

page

chat_history
id

user_id

question

answer

timestamp
6. AI Pipeline
Indexing
Upload

↓

PDF Loader

↓

Text Splitter

↓

Embedding

↓

Vector DB
Retrieval
Question

↓

Embedding

↓

Similarity Search

↓

Top K

↓

Prompt

↓

LLM

↓

Answer
7. REST API
Upload
POST

/api/documents
List
GET

/api/documents
Delete
DELETE

/api/documents/{id}
Ask
POST

/api/chat

Body

{
  "question": "Explain Docker"
}

Response

{
  "answer": "...",
  "sources": [
    {
      "document": "Docker Guide.pdf",
      "page": 12
    }
  ]
}
8. AI Service API
POST

/index
{
  "documentId":123
}
POST

/query
{
 "question":"..."
}
9. Agent (Phase 2)

Thêm Tools

Agent

↓

Need document?

↓

Yes

↓

RAG Tool

Hoặc

Need database?

↓

SQL Tool

Hoặc

Need internet?

↓

Web Search Tool

Agent Router

User

↓

Planner

↓

Tool Selection

↓

Execute

↓

LLM

↓

Answer
10. Docker
frontend

backend

python-ai

postgres

chromadb

minio

nginx
11. Folder Structure
project/

    frontend/

    backend/

    ai-service/

    docker/

    docs/

    scripts/

    .github/
12. Roadmap
Phase 1
Authentication
Upload PDF
Chat với PDF
Citation

Phase 2
DOCX
Markdown
Semantic Search
Metadata Filter

Phase 3
AI Agent
Memory
Multi-document Chat
Conversation Context

Phase 4
OCR
Image Search
Table Extraction
Hybrid Search
Reranker

Gợi ý nâng cấp để dự án nổi bật hơn

Hỗ trợ nhiều LLM (OpenAI, Gemini, Ollama) thông qua một lớp adapter.
Thêm Redis để cache kết quả embedding hoặc câu trả lời phổ biến.
Sử dụng PostgreSQL + pgvector thay vì ChromaDB ở phiên bản production để giảm số lượng dịch vụ cần quản lý.
Thêm cơ chế background jobs (RabbitMQ hoặc Kafka) để việc indexing diễn ra bất đồng bộ sau khi upload tài liệu.
Theo dõi hệ thống bằng Prometheus + Grafana và ghi log tập trung với Loki hoặc ELK.
Bổ sung đánh giá chất lượng RAG (retrieval precision, latency, nguồn trích dẫn) để thể hiện tư duy kỹ thuật, không chỉ xây chatbot.