#!/usr/bin/env bash
# tools/rename-brand.sh
#
# Brand-prefix renamer for the TraceHarbor monorepo.
# Safely migrates a Java/Kotlin/JNI/Gradle package prefix to a new one across
# the whole repo: source files (package decl, imports, string literals),
# physical directories (via `git mv` to preserve history), JNI symbol names,
# Android Manifest `package=`, and Gradle `namespace`.
#
# Usage:
#   tools/rename-brand.sh --from <old.prefix> --to <new.prefix> [options]
#
# Required:
#   --from <prefix>        e.g. com.kernelflux  or  com.tencent
#   --to   <prefix>        e.g. cn.example      or  com.kernelflux
#
# Modes (default = dry-run):
#   --dry-run              Print what would change. Default.
#   --apply                Actually perform the changes.
#
# Optional flags:
#   --no-jni               Skip JNI symbol rewriting (Java_<pkg>_…).
#   --no-namespace         Skip Android Manifest `package=` and Gradle namespace.
#   --no-strings           Skip rewriting "com/old/pkg" / "Lcom/old/pkg;" style
#                          string literals (use only if you know what you do).
#   --root <dir>           Repo root. Default: repo root containing this script.
#   -h | --help            Show help.
#
# Safety guarantees:
#   - Refuses to run --apply on a dirty git tree (use `git stash` first).
#   - Skips: LICENSE*, NOTICE*, COPYRIGHT*, THIRD_PARTY_NOTICES*, *.lock,
#            */build/, */.gradle/, */.cxx/, */.idea/, */.git/, */node_modules/,
#            */docs/ (license/attribution writeups stay verbatim).
#   - Uses `git mv` for directories so blame/history survive.
#   - All matches are anchored on dotted/slashed/underscored package boundaries
#     to avoid eating unrelated identifiers.
#
# Exit code: 0 on success, non-zero otherwise.
set -euo pipefail

# ──────────────────────────────────────────────────────────────────────
# Helpers
# ──────────────────────────────────────────────────────────────────────
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
DEFAULT_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd)"

print_help() {
    sed -n '2,40p' "${BASH_SOURCE[0]}" | sed -e 's/^# \{0,1\}//'
}

err()  { printf '\033[31m[err]\033[0m  %s\n' "$*" >&2; }
warn() { printf '\033[33m[warn]\033[0m %s\n' "$*" >&2; }
info() { printf '\033[36m[info]\033[0m %s\n' "$*" >&2; }
ok()   { printf '\033[32m[ok]\033[0m   %s\n' "$*" >&2; }

# Cross-platform sed -i (GNU vs BSD/macOS).
sed_i() {
    if sed --version >/dev/null 2>&1; then
        sed -i "$@"      # GNU
    else
        sed -i '' "$@"   # BSD/macOS
    fi
}

# ──────────────────────────────────────────────────────────────────────
# Args
# ──────────────────────────────────────────────────────────────────────
FROM=""
TO=""
MODE="dry-run"
DO_JNI=1
DO_NAMESPACE=1
DO_STRINGS=1
ROOT="${DEFAULT_ROOT}"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --from)         FROM="$2"; shift 2 ;;
        --to)           TO="$2";   shift 2 ;;
        --dry-run)      MODE="dry-run"; shift ;;
        --apply)        MODE="apply";   shift ;;
        --no-jni)       DO_JNI=0;       shift ;;
        --no-namespace) DO_NAMESPACE=0; shift ;;
        --no-strings)   DO_STRINGS=0;   shift ;;
        --root)         ROOT="$2"; shift 2 ;;
        -h|--help)      print_help; exit 0 ;;
        *)
            err "Unknown arg: $1"
            print_help
            exit 64
            ;;
    esac
done

if [[ -z "$FROM" || -z "$TO" ]]; then
    err "--from and --to are required"
    print_help
    exit 64
fi

