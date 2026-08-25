---
name: fory-release
description: Prepare an Apache Fory release candidate from a clean release branch, including the version bump, JVM staging, RC tag, ASF source artifacts, SVN upload, and vote email. Use when creating or rerunning a Fory release candidate.
---

# Apache Fory Release

Use the repository release script for the release work. Do not manually reproduce its version-bump, JVM-publication, or source-build logic, and do not add unrelated test runs.

## Required Inputs

Collect these values before starting:

- `release_version`: final version without `v` or an RC suffix, such as `1.7.0`.
- `rc`: RC suffix, such as `rc3`.
- `previous_version`: previous release tag version, such as `1.6.1`.
- Release discussion URL.

Use the release manager defaults and derive the working-copy path from the repository location:

```bash
repo_root="$(git rev-parse --show-toplevel)"
release_branch="releases-${release_version}"
rc_tag="v${release_version}-${rc}"
dist_version="${release_version}"
release_manager_name="Shawn Yang"
apache_email="chaokunyang@apache.org"
gpg_fingerprint="1E2CDAE4C08AD7D694D1CB139D7BE8E45E580BA4"
svn_wc="${FORY_DIST_DEV_WC:-$(cd "$repo_root/.." && pwd)/fory-dist-dev}"
```

Use the same `dist_version` in Subversion and the vote email. The default SVN working copy is the `fory-dist-dev` directory next to the Fory repository; set `FORY_DIST_DEV_WC` only when the checkout is elsewhere.

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

### 4. Publish JVM artifacts

```bash
python3 ci/release.py publish_jvm
```

The command publishes from a temporary worktree at the committed `HEAD` and removes that worktree afterward. Record the Java/Kotlin and Scala Nexus staging repository IDs from the output. In Nexus, close both repositories and confirm their public URLs are readable before starting the vote. Do not release them until the vote passes; drop a failed candidate instead.

### 5. Create and push the RC tag

Confirm that neither the local nor remote tag already exists. An invalid RC gets a new RC number; never move or reuse an RC tag.

```bash
test -z "$(git tag --list "$rc_tag")"
test -z "$(git ls-remote --tags apache "refs/tags/${rc_tag}")"
test "$(git rev-parse HEAD)" = "$release_commit"
git tag "$rc_tag" && git push apache "$rc_tag"
test "$(git rev-parse "${rc_tag}^{commit}")" = "$release_commit"
```

The tag starts the ecosystem package-release workflows. Check that the tag-triggered workflows complete before sending the vote.

### 6. Build the ASF source release

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

### 7. Commit the source release to ASF Subversion

Use a clean, updated working copy of the ASF development distribution repository.

```bash
test -d "$svn_wc/.svn" || svn checkout https://dist.apache.org/repos/dist/dev/fory "$svn_wc"
svn update "$svn_wc"
mkdir -p "$svn_wc/$dist_version"
cp dist/* "$svn_wc/$dist_version/"
svn add --force "$svn_wc/$dist_version"
svn status "$svn_wc/$dist_version"
svn commit "$svn_wc/$dist_version" -m "Prepare Apache Fory ${rc_tag}"
svn ls "https://dist.apache.org/repos/dist/dev/fory/${dist_version}/"
```

Inspect `svn status` before committing. The upload is complete only after `svn commit` returns a revision and the remote `svn ls` shows the three release files; local `A` status alone is not an upload.

### 8. Draft the vote email

Produce a complete, copyable email from this template. Fill every placeholder from verified output, use an explicit UTC deadline at least 72 hours after sending, and do not send the email unless requested.

```text
Subject: [VOTE] Release Apache Fory v${release_version}-${rc}

Hello, Apache Fory Community:

This is a call for vote to release Apache Fory version v${release_version}.

Apache Fory - A blazingly fast multi-language serialization framework
for idiomatic domain objects, schema IDL, and cross-language data exchange.

The discussion thread:
${discussion_url}

The change list for this release:
https://github.com/apache/fory/compare/v${previous_version}...${rc_tag}

The release candidate:
https://dist.apache.org/repos/dist/dev/fory/${dist_version}/

The Maven staging repositories for this release:
https://repository.apache.org/content/repositories/${java_kotlin_staging_id}/
  (Java/Kotlin artifacts)
https://repository.apache.org/content/repositories/${scala_staging_id}/
  (Scala artifacts)

Git tag for the release candidate:
https://github.com/apache/fory/releases/tag/${rc_tag}

Git commit for the release candidate:
https://github.com/apache/fory/commit/${release_commit}

The artifacts are signed with PGP key fingerprint
${gpg_fingerprint}, corresponding to ${apache_email}, which can be found in
the KEYS file:
https://downloads.apache.org/fory/KEYS

The vote will remain open for at least 72 hours and close at
${vote_deadline_utc}.

Please vote accordingly:

[ ] +1 approve
[ ] +0 no opinion
[ ] -1 disapprove with the reason

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

Stop before the next irreversible step if the Git tree is dirty, a command fails, an RC tag already exists, the tag and commit differ, either staging repository is not closed and public, or the Subversion commit is not remotely visible. Fix the issue and create a higher RC number instead of mutating a published candidate.

## References

- [Apache Fory release guide](https://fory.apache.org/docs/community/how_to_release)
- [Apache OpenDAL release skill](https://github.com/apache/opendal/blob/main/.agents/skills/opendal-release/SKILL.md), used only as secondary operational guidance
