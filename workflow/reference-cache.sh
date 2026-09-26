#!/usr/bin/env bash
# Rebuilds the strict-review reference cache (see workflow/rules/reference-cache.md).
#
#   workflow/reference-cache.sh [cache-root]      # default: <repo root>/.reference-cache
#
# Repo-local shape: a real directory whose own .gitignore holds `*`. Idempotent: an entry that
# already exists is skipped. Every command that shapes an entry runs inside that entry and is
# appended to its SOURCE, so SOURCE is the exact chain that produced the tree.
set -euo pipefail

repo_root=$(git -C "$(dirname "$0")" rev-parse --show-toplevel)
root=${1:-$repo_root/.reference-cache}

mkdir -p "$root"
chmod u+w "$root"
[ -f "$root/.gitignore" ] || { printf '*\n' > "$root/.gitignore"; chmod a-w "$root/.gitignore"; }

# --- deletions shared by every entry ---------------------------------------------------------
# Tooling, CI, agent-instruction files, wrappers, lockfiles, tests and every binary: none of it
# answers a question about how the library behaves or is meant to be used.
common_deletions=(
  "rm -rf .git"
  "find . -type d \( -name .github -o -name .circleci -o -name .gitlab -o -name .buildkite -o -name .devcontainer -o -name .vscode -o -name .idea -o -name .husky -o -name .changeset -o -name .yarn -o -name .claude -o -name .cursor -o -name .junie -o -name .mvn -o -name .teamcity -o -name .azure-pipelines -o -name __tests__ -o -name __snapshots__ -o -name __fixtures__ -o -name __mocks__ -o -name e2e -o -name jmh -o -name benchmark -o -name benchmarks -o -name testFixtures -o -name integTest -o -name crossVersionTest -o -name smokeTest -o -name docsTest -o -name fixtures -o -name playground -o -name test-results -o -name skills \) -prune -exec rm -rf {} +"
  "find . -type d \( -path '*/src/test' -o -path '*/src/tests' -o -path ./test -o -path ./tests -o -path './packages/*/test' -o -path './packages/*/tests' -o -path './packages/*/*/test' -o -path './packages/*/*/tests' \) -prune -exec rm -rf {} +"
  "find . -type f \( -name CLAUDE.md -o -name AGENTS.md -o -name GEMINI.md -o -name .cursorrules -o -name .windsurfrules -o -name copilot-instructions.md -o -name gradlew -o -name gradlew.bat -o -name 'gradle-wrapper.*' -o -name mvnw -o -name mvnw.cmd -o -name 'Dockerfile*' -o -name 'docker-compose*' -o -name .gitlab-ci.yml -o -name .travis.yml -o -name appveyor.yml -o -name azure-pipelines.yml -o -name Jenkinsfile -o -name 'renovate.json*' -o -name .editorconfig -o -name .gitattributes -o -name .gitignore -o -name .npmignore -o -name .npmrc -o -name .nvmrc -o -name .node-version -o -name pnpm-lock.yaml -o -name yarn.lock -o -name package-lock.json -o -name 'bun.lock*' -o -name '*.test.*' -o -name '*.spec.ts' -o -name '*.spec.tsx' -o -name '*.spec.js' -o -name '*.spec.jsx' -o -name '*.snap' -o -iname '*.svg' \) -delete"
  "find . -type f -size +0 -exec grep -ILZ . {} + | xargs -0 -r rm -f"
  "find . -type l -delete"
  "find . -type d -empty -delete"
)