if ! [[ "$FROM" =~ ^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$ ]]; then
    err "--from must be a dotted lowercase prefix (e.g. com.kernelflux)"
    exit 64
fi
if ! [[ "$TO" =~ ^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$ ]]; then
    err "--to must be a dotted lowercase prefix (e.g. cn.example)"
    exit 64
fi
if [[ "$FROM" == "$TO" ]]; then
    err "--from and --to are identical"
    exit 64
fi

cd "$ROOT"
if [[ ! -d ".git" ]]; then
    err "Repo root $ROOT is not a git repo. Aborting."
    exit 65
fi

if [[ "$MODE" == "apply" ]]; then
    if [[ -n "$(git status --porcelain)" ]]; then
        err "Working tree is dirty. Commit or stash first, then re-run --apply."
        git status --short
        exit 66
    fi
fi

# Derived forms (use `tr` to avoid bash escaping quirks with `/` inside ${//}).
FROM_DOT="$FROM"                                          # com.kernelflux
TO_DOT="$TO"                                              # cn.example
FROM_SLASH="$(printf '%s' "$FROM" | tr '.' '/')"          # com/kernelflux
TO_SLASH="$(printf '%s' "$TO"   | tr '.' '/')"            # cn/example
FROM_UNDER="$(printf '%s' "$FROM" | tr '.' '_')"          # com_kernelflux  (JNI)
TO_UNDER="$(printf '%s' "$TO"   | tr '.' '_')"            # cn_example      (JNI)
# Sed-escaped forms (escape `.` and `/` for ERE patterns / replacements).
FROM_DOT_ESC="$(printf '%s' "$FROM_DOT"   | sed -e 's|[.]|\\.|g')"
FROM_SLASH_ESC="$(printf '%s' "$FROM_SLASH" | sed -e 's|/|\\/|g')"

info "rename-brand.sh"
info "  root  = $ROOT"
info "  from  = $FROM_DOT  ($FROM_SLASH, JNI: $FROM_UNDER)"
info "  to    = $TO_DOT  ($TO_SLASH, JNI: $TO_UNDER)"
info "  mode  = $MODE   jni=$DO_JNI  namespace=$DO_NAMESPACE  strings=$DO_STRINGS"

# ──────────────────────────────────────────────────────────────────────
# File filter — skip globs we never want to touch
# ──────────────────────────────────────────────────────────────────────
EXCLUDES=(
    -path './.git/*' -o
    -path '*/build/*' -o
    -path '*/.gradle/*' -o
    -path '*/.cxx/*' -o
    -path '*/.idea/*' -o
    -path '*/node_modules/*' -o
    -path '*/.kotlin/*' -o
    -name 'LICENSE*' -o
    -name 'NOTICE*' -o
    -name 'COPYRIGHT*' -o
    -name 'THIRD_PARTY_NOTICES*' -o
    -name '*.png' -o -name '*.jpg' -o -name '*.jpeg' -o -name '*.gif' -o
    -name '*.webp' -o -name '*.so' -o -name '*.a' -o -name '*.jar' -o
    -name '*.class' -o -name '*.zip' -o -name '*.aar' -o -name '*.apk'
)

# Source-content extensions we touch in Step 2 (broad text rewrite).
#
# DELIBERATELY EXCLUDED:
#   *.gradle / *.gradle.kts / *.toml / *.properties / *.json / *.xml :
#       these may legitimately reference third-party maven coordinates
#       like `com.tencent:mmkv` or `com.tencent.tinker:tinker-…` that
#       MUST NOT be rewritten. Their relevant fields (namespace=,
#       package=, androidNamespaces map) are handled in Step 3.
#   *.pro :
#       ProGuard `-keep class com.tencent.<lib>...` usually targets
#       real third-party (e.g. WeChat hook) classes.
#
TEXT_EXTS=(
    java kt
    cpp cc cxx c h hpp hxx
)

