# Memories search evaluation

`dataset.example.json` documents the local labeled-data format. Replace sample IDs
with IDs from a consented, non-personal evaluation library before reporting
Recall@1/5/10 or MRR. The harness compares ranked ID lists without uploading
queries, media, metadata, or embeddings.

Required comparison modes for a real evaluation run:

1. TinyCLIP similarity only
2. legacy hybrid weights
3. improved hybrid ranker

No real-media quality metrics have been measured yet.
