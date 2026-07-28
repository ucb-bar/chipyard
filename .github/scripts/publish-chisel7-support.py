#!/usr/bin/env python3

import argparse
import datetime as dt
import json
import os
import sys


def utc_now():
    return dt.datetime.now(dt.timezone.utc).isoformat().replace("+00:00", "Z")


def main():
    parser = argparse.ArgumentParser(description="Publish Chisel 7 support audit results to MongoDB.")
    parser.add_argument("results_json")
    parser.add_argument("--database", default="main")
    parser.add_argument("--collection", default="chisel7_support")
    parser.add_argument("--mongo-env", default="MONGODB_SRV")
    args = parser.parse_args()

    mongo_uri = os.environ.get(args.mongo_env)
    if not mongo_uri:
        print(f"Missing {args.mongo_env}; skipping MongoDB publish", file=sys.stderr)
        return 0

    try:
        from pymongo import MongoClient
    except ImportError:
        print("pymongo is not installed; run `python3 -m pip install pymongo` first", file=sys.stderr)
        return 2

    with open(args.results_json, encoding="utf-8") as results_file:
        document = json.load(results_file)

    document["published_at"] = utc_now()

    client = MongoClient(mongo_uri, serverSelectionTimeoutMS=15000)
    collection = client[args.database][args.collection]
    inserted = collection.insert_one(document)
    print(f"Inserted Chisel 7 support audit document: {inserted.inserted_id}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
