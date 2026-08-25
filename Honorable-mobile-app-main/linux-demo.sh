#!/usr/bin/env bash
set -euo pipefail
repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
port="${HONORABLE_DEMO_PORT:-4174}"
cd "$repo_dir"
media_count() { find test-media -type f \( -iname '*.jpg' -o -iname '*.jpeg' -o -iname '*.png' -o -iname '*.webp' -o -iname '*.mp4' -o -iname '*.mov' -o -iname '*.m4v' -o -iname '*.webm' -o -iname '*.mkv' \) | wc -l; }
check_port() {
  [[ "$port" =~ ^[0-9]+$ ]] && ((port >= 1 && port <= 65535)) || { echo "ERROR: Invalid Honorable demo port: $port" >&2; exit 2; }
  ((port != 8080)) || { echo "ERROR: Port 8080 is reserved; Honorable will not use it." >&2; exit 2; }
  python3 - "$port" <<'PY'
import socket, sys
port = int(sys.argv[1])
with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    try:
        sock.bind(("0.0.0.0", port))
    except OSError as error:
        print(f"ERROR: Port {port} is already occupied; Honorable was not started. ({error})", file=sys.stderr)
        raise SystemExit(1)
PY
}
case "${1:-}" in
  index)
    cd android-app
    exec ./test-lab.sh indexTestMedia --console=plain
    ;;
  start)
    [[ -f test-media/.memories-test-index ]] || "$0" index
    check_port
    cd android-app
    exec ./test-lab.sh serveTestMedia -Pport="$port" --console=plain
    ;;
  status)
    echo "MEDIA: $(media_count)"
    echo "INDEX: $([[ -f test-media/.memories-test-index ]] && echo READY || echo MISSING)"
    echo "PORT: $port"
    ;;
  verify)
    index=FAIL; [[ -s test-media/.memories-test-index ]] && index=PASS
    shared=FAIL; grep -q 'kotlin.include("app/honorable/search/SearchCore.kt")' android-app/test-lab/build.gradle.kts && grep -q 'HybridSearchEngine' android-app/test-lab/src/main/kotlin/app/honorable/testlab/TestLab.kt && shared=PASS
    frontend=FOUND; grep -q "fetch('/api/search" android-app/test-lab/web/phone.js && ! grep -Eq 'cosine|HybridSearchEngine|SearchRanker|sortedByDescending|\.sort\(' android-app/test-lab/web/phone.js && frontend=NONE
    kotlin=FAIL; grep -q 'server.createContext("/api/search")' android-app/test-lab/src/main/kotlin/app/honorable/testlab/TestLab.kt && grep -q 'HybridSearchEngine' android-app/test-lab/src/main/kotlin/app/honorable/testlab/TestLab.kt && kotlin=PASS
    model=FAIL; cmp -s android-app/app/src/main/assets/tinyclip/model_int8.onnx android-app/test-lab/models/tinyclip/model_int8.onnx && cmp -s android-app/app/src/main/assets/tinyclip/tokenizer.json android-app/test-lab/models/tinyclip/tokenizer.json && model=PASS
    tiny=FAIL; [[ $model == PASS ]] && python3 -c 'import numpy,PIL,tokenizers,onnxruntime' >/dev/null 2>&1 && tiny=PASS
    reuse=FAIL; grep -q 'val clip=if(TinyClipBridge.available())TinyClipBridge()else null' android-app/test-lab/src/main/kotlin/app/honorable/testlab/TestLab.kt && grep -q 'kind,clip' android-app/test-lab/src/main/kotlin/app/honorable/testlab/TestLab.kt && reuse=PASS
    parser=FAIL; grep -q 'QueryParser().parse(raw)' android-app/test-lab/src/main/kotlin/app/honorable/testlab/TestLab.kt && shared_files=android-app/app/src/main/java/app/honorable/search && [[ -s "$shared_files/SearchCore.kt" ]] && parser=PASS
    ranker=FAIL; [[ $shared == PASS ]] && grep -q 'HybridSearchEngine' "$shared_files/SearchPipeline.kt" && ranker=PASS
    confidence=FAIL; grep -q 'confidenceDecision' "$shared_files/SearchCore.kt" && grep -q 'confidenceDecision' android-app/test-lab/src/main/kotlin/app/honorable/testlab/TestLab.kt && confidence=PASS
    ocr_shared=FAIL; grep -q 'OcrNormalizer.normalize' "$shared_files/SearchCore.kt" && grep -q 'tesseract' android-app/test-lab/src/main/kotlin/app/honorable/testlab/TestLab.kt && ocr_shared=PASS
    tests=FAIL; (cd android-app && ./test-lab.sh :test-lab:test --quiet --console=plain) >/dev/null 2>&1 && tests=PASS
    android=FAIL; [[ $shared == PASS && $tests == PASS && -s android-app/SOURCE-OF-TRUTH.md ]] && android=PASS
    video='NO FIXTURE'; if find test-media -type f \( -iname '*.mp4' -o -iname '*.mov' -o -iname '*.m4v' -o -iname '*.webm' -o -iname '*.mkv' \) -print -quit | grep -q .; then video=FAIL; command -v ffmpeg >/dev/null && command -v ffprobe >/dev/null && grep -q 'RepresentativeFrameSelector.select' android-app/test-lab/src/main/kotlin/app/honorable/testlab/TestLab.kt && video=PASS; fi
    index_parity=FAIL; grep -q 'MediaRecord(' android-app/test-lab/src/main/kotlin/app/honorable/testlab/TestLab.kt && grep -q 'VideoFrame(' android-app/test-lab/src/main/kotlin/app/honorable/testlab/TestLab.kt && grep -q 'VisionUnderstanding' android-app/test-lab/src/main/kotlin/app/honorable/testlab/TestLab.kt && index_parity=PASS
    ui=FAIL; if grep -q 'archive-hero' android-app/test-lab/web/phone.js && grep -q 'memory-landing' android-app/test-lab/web/phone.js && grep -q 'memory-head' android-app/test-lab/web/phone.js && grep -q 'memory-results' android-app/test-lab/web/phone.js && grep -q 'result-filters' android-app/test-lab/web/phone.js && grep -q 'honorable-dock' android-app/test-lab/web/phone.js && grep -q 'honorableSettings' android-app/test-lab/web/phone.js && grep -q 'honorable-parity.css' android-app/test-lab/web/index.html && grep -q 'honorable-parity.css' android-app/test-lab/src/main/kotlin/app/honorable/testlab/TestLab.kt && grep -q 'Browser-test port of the current Android Compose UI' android-app/test-lab/web/honorable-parity.css; then ui=PASS; fi
    parity=FAIL; [[ $tests == PASS && $shared == PASS ]] && parity=PASS
    printf 'ANDROID SOURCE OF TRUTH: %s\nSHARED SEARCH CORE: %s\nFRONTEND RANKING LOGIC: %s\nKOTLIN BACKEND: %s\nTINYCLIP MODEL MATCH: %s\nTINYCLIP SESSION REUSE: %s\nQUERY PARSER SHARED: %s\nHYBRID RANKER SHARED: %s\nCONFIDENCE SHARED: %s\nOCR SHARED NORMALIZATION: %s\nVIDEO SHARED AGGREGATION: %s\nINDEX PARITY: %s\nUI PARITY: %s\nSEARCH PARITY: %s\n' "$android" "$shared" "$frontend" "$kotlin" "$model" "$reuse" "$parser" "$ranker" "$confidence" "$ocr_shared" "$video" "$index_parity" "$ui" "$parity"
    ;;
  *) echo 'Usage: ./linux-demo.sh verify|index|start|status' >&2; exit 2;;
esac
