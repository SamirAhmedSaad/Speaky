-- Applied via MCP. Waveform of a voice message: one digit 0-9 per bar (e.g. 48 bars), drawn by the app.
alter table public.room_messages add column if not exists voice_waveform text
  check (voice_waveform is null or voice_waveform ~ '^[0-9]{1,120}$');
