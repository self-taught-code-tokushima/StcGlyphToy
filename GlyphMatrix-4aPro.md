# Nothing Phone (4a) Pro で Glyph Matrix に固定文字を表示する

対象: **Nothing Phone (4a) Pro**（機種コード `25111p` / `A069P`）
SDK: **glyph-matrix-sdk-2.0.aar**（[GlyphMatrix-Developer-Kit](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit) 内、実クラスは `com.nothing.ketchum.*`）
検証: 本ガイドの API シグネチャ・定数値は SDK の AAR を展開し `javap` で実際のクラス定義を確認した上で記載している（2026-10 時点、SDK 2.0）。

---

## 0. 前提知識: Phone (4a) Pro の Glyph Matrix の制約

Phone (3) と違い、4a Pro はかなり制限されています。ここを理解していないと動かないものになります。

| 項目 | Phone (3) | **Phone (4a) Pro** |
|:--|:--|:--|
| 機種識別子 | `Glyph.DEVICE_23112` | **`Glyph.DEVICE_25111p`**（文字列値は `"A069P"`） |
| マトリクスサイズ | 25×25 LED | **13×13 LED**（`DEVICE_25111p_MATRIX_LENGTH = 13`） |
| Glyph Button（長押し/タッチ操作） | 全 Toy で対応 | **非対応**（Glyph Touch なし） |
| Toy の種類 | 全部 | **AOD Toy のみ**（常時表示専用の Toy だけ） |
| 常時表示の方法 | Glyph Toy キャルフセル | **Settings > Glyph Interface > Flip to Glyph > Always-on Glyph Toy** で選ばれると `EVENT_AOD` が**毎分**届く |

つまり 4a Pro で「固定の文字を表示し続ける」には:

- **方法 A（推奨）: AOD Glyph Toy として登録** — ユーザーが設定で自分の Toy を Always-on Glyph Toy に選ぶと、常時表示される（毎分の `EVENT_AOD` で再描画）。公式 Digital Clock / Battery / Solar Path と同じ仕組み。
- **方法 B: 通常アプリから直接描画** — `setAppMatrixFrame()`（フォアグラウンド限定、画面がスリープすると消える。「固定表示」には不向き）。

LED 配置は**バウンディングボックスの左上が (0,0)**、右下が (12,12)。マトリクスは円形にマスクされているので、**四隅の LED は実在しない**（円に内接する 13×13 として扱う）。中心は (6,6)。

```
Phone (4a) Pro 13x13（◯=実在LED想定、円マスクで四隅は使えない）
■■■■■■●●●●●●■        y=0..1  は中央付近のみ
■■■●●●●●●●●●■
■●●●●●●●●●●●■
●●●●●●●●●●●●●
...中央行 y=6 は x=0..12 全部点灯
```

---

## 1. プロジェクト準備

### 1.1 リポジトリを取得

```bash
git clone https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit.git
# 公式デモ（Kotlin ラッパー GlyphMatrixService.kt が便利）
git clone https://github.com/Nothing-Developer-Programme/GlyphMatrix-Example-Project.git
```

### 1.2 AAR を追加

1. Android Studio で新規プロジェクト（minSdk 33 以上 — AAR のマニフェストが `minSdkVersion=33`）
2. `app/libs/` フォルダを作成
3. `glyph-matrix-sdk-2.0.aar` を `app/libs/` にコピー
4. `app/build.gradle(.kts)` に依存追加:

```kotlin
// build.gradle.kts
dependencies {
    implementation(files("libs/glyph-matrix-sdk-2.0.aar"))
}
```

> 注意: 旧 `Glyph-Developer-Kit`（ライトストライプ用、`GlyphManager`）とは別物です。4a Pro には **GlyphMatrix 側**を使います。Glyph SDK と GlyphMatrix SDK は同じものだと README にあります。

### 1.3 AndroidManifest.xml

