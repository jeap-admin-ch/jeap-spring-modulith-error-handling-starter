#!/usr/bin/env sh

set -eu

if [ "$#" -ne 1 ]; then
    printf 'Usage: %s <version>\n' "$0" >&2
    exit 1
fi

./mvnw org.codehaus.mojo:versions-maven-plugin:2.21.0:set \
    -DnewVersion="$1" \
    -DgenerateBackupPoms=false
