#!/bin/sh

HOOK_DIR=".git/hooks"
SRC_DIR="scripts/git-hooks"

echo "Installing Git Hooks..."

mkdir -p "$HOOK_DIR"

cp "$SRC_DIR/prepare-commit-msg" "$HOOK_DIR/prepare-commit-msg"
cp "$SRC_DIR/commit-msg" "$HOOK_DIR/commit-msg"

if [ -f "$SRC_DIR/pre-commit" ]; then
  cp "$SRC_DIR/pre-commit" "$HOOK_DIR/pre-commit"
fi

chmod +x "$HOOK_DIR/prepare-commit-msg"
chmod +x "$HOOK_DIR/commit-msg"

if [ -f "$HOOK_DIR/pre-commit" ]; then
  chmod +x "$HOOK_DIR/pre-commit"
fi

echo "🎉 Git hooks installed successfully"