# entry <source> <name@version> <repo> <tag|commit:SHA> [--sparse <path>...] [--extra <command>...]
entry() {
  local source=$1 id=$2 repo=$3 ref=$4
  shift 4
  local sparse=() extra=() mode=
  for arg in "$@"; do
    case $arg in
      --sparse) mode=sparse ;;
      --extra) mode=extra ;;
      *) if [ "$mode" = sparse ]; then sparse+=("$arg"); else extra+=("$arg"); fi ;;
    esac
  done

  local dest=$root/$source/$id
  if [ -e "$dest" ]; then echo "skip   $source/$id"; return; fi
  echo "build  $source/$id"

  mkdir -p "$root/$source"
  chmod u+w "$root/$source"
  local work=$dest.partial log
  rm -rf "$work"; mkdir "$work"
  log=$(mktemp)

  local cmds=()
  if [[ $ref == commit:* ]]; then
    local sha=${ref#commit:}
    cmds+=("git init -q . && git remote add origin $repo")
    if [ ${#sparse[@]} -gt 0 ]; then
      cmds+=("git fetch -q --depth 1 --filter=blob:none origin $sha")
      cmds+=("git sparse-checkout set ${sparse[*]}")
    else
      cmds+=("git fetch -q --depth 1 origin $sha")
    fi
    cmds+=("git checkout -q FETCH_HEAD")
  elif [ ${#sparse[@]} -gt 0 ]; then
    cmds+=("git clone -q --depth 1 --filter=blob:none --sparse --branch $ref $repo .")
    cmds+=("git sparse-checkout set ${sparse[*]}")
  else
    cmds+=("git clone -q --depth 1 --branch $ref $repo .")
  fi
  cmds+=("${extra[@]}" "${common_deletions[@]}")

  for c in "${cmds[@]}"; do
    printf '%s\n' "$c" >> "$log"
    (cd "$work" && bash -c "$c")
  done

  {
    printf '# %s/%s\n# repo: %s\n# ref:  %s\n' "$source" "$id" "$repo" "$ref"
    [ ${#sparse[@]} -gt 0 ] && printf '# sparse: owner-approved deviation from whole-tree cloning\n'
    [ -n "${NOTE:-}" ] && printf '# note: %s\n' "$NOTE"
    printf '# commands, run in order inside the entry directory:\n'
    cat "$log"
  } > "$work/SOURCE"
  rm -f "$log"

  mv "$work" "$dest"
  chmod -R a-w "$dest"
  chmod a-w "$root/$source"
}

gh=https://github.com

# --- backend ---------------------------------------------------------------------------------
entry spring-projects spring-boot@4.1.1        $gh/spring-projects/spring-boot       v4.1.1
entry spring-projects spring-framework@7.0.9   $gh/spring-projects/spring-framework  v7.0.9
entry FasterXML       jackson-databind@3.1.5   $gh/FasterXML/jackson-databind        jackson-databind-3.1.5
entry FasterXML       jackson-core@3.1.5       $gh/FasterXML/jackson-core            jackson-core-3.1.5
# Version unresolved: Spring Boot 4.1.1's BOM says 1.5.38, libprunus pins 1.6.3; both kept.
entry qos-ch          logback@1.5.38           $gh/qos-ch/logback                    v_1.5.38
entry qos-ch          logback@1.6.3            $gh/qos-ch/logback                    v_1.6.3
entry jakartaee       servlet@6.1.0            $gh/jakartaee/servlet                 6.1.0-RELEASE
entry flyway          flyway@12.4.0            $gh/flyway/flyway                     flyway-12.4.0
entry jspecify        jspecify@1.0.1           $gh/jspecify/jspecify                 v1.0.1
NOTE="the public branch is 0.3.0-SNAPSHOT; the project builds against 0.4.0-SNAPSHOT, which is not published" \
entry libprunus       libprunus-java-core@089136d $gh/libprunus/libprunus-java-core commit:089136d54b8387f2ac70e9713eee3ef0a29a189b
entry gradle          gradle@9.7.1             $gh/gradle/gradle                     v9.7.1 \
  --sparse platforms/documentation/docs/src/docs/userguide platforms/documentation/docs/src/docs/dsl \
           subprojects/core-api/src/main platforms/software/dependency-management/src/main \
           platforms/core-configuration/model-core/src/main platforms/core-configuration/file-collections/src/main \
           platforms/software/testing-base/src/main platforms/jvm/testing-jvm/src/main \
           platforms/core-configuration/kotlin-dsl/src/main
entry node-gradle     gradle-node-plugin@7.1.0 $gh/node-gradle/gradle-node-plugin    7.1.0
entry autonomousapps  dependency-analysis-gradle-plugin@3.19.1 $gh/autonomousapps/dependency-analysis-gradle-plugin v3.19.1
entry apache          tomcat@11.0.24           $gh/apache/tomcat                     11.0.24
entry jk1             Gradle-License-Report@3.1.4 $gh/jk1/Gradle-License-Report     v3.1.4
entry zonkyio         embedded-postgres@2.2.2  $gh/zonkyio/embedded-postgres         v2.2.2
entry openjdk         jdk@25                   $gh/openjdk/jdk                       jdk-25-ga \
  --sparse src/java.base/share/classes

# --- frontend --------------------------------------------------------------------------------
entry facebook        react@19.3.0             $gh/facebook/react                    v19.3.0 \
  --extra "rm -rf fixtures compiler"
entry facebook        react@eslint-plugin-react-hooks-7.1.1 $gh/facebook/react       eslint-plugin-react-hooks@7.1.1 \
  --sparse packages/eslint-plugin-react-hooks compiler/packages/babel-plugin-react-compiler/src
entry mui             material-ui@9.4.0        $gh/mui/material-ui                   v9.4.0 \
  --extra "rm -rf docs/translations docs/public" \
          "find packages/mui-icons-material/lib -maxdepth 1 -name '*.js' -printf '%f\\n' | sed 's/\\.js\$//' | sort > packages/mui-icons-material/ICON_NAMES.txt" \
          "rm -rf packages/mui-icons-material/lib packages/mui-icons-material/legacy" \
          "find docs -type f \( -name '*-zh.md' -o -name '*-pt.md' -o -name '*-es.md' -o -name '*-ja.md' \) -delete"
entry emotion-js      emotion@react-11.14.0    $gh/emotion-js/emotion                @emotion/react@11.14.0
entry emotion-js      emotion@styled-11.14.1   $gh/emotion-js/emotion                @emotion/styled@11.14.1
entry remix-run       react-router@8.4.0       $gh/remix-run/react-router            react-router@8.4.0
entry formatjs        formatjs@react-intl-12.1.1 $gh/formatjs/formatjs               react-intl@12.1.1
entry vitejs          vite@8.3.0               $gh/vitejs/vite                       v8.3.0
entry vitest-dev      vitest@5.0.1             $gh/vitest-dev/vitest                 v5.0.1
entry vitejs          vite-plugin-react@plugin-react-6.1.1 $gh/vitejs/vite-plugin-react plugin-react@6.1.1
NOTE="upstream never tagged 2.3.3; this is the gitHead npm records for the published 2.3.3" \
entry richardtallent  vite-plugin-singlefile@2.3.3 $gh/richardtallent/vite-plugin-singlefile commit:fbd6d0dee4becf69df0ab7b33d95c7b97f9a5376
entry eslint          eslint@10.10.0           $gh/eslint/eslint                     v10.10.0
NOTE="@eslint/js 10.0.1 is released from the eslint repo at its own commit (npm gitHead)" \
entry eslint          eslint@js-10.0.1         $gh/eslint/eslint                     commit:84fb885d49ac810e79a9491276b4828b53d913e5 \
  --sparse packages/js
entry typescript-eslint typescript-eslint@8.70.0 $gh/typescript-eslint/typescript-eslint v8.70.0
entry microsoft       TypeScript@6.0.3         $gh/microsoft/TypeScript              v6.0.3 \
  --sparse src/compiler src/lib
NOTE="react.dev publishes no tags; pinned to the default branch HEAD on 2026-09-26" \
entry reactjs         react.dev@44b0b5f        $gh/reactjs/react.dev                 commit:44b0b5f10b7f6477bf146d26444717fb4930439f
entry rolldown        rolldown@1.2.9           $gh/rolldown/rolldown                 v1.2.9

chmod a-w "$root"
echo "done: $root"