# Directory globs to skip (passed to grep --exclude-dir).
# - docs/  : intentionally references old prefixes in plans / migration guides
# - tools/ : the rename script itself contains examples like `--from com.tencent`
GREP_EXCLUDE_DIRS=( .git build .gradle .cxx .idea node_modules .kotlin docs tools )
# File globs to skip (passed to grep --exclude).
# - *.pro              : ProGuard `-keep class com.tencent.x.NativeHook` rules
#                        usually reference real third-party (e.g. WeChat hook
#                        framework) classes — touching them breaks runtime.
# - dependencies.gradle: hand-rolled dependency lists may reference 3rd-party
#                        maven coordinates (`com.tencent.stubs:logger:…`).
GREP_EXCLUDE_FILES=( 'LICENSE*' 'NOTICE*' 'COPYRIGHT*' 'THIRD_PARTY_NOTICES*'
                     '*.pro' 'dependencies.gradle'
                     '*.png' '*.jpg' '*.jpeg' '*.gif' '*.webp'
                     '*.so' '*.a' '*.jar' '*.class' '*.zip' '*.aar' '*.apk' )

# Run `grep -rl` with all our include/exclude filters baked in.
grep_repo() {
    local pattern="$1"
    local args=( -r -l -E )
    local ext
    for ext in "${TEXT_EXTS[@]}"; do
        args+=( --include "*.${ext}" )
    done
    local d
    for d in "${GREP_EXCLUDE_DIRS[@]}"; do
        args+=( --exclude-dir "$d" )
    done
    local f
    for f in "${GREP_EXCLUDE_FILES[@]}"; do
        args+=( --exclude "$f" )
    done
    grep "${args[@]}" -- "$pattern" . 2>/dev/null || true
}

# Run `grep -c` for hit counting (single file).
grep_count() {
    local pattern="$1"
    local file="$2"
    grep -cE -- "$pattern" "$file" 2>/dev/null || echo 0
}

