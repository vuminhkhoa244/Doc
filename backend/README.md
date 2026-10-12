# AI Document Assistant Backend

Spring Boot backend for the document assistant:

- Register/login with JWT bearer auth
- Upload, list, and delete PDF, DOCX, Markdown, and TXT documents
- Extract document text, split it into chunks, and store citations
- Ask questions across the authenticated user's indexed documents
- Run semantic search with document metadata filters

## Run

```bash
mvn spring-boot:run
```

The API listens on `http://localhost:8080`.

For the frontend, set:

```bash
VITE_API_BASE_URL=http://localhost:8080
```

## Storage

By default the app uses an H2 database at `./data/assistant-db` and file uploads at `./data/uploads`.
Use `SPRING_DATASOURCE_*` variables or a Spring profile to point it at PostgreSQL.
