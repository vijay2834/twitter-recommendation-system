"""PySpark ETL: raw tweet JSON -> `users` and `interactions` tables.

    spark-submit etl/build_tables.py tweets.jsonl out/            # writes CSVs for LOAD DATA
    (on Databricks / EMR point the input at s3:// or dbfs:/ and use --jdbc to write straight to MySQL)
"""
import argparse

from pyspark.sql import SparkSession, Window
from pyspark.sql import functions as F


def build(spark, src):
    raw = spark.read.json(src)
    t = raw.select(
        F.col("id").alias("tweet_id"), F.col("created_at"), F.col("text"),
        F.col("user.id").alias("user_id"), F.col("user.screen_name").alias("screen_name"),
        F.col("user.description").alias("description"),
        F.col("in_reply_to_user_id").alias("reply_to"),
        F.col("retweeted_status.user.id").alias("retweet_of"),
        F.col("entities.hashtags.text").alias("hashtags"),
        F.col("entities.user_mentions.id").alias("mentions"),
    ).dropDuplicates(["tweet_id"]).filter(F.col("user_id").isNotNull())

    # one row per (author, contact, kind); interactions are symmetric for recommendation purposes
    edges = (t.filter(F.col("reply_to").isNotNull()).select("user_id", F.col("reply_to").alias("contact_id"), F.lit("reply").alias("kind"), "text", "created_at")
             .unionByName(t.filter(F.col("retweet_of").isNotNull()).select("user_id", F.col("retweet_of").alias("contact_id"), F.lit("retweet").alias("kind"), "text", "created_at"))
             .unionByName(t.select("user_id", F.explode_outer("mentions").alias("contact_id"), F.lit("mention").alias("kind"), "text", "created_at").filter(F.col("contact_id").isNotNull())))
    both = edges.unionByName(edges.select(F.col("contact_id").alias("user_id"), F.col("user_id").alias("contact_id"), "kind", "text", "created_at"))
    both = both.filter(F.col("user_id") != F.col("contact_id"))
    latest = Window.partitionBy("user_id", "contact_id").orderBy(F.col("created_at").desc())
    interactions = (both.withColumn("rn", F.row_number().over(latest))
                    .groupBy("user_id", "contact_id")
                    .agg(F.sum((F.col("kind") == "reply").cast("int")).alias("replies"),
                         F.sum((F.col("kind") == "retweet").cast("int")).alias("retweets"),
                         F.sum((F.col("kind") == "mention").cast("int")).alias("mentions"),
                         F.max(F.when(F.col("rn") == 1, F.col("text"))).alias("latest_tweet")))

    tags = (t.select("user_id", F.explode("hashtags").alias("tag")).withColumn("tag", F.lower("tag"))
            .groupBy("user_id", "tag").count()
            .groupBy("user_id").agg(F.to_json(F.map_from_entries(F.collect_list(F.struct("tag", "count")))).alias("hashtags_json")))
    latest_profile = Window.partitionBy("user_id").orderBy(F.col("created_at").desc())
    users = (t.withColumn("rn", F.row_number().over(latest_profile)).filter("rn = 1")
             .select("user_id", "screen_name", "description").join(tags, "user_id", "left"))
    return users, interactions


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("src"); ap.add_argument("out")
    ap.add_argument("--jdbc", help="jdbc:mysql://host/db (writes directly instead of CSV)")
    a = ap.parse_args()
    spark = SparkSession.builder.appName("recsys-etl").getOrCreate()
    users, inter = build(spark, a.src)
    if a.jdbc:
        for name, df in (("users", users), ("interactions", inter)):
            df.write.mode("append").format("jdbc").option("url", a.jdbc).option("dbtable", name).save()
    else:
        users.coalesce(1).write.mode("overwrite").option("escape", '"').csv(f"{a.out}/users", header=False)
        inter.coalesce(1).write.mode("overwrite").option("escape", '"').csv(f"{a.out}/interactions", header=False)
    print(f"users={users.count()} interactions={inter.count()}")
    spark.stop()
