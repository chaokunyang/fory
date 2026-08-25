# Apache Nexus Staging Closure

Read this reference after `ci/release.py publish_jvm` creates the distinct
Java/Kotlin and Scala staging repositories and their IDs have been recorded in
`java_kotlin_staging_id` and `scala_staging_id`.

## Nexus Repository 2 API

Apache `repository.apache.org` currently exposes the Nexus Repository 2
staging API. The
[Sonatype Nexus 2 staging REST example](https://support.sonatype.com/hc/en-us/articles/213465448-Automatically-dropping-old-staging-repositories)
documents the repository-list response and bulk-action payload. Its
`/bulk/drop` operation deletes a staging repository; it is not the close
operation used here. Close both Fory repositories with `/bulk/close`.

Use the same credentials as the Maven server `apache.releases.https`. Load them
into `NEXUS_USERNAME` and `NEXUS_PASSWORD` from the release manager's secret
store. Do not print them, put them in `.local/fory-release.env`, or write them
to task logs.

```bash
: "${NEXUS_USERNAME:?export the Apache Nexus username}"
: "${NEXUS_PASSWORD:?export the Apache Nexus password}"
nexus_base="https://repository.apache.org"

nexus_api() {
  curl --fail-with-body --silent --show-error \
    --user "${NEXUS_USERNAME}:${NEXUS_PASSWORD}" \
    "$@"
}

check_nexus_state() {
  expected_state="$1"
  nexus_api \
    --header "Accept: application/json" \
    "$nexus_base/service/local/staging/profile_repositories" |
    python3 -c '
import json
import sys

expected_state = sys.argv[1]
wanted = sys.argv[2:]
repositories = {
    item.get("repositoryId"): item
    for item in json.load(sys.stdin).get("data", [])
}
missing = [
    repository_id
    for repository_id in wanted
    if repository_id not in repositories
]
if missing:
    raise SystemExit(f"Missing Nexus staging repositories: {missing}")
states = {
    repository_id: repositories[repository_id].get("type")
    for repository_id in wanted
}
for repository_id in wanted:
    print(f"{repository_id}: {states[repository_id]}")
if any(state != expected_state for state in states.values()):
    raise SystemExit(1)
' "$expected_state" "$java_kotlin_staging_id" "$scala_staging_id"
}
```

## Verify the Open Repositories

Confirm that both recorded repositories exist and are open. This prevents an
old or unrelated staging repository from being closed accidentally.

```bash
check_nexus_state open
```

Stop if either ID is absent or not open. Do not substitute a similarly named
repository.

## Close Both Repositories

Build the JSON payload from the two recorded IDs and require the Nexus close
request to return HTTP 201:

```bash
close_payload="$(
  python3 -c '
import json
import sys

print(json.dumps({
    "data": {
        "stagedRepositoryIds": sys.argv[1:3],
        "description": sys.argv[3],
    }
}))
' \
    "$java_kotlin_staging_id" \
    "$scala_staging_id" \
    "Close Apache Fory ${rc_tag} staging repositories"
)"

close_http_code="$(
  nexus_api \
    --request POST \
    --header "Accept: application/json" \
    --header "Content-Type: application/json" \
    --data "$close_payload" \
    --output /dev/null \
    --write-out "%{http_code}" \
    "$nexus_base/service/local/staging/bulk/close"
)"
test "$close_http_code" = 201
```

Closing performs server-side validation asynchronously. Poll for up to five
minutes and require both repositories to reach `closed`:

```bash
nexus_closed=false
for attempt in $(seq 1 30); do
  if check_nexus_state closed; then
    nexus_closed=true
    break
  fi
  sleep 10
done
test "$nexus_closed" = true
```

If the timeout expires, inspect the close activity for both repositories and
stop. Do not repeatedly submit close requests or proceed to the vote while a
repository is open or transitioning.

```bash
for staging_id in "$java_kotlin_staging_id" "$scala_staging_id"; do
  nexus_api \
    --header "Accept: application/json" \
    "$nexus_base/service/local/staging/repository/${staging_id}/activity" |
    python3 -m json.tool
done
```

## Verify Anonymous Artifact Access

Verify anonymous access to the repository roots and representative Java,
Kotlin, Scala 2.13, and Scala 3 artifacts. HTTP 200 from an authenticated API
request is not a substitute for these public download checks.

```bash
java_kotlin_staging_url="${nexus_base}/content/repositories/${java_kotlin_staging_id}/"
scala_staging_url="${nexus_base}/content/repositories/${scala_staging_id}/"

for artifact_url in \
  "$java_kotlin_staging_url" \
  "${java_kotlin_staging_url}org/apache/fory/fory-core/${release_version}/fory-core-${release_version}.jar" \
  "${java_kotlin_staging_url}org/apache/fory/fory-kotlin/${release_version}/fory-kotlin-${release_version}.jar" \
  "$scala_staging_url" \
  "${scala_staging_url}org/apache/fory/fory-scala_2.13/${release_version}/fory-scala_2.13-${release_version}.jar" \
  "${scala_staging_url}org/apache/fory/fory-json-scala_3/${release_version}/fory-json-scala_3-${release_version}.jar"
do
  artifact_http_code="$(
    curl --location --silent --show-error \
      --output /dev/null \
      --write-out "%{http_code}" \
      "$artifact_url"
  )"
  printf "%s %s\n" "$artifact_http_code" "$artifact_url"
  test "$artifact_http_code" = 200
done
```

Record both repository IDs, their `closed` states, and the public verification
results. Keep the repositories closed during the vote. Do not promote/release
them until the vote passes. Drop a failed candidate only after the release
manager decides to abandon that RC.
