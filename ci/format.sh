#!/usr/bin/env bash

# This script is derived from https://github.com/ray-project/ray/blob/5ce25a57a0949673d17f3a8784f05b2d65290524/ci/lint/format.sh.

# Ruff formatter (if installed). This script formats all changed files from the last mergebase.
# You are encouraged to run this locally before pushing changes for review.

# Cause the script to exit if a single command fails
set -euox pipefail

SHELLCHECK_VERSION_REQUIRED="0.7.1"

install_nodejs() {
  #intall nodejs
  filename="node-v16.17.1-linux-x64"
  pkg="$filename.tar.gz"
  NODE_URL="https://nodejs.org/dist/v16.17.1/$pkg"
  echo "start to download $pkg from $NODE_URL"
  wget -q $NODE_URL -O "$pkg"
  echo "download $pkg succeeds"
  tar -C . -xzf "$pkg"
  export PATH="$(pwd)/$filename/bin:$PATH"
  node -v
  npm -v
}

# Check for ruff
if ! [ -x "$(command -v ruff)" ]; then
    echo "ruff not installed. Install with: pip install ruff"
    exit 1
fi

# this stops git rev-parse from failing if we run this from the .git directory
builtin cd "$(dirname "${BASH_SOURCE:-$0}")"

ROOT="$(git rev-parse --show-toplevel)"
builtin cd "$ROOT" || exit 1

# params: tool name, tool version, required version
tool_version_check() {
    if [ "$2" != "$3" ]; then
        echo "WARNING: Fory uses $1 $3, You currently are using $2. This might generate different results."
    fi
}

if command -v shellcheck >/dev/null; then
    SHELLCHECK_VERSION=$(shellcheck --version | awk '/^version:/ {print $2}')
    tool_version_check "shellcheck" "$SHELLCHECK_VERSION" "$SHELLCHECK_VERSION_REQUIRED"
else
    echo "INFO: Fory uses shellcheck for shell scripts, which is not installed. You may install shellcheck=$SHELLCHECK_VERSION_REQUIRED with your system package manager."
fi

if command -v clang-format >/dev/null; then
  CLANG_FORMAT_VERSION=$(clang-format --version | awk '{print $3}')
  tool_version_check "clang-format" "$CLANG_FORMAT_VERSION" "12.0.0"
else
    echo "WARNING: clang-format is not installed!"
fi

if ! command -v node >/dev/null; then
  echo "INFO: node is not installed, start to install it"
  install_nodejs
fi

if [ ! -f "$ROOT/javascript/node_modules/.bin/eslint" ]; then
  echo "eslint is not installed, start to install it."
  pushd "$ROOT/javascript"
  npm install --registry=https://registry.npmmirror.com
  popd
fi

if command -v java >/dev/null; then
    echo "Java installed"
    java -version
else
    echo "WARNING:java is not installed, skip format java files!"
fi

SHELLCHECK_FLAGS=(
  --exclude=1090  # "Can't follow non-constant source. Use a directive to specify location."
  --exclude=1091  # "Not following {file} due to some error"
  --exclude=2207  # "Prefer mapfile or read -a to split command output (or quote to avoid splitting)." -- these aren't compatible with macOS's old Bash
)

GIT_LS_EXCLUDES=(
  ':(exclude)src/thirdparty/'
)

# Format specified files
format_files() {
    local shell_files=() python_files=() bazel_files=()

    local name
    for name in "$@"; do
      local base="${name%.*}"
      local suffix="${name#${base}}"

      local shebang=""
      read -r shebang < "${name}" || true
      case "${shebang}" in
        '#!'*)
          shebang="${shebang#/usr/bin/env }"
          shebang="${shebang%% *}"
          shebang="${shebang##*/}"
          ;;
      esac

      if [ "${base}" = "WORKSPACE" ] || [ "${base}" = "BUILD" ] || [ "${suffix}" = ".BUILD" ] || [ "${suffix}" = ".bazel" ] || [ "${suffix}" = ".bzl" ]; then
        bazel_files+=("${name}")
      elif [ -z "${suffix}" ] && [ "${shebang}" != "${shebang#python}" ] || [ "${suffix}" != "${suffix#.py}" ]; then
        python_files+=("${name}")
      elif [ -z "${suffix}" ] && [ "${shebang}" != "${shebang%sh}" ] || [ "${suffix}" != "${suffix#.sh}" ]; then
        shell_files+=("${name}")
      else
        echo "error: failed to determine file type: ${name}" 1>&2
        return 1
      fi
    done

    if [ 0 -lt "${#python_files[@]}" ]; then
      ruff format "${python_files[@]}"
      ruff check --fix "${python_files[@]}"
    fi
}

format_all_scripts() {
    echo "$(date)" "Ruff format...."
    git ls-files -- '*.py' '*.pyx' '*.pxd' '*.pxi' "${GIT_LS_EXCLUDES[@]}" | xargs -P 10 \
      ruff format

    echo "$(date)" "Ruff check...."
    git ls-files -- '*.py' '*.pyx' '*.pxd' '*.pxi' "${GIT_LS_EXCLUDES[@]}" | xargs \
      ruff check --fix
}

