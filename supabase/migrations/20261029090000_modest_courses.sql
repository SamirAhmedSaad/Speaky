-- Modest courses (male teachers / cartoons), created unpublished; lessons are uploaded as drafts.
insert into public.courses (slug, title, subtitle, sort, published, level, channel_name, channel_url, image_url) values
('singsing-dialogues', 'Singsing · First Conversations', 'Short cartoon dialogues for your first English words', 5, false, 'Beginner', 'English Singsing', 'https://www.youtube.com/channel/UCGwA4GjY4nGMIYvaJiA0EGA', 'https://yt3.googleusercontent.com/wXeMaXCcCpJAl8dDc4Exw9OZFWCEBkn4BqX8Bcx1mscv0_4IScyF6MPiNl_ECGoNlfp9NpC15w=s240-c-k-c0x00ffffff-no-rj'),
('bbc-storytellers', 'BBC · Storytellers', 'Short animated stories for beginners', 8, false, 'Beginner', 'BBC Learning English', 'https://www.youtube.com/@bbclearningenglish', 'https://yt3.googleusercontent.com/ANAs2cAwxesjyoe3vsyhZGzlTafbicnuGMzPNSAuJdpTwg8CesHkViMTCrYqAJEGaKXG6_KEUg=s240-c-k-c0x00ffffff-no-rj'),
('english-together', 'English Together · Daily Conversations', 'Animated everyday situations', 12, false, 'Beginner → Intermediate', 'English Together', 'https://www.youtube.com/channel/UCIrLzmyYoeIaBhqA0ma4vpg', 'https://yt3.googleusercontent.com/w9Yg5ICIaE2jENtCVSh3mNdzo-NDOW-r8FGYcrqidb-59tPB7kBX4bDCPqbpOB9l3cPJ80V4OA=s240-c-k-c0x00ffffff-no-rj'),
('james-grammar', 'James · English Grammar', 'Clear whiteboard grammar lessons', 35, false, 'Intermediate', 'ENGLISH with James · engVid', 'https://www.youtube.com/channel/UCwA7Aepp7nRUJNa8roQ-6Bw', 'https://yt3.googleusercontent.com/lThIdbFCrFiEf3C_Xh8jJVyEohivnd_DuqZi7Fss9UjMDfxlWaC2erUJtmlbJNM61UFbVlst=s240-c-k-c0x00ffffff-no-rj'),
('harry-conversations', 'Harry · Everyday Conversations', 'Natural phrases for daily situations', 40, false, 'Intermediate → Upper', 'Learn English with Harry', 'https://www.youtube.com/channel/UC3X5lXWdDrYRDCW7k01Ka4Q', 'https://yt3.googleusercontent.com/i2W-niqljIPiY-zyhcZ8AphCa-8b15669FSdw6Xd3nLZ9KAUFJWmRu4x3PdgQgzu7fa_9iSCJg=s240-c-k-c0x00ffffff-no-rj'),
('james-vocab', 'James · Vocabulary Boost', 'Say more with better words', 60, false, 'Upper-Intermediate', 'ENGLISH with James · engVid', 'https://www.youtube.com/channel/UCwA7Aepp7nRUJNa8roQ-6Bw', 'https://yt3.googleusercontent.com/lThIdbFCrFiEf3C_Xh8jJVyEohivnd_DuqZi7Fss9UjMDfxlWaC2erUJtmlbJNM61UFbVlst=s240-c-k-c0x00ffffff-no-rj')
on conflict (slug) do nothing;

insert into public.course_books (course_id, number, title)
select c.id, b.number, b.title from public.courses c
join (values
  ('singsing-dialogues', 1, 'All Lessons'),
  ('bbc-storytellers', 1, 'All Stories'),
  ('english-together', 1, 'Everyday Life'),
  ('english-together', 2, 'Out and About'),
  ('james-grammar', 1, 'All Lessons'),
  ('harry-conversations', 1, 'All Lessons'),
  ('james-vocab', 1, 'All Lessons')
) as b(slug, number, title) on b.slug = c.slug
on conflict do nothing;
