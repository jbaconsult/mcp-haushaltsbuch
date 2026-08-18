#!/usr/bin/env bash
# =============================================================================
# Image-Tags aus einem Git-Tag ableiten
#
#   .github/skripte/image-tags.sh v0.1.0   -> 0.1.0
#                                             0.1
#                                             latest
#   .github/skripte/image-tags.sh --pruefen -> Selbstpruefung
#
# Ein Skript und keine Zeilen im Workflow, aus einem Grund: Das hier ist die
# einzige echte Logik am Release, und Logik in YAML laesst sich nicht
# ausprobieren. Ein Fehler darin faellt sonst erst auf, wenn ein Tag gesetzt ist
# - und dann steht ein falsch benanntes Image in der Registry, das jemand zieht.
#
# Die Selbstpruefung laeuft in der Pull-Request-Pipeline mit.
# =============================================================================
set -euo pipefail

tags_aus() {
  local git_tag="$1"

  # Das fuehrende v ist Pflicht, nicht Zierde: der Workflow loest auf "v*" aus,
  # und eine Pruefung, die auch ohne durchlaesst, verspricht eine Strenge, die
  # sie nicht hat.
  if [[ "$git_tag" != v* ]]; then
    echo "::error::Tag '$git_tag' beginnt nicht mit v." >&2
    return 1
  fi

  local version="${git_tag#v}"

  # Streng geprueft, nicht wohlwollend geraten: aus einem Tag entsteht ein Image,
  # das jemand spaeter zieht. "v1.2" oder "release-3" ergaeben stillen Unsinn
  # statt eines Fehlers.
  if [[ ! "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?$ ]]; then
    echo "::error::Tag '$git_tag' passt nicht auf v<major>.<minor>.<patch>[-vorab]." >&2
    return 1
  fi

  echo "$version"

  # Eine Vorabversion bleibt fuer sich. Sie darf weder :latest noch die
  # gleitenden Tags bewegen - wer :0.1 zieht, will keinen Release Candidate, und
  # wer :latest zieht, erst recht nicht.
  if [[ "$version" == *-* ]]; then
    return 0
  fi

  local major="${version%%.*}"
  local rest="${version#*.}"
  local minor="${rest%%.*}"

  echo "${major}.${minor}"

  # Ein Major-Tag erst ab 1. Bei 0.x sagt SemVer nichts ueber Kompatibilitaet zu;
  # ein Tag :0, der von 0.1 auf 0.2 springt, verspraeche eine Stabilitaet, die es
  # ausdruecklich nicht gibt.
  if [ "$major" -ge 1 ]; then
    echo "$major"
  fi

  echo "latest"
}

# --- Selbstpruefung ----------------------------------------------------------
selbstpruefung() {
  local fehler=0

  pruefe() {
    local tag="$1" erwartet="$2" ist
    ist="$(tags_aus "$tag" 2>/dev/null | tr '\n' ' ' | sed 's/ $//')" || true
    if [ "$ist" = "$erwartet" ]; then
      printf '  ok    %-16s -> %s\n' "$tag" "$ist"
    else
      printf '  FEHL  %-16s -> %-28s (erwartet: %s)\n' "$tag" "$ist" "$erwartet"
      fehler=1
    fi
  }

  pruefe_abgelehnt() {
    if tags_aus "$1" >/dev/null 2>&1; then
      printf '  FEHL  %-16s -> angenommen, sollte abgelehnt werden\n' "$1"
      fehler=1
    else
      printf '  ok    %-16s -> abgelehnt\n' "$1"
    fi
  }

  echo "Regulaere Versionen:"
  pruefe "v0.1.0"      "0.1.0 0.1 latest"
  pruefe "v0.2.7"      "0.2.7 0.2 latest"
  pruefe "v1.0.0"      "1.0.0 1.0 1 latest"
  pruefe "v1.2.3"      "1.2.3 1.2 1 latest"
  pruefe "v10.20.30"   "10.20.30 10.20 10 latest"

  echo "Vorabversionen bewegen weder latest noch die gleitenden Tags:"
  pruefe "v1.0.0-rc.1" "1.0.0-rc.1"
  pruefe "v0.2.0-beta" "0.2.0-beta"

  echo "Abgelehnt:"
  pruefe_abgelehnt "v1.2"
  pruefe_abgelehnt "v1.2.3.4"
  pruefe_abgelehnt "release-3"
  pruefe_abgelehnt "v"
  pruefe_abgelehnt "1.2.3"

  if [ "$fehler" -ne 0 ]; then
    echo "::error::Die Ableitung der Image-Tags ist nicht wie erwartet."
    return 1
  fi
  echo "Alle Faelle wie erwartet."
}

case "${1:-}" in
  --pruefen) selbstpruefung ;;
  "")        echo "Aufruf: $0 <git-tag> | --pruefen" >&2; exit 2 ;;
  *)         tags_aus "$1" ;;
esac
