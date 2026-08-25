---
name: fory-release
description: Prepare an Apache Fory release candidate from a clean release branch, including the version bump, RC tag, JVM staging, ASF source artifacts, SVN upload, and vote email. Use when creating or rerunning a Fory release candidate.
---

# Apache Fory Release

Use the repository release script for the release work. Do not manually reproduce its version-bump, JVM-publication, or source-build logic, and do not add unrelated test runs.

## Required Inputs

Collect these values before starting:

- `release_version`: final version without `v` or an RC suffix, such as `1.7.0`.
- `rc`: RC suffix, such as `rc3`.
- `previous_version`: previous release tag version, such as `1.6.1`.
- Release discussion URL, if already known. If it is not supplied, find the
  exact release thread in the Fory development-list archive as described below.

Load release-manager details from `.local/fory-release.env`. If it does not exist, ask for the following values once, create the ignored local file, and continue. Never commit this file.

```bash
FORY_RELEASE_MANAGER_NAME="..."
FORY_RELEASE_APACHE_EMAIL="..."
FORY_RELEASE_GPG_FINGERPRINT="..."
FORY_DIST_DEV_WC="..."
```

Load the cached values and derive the release values:

```bash
repo_root="$(git rev-parse --show-toplevel)"
release_config="$repo_root/.local/fory-release.env"
test -f "$release_config"
. "$release_config"

release_branch="releases-${release_version}"
rc_tag="v${release_version}-${rc}"
dist_version="${release_version}"
release_manager_name="${FORY_RELEASE_MANAGER_NAME:?missing release manager name}"
apache_email="${FORY_RELEASE_APACHE_EMAIL:?missing Apache email}"
gpg_fingerprint="${FORY_RELEASE_GPG_FINGERPRINT:?missing GPG fingerprint}"
svn_wc="${FORY_DIST_DEV_WC:?missing ASF Subversion working-copy path}"
```

Use the same `dist_version` in Subversion and the vote email.

### Find the release discussion

