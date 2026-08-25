const DB_NAME = "NovelLensDB";
const DB_VERSION = 2;

function requestResult(request) {
  return new Promise((resolve, reject) => {
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

export function openDatabase() {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION);
    request.onupgradeneeded = () => {
      const db = request.result;
      if (!db.objectStoreNames.contains("novels")) db.createObjectStore("novels", { keyPath: "id" });
      if (!db.objectStoreNames.contains("characters")) {
        const store = db.createObjectStore("characters", { keyPath: "id" });
        store.createIndex("novelId", "novelId", { unique: false });
      }
      if (!db.objectStoreNames.contains("details")) db.createObjectStore("details", { keyPath: "characterId" });
      if (!db.objectStoreNames.contains("sync")) db.createObjectStore("sync", { keyPath: "novelId" });
      if (!db.objectStoreNames.contains("unadded")) {
        const pending = db.createObjectStore("unadded", { keyPath: "id" });
        pending.createIndex("novelId", "novelId", { unique: false });
      }
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

async function transact(storeName, mode, action) {
  const db = await openDatabase();
  try {
    const transaction = db.transaction(storeName, mode);
    const result = await action(transaction.objectStore(storeName));
    await new Promise((resolve, reject) => {
      transaction.oncomplete = resolve;
      transaction.onerror = () => reject(transaction.error);
      transaction.onabort = () => reject(transaction.error);
    });
    return result;
  } finally {
    db.close();
  }
}

export const db = {
  get: (store, key) => transact(store, "readonly", (objectStore) => requestResult(objectStore.get(key))),
  put: (store, value) => transact(store, "readwrite", (objectStore) => requestResult(objectStore.put(value))),
  putMany: (store, values) => transact(store, "readwrite", async (objectStore) => {
    for (const value of values) await requestResult(objectStore.put(value));
  }),
  deleteMany: (store, keys) => transact(store, "readwrite", async (objectStore) => {
    for (const key of keys) await requestResult(objectStore.delete(key));
  }),
  byNovel: (novelId) => transact("characters", "readonly", (store) => requestResult(store.index("novelId").getAll(novelId))),
  pendingByNovel: (novelId) => transact("unadded", "readonly", (store) => requestResult(store.index("novelId").getAll(novelId)))
};
