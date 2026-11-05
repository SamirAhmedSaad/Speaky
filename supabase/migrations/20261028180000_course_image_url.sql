alter table public.courses add column if not exists image_url text;

-- YouTube channel logos (video thumbnails often show unveiled people)
update public.courses set image_url = 'https://yt3.googleusercontent.com/bYjN-CYamiBAcRhvsN5ONAd21QHXCZNN9IPDtx5SK76mkKLv642FOm2wr1B9edvuQ_zcctLMwA=s240-c-k-c0x00ffffff-no-rj'
  where channel_url = 'https://www.youtube.com/@VOALearningEnglish';
update public.courses set image_url = 'https://yt3.googleusercontent.com/ANAs2cAwxesjyoe3vsyhZGzlTafbicnuGMzPNSAuJdpTwg8CesHkViMTCrYqAJEGaKXG6_KEUg=s240-c-k-c0x00ffffff-no-rj'
  where channel_url = 'https://www.youtube.com/@bbclearningenglish';
update public.courses set image_url = 'https://yt3.googleusercontent.com/FbldndT8M3ARKmYAm3N6lJJcx29AOrqt9t3uaA1UUe1eGWleSOxXT2LnKv9rlptl30UdiKJeo-8=s240-c-k-c0x00ffffff-no-rj'
  where channel_url = 'https://www.youtube.com/@Taleek';

-- Apps re-download courses
update public.app_settings set value = to_jsonb(coalesce((value #>> '{}')::int, 0) + 1) where key = 'courses_version';
