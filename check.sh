#!/bin/bash

set -e

REPO_URL="$1"
PROPERTIES="$2"
KEEP_CLONE=false

ORIG_DIR=$(pwd)

if [[ -z "$REPO_URL" ]]; then
  echo "❌ Usage: $0 <git-url> [propertiesToCheck] [--keep]"
  exit 1
fi

if [[ "$3" == "--keep" || "$2" == "--keep" ]]; then
  KEEP_CLONE=true
fi

# Listes des checkers
CHECKER_IDS=(1 2 3 4 5 6 7 8 9 10 11)
CHECKER_NAMES=("expectedModules" "parentVersion" "propertyPresence" "urls" "hardcodedVersion" "outdatedDependencies" "commentedTags" "redundantProperties" "unusedDependencies" "dependenciesRedefinition" "interfaceConformity")

echo "📋 Checkers disponibles :"
for i in "${!CHECKER_IDS[@]}"; do
  printf "  %2s. %s\n" "${CHECKER_IDS[$i]}" "${CHECKER_NAMES[$i]}"
done
echo "  0. 🔄 Tous les checkers"
echo

read -p "👉 Entrez les numéros à exécuter (ex: 1 4 7 ou 0 pour tous) : " -a SELECTION

if [[ ${#SELECTION[@]} -eq 0 ]]; then
  echo "❌ Aucun choix effectué. Abandon."
  exit 1
fi

if [[ "${SELECTION[0]}" == "0" ]]; then
  SELECTED=""
else
  SELECTED_LIST=()
  for num in "${SELECTION[@]}"; do
    found=false
    for i in "${!CHECKER_IDS[@]}"; do
      if [[ "$num" == "${CHECKER_IDS[$i]}" ]]; then
        SELECTED_LIST+=("${CHECKER_NAMES[$i]}")
        found=true
        break
      fi
    done
    if [[ "$found" == false ]]; then
      echo "❌ Numéro invalide : $num"
      exit 1
    fi
  done
  SELECTED=$(IFS=, ; echo "${SELECTED_LIST[*]}")
fi

echo
echo "🚀 Dépôt à analyser : $REPO_URL"
echo "🔍 Checkers sélectionnés : ${SELECTED:-TOUS}"
echo

read -p "✅ Confirmer l'exécution ? [o/N] " CONFIRM
[[ "$CONFIRM" =~ ^[oO]$ ]] || { echo "⏹️  Annulé."; exit 1; }

TMP_DIR=$(mktemp -d -t checker-clone-XXXXXXXX)
echo "📥 Clonage du dépôt Git : $REPO_URL"
git clone --depth 1 "$REPO_URL" "$TMP_DIR"

cd "$TMP_DIR"
echo "🚀 Exécution du plugin Maven dans $TMP_DIR"

mvn org.elitost.maven.plugins:checker-maven-plugin:check \
  -DpropertiesToCheck="${PROPERTIES:-}" \
  ${SELECTED:+-DcheckersToRun="$SELECTED"} \
  -Dformat=html

REPORT_FILE="module-check-report.html"
if [[ -f "$REPORT_FILE" ]]; then
  FINAL_REPORT="$ORIG_DIR/module-check-report.html"
  cp "$REPORT_FILE" "$FINAL_REPORT"
  echo "📄 Rapport copié dans : $FINAL_REPORT"

  if command -v open >/dev/null; then
    open "$FINAL_REPORT"
  elif command -v xdg-open >/dev/null; then
    xdg-open "$FINAL_REPORT"
  fi
else
  echo "❌ Aucun rapport généré."
fi

if [[ "$KEEP_CLONE" = false ]]; then
  echo "🧹 Suppression du dossier temporaire : $TMP_DIR"
  rm -rf "$TMP_DIR"
else
  echo "📦 Dossier conservé : $TMP_DIR"
fi