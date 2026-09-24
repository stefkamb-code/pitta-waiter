#!/usr/bin/env bash
# Χτίζει το release APK και το αφήνει στην Επιφάνεια με την ΕΚΔΟΣΗ στο όνομα:
#
#   PittaWaiter-v3.1.apk
#
# Γιατί: το `app-release.apk` βαθιά μέσα στο app/build/outputs λέγεται ίδια σε κάθε έκδοση, οπότε δεν
# ξεχωρίζει ποιο στάλθηκε στα κινητά — και ένα παλιό αντίγραφο στα «Ληφθέντα» του τηλεφώνου μοιάζει
# ολόιδιο με το καινούριο. Ίδια λογική με το scripts/make-setup.sh του ταμείου.
#
# ΠΡΟΣΟΧΗ: το APK και το setup του ταμείου πάνε ΜΑΖΙ — από την 3.1 ο κωδικός ταξιδεύει και στα GET,
# οπότε παλιότερο APK με ταμείο 1.0.97+ κλειδώνεται έξω («Λάθος κωδικός»).
set -e
cd "$(dirname "$0")/.."

DESKTOP="/c/Users/stefa/Desktop"
VERSION="$(grep -oP 'versionName\s*=\s*"\K[^"]+' app/build.gradle.kts)"
CODE="$(grep -oP 'versionCode\s*=\s*\K[0-9]+' app/build.gradle.kts)"
OUT="$DESKTOP/PittaWaiter-v$VERSION.apk"

echo "Building PittaWaiter $VERSION (versionCode $CODE)..."
./gradlew.bat assembleRelease --console=plain -q

# Μένει ΕΝΑ APK στην Επιφάνεια, με το σωστό όνομα — χωρίς σύγχυση για το ποιο είναι το τρέχον.
rm -f "$DESKTOP"/PittaWaiter-v*.apk
cp app/build/outputs/apk/release/app-release.apk "$OUT"

echo "Done: $OUT"
