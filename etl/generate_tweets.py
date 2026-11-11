"""Generate synthetic tweets in the Twitter API v1.1 JSON shape (one JSON object per line).
Users belong to interest communities, so hashtags and interactions cluster realistically."""
import json
import random
import sys

TOPICS = {"ml": ["machinelearning", "datascience", "python", "ai"], "rust": ["rustlang", "systems", "wasm"],
          "nba": ["nba", "lakers", "basketball"], "travel": ["travel", "wanderlust", "photography"],
          "finance": ["fintech", "investing", "crypto"], "cook": ["recipes", "foodie", "vegan"]}
POPULAR = ["news", "love", "music", "follow", "photo"]
WORDS = "great today new love think build data game trip market recipe launch learn ship win team".split()


def main(out, n_users=20000, n_tweets=400000, seed=3):
    rng = random.Random(seed)
    community = {u: rng.choice(list(TOPICS)) for u in range(1, n_users + 1)}
    members = {}
    for u, c in community.items():
        members.setdefault(c, []).append(u)
    with open(out, "w") as f:
        for tid in range(n_tweets):
            u = rng.randint(1, n_users); c = community[u]
            tags = rng.sample(TOPICS[c], k=rng.randint(0, 2)) + ([rng.choice(POPULAR)] if rng.random() < .3 else [])
            # 80% of interactions stay inside the author's community
            other = rng.choice(members[c]) if rng.random() < .8 else rng.randint(1, n_users)
            kind = rng.random()
            tweet = {"id": tid, "created_at": "2023-11-%02d" % rng.randint(1, 30),
                     "user": {"id": u, "screen_name": f"user{u}", "description": f"{c} enthusiast"},
                     "text": " ".join(rng.choices(WORDS, k=8)) + " " + " ".join("#" + t for t in tags),
                     "entities": {"hashtags": [{"text": t} for t in tags], "user_mentions": []}}
            if kind < .25 and other != u:
                tweet["in_reply_to_user_id"] = other
            elif kind < .45 and other != u:
                tweet["retweeted_status"] = {"user": {"id": other, "screen_name": f"user{other}"}}
            elif kind < .55 and other != u:
                tweet["entities"]["user_mentions"] = [{"id": other}]
            f.write(json.dumps(tweet) + "\n")


if __name__ == "__main__":
    main(sys.argv[1], *map(int, sys.argv[2:]))
