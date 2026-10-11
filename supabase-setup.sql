-- =============================================
-- SpeakMind Supabase Setup
-- Run this in your Supabase SQL Editor
-- Project: bkivjjvgzsqgbkfubjtx
-- =============================================

-- 1. User profiles (extends auth.users)
CREATE TABLE IF NOT EXISTS profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    display_name TEXT NOT NULL,
    level TEXT NOT NULL DEFAULT 'A2',
    streak_days INT NOT NULL DEFAULT 0,
    last_active_date TEXT NOT NULL DEFAULT '',
    total_vocab_learned INT NOT NULL DEFAULT 0,
    total_conversations INT NOT NULL DEFAULT 0,
    total_minutes INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 2. Conversations
CREATE TABLE IF NOT EXISTS conversations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    scenario_id TEXT,
    last_message_preview TEXT NOT NULL DEFAULT '',
    last_message_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    message_count INT NOT NULL DEFAULT 0
);

-- 3. Messages
CREATE TABLE IF NOT EXISTS messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    role TEXT NOT NULL CHECK (role IN ('user', 'assistant', 'system')),
    content TEXT NOT NULL,
    corrections_json TEXT,
    voice_url TEXT,
    voice_duration_ms INTEGER DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 4. Enable Row Level Security
ALTER TABLE profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE conversations ENABLE ROW LEVEL SECURITY;
ALTER TABLE messages ENABLE ROW LEVEL SECURITY;

-- 5. RLS Policies — each user sees only their own data
CREATE POLICY "Users own their profile"
    ON profiles FOR ALL
    USING (auth.uid() = id);

CREATE POLICY "Users own their conversations"
    ON conversations FOR ALL
    USING (auth.uid() = user_id);

CREATE POLICY "Users see their messages"
    ON messages FOR ALL
    USING (conversation_id IN (
        SELECT id FROM conversations WHERE user_id = auth.uid()
    ));

-- 6. Indexes for performance
CREATE INDEX IF NOT EXISTS idx_conversations_user_id ON conversations(user_id);
CREATE INDEX IF NOT EXISTS idx_conversations_last_message ON conversations(last_message_at DESC);
CREATE INDEX IF NOT EXISTS idx_messages_conversation_id ON messages(conversation_id);
CREATE INDEX IF NOT EXISTS idx_messages_created_at ON messages(created_at ASC);

-- 7. Storage bucket for voice recordings
INSERT INTO storage.buckets (id, name, public)
VALUES ('voice-recordings', 'voice-recordings', true)
ON CONFLICT (id) DO NOTHING;

-- 8. Storage RLS — users can only access their own voice files
CREATE POLICY "Users upload own voice recordings"
    ON storage.objects FOR INSERT
    WITH CHECK (
        bucket_id = 'voice-recordings'
        AND (storage.foldername(name))[1] = auth.uid()::text
    );

CREATE POLICY "Users read own voice recordings"
    ON storage.objects FOR SELECT
    USING (
        bucket_id = 'voice-recordings'
        AND (storage.foldername(name))[1] = auth.uid()::text
    );

CREATE POLICY "Users delete own voice recordings"
    ON storage.objects FOR DELETE
    USING (
        bucket_id = 'voice-recordings'
        AND (storage.foldername(name))[1] = auth.uid()::text
    );

-- 9. Enable Realtime for typing indicators
ALTER PUBLICATION supabase_realtime ADD TABLE messages;
