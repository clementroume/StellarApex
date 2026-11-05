#!/bin/bash
# PreToolUse hook (Bash): refuse `git commit` / `gh pr create` / `gh pr edit` whose message or body
# carries a Claude attribution line, given inline or through a message file (-F, --file, --body-file).
# Project rule: no attribution in commits or pull requests.
if ! command -v jq >/dev/null; then
  echo "Blocked: jq is required by this hook (brew install jq)." >&2
  exit 2
fi
command=$(jq -r '.tool_input.command // empty')

PATTERN='co-authored-by|generated with|🤖'

if echo "$command" | grep -Eq '(^|[;&|[:space:]])(git[[:space:]]+([^;&|]*[[:space:]])?commit|gh[[:space:]]+pr[[:space:]]+(create|edit))([[:space:]]|$)'; then
  text="$command"
  # Message files: -F f, -Ff, --file f, --file=f, --body-file f, --body-file=f (first word after the flag)
  files=$(echo "$command" | grep -Eo '(-F[[:space:]]*|--file[=[:space:]]+|--body-file[=[:space:]]+)[^[:space:];&|]+' \
    | sed -E 's/^(-F[[:space:]]*|--file[=[:space:]]+|--body-file[=[:space:]]+)//; s/^["'"'"']//; s/["'"'"']$//')
  while IFS= read -r f; do
    [ -n "$f" ] && [ -r "$f" ] && text="$text"$'\n'"$(cat "$f")"
  done <<< "$files"
  if echo "$text" | grep -Eiq "$PATTERN"; then
    echo "Blocked: this project forbids Claude attribution lines (Co-Authored-By, 'Generated with') in commit messages and pull request descriptions. Remove the line and retry." >&2
    exit 2
  fi
fi
exit 0