If the release discussion URL was not supplied, search the
[Fory development-list archive](https://lists.apache.org/list.html?dev@fory.apache.org)
for the exact release version and a `[DISCUSS]` subject. The following command
uses the same Apache Pony Mail search endpoint as the archive UI and accepts
exactly one root discussion thread:

```bash
discussion_url="$(
  curl --fail --silent --show-error --get \
    --data-urlencode "d=lte=1y" \
    --data-urlencode "list=dev" \
    --data-urlencode "domain=fory.apache.org" \
    --data-urlencode "q=${release_version}" \
    --data-urlencode "header_subject=DISCUSS" \
    https://lists.apache.org/api/stats.lua |
    python3 -c '
import json
import sys

version = sys.argv[1]
messages = json.load(sys.stdin).get("emails", [])
matches = [
    message
    for message in messages
    if not message.get("in-reply-to")
    and "[DISCUSS]" in str(message.get("subject", "")).upper()
    and version in str(message.get("subject", ""))
]
if len(matches) != 1:
    raise SystemExit(
        f"Expected one root discussion for {version}, found {len(matches)}"
    )
print("https://lists.apache.org/thread/" + matches[0]["id"])
' "$release_version"
)"
```

Open the resulting URL and verify that its subject and body discuss the exact
`release_version`. If the automated search finds zero or multiple roots, use
the archive UI to search the same exact version and `[DISCUSS]`; ask the release
manager only if the result remains absent or ambiguous. When a URL is supplied,
open and verify it instead of assuming it matches this release.

## Release Workflow

### 1. Create a clean release branch

Run from the repository root. Clean means no staged, modified, or untracked files.

```bash
test -z "$(git status --porcelain)"
test "$(git remote get-url apache)" = "git@github.com:apache/fory.git"
git fetch apache main --tags
git switch -c "$release_branch" apache/main
test -z "$(git status --porcelain)"
```

Stop if the branch already exists or either cleanliness check fails. Do not remove or hide user files to make the check pass.

### 2. Bump the version

```bash
python3 ci/release.py bump_version -version "$release_version" -l all
git diff --check
git status --short
```

Review the version diff. Use this command directly; do not substitute another version-bump workflow.

### 3. Commit the release version

```bash
git add -u
git commit -m "prepare release for ${release_version}"
test -z "$(git status --porcelain)"
release_commit="$(git rev-parse HEAD)"
```

Stage only the version changes produced by the release script.

### 4. Create and push the RC tag

Confirm that neither the local nor remote tag already exists. An invalid RC gets a new RC number; never move or reuse an RC tag.

```bash
test -z "$(git tag --list "$rc_tag")"
test -z "$(git ls-remote --tags apache "refs/tags/${rc_tag}")"
test "$(git rev-parse HEAD)" = "$release_commit"
git tag "$rc_tag" && git push apache "$rc_tag"
test "$(git rev-parse "${rc_tag}^{commit}")" = "$release_commit"
```

The tag starts the ecosystem package-release workflows. Do not wait for them
here: start JVM publication immediately so the remote workflows and JVM staging
run in parallel. Once the tag has been pushed, any JVM or later release failure
invalidates this RC and requires a higher RC number; never move or reuse the
tag.

### 5. Publish JVM artifacts

```bash
python3 ci/release.py publish_jvm
```

The command publishes from a temporary worktree at the committed `HEAD` and
removes that worktree afterward. Record the distinct Java/Kotlin and Scala
Nexus staging repository IDs from the output:

```bash
java_kotlin_staging_id="orgapachefory-..."
scala_staging_id="orgapachefory-..."
test -n "$java_kotlin_staging_id"
test -n "$scala_staging_id"
test "$java_kotlin_staging_id" != "$scala_staging_id"
```

After recording both IDs, read and follow
[Nexus staging closure](references/nexus-staging.md). It contains the
credential rules, authenticated state checks, `/bulk/close` request, HTTP 201
gate, close polling, failure inspection, and anonymous artifact checks. Do not
close any repository ID that was not recorded from this publication. Keep both
repositories closed during the vote; do not promote them until the vote passes.

### 6. Check the tag-triggered workflows

After JVM publication and Nexus closure, inspect the workflows that have been
running since the tag was pushed. Filter by the tag rather than only by commit
SHA so main-branch runs at the same commit are not mixed into the result.

```bash
workflow_runs_json="$(
  gh run list \
    --repo apache/fory \
    --branch "$rc_tag" \
    --event push \
    --limit 100 \
    --json databaseId,workflowName,status,conclusion,headSha,headBranch,url
)"

WORKFLOW_RUNS_JSON="$workflow_runs_json" \
RELEASE_COMMIT="$release_commit" \
RC_TAG="$rc_tag" \
python3 - <<'PY'
import json
import os

runs = json.loads(os.environ["WORKFLOW_RUNS_JSON"])
release_commit = os.environ["RELEASE_COMMIT"]
rc_tag = os.environ["RC_TAG"]
if not runs:
    raise SystemExit(f"No tag-triggered workflows found for {rc_tag}")
wrong_revision = [
    run
    for run in runs
    if run["headSha"] != release_commit or run["headBranch"] != rc_tag
]
if wrong_revision:
    raise SystemExit(f"Unexpected workflow revisions: {wrong_revision}")
for run in sorted(runs, key=lambda item: item["workflowName"]):
    print(
        run["databaseId"],
        run["workflowName"],
        run["status"],
        run["conclusion"] or "-",
        run["url"],
    )
failed = [
    run
    for run in runs
    if run["status"] == "completed" and run["conclusion"] != "success"
]
if failed:
    raise SystemExit(f"Failed tag-triggered workflows: {failed}")
PY
```

By default, wait for every incomplete run and require a successful conclusion:

```bash
printf "%s\n" "$workflow_runs_json" |
  python3 -c '
import json
import sys

for run in json.load(sys.stdin):
    if run["status"] != "completed":
        print(run["databaseId"])
' |
  while IFS= read -r workflow_id; do
    gh run watch "$workflow_id" --repo apache/fory --exit-status || exit 1
  done
```

Re-query by tag before sending the vote to catch any later-created run. If the
release manager explicitly waives workflow monitoring for a particular RC,
record the snapshot IDs, states, and reason; do not cancel the remote workflows
and do not report incomplete runs as successful.

### 7. Build the ASF source release

Start from the clean release branch. The build temporarily commits release-archive changes and resets them, so verify that it restores the original commit and clean tree.

```bash
test -z "$(git status --porcelain)"
before_build="$(git rev-parse HEAD)"
python3 ci/release.py build -v "$release_version"
test "$(git rev-parse HEAD)" = "$before_build"
test -z "$(git status --porcelain)"
test -f "dist/apache-fory-${release_version}-src.tar.gz"
test -f "dist/apache-fory-${release_version}-src.tar.gz.asc"
test -f "dist/apache-fory-${release_version}-src.tar.gz.sha512"
```

The build command verifies the generated PGP signature and SHA-512 checksum.

### 8. Commit the source release to ASF Subversion

Use a clean, updated working copy of the ASF development distribution repository.

```bash
test -d "$svn_wc/.svn" || svn checkout https://dist.apache.org/repos/dist/dev/fory "$svn_wc"
svn update "$svn_wc"
mkdir -p "$svn_wc/$dist_version"
cp dist/* "$svn_wc/$dist_version/"
svn add --force "$svn_wc/$dist_version"
svn status "$svn_wc/$dist_version"
svn commit "$svn_wc/$dist_version" -m "Prepare Apache Fory ${rc_tag}"
test -z "$(svn status "$svn_wc/$dist_version")"
svn log -l 1 "$svn_wc/$dist_version"
svn ls "https://dist.apache.org/repos/dist/dev/fory/${dist_version}/"
```

Inspect `svn status` before committing. The upload is complete only after `svn commit` returns a revision and the remote `svn ls` shows the three release files; local `A` status alone is not an upload.

### 9. Draft the vote email

Produce a complete, copyable email from this template. Fill every placeholder from verified output, use an explicit UTC deadline at least 72 hours after sending, and do not send the email unless requested.

```text
Subject: [VOTE] Release Apache Fory v${release_version}-${rc}

Hello, Apache Fory Community:

This is a call for vote to release Apache Fory ${release_version}.

Apache Fory is a blazingly fast multi-language serialization framework
for idiomatic domain objects, schema IDL, and cross-language data exchange.

The discussion thread:
${discussion_url}

The change list since Apache Fory ${previous_version}:
https://github.com/apache/fory/compare/v${previous_version}...${rc_tag}

The release candidate artifacts:
https://dist.apache.org/repos/dist/dev/fory/${dist_version}/

The Maven staging repositories:
Java and Kotlin:
https://repository.apache.org/content/repositories/${java_kotlin_staging_id}/
Scala:
https://repository.apache.org/content/repositories/${scala_staging_id}/

The release tag:
https://github.com/apache/fory/releases/tag/${rc_tag}

The release commit:
https://github.com/apache/fory/commit/${release_commit}

The artifacts are signed with PGP key fingerprint
${gpg_fingerprint}, corresponding to ${apache_email}. The key can be found in
the KEYS file:
https://downloads.apache.org/fory/KEYS

The vote will remain open for at least 72 hours and close at
${vote_deadline_utc}.

Please vote accordingly:

[ ] +1 approve
[ ] +0 no opinion
[ ] -1 disapprove (please explain why)

To learn more about Fory, please see:
https://fory.apache.org/

Checklist for reference:

[ ] The Fory source archive downloads successfully.
[ ] Checksums and PGP signatures are valid.
[ ] Source distribution names match the release version.
[ ] LICENSE and NOTICE files are correct.
[ ] Files have license headers where required.
[ ] No compiled archives are bundled in the source archive.
[ ] The release can be built from source.

How to build and test:
https://github.com/apache/fory/blob/${rc_tag}/docs/development/index.md

Thanks,
${release_manager_name}
```

Before sending, verify the tag and commit, all URLs, both closed Maven staging repositories, the remote Subversion files, PGP fingerprint, and UTC deadline against the actual release outputs.

## Stop Conditions

Before pushing the tag, stop if the Git tree is dirty, a command fails, the RC
tag already exists, or the tag target would differ from the release commit.
After pushing the immutable tag, any failed JVM publication, workflow,
artifact verification, or Subversion publication invalidates that candidate;
fix the issue and create a higher RC instead of moving or reusing the tag.
Before sending the vote, require both staging repositories to be closed and
public, the Subversion commit to be remotely visible, and the tag workflows to
be successful unless the release manager explicitly waived monitoring.

## References

- [Apache Fory release guide](https://fory.apache.org/docs/community/how_to_release)
- [Fory development-list archive](https://lists.apache.org/list.html?dev@fory.apache.org)
- [Sonatype Nexus 2 staging REST example](https://support.sonatype.com/hc/en-us/articles/213465448-Automatically-dropping-old-staging-repositories)
- [Apache OpenDAL release skill](https://github.com/apache/opendal/blob/main/.agents/skills/opendal-release/SKILL.md), used only as secondary operational guidance
