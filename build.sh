#!/usr/bin/env bash
# macOS / Linux 构建入口；流程与参数说明见 docs/technical.md。
set -euo pipefail

project_root="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
if ! command -v python3 >/dev/null 2>&1; then
    printf '%s\n' '构建需要 Python 3.9+，请安装后重试（见 docs/technical.md）。' >&2
    exit 1
fi
exec python3 "$project_root/scripts/build_support.py" "$@"
