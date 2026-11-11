CREATE TABLE IF NOT EXISTS users (
  user_id       BIGINT PRIMARY KEY,
  screen_name   VARCHAR(64),
  description   TEXT,
  hashtags_json JSON
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS interactions (
  user_id      BIGINT NOT NULL,
  contact_id   BIGINT NOT NULL,
  replies      INT NOT NULL DEFAULT 0,
  retweets     INT NOT NULL DEFAULT 0,
  mentions     INT NOT NULL DEFAULT 0,
  latest_tweet TEXT,
  PRIMARY KEY (user_id, contact_id)          -- clustered: one range scan per request
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