# ──────────────────────────────────────────────────────────────────────
# Step 1: rename physical directories  (git mv)
# ──────────────────────────────────────────────────────────────────────
rename_directories() {
    info "── Step 1: rename directories matching */${FROM_SLASH}/* ──"

    local from_segs to_segs
    IFS='/' read -ra from_segs <<< "$FROM_SLASH"
    IFS='/' read -ra to_segs   <<< "$TO_SLASH"
    local from_first="${from_segs[0]}"   # e.g. com
    local to_first="${to_segs[0]}"       # e.g. cn

    # Find all leaf-most directories ending in /<FROM_SLASH> (e.g. */com/kernelflux)
    # Sort longest-first so nested moves don't trip on each other.
    local dirs
    dirs=$(find . -type d -path "*/${FROM_SLASH}" \
                ! -path './.git/*' ! -path '*/build/*' \
                ! -path '*/.gradle/*' ! -path '*/.cxx/*' \
                ! -path '*/.idea/*' \
                | awk '{ print length, $0 }' | sort -rn | cut -d' ' -f2-)

    if [[ -z "$dirs" ]]; then
        ok "no directories to move"
        return
    fi

    while IFS= read -r src; do
        # src looks like:  ./traceharbor-arscutil/src/main/java/com/tencent
        # We need the parent of the FROM_SLASH (i.e. the .../java part) and
        # the new dest  ".../java/<TO_SLASH>".
        # We deconstruct using the last N path components, where N = len(from_segs).
        local parent="$src"
        local i
        for ((i=0; i<${#from_segs[@]}; i++)); do
            parent="$(dirname "$parent")"
        done
        local dest="${parent}/${TO_SLASH}"

        if [[ -e "$dest" ]]; then
            warn "destination exists, will merge contents: $dest"
        fi

        if [[ "$MODE" == "dry-run" ]]; then
            printf '  git mv %s -> %s\n' "$src" "$dest"
        else
            mkdir -p "$(dirname "$dest")"
            if [[ -e "$dest" ]]; then
                # Merge: move children one by one
                find "$src" -mindepth 1 -maxdepth 1 -print0 | while IFS= read -r -d '' child; do
                    git mv "$child" "$dest/$(basename "$child")"
                done
                rmdir "$src" 2>/dev/null || true
            else
                git mv "$src" "$dest"
            fi
            printf '  moved: %s -> %s\n' "$src" "$dest"
        fi
    done <<< "$dirs"
}

# ──────────────────────────────────────────────────────────────────────
# Step 2: rewrite text content
# ──────────────────────────────────────────────────────────────────────
rewrite_content() {
    info "── Step 2: rewrite source content ──"

    # sed -E expressions to apply.
    local sed_exprs=()

    # 2a. dotted package names: com.kernelflux.bar  ->  cn.example.bar
    sed_exprs+=( -e "s|${FROM_DOT_ESC}|${TO_DOT}|g" )

    # 2b. slashed (Type descriptor / JVM internal-name) form: com/kernelflux/x
    if [[ $DO_STRINGS -eq 1 ]]; then
        sed_exprs+=( -e "s|${FROM_SLASH_ESC}|${TO_SLASH}|g" )
    fi

    # 2c. JNI symbol form:  Java_com_kernelflux_xxx
    if [[ $DO_JNI -eq 1 ]]; then
        sed_exprs+=( -e "s|Java_${FROM_UNDER}_|Java_${TO_UNDER}_|g" )
    fi

    # Combined detection regex (ERE).
    local detect="${FROM_DOT_ESC}|${FROM_SLASH_ESC}"
    [[ $DO_JNI -eq 1 ]] && detect="${detect}|Java_${FROM_UNDER}_"

    local files
    files=$(grep_repo "$detect")

    if [[ -z "$files" ]]; then
        ok "no text files contain the prefix"
        return
    fi

    local n=0
    while IFS= read -r f; do
        [[ -z "$f" ]] && continue
        n=$((n + 1))
        if [[ "$MODE" == "dry-run" ]]; then
            local hits
            hits=$(grep_count "$detect" "$f")
            printf '  %s (%s hits)\n' "$f" "$hits"
        else
            sed_i "${sed_exprs[@]}" "$f"
        fi
    done <<< "$files"

    if [[ "$MODE" == "apply" ]]; then
        ok "rewrote $n file(s)"
    else
        ok "would rewrite $n file(s)"
    fi
}

# ──────────────────────────────────────────────────────────────────────
# Step 3: Android Manifest `package=` + Gradle `namespace`
# ──────────────────────────────────────────────────────────────────────
rewrite_namespace() {
    [[ $DO_NAMESPACE -eq 0 ]] && { info "── Step 3: namespace rewriting skipped (--no-namespace) ──"; return; }
    info "── Step 3: AndroidManifest package= and Gradle namespace ──"

    # Manifests
    local manifests
    manifests=$(find . -type f -name 'AndroidManifest.xml' \
        ! -path '*/build/*' ! -path '*/.gradle/*' ! -path '*/.cxx/*')
    while IFS= read -r m; do
        [[ -z "$m" ]] && continue
        if grep -q "package=\"${FROM_DOT}" "$m" 2>/dev/null; then
            if [[ "$MODE" == "dry-run" ]]; then
                printf '  manifest:  %s\n' "$m"
            else
                sed_i -e "s|package=\"${FROM_DOT_ESC}|package=\"${TO_DOT}|g" "$m"
            fi
        fi
    done <<< "$manifests"

    # Gradle namespaces (Groovy + KTS)
    local gfiles
    gfiles=$(find . -type f \( -name 'build.gradle' -o -name 'build.gradle.kts' \) \
        ! -path '*/build/*' ! -path '*/.gradle/*')
    while IFS= read -r g; do
        [[ -z "$g" ]] && continue
        if grep -qE "namespace[[:space:]=]+['\"]${FROM_DOT}" "$g" 2>/dev/null; then
            if [[ "$MODE" == "dry-run" ]]; then
                printf '  gradle:    %s\n' "$g"
            else
                sed_i -E "s|(namespace[[:space:]=]+['\"])${FROM_DOT_ESC}|\\1${TO_DOT}|g" "$g"
            fi
        fi
    done <<< "$gfiles"

    # androidNamespaces map keys/values in root build.gradle (best-effort literal).
    # We rewrite quoted literals that start with the prefix; this catches the
    # `':module': 'com.kernelflux.x.y'` map entries without touching maven
    # coordinates (which look like 'com.kernelflux:lib:1.0').
    if grep -RqE "androidNamespaces" --include='build.gradle*' . 2>/dev/null; then
        if [[ "$MODE" == "apply" ]]; then
            local gf
            while IFS= read -r gf; do
                [[ -z "$gf" ]] && continue
                # Only quoted dotted package literals (closing quote = same kind)
                # to avoid eating maven coords like 'com.kernelflux:foo:1.0'.
                sed_i \
                    -E "s|'(${FROM_DOT_ESC})([.a-zA-Z0-9_]*)'|'${TO_DOT}\\2'|g" \
                    -E "s|\"(${FROM_DOT_ESC})([.a-zA-Z0-9_]*)\"|\"${TO_DOT}\\2\"|g" \
                    "$gf"
            done < <(find . -type f \( -name 'build.gradle' -o -name 'build.gradle.kts' \) \
                ! -path '*/build/*' ! -path '*/.gradle/*')
        else
            info "  androidNamespaces map will also be touched (root build.gradle*)"
        fi
    fi
}

# ──────────────────────────────────────────────────────────────────────
# Step 4: JNI source filenames sometimes encode the package in the
# *file name itself*  (e.g.  com_kernelflux_xxx_Foo.cpp).  Rename those.
# ──────────────────────────────────────────────────────────────────────
rename_jni_files() {
    [[ $DO_JNI -eq 0 ]] && { info "── Step 4: JNI filename rename skipped (--no-jni) ──"; return; }
    info "── Step 4: rename JNI source files containing ${FROM_UNDER} ──"

    local jfiles
    jfiles=$(find . -type f \( -name "*${FROM_UNDER}*" \) \
        ! -path '*/build/*' ! -path '*/.gradle/*' ! -path '*/.cxx/*' ! -path '*/.git/*' \
        || true)

    if [[ -z "$jfiles" ]]; then
        ok "no JNI filename to rename"
        return
    fi

    while IFS= read -r f; do
        [[ -z "$f" ]] && continue
        local newname
        newname=$(echo "$f" | sed -e "s|${FROM_UNDER}|${TO_UNDER}|g")
        if [[ "$MODE" == "dry-run" ]]; then
            printf '  git mv %s -> %s\n' "$f" "$newname"
        else
            git mv "$f" "$newname"
        fi
    done <<< "$jfiles"
}

# ──────────────────────────────────────────────────────────────────────
# Run pipeline
# ──────────────────────────────────────────────────────────────────────
rewrite_content      # do content first so newly-renamed dirs don't break grep
rename_directories
rewrite_namespace
rename_jni_files

if [[ "$MODE" == "dry-run" ]]; then
    echo
    warn "Dry-run only. Re-run with --apply to perform the changes."
else
    echo
    ok "Done. Recommended next steps:"
    cat <<EOF
   1. Drop stale build caches:
        find . -type d \( -name '.cxx' -o -name 'build' \) -prune -exec rm -rf {} +
   2. Verify compile:
        ./gradlew --stop
        ./gradlew assembleRelease
   3. Inspect diff:
        git status
        git diff --stat
   4. Commit:
        git commit -am "refactor(brand): rename ${FROM_DOT} -> ${TO_DOT}"
EOF
fi
