# Web test storage index

This manifest is used only by the browser test shell. It does not alter the
Android MediaStore, Room database, or production index.

1. Put a photo or video somewhere under `test-media/`, for example
   `test-media/web-storage/beach.jpg`.
2. Add an item to `storage-index.json` using its path relative to `test-media`:

```json
{
  "adapter": "web-test-storage-index",
  "items": [
    {
      "name": "Beach afternoon",
      "uri": "web-storage/beach.jpg",
      "type": "IMAGE",
      "capturedAt": 1788566400000
    }
  ]
}
```

Allowed `type` values are `IMAGE` and `VIDEO`. Reload the browser to display
manifest entries. Use the existing **Refresh media** adapter or restart with
`./linux-demo.sh index` when the file must also be searchable through the shared
Kotlin engine.
