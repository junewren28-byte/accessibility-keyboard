#!/data/data/com.termux/files/usr/bin/sh
set -eu

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
ANDROID_JAR="$ROOT/.cache/sdk31/android-12/android.jar"
BUILD="$ROOT/build"
GEN="$BUILD/generated"
CLASSES="$BUILD/classes"
DEX="$BUILD/dex"

if [ ! -f "$ANDROID_JAR" ]; then
    echo "Missing Android SDK platform at $ANDROID_JAR" >&2
    exit 1
fi

rm -rf "$BUILD"
mkdir -p "$GEN" "$CLASSES" "$DEX" "$BUILD/unit-test-classes"

javac \
    -source 8 \
    -target 8 \
    -Xlint:all,-options \
    -d "$BUILD/unit-test-classes" \
    "$ROOT/src/org/example/speechboard/SpeechTranscriptBuffer.java" \
    "$ROOT/src/org/example/speechboard/SpeechRecognitionExtras.java" \
    "$ROOT/src/org/example/speechboard/KeyboardTextConnection.java" \
    "$ROOT/src/org/example/speechboard/KeyboardTextActions.java" \
    "$ROOT/src/org/example/speechboard/SpeechBiasPhrases.java" \
    "$ROOT/test/src/org/example/speechboard/SpeechTranscriptBufferTest.java" \
    "$ROOT/test/src/org/example/speechboard/SpeechRecognitionExtrasTest.java" \
    "$ROOT/test/src/org/example/speechboard/KeyboardTextActionsTest.java" \
    "$ROOT/test/src/org/example/speechboard/SpeechBiasPhrasesTest.java"
java -cp "$BUILD/unit-test-classes" org.example.speechboard.SpeechTranscriptBufferTest

aapt2 compile --dir "$ROOT/res" -o "$BUILD/resources.zip"
aapt2 link \
    -I "$ANDROID_JAR" \
    --manifest "$ROOT/AndroidManifest.xml" \
    --java "$GEN" \
    -o "$BUILD/resources.ap_" \
    "$BUILD/resources.zip"

javac \
    -source 8 \
    -target 8 \
    -Xlint:all,-options \
    -classpath "$ANDROID_JAR" \
    -d "$CLASSES" \
    $(find "$ROOT/src" "$GEN" -name '*.java' -print)

d8 --min-api 31 --output "$DEX" $(find "$CLASSES" -name '*.class' -print)

cp "$BUILD/resources.ap_" "$BUILD/unsigned.apk"
zip -j -q "$BUILD/unsigned.apk" "$DEX/classes.dex"
zipalign -f 4 "$BUILD/unsigned.apk" "$BUILD/aligned.apk"

KEYSTORE="$ROOT/.speechboard-debug.keystore"
if [ ! -f "$KEYSTORE" ]; then
    keytool -genkeypair \
        -keystore "$KEYSTORE" \
        -storepass android \
        -keypass android \
        -alias speechboard \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -dname "CN=SpeechBoard, O=Local"
fi

apksigner sign \
    --ks "$KEYSTORE" \
    --ks-pass pass:android \
    --key-pass pass:android \
    --out "$BUILD/SpeechBoard.apk" \
    "$BUILD/aligned.apk"

apksigner verify --verbose "$BUILD/SpeechBoard.apk"

TEST_BUILD="$BUILD/test"
mkdir -p "$TEST_BUILD/classes" "$TEST_BUILD/dex"
aapt2 link \
    -I "$ANDROID_JAR" \
    --manifest "$ROOT/test/AndroidManifest.xml" \
    -o "$TEST_BUILD/resources.ap_"
javac \
    -source 8 \
    -target 8 \
    -Xlint:all,-options \
    -classpath "$ANDROID_JAR" \
    -d "$TEST_BUILD/classes" \
    $(find "$ROOT/test/src/org/example/speechboard/test" -name '*.java' -print)
d8 --min-api 31 --output "$TEST_BUILD/dex" \
    $(find "$TEST_BUILD/classes" -name '*.class' -print)
cp "$TEST_BUILD/resources.ap_" "$TEST_BUILD/unsigned.apk"
zip -j -q "$TEST_BUILD/unsigned.apk" "$TEST_BUILD/dex/classes.dex"
zipalign -f 4 "$TEST_BUILD/unsigned.apk" "$TEST_BUILD/aligned.apk"
apksigner sign \
    --ks "$KEYSTORE" \
    --ks-pass pass:android \
    --key-pass pass:android \
    --out "$BUILD/SpeechBoardTest.apk" \
    "$TEST_BUILD/aligned.apk"
apksigner verify --verbose "$BUILD/SpeechBoardTest.apk"

DOWNLOADS="$HOME/storage/downloads"
VERSION_NAME="$(aapt2 dump badging "$BUILD/SpeechBoard.apk" \
    | sed -n "s/.*versionName='\([^']*\)'.*/\1/p")"
PUBLIC_APK="$DOWNLOADS/SpeechBoard-$VERSION_NAME.apk"
PUBLIC_TEST_APK="$DOWNLOADS/SpeechBoardTest-$VERSION_NAME.apk"
if [ ! -d "$DOWNLOADS" ]; then
    echo "Missing shared Downloads directory; run termux-setup-storage" >&2
    exit 1
fi
cp "$BUILD/SpeechBoard.apk" "$PUBLIC_APK"
cp "$BUILD/SpeechBoardTest.apk" "$PUBLIC_TEST_APK"
for FILE in "$PUBLIC_APK" "$PUBLIC_TEST_APK"; do
    PATH=/system/bin:/system/xbin:$PATH /system/bin/am broadcast \
        --user 0 \
        -a android.intent.action.MEDIA_SCANNER_SCAN_FILE \
        -d "file://$FILE" >/dev/null
 done

echo "Built $BUILD/SpeechBoard.apk"
echo "Built $BUILD/SpeechBoardTest.apk"
echo "Synced $PUBLIC_APK"
echo "Synced $PUBLIC_TEST_APK"
