#!/bin/bash
# Runs before every commit. Exit 2 = block. Exit 0 = allow.

# If any engine/ file changed, run the unit tests.
ENGINE_CHANGED=$(git diff --cached --name-only | grep "engine/")
if [ -n "$ENGINE_CHANGED" ]; then
  echo "Engine files changed — running unit tests..."
  ./gradlew :app:testDebugUnitTest --console=plain -q 2>&1
  if [ $? -ne 0 ]; then
    echo "ERROR: Engine unit tests failed. Fix tests before committing."
    exit 2
  fi
  echo "Engine tests passed."
fi

# Warn if keystore.properties is staged (should never be committed).
KEYSTORE=$(git diff --cached --name-only | grep -E "keystore\.properties|\.jks|\.keystore")
if [ -n "$KEYSTORE" ]; then
  echo "ERROR: Signing credential files must not be committed: $KEYSTORE"
  exit 2
fi

# Warn if local.properties is staged.
LOCAL_PROPS=$(git diff --cached --name-only | grep "local\.properties")
if [ -n "$LOCAL_PROPS" ]; then
  echo "ERROR: local.properties is machine-specific and must not be committed."
  exit 2
fi

echo "Pre-commit checks passed."
exit 0