```xml
<manifest>
    <!-- 必須権限 -->
    <uses-permission android:name="com.nothing.ketchum.permission.ENABLE"/>

    <application>
        <!-- 旧SDK由来のデバッグ有効化（Glyph-Developer-Kit 側の手順。任意）:
             adb shell settings put global nt_glyph_interface_debug_enable 1
             （48時間で自動無効化される） -->

        <!-- AOD Toy の登録 -->
        <service
            android:name=".FixedTextToyService"
            android:exported="true">
            <intent-filter>
                <action android:name="com.nothing.glyph.TOY"/>
            </intent-filter>

            <!-- 必須: Glyph Toys 一覧に表示される名前 -->
            <meta-data
                android:name="com.nothing.glyph.toy.name"
                android:resource="@string/toy_name"/>

            <!-- 必須: 設定アプリ用のプレビュー画像（仕様は §5） -->
            <meta-data
                android:name="com.nothing.glyph.toy.image"
                android:resource="@drawable/toy_preview"/>

            <!-- 任意: 説明 -->
            <meta-data
                android:name="com.nothing.glyph.toy.summary"
                android:resource="@string/toy_summary"/>

            <!-- ★ 4a Pro では必須: AOD Toy であることの宣言。
                 これがないと 4a Pro では Toy として使えない -->
            <meta-data
                android:name="com.nothing.glyph.toy.aod_support"
                android:value="1"/>
        </service>
    </application>
</manifest>
```

> 4a Pro は Glyph Touch 非対応なので `com.nothing.glyph.toy.longpress` は無意味（付けない）。

---

## 2. テキスト表示 API の実態（AAR を解析して確認した事実）

固定文字表示の中心は `GlyphMatrixObject.Builder.setText()` です。README に「`setText(String)` で文字列を表示」としか書かれていない部分の追加情報を、クラスファイルを解析して確定しました。

### 2.1 setText のオーバーロード（SDK 2.0 に実在）

```java
Builder setText(String text);                                   // 通常
Builder setText(String text, int marqueeType);                  // マーキー種別を指定
Builder setText(String text, int marqueeType,
                Range<Integer> marqueeRange);                   // マーキーの表示範囲
Builder setTextStyle(String textStyle);                         // フォント（後述）
```

`marqueeType` の定数（`GlyphMatrixObject` に public 定義、javap で確認済み）:

| 定数 | 値 | 意味 |
|:--|:--|:--|
| `TYPE_MARQUEE_NONE` | 0 | **マーキーなし＝固定表示**（デフォルト） |
| `TYPE_MARQUEE_FORCE` | 1 | 必ずスクロール（長いテキストを流すとき） |
| `TYPE_MARQUEE_AUTO` | 2 | 幅に収まらなければ自動でスクロール |

**固定文字を出したいだけなら `setText("NTHNG")`（1引数）で OK。marqueeType のデフォルトは 0 = 固定です。**

- `setText()` と `setImageSource()` の同時設定は禁止（ビルド時に `"Can't set text and image together"` で弾かれる）。
- `marqueeRange` のデフォルトは `Range(0, getDeviceMatrixLength()-1)` = 4a Pro では 0〜12（マトリクス全域）。

### 2.2 フォントの実態: NDot + drawable フォールバック

- SDK 内蔵の既定フォントは **NDot55All**（Nothing 公式フォント NDot の 55 幅全グリフ版、内部文字マップ名 `letter_*`）。
- `setTextStyle(String s)` を呼ぶと、`getIdentifier(s + "_" + glyph, "drawable", packageName)` で**アプリ自身の drawable を参照**します。つまりアプリ側に `ndot_a.png`, `ndot_b.png` … のような命名の drawable を同梱すれば独自グリフに差し替え可能（独自フォントが欲しいときだけ検討。まず既定で試すこと）。
- `GlyphMatrixUtils.getLetterConfigs(text, context, textStyle)` / `getLetterMaxLength(...)` で、描画前のグリフ生成と**テキストの総幅（LED 数）**を事前計算できる。固定表示のセンタリングに便利。

