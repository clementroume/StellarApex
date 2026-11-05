#!/bin/bash
# PreToolUse hook (Bash): refuse `git commit` / `gh pr create` / `gh pr edit` whose message or body
# carries a Claude attribution line. Project rule: no attribution in commits or pull requests.
command=$(jq -r '.tool_input.command // empty')

if echo "$command" | grep -Eq '(^|[;&|[:space:]])(git[[:space:]]+([^;&|]*[[:space:]])?commit|gh[[:space:]]+pr[[:space:]]+(create|edit))([[:space:]]|$)'; then
  if echo "$command" | grep -Eiq 'co-authored-by|generated with|🤖'; then
    echo "Blocked: this project forbids Claude attribution lines (Co-Authored-By, 'Generated with') in commit messages and pull request descriptions. Remove the line and retry." >&2
    exit 2
  fi
fi
exit 0