format_java() {
    if command -v mvn >/dev/null ; then
      echo "Maven installed"
      cd "$ROOT/java"
      mvn -T10 --no-transfer-progress spotless:apply
      mvn -T10 --no-transfer-progress checkstyle:check
      cd "$ROOT/java/benchmark"
      mvn -T10 --no-transfer-progress spotless:apply
      cd "$ROOT/integration_tests"
      dirs=("graalvm_tests" "jdk_compatibility_tests" "latest_jdk_tests")
      for d in "${dirs[@]}" ; do
        pushd "$d"
          mvn -T10 --no-transfer-progress spotless:apply
        popd
      done
    else
      echo "Maven not installed, skip java check"
    fi
}

# Format all files, and print the diff to stdout for travis.
format_all() {
    format_all_scripts "${@}"

    echo "$(date)" "clang-format...."
    if command -v clang-format >/dev/null; then
      git ls-files -- '*.cc' '*.h' '*.proto' "${GIT_LS_EXCLUDES[@]}" | xargs -P 5 clang-format -i
    fi

    echo "$(date)" "format java...."
    if command -v java >/dev/null; then
      format_java
    fi

    echo "$(date)" "format javascript...."
    if command -v node >/dev/null; then
      pushd "$ROOT"
      git ls-files -- '*.ts' "${GIT_LS_EXCLUDES[@]}" | xargs -P 5 node ./javascript/node_modules/.bin/eslint
      popd
    fi

    echo "$(date)" "format go...."
    if command -v go >/dev/null; then
      git ls-files -- '*.go' "${GIT_LS_EXCLUDES[@]}" | xargs -P 5 gofmt -w
    fi

    echo "$(date)" "done!"
}

# Format files that differ from main branch. Ignores dirs that are not slated
# for autoformat yet.
format_changed() {
    # The `if` guard ensures that the list of filenames is not empty, which
    # could cause the formatter to receive 0 positional arguments, making
    # it error.
    #
    # `diff-filter=ACRM` and $MERGEBASE is to ensure we only format files that
    # exist on both branches.
    MERGEBASE="$(git merge-base origin/main HEAD)"

    if ! git diff --diff-filter=ACRM --quiet --exit-code "$MERGEBASE" -- '*.py' '*.pyx' '*.pxd' '*.pxi' &>/dev/null; then
        git diff --name-only --diff-filter=ACRM "$MERGEBASE" -- '*.py' '*.pyx' '*.pxd' '*.pxi' | xargs -P 5 \
            ruff format
        git diff --name-only --diff-filter=ACRM "$MERGEBASE" -- '*.py' '*.pyx' '*.pxd' '*.pxi' | xargs -P 5 \
            ruff check --fix
    fi

    if which clang-format >/dev/null; then
        if ! git diff --diff-filter=ACRM --quiet --exit-code "$MERGEBASE" -- '*.cc' '*.h' &>/dev/null; then
            git diff --name-only --diff-filter=ACRM "$MERGEBASE" -- '*.cc' '*.h' | xargs -P 5 \
                 clang-format -i
        fi
    fi

    if command -v java >/dev/null; then
       if ! git diff --diff-filter=ACRM --quiet --exit-code "$MERGEBASE" -- '*.java' &>/dev/null; then
         format_java
       fi
    fi

    if which go >/dev/null; then
        if ! git diff --diff-filter=ACRM --quiet --exit-code "$MERGEBASE" -- '*.go' &>/dev/null; then
            git diff --name-only --diff-filter=ACRM "$MERGEBASE" -- '*.go' | xargs -P 5 \
                  gofmt -w
        fi
    fi

    if which node >/dev/null; then
        pushd "$ROOT"
        if ! git diff --diff-filter=ACRM --quiet --exit-code "$MERGEBASE" -- '*.ts' &>/dev/null; then
            git diff --name-only --diff-filter=ACRM "$MERGEBASE" -- '*.ts' | xargs -P 5 \
                  node ./javascript/node_modules/.bin/eslint
        fi
        # Install prettier globally
        npm install -g prettier
        # Fix markdown files
        prettier --write "**/*.md"
        popd
    fi
}


# This flag formats individual files. --files *must* be the first command line
# arg to use this option.
if [ "${1-}" == '--files' ]; then
    format_files "${@:2}"
# If `--all` or `--scripts` are passed, then any further arguments are ignored.
# Format the entire python directory and other scripts.
elif [ "${1-}" == '--all-scripts' ]; then
    format_all_scripts "${@}"
    if [ -n "${FORMAT_SH_PRINT_DIFF-}" ]; then git --no-pager diff; fi
# Format the all Python, C++, Java and other script files.
elif [ "${1-}" == '--all' ]; then
    format_all "${@}"
    if [ -n "${FORMAT_SH_PRINT_DIFF-}" ]; then git --no-pager diff; fi
elif [ "${1-}" == '--java' ]; then
    format_java
else
    # Add the origin remote if it doesn't exist
    if ! git remote -v | grep -q origin; then
        git remote add 'origin' 'https://github.com/apache/fory.git'
    fi

    # use unshallow fetch for `git merge-base origin/main HEAD` to work.
    # Only fetch main since that's the branch we're diffing against.
    git fetch origin main --unshallow || true

    echo "Format only the files that changed in last commit."
    format_changed
fi

# Ensure import ordering
# Make sure that for every import psutil; import setproctitle
# There's a import ray above it.

PYTHON_EXECUTABLE=${PYTHON_EXECUTABLE:-python}

if ! git diff --quiet &>/dev/null; then
    echo 'Reformatted changed files. Please review and stage the changes.'
    echo 'Files updated:'
    echo

    git --no-pager diff

    exit 1
fi