### 2.3 13×13 に収まる文字数は？

文字は 1 文字あたり縦 3〜5 LED 程度のグリフ + `LETTER_SPACING` の間隔で横に並びます。**体感の目安（NDot 既定グリフ）:**

| 用途 | 収まりやすい文字数 |
|:--|:--|
| デジタル時計 `10:30` のような短句 | 4〜5 文字 |
| 英単語（`HERMES` 等） | 5〜6 文字 |
| それ以上 | `TYPE_MARQUEE_AUTO` で流すか、`TYPE_MARQUEE_FORCE` + `marqueeRange` |

正確な幅は `GlyphMatrixUtils.getLetterMaxLength(configs, reverse)` で計算してから `setPosition` を決めるのが確実。

---

## 3. 方法 A（本命）: AOD Glyph Toy として固定文字を表示する

### 3.1 全体コード（Kotlin）

公式 Example-Project の `GlyphMatrixService.kt` ラッパーを app にコピーしている前提で書きます（無ければ §3.2 の Java 直書きを参考に `Service` を自前で継承）。

```kotlin
// FixedTextToyService.kt
package com.example.fixedtexttoy

import android.content.Context
import android.os.Handler
import com.nothing.ketchum.Common
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixFrame
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphMatrixObject
import com.nothinglondon.sdkdemo.demos.GlyphMatrixService // 公式ラッパー

class FixedTextToyService : GlyphMatrixService("FixedText") {

    private val FIXED_TEXT = "NTHNG"   // ← 表示したい固定文字

    override fun performOnServiceConnected(
        context: Context,
        glyphMatrixManager: GlyphMatrixManager
    ) {
        // 4a Pro では register(Glyph.DEVICE_25111p) が必要なため、
        // 公式ラッパー(23112固定)をオーバーライドするか、下記 §3.2 の直実装を使うこと。
        drawFixedText(context, glyphMatrixManager)
    }

    private fun drawFixedText(context: Context, gmm: GlyphMatrixManager) {
        val len = Common.getDeviceMatrixLength()      // 4a Pro = 13

        val textObj = GlyphMatrixObject.Builder()
            .setText(FIXED_TEXT)                      // marqueeType 既定0 = 固定表示
            // .setTextStyle("myfont")                // 独自グリフにする場合のみ
            .setPosition(0, (len / 2) - 2)            // 縦は中央付近（グリフ高さに応じ調整）
            .setBrightness(255)
            .build()

        val frame = GlyphMatrixFrame.Builder()
            .addTop(textObj)                          // レイヤーは top/mid/low 各1つまで
            .build(context)

        gmm.setMatrixFrame(frame.render())
    }

    // AOD として選ばれている間、毎分 EVENT_AOD が届く。消灯割り込み等からの復旧用に再描画しておく。
    override fun onAod() {
        glyphMatrixManager?.let { drawFixedText(applicationContext, it) }
    }
}
```

> `GlyphMatrixService` ラッパーの `register(Glyph.DEVICE_23112)` は Phone (3) 固定です。**4a Pro では必ず `Glyph.DEVICE_25111p` を登録してください**（§3.2 参照）。ラッパーの register をコンストラクタ引数化するのが手軽。

### 3.2 最小完整 Service（Java 直実装、ラッパー使わず）

README の全例を 4a Pro 向けに修正したもの:

