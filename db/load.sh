#!/usr/bin/env bash
# Generate synthetic tweets, run the Spark ETL and bulk-load MySQL.
# usage: ./db/load.sh [mysql host] [user] [password]
set -euo pipefail
HOST=${1:-127.0.0.1}; USER=${2:-recsys}; PASS=${3:-recsys}
mkdir -p data
python etl/generate_tweets.py data/tweets.jsonl
spark-submit etl/build_tables.py data/tweets.jsonl data/out
U=$(ls data/out/users/*.csv); I=$(ls data/out/interactions/*.csv)
mysql --local-infile=1 -h"$HOST" -u"$USER" -p"$PASS" recsys <<SQL
LOAD DATA LOCAL INFILE '$U' INTO TABLE users FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '"' ESCAPED BY '"'
  (user_id, screen_name, description, @h) SET hashtags_json = NULLIF(@h, '');
LOAD DATA LOCAL INFILE '$I' INTO TABLE interactions FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '"' ESCAPED BY '"'
  (user_id, contact_id, replies, retweets, mentions, latest_tweet);
SQL
echo "loaded"
