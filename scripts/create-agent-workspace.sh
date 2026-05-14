#!/usr/bin/env bash
set -euo pipefail

# Creates a git worktree for an agent persona to work in isolation.
#
# Usage:   create-agent-workspace.sh <agent-name> <task-id>
# Example: create-agent-workspace.sh developer-1 IOS-001
#
# Creates:
#   ~/IdeaProjects/agent-workspaces/Heirlooms/<agent-name>/
#   branch: agent/<agent-name>/<task-id>  (based on current main)
#
# When the task is done, the PA merges the branch to main, then cleans up:
#   git worktree remove ~/IdeaProjects/agent-workspaces/Heirlooms/<agent-name>
#   git branch -d agent/<agent-name>/<task-id>

AGENT_NAME="${1:?Usage: $0 <agent-name> <task-id>}"
TASK_ID="${2:?Usage: $0 <agent-name> <task-id>}"

REPO_ROOT="$(git -C "$(dirname "$0")/.." rev-parse --show-toplevel)"
WORKSPACE_BASE="${HOME}/IdeaProjects/agent-workspaces/Heirlooms"
WORKSPACE_PATH="${WORKSPACE_BASE}/${AGENT_NAME}"
BRANCH="agent/${AGENT_NAME}/${TASK_ID}"

mkdir -p "${WORKSPACE_BASE}"

if [ -d "${WORKSPACE_PATH}" ]; then
    echo "ERROR: workspace already exists at ${WORKSPACE_PATH}" >&2
    echo "Remove it first:" >&2
    echo "  git -C '${REPO_ROOT}' worktree remove '${WORKSPACE_PATH}'" >&2
    echo "  git -C '${REPO_ROOT}' branch -d <old-branch>" >&2
    exit 1
fi

# Ensure main is up to date before branching
git -C "${REPO_ROOT}" fetch origin main --quiet

git -C "${REPO_ROOT}" worktree add -b "${BRANCH}" "${WORKSPACE_PATH}" origin/main

echo ""
echo "Agent workspace ready"
echo "  Path:   ${WORKSPACE_PATH}"
echo "  Branch: ${BRANCH}"
echo ""
echo "Cleanup when done:"
echo "  git -C '${REPO_ROOT}' worktree remove '${WORKSPACE_PATH}'"
echo "  git -C '${REPO_ROOT}' branch -d '${BRANCH}'"