```java
public class FixedTextToyService extends Service {
    private GlyphMatrixManager mGM;

    @Override
    public IBinder onBind(Intent intent) {
        mGM = GlyphMatrixManager.getInstance(getApplicationContext());
        mGM.init(new GlyphMatrixManager.Callback() {
            @Override public void onServiceConnected(ComponentName n) {
                mGM.register(Glyph.DEVICE_25111p);            // ★ 4a Pro
                showFixedText();
            }
            @Override public void onServiceDisconnected(ComponentName n) {}
        });
        return null;   // 4a Pro は Glyph Touch 非対応なので Messenger を返す必要はない
    }

    private void showFixedText() {
        int len = Common.getDeviceMatrixLength();  // 13
        GlyphMatrixObject text = new GlyphMatrixObject.Builder()
                .setText("NTHNG")                  // 固定（TYPE_MARQUEE_NONE）
                .setPosition(0, len / 2 - 2)
                .setBrightness(255)
                .build();
        GlyphMatrixFrame frame = new GlyphMatrixFrame.Builder()
                .addTop(text)
                .build(getApplicationContext());
        mGM.setMatrixFrame(frame.render());
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (mGM != null) { mGM.turnOff(); mGM.unInit(); mGM = null; }
        return false;
    }
    @Override public IBinder onBind(Intent i, String n, Bundle a) { return null; }
}
```

AOD で毎分再描画したい場合は README の `GlyphToy.MSG_GLYPH_TOY` / `EVENT_AOD` ハンドラ（Messenger を `onBind()` で返す）を追加し、`EVENT_AOD` 受信時に `showFixedText()` を呼ぶ。

### 3.3 実機での有効化手順

1. APK をインストール
2. **Settings → Glyph Interface → Flip to Glyph → Always-on Glyph Toy** を開く
3. 一覧に自分の Toy 名（`toy.name`）が出ているので選択 → 背面マトリクスに固定文字が点灯
4. 出ていない場合: Toy が一覧自体に出ない＝manifest 不正、選択できない＝`aod_support=1` 漏れを疑う

---

## 4. 方法 B: 通常アプリから `setAppMatrixFrame()` で描画

常時表示ではないが、アプリ起動中に一時的な固定文字を出したい場合。

- **`setMatrixFrame` ではなく `setAppMatrixFrame` を使う**（Glyph Toy と衝突するため）。システムバージョン 20250801 以降が必要。
- 終了時は `closeAppMatrix()` を呼ぶ。
- **優先順位に注意**: Glyph Button が押されると Toy キャルフセルがアプリ表示をOverrideする（README の priority 図参照）。
- 同様の手順で `setText` オブジェクトを渡せば固定文字が出る:

```kotlin
val gmm = GlyphMatrixManager.getInstance(context)
gmm.init(callback)  // onServiceConnected で gmm.register(Glyph.DEVICE_25111p)
val frame = GlyphMatrixFrame.Builder()
    .addTop(GlyphMatrixObject.Builder().setText("HELLO").setPosition(0, 5).build())
    .build(context)
gmm.setAppMatrixFrame(frame.render())
// ...後で
gmm.closeAppMatrix()
```

---

## 5. Toy プレビュー画像の仕様（Phone (4a) Pro）

`toy.image` に渡す SVG の公式仕様（`image/25111_spec.svg` を解析して確認した実数値）:

| 項目 | 値 |
|:--|:--|
| キャンバス（背景含む） | **512 × 512 px** |
| Glyph Matrix 描画領域 | **440 × 440 px**（円形マスク） |
| 余白 | 上下左右 **36 px** |
| LED 1セル | **30 × 30 px** |
| 背景色 | `#000000` |
| 点灯 LED（100%輝度） | `#FFFFFF` |
| 消灯 LED（0%輝度） | `#1C1C1C` |

SVG を作って Android Studio の **Vector Asset Studio**（File → New → Vector Asset → SVG）でベクタードローアブル化するのが公式推奨フロー。プレビュー画像に自分の固定文字のイメージ（440px 円内に 13×13=LED 30px グリッドで文字を打つ）を描いておくと設定画面で見栄えが良くなる。

---

## 6. 便利ユーティリティ（`GlyphMatrixUtils` に実在するもの）

