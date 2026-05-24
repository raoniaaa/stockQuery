-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- stock_analyses table
CREATE TABLE IF NOT EXISTS stock_analyses (
    id BIGSERIAL PRIMARY KEY,
    stock_code TEXT NOT NULL,
    stock_name TEXT,
    analysis_type TEXT,
    content TEXT,
    summary TEXT,
    sentiment TEXT,
    risk_level TEXT,
    model_used TEXT,
    client_ip TEXT,
    memory_id TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- chat_memory table
CREATE TABLE IF NOT EXISTS chat_memory (
    memory_id VARCHAR(255) PRIMARY KEY,
    messages TEXT NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- embedding_chunks table (langchain4j-pgvector 1.0.0-beta5 schema)
CREATE TABLE IF NOT EXISTS embedding_chunks (
    embedding_id UUID PRIMARY KEY,
    embedding vector(1024),
    text TEXT NULL
);
