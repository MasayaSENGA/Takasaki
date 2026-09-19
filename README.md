# Takasaki Player

Android 用の最小構成の音楽再生アプリです。

## 機能
- 端末内の音楽ファイル（MediaStore）を一覧表示
- タップで再生、再生 / 一時停止、前の曲 / 次の曲
- シークバーで再生位置の変更
- 曲の終わりで自動的に次の曲へ（一覧の最後まで行くと先頭に戻る）
- バックグラウンド再生（ホームに戻っても・画面を消しても再生を継続）
- 通知・ロック画面・イヤホンのボタンから再生操作
- イヤホンを抜くと自動で一時停止
- 端末のライト／ダーク設定に追従した濃い緑のテーマ

## 構成
- Kotlin + Jetpack Compose (Material3)
- 再生は Jetpack Media3（ExoPlayer + MediaSessionService）
- minSdk 26 / targetSdk 36

| ファイル | 役割 |
| --- | --- |
| `MainActivity.kt` | 画面（曲一覧・操作バー）と権限の取得 |
| `PlayerViewModel.kt` | 曲一覧の読み込みと、MediaController 経由での再生指示 |
| `PlaybackService.kt` | ExoPlayer を持つフォアグラウンドサービス。通知・ロック画面操作は Media3 が自動生成 |
| `Theme.kt` | 濃い緑ベースの配色（ライト／ダーク） |

## ビルド
Android Studio でこのフォルダを開いて Run するか、コマンドラインで:

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 今後追加候補（現状は未対応）
- シャッフル・リピート切り替え、プレイリスト、アルバムアート
