# VOR Concierge

Enterprise Knowledge Concierge — an on-premise, multi-tenant RAG (retrieval-augmented
generation) chat platform. Companies upload their internal documents, set up their own
departments and role hierarchy, and staff ask natural-language questions that are
answered strictly from whatever documents their role and department give them access
to.

See [`PROGRESS.md`](./PROGRESS.md) for the full architecture, what's been built and
verified so far, and the complete remaining roadmap.

## Stack

- **Backend** — `backend/`: Spring Boot 3.4 / Java 21, plain JDBC, Postgres + `pgvector`,
  Apache Tika, Ollama (embeddings + generation), JWT auth, SSE streaming.
- **Frontend** — `frontend/`: React 19, Vite, Tailwind CSS.

## Running locally

1. **Postgres** (via Docker):
   ```
   cd backend
   docker compose up -d postgres
   ```
2. **Ollama** — install natively and pull the required models:
   ```
   ollama pull llama3.1
   ollama pull mxbai-embed-large
   ```
   (Or set `OLLAMA_LLM_MODEL`/`OLLAMA_EMBED_MODEL` to whichever models you have.)
3. **Backend**:
   ```
   cd backend
   ./mvnw spring-boot:run
   ```
   On first boot against an empty database this seeds a platform-operator account and
   logs its randomly generated password once — watch the console output.
4. **Frontend**:
   ```
   cd frontend
   npm install
   npm run dev
   ```

## License

Proprietary — all rights reserved.
