# OnHand LLM for Android

Android 端末だけでローカル LLM を GUI から起動・管理・利用できるアプリです。
コマンド操作は一切不要。モデルの追加からチャット、API サーバーの起動までぜんぶ画面から操作できます。

## 特徴

- **完全ローカル推論** — MediaPipe LLM Inference API を使い、端末上で LLM を実行。ネット接続なしでチャットできます
- **GUI だけで完結** — モデルの追加(ファイル / URL ダウンロード)、読み込み、削除、チャット、サーバー起動をすべて画面から操作
- **アプリ内チャット** — ストリーミング表示、生成の停止、会話クリア、履歴の自動保存
- **OpenAI 互換ローカル API サーバー** — `POST /v1/chat/completions`(stream 対応)。同一 Wi-Fi 内の PC など他の端末からも利用可能
- **ホーム画面ウィジェット** — モデル / サーバーの状態表示、チャット起動、API サーバーの ON/OFF
- **設定の永続化** — サンプリング設定・使用モデル・サーバーポートなどを保存し、起動時に前回のモデルを自動読み込み
- **状態とエラーの可視化** — 読み込み中 / 生成中 / エラーをバナーやチップで表示

## 動作要件

- Android 8.0 (API 26) 以上
- RAM 6GB 以上推奨(1B クラスのモデルは 4GB でも動作可)
- 対応モデル形式: `.task` / `.litertlm`(MediaPipe LLM Inference 用バンドル)

## 使い方

### 1. モデルを入手する

「モデル」タブから 2 通りの方法で追加できます。

- **ファイルから追加**: PC などでダウンロードした `.task` / `.litertlm` ファイルを端末に転送し、ファイルピッカーで選択
- **URL からダウンロード**: Hugging Face などの直接ダウンロード URL を入力(おすすめモデルはワンタップ)

主なモデルの入手先は Hugging Face の [litert-community](https://huggingface.co/litert-community) です。
Gemma 系はゲート付きモデルのため、ブラウザでライセンスに同意した上で、「設定」タブに
Hugging Face アクセストークンを登録してからダウンロードしてください。

> おすすめモデルの URL は Hugging Face 側の都合で変わることがあります。失敗する場合はリポジトリページで最新のファイル名を確認し、「URLからダウンロード」を使ってください。

### 2. モデルを読み込んでチャット

モデル一覧の「読み込む」を押すと推論エンジンにロードされ、「チャット」タブで会話できます。
応答はストリーミング表示され、■ボタンでいつでも停止できます。

### 3. ローカル API サーバー

「サーバー」タブでスイッチを ON にすると、OpenAI 互換 API サーバーが起動します(フォアグラウンドサービスとして常駐)。

```bash
curl http://<端末のIP>:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "messages": [{"role": "user", "content": "こんにちは"}],
    "stream": false
  }'
```

| エンドポイント | 説明 |
|---|---|
| `GET /` | ステータスページ (ブラウザで確認可) |
| `GET /health` | ヘルスチェック |
| `GET /v1/models` | 読み込み済みモデル一覧 |
| `POST /v1/chat/completions` | チャット補完。`stream: true` で SSE ストリーミング |

### 4. ウィジェット

ホーム画面に OnHand LLM ウィジェットを追加すると、モデル / API サーバーの状態確認、
チャットの起動、API サーバーの ON/OFF ができます。

## ビルド方法

```bash
./gradlew assembleDebug
# 出力: app/build/outputs/apk/debug/app-debug.apk
```

- JDK 17 と Android SDK (compileSdk 35) が必要です
- GitHub Actions (`.github/workflows/android.yml`) で push ごとにデバッグ APK をビルドし、Artifacts に添付します

## アーキテクチャ

```
app/src/main/java/com/onhand/llm/
├── OnHandApp.kt          # Application / DI コンテナ入口
├── AppContainer.kt       # 手動 DI (シングルトン保持・ウィジェット同期・自動読み込み)
├── MainActivity.kt
├── engine/               # 推論エンジン
│   ├── InferenceEngine.kt    # 抽象インターフェース (将来 GGUF 等を追加可能)
│   └── MediaPipeEngine.kt    # MediaPipe LLM Inference 実装
├── data/                 # 永続化
│   ├── ModelRepository.kt    # モデルのインポート / ダウンロード / 登録 (models.json)
│   ├── SettingsRepository.kt # DataStore による設定保存
│   └── ChatStore.kt          # チャット履歴 (JSON)
├── server/               # ローカル API サーバー
│   ├── ApiServer.kt          # NanoHTTPD / OpenAI 互換エンドポイント
│   ├── ServerController.kt   # 起動/停止・ログ・状態
│   └── ServerService.kt      # フォアグラウンドサービス
├── widget/               # Glance ホーム画面ウィジェット
└── ui/                   # Jetpack Compose (チャット / モデル / サーバー / 設定)
```

技術選定の理由や開発メモは [CLAUDE.md](CLAUDE.md) を参照してください。

## 進捗

- [x] プロジェクト基盤 (Kotlin / Compose / Material 3 / MVVM)
- [x] MediaPipe LLM Inference による端末内推論
- [x] モデル管理 GUI (ファイルインポート / URL ダウンロード / 削除 / おすすめモデル)
- [x] アプリ内チャット (ストリーミング / 停止 / 履歴保存 / 会話クリア)
- [x] OpenAI 互換ローカル API サーバー (stream 対応, ステータスページ, リクエストログ)
- [x] ホーム画面ウィジェット (状態表示 / チャット起動 / サーバー切替)
- [x] 設定の永続化と起動時のモデル自動読み込み
- [x] エラー / 状態の GUI 表示 (バナー, チップ, ログ)
- [x] GitHub Actions CI (APK ビルド)
- [ ] 実機での動作確認
- [ ] GGUF (llama.cpp) バックエンドの追加
- [ ] チャット履歴の複数会話対応
- [ ] ウィジェットからのクイック質問

## ライセンス

未定 (TBD)