| メソッド | 用途 |
|:--|:--|
| `getLetterConfigs(text, context, textStyle)` | 文字列 → `LetterMatrix`（グリフ）リスト |
| `getLetterMaxLength(configs, reverse)` | 幅（LED 数）を計算してセンタリングに使う |
| `createBitmapWithText(text, size, brightness)` | テキストからビットマップ生成 |
| `createBitmapWithTextByPng(text, context)` | PNG ドローアブルからのテキスト bitmap |
| `dilation / erosion(bitmap)` | ドットの膨張/収縮（13×13 で細い文字を太く見せる） |
| `toGrayscaleBitmap / toGrayscaleArray` | グレースケール（調光）変換 |
| `convertToGlyphMatrix(...)` | bitmap/グリフリスト → 生のマトリクス int[] |
| `generateMatrixProgress(...)` | プログレスバー等のフレーム生成 |
| `ARROW_3x2 / ARROW_5x6` | 既定の矢印グリフ（int[][]） |

### レイヤー構成
`GlyphMatrixFrame.Builder` は **top / mid / low の 3 レイヤーまで**（各 1 オブジェクト）。テキスト + 背景パターン等を重ねられる。レイヤー間合成の配列長は一致必須（`"All arrays length must be equal"`）。

### スケール・回転
`setScale(0..200)`（100=等倍、アンカーは中央）、`setOrientation(角度)`（中心基準の回転）。13×13 では拡大すると直ぐにクリップされるので注意。

---

## 7. はまりどころ

1. **`register(Glyph.DEVICE_25111p)` を忘れると何も点かない。** 公式 Example-Project のラッパーは `DEVICE_23112` 固定なので、コピペだと 4a Pro で動かない。
2. **`aod_support=1` が無い Toy は 4a Pro では選択不可**（4a Pro は AOD-only）。
3. **日本語/全角文字は NDot グリフマップに無い可能性が高い。** `getLetterConfigs` はマップに無い文字をどう扱うか保証がない（ASCII 前提）。ひらがな・漢字を出したい場合は `setText` を諦め、`GlyphMatrixUtils.createBitmapWithText()` で Android の Paint によるbitmapを作り、`setImageSource(bitmap)`（1:1 の正方形 bitmap 必須）で描画する方が確実。ただし 13×13 まで downsample されるので可読性は低い。
4. **`setText` と `setImageSource` は同時設定不可**（ビルド時例外）。
5. **円形マスク**: 四隅の LED は物理的に無い。13×13 前提で配置すると欠ける。実効描画領域は直径約 13 の円。
6. **`build()` の引数**: `GlyphMatrixFrame.Builder.build(context)` は Context 必須（README のサンプルは古い）。`GlyphMatrixObject.Builder.build()` は Context 不要。
7. **调试モード**: 旧キット由来で `adb shell settings put global nt_glyph_interface_debug_enable 1`（48h で自動 OFF）。Toy が認識されないとき確認。
8. **配布前**は必ず Manage Glyph Toys 画面への誘導を入れる（README の Best Practice）:
   ```kotlin
   Intent().apply {
       setComponent(ComponentName(
           "com.nothing.thirdparty",
           "com.nothing.thirdparty.matrix.toys.manager.ToysManagerActivity"))
   } // システムバージョン 20250829 以降
   ```

---

## 8. 参考リンク

- Kit: https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit
- 公式デモ: https://github.com/Nothing-Developer-Programme/GlyphMatrix-Example-Project（Kotlin ラッパー `GlyphMatrixService.kt` 同梱）
- 旧ストライプ用キット（Phone(1)/(2) 系): https://github.com/Nothing-Developer-Programme/Glyph-Developer-Kit
- コミュニティ（サポートより速い可能性）: https://nothing.community/t/glyph-sdk
- サポート: GDKsupport@nothing.tech

### 本ガイドの手順（再現可能）
```bash
git clone --depth 1 https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit.git
unzip glyph-matrix-sdk-2.0.aar -d aar/ && unzip aar/classes.jar -d classes/
javap -p -constants -classpath classes com.nothing.ketchum.GlyphMatrixObject        # marquee 定数
javap -p -classpath classes com.nothing.ketchum.GlyphMatrixObject\$Builder           # setText オーバーロード
javap -p -c  -classpath classes com.nothing.ketchum.Glyph                           # 機種コード・マトリクス長
```
