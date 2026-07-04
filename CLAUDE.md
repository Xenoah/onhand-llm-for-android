# CLAUDE.md — OnHand LLM for Android 開発メモ

Android 端末だけでローカル LLM を GUI から起動・管理・利用するアプリ。
このファイルは仕様・技術判断・進捗の記録。作業のたびに更新すること。

## プロジェクト概要

- パッケージ: `com.onhand.llm` / アプリ名: OnHand LLM
- minSdk 26 / targetSdk & compileSdk 35 / JDK 17
- Kotlin 2.0.21 + Jetpack Compose (BOM 2024.12.01, Material 3) + MVVM
- AGP 8.7.3 / Gradle 8.10.2 (wrapper)
- DI は Hilt を使わず `AppContainer` による手動 DI(規模が小さいため)

## ビルド

```bash
./gradlew assembleDebug
```

- CI: `.github/workflows/android.yml` が push ごとに assembleDebug を実行し APK を Artifacts に添付
- 注意: Claude Code のリモート実行環境は dl.google.com / maven.google.com に接続できないため
  **ローカルでのビルド検証は不可**。ビルド確認は GitHub Actions で行うこと

## 技術選定と理由

| 項目 | 選定 | 理由 |
|---|---|---|
| 推論エンジン | MediaPipe LLM Inference API (`com.google.mediapipe:tasks-genai:0.10.24`) | Gradle 依存のみで NDK ビルド不要。`.task`/`.litertlm` (Gemma 3, Phi-4 mini, Qwen2.5 等) を CPU/GPU で実行できる。llama.cpp は JNI ビルドが必要で保守コストが高いため v1 では見送り |
| エンジン抽象化 | `engine/InferenceEngine.kt` | 将来 llama.cpp (GGUF) バックエンドを追加できるよう interface 分離 |
| API サーバー | NanoHTTPD 2.3.1 | 単一依存・実績十分。OpenAI 互換 (`/v1/chat/completions`, SSE stream 対応)。`useGzipWhenAccepted` を無効化して SSE の gzip 破損を防止 |
| ウィジェット | Glance 1.1.1 | Compose ライクに書ける。RemoteViews に EditText は置けないため、クイック質問ではなく状態表示 + チャット起動 + サーバー切替 |
| モデルDL | DownloadManager | アプリ終了後も継続、通知も自動。進捗は 1 秒ポーリング。HF トークンは Authorization ヘッダーで付与 |
| 永続化 | DataStore (設定) + JSON ファイル (モデル登録 `models.json`, チャット履歴 `chat_history.json`) | Room + KSP を入れるほどのデータ量ではない |

## アーキテクチャの要点

- `AppContainer`: 全シングルトン (settings/models/engine/chatStore/server) を保持。
  エンジン・サーバー状態を combine してウィジェットを `updateAll`。起動時のモデル自動読み込みもここ
- `MediaPipeEngine`:
  - `LlmInference` (エンジン) + `LlmInferenceSession` (チャット用継続セッション) の 2 層
  - maxTokens / backend はエンジンレベル → 変更にはモデル再読み込みが必要
  - temperature / topK / topP はセッションレベル → 「会話をクリア」で反映
  - 生成はコールバックを `callbackFlow` に変換。Flow キャンセル = `cancelGenerateResponseAsync`
  - `engineMutex` で load/unload/生成を直列化(エンジンは同時 1 生成のみ)
  - API サーバー用の `generateDetached` はリクエストごとに一時セッションを作って破棄
    (チャットの会話コンテキストを汚さない)
- プロンプトのターンテンプレート (`<start_of_turn>` 等) は `.task` バンドル側で適用されるため
  `addQueryChunk` に生テキストを渡すだけでよい。API サーバーは messages を役割ラベル付きで
  1 チャンクに連結する簡易方式 (v1 の割り切り)
- `ServerService`: FGS (type=dataSync)。サーバー本体は `ServerController` がプロセス内で保持し、
  Service は通知と生存管理のみ。ウィジェットからも start/stop
- モデル保存先: SAF インポート → `filesDir/models/`、DownloadManager → `getExternalFilesDir/models/`。
  起動時に完了済みダウンロードと孤児ファイルを registry へ自動取り込み(自己修復)

## 既知の制約 / 注意点

- MediaPipe のおすすめモデル URL (litert-community) は変わることがある。`RECOMMENDED_MODELS` は
  ベストエフォート。Gemma 系はゲート付きで HF トークン + ライセンス同意が必要
- GPU バックエンドは端末依存。失敗したら CPU に切り替えるよう UI で案内(自動フォールバックは未実装)
- API サーバーに認証はない(ローカル利用前提)。公開ネットワークでの利用は想定しない
- `strings.xml` は最小限で、UI 文言は Compose 内に日本語直書き(個人アプリの割り切り)
- 生成中のエラーは ProgressListener に error コールバックがないため、同期例外のみ捕捉

## 進捗ログ

### 2026-07-04 (初回実装)

- プロジェクト骨格を新規作成 (Gradle / version catalog / wrapper 8.10.2 / CI)
- 推論エンジン: `InferenceEngine` 抽象 + `MediaPipeEngine` 実装
- データ層: `ModelRepository` (SAF インポート / DownloadManager DL / models.json)、
  `SettingsRepository` (DataStore)、`ChatStore` (履歴 JSON)
- API サーバー: NanoHTTPD で OpenAI 互換 (`/`, `/health`, `/v1/models`, `/v1/chat/completions`
  stream 対応) + `ServerController` + FGS `ServerService`
- UI: 4 タブ (チャット / モデル / サーバー / 設定) を Compose で実装
- ウィジェット: Glance で状態表示 + チャット起動 + サーバー切替
- README.md / CLAUDE.md 作成
- 未検証: 実機動作。CI での初回ビルド結果を要確認

## 次にやること

1. GitHub Actions の初回ビルドを green にする(依存バージョンの実在確認を含む)
2. 実機で Gemma 3 1B の読み込み〜チャット〜API〜ウィジェットを通しで動作確認
3. GPU バックエンド失敗時の CPU 自動フォールバック
4. チャット履歴の複数会話対応(会話リスト + 切替)
5. GGUF (llama.cpp) バックエンド追加の検討
