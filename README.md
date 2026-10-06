# STC Glyph Toy

Nothing Phone (4a) Pro の背面 Glyph Matrix (13×13 LED) に **「STC」を固定表示**する AOD Glyph Toy アプリ。

プレビュー画像（Vector Asset、設定画面の Toy 一覧表示用）: `app/src/main/res/drawable/toy_preview.xml`

## 仕組み

- Nothing 公式 [GlyphMatrix-Developer-Kit](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit) の `glyph-matrix-sdk-2.0.aar` を使用
- AOD Glyph Toy として登録される（`com.nothing.glyph.toy.aod_support=1`）
- `setText("STC")` は marqueeType 既定値 `TYPE_MARQUEE_NONE` なので固定表示になる
- 常時表示中は毎分 `EVENT_AOD` が届くので、そのたびに再描画して割り込みからの復帰を保証
- 縦位置は `setPosition(0, len/2 - 3)`（実機調整済み）

## 必要環境

| 項目 | 値 |
|:--|:--|
| JDK | 17 |
| Android SDK | platform 35 / build-tools 35.0.0（`minSdk 33`, `targetSdk 35`） |
| Gradle | 8.11.1（wrapper 同梱） |
| 端末 | Nothing Phone (4a) Pro / Nothing OS（Toy 一覧が出ない場合は 2025 秋以降のシステムへ更新） |

## ビルド

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=$HOME/android-sdk

./gradlew assembleDebug
# => app/build/outputs/apk/debug/app-debug.apk
```

## インストールと有効化

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

1. 初回起動時に Phone へ「**不明なアプリ**」ダイアログが出るので**許可**する（許可しないと Toy 一覧に出ない）
2. アプリを起動 → 「Manage Glyph Toys を開く」ボタン（手動でも可）
3. **設定 → Glyph Interface → Glyph に切り替え（Flip to Glyph）→ 常に Glyph Toy に表示** で「STC」を選択
4. 画面を伏せると背面マトリクスに STC が点灯

## 文字を変える

`app/src/main/java/tech/stc/glyph/stctoy/StcTextToyService.kt` の

```kotlin
private const val FIXED_TEXT = "STC"
```

を書き換えて再ビルドするだけ。ASCII 文字が前提（NDot グリフは日本語非対応の可能性が高い）。13×13 に収める目安は 5〜6 文字まで。

## 実装の注意（開発時の落とし穴）

- `<meta-data android:name="NothingKey" android:value="test"/>` が `application` に**必須**。無いと Toy 一覧に一切現れない（Developer-Kit の README には記載なし）
- `register(Glyph.DEVICE_25111p)` が必須。公式サンプルの Kotlin ラッパーは Phone (3) 用 (`DEVICE_23112`) 固定なのでコピペ不可
- `aod_support=1` が無い Toy は 4a Pro では選択できない（4a Pro は AOD 専用 Toy のみ）
- `setText()` と `setImageSource()` は同時設定不可
- Phone (4a) Pro は Glyph Touch（長押し等）非対応

## 参考

- 詳細 API ガイド: `GlyphMatrix-4aPro.md`
- プレビュー画像仕様（512×512 / LED 30px / 円マスク / #1C1C1C）に沿った自作 vector drawable を `res/drawable/toy_preview.xml` に同梱
