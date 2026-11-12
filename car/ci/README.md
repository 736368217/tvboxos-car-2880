# 共享签名密钥（覆盖安装用）

`debug.keystore` 是**原打包机** `~/.android/debug.keystore` 的备份。r74–r80 的官方包都是用它签的，
车机上已安装的版本只认这把证书；换任何别的 keystore（包括 `.github/workflows/TVBoxOSC.jks`，
alias `tvboxosc`）都会导致 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`，只能卸载重装。

Android Studio 每台机器的 debug keystore 都是首次构建时随机生成的，所以其他机器必须用这份
共享文件签名，包才能覆盖安装。

## 身份

| 项 | 值 |
|---|---|
| 文件 SHA-256 | `329FFAAE95367D9C9685EC39F657A495D811FE694175009F8845930B59365802` |
| 证书 SHA-256 | `2F:EB:63:C5:11:CD:72:57:95:CA:DC:8F:A8:90:F9:CB:C7:54:FE:59:C0:BB:FE:07:87:AC:33:F9:4F:44:FA:9C` |
| alias | `androiddebugkey` |
| store/key 密码 | `android` |
| 用途 | `com.github.tvbox.osc.jun` 的 debug 签名，**不是** Play/正式发布密钥 |

## 用法

本机（Windows）：

```powershell
$tools = 'D:\codex\projects\2026-08-21\a\work\android-toolchain\sdk\build-tools\30.0.3'
& "$tools\zipalign.exe" -f -p 4 unsigned.apk aligned.apk
& "$tools\apksigner.bat" sign --ks car\ci\debug.keystore `
  --ks-pass pass:android --key-pass pass:android --ks-key-alias androiddebugkey `
  --v1-signing-enabled true --v2-signing-enabled true --v4-signing-enabled true `
  --out signed.apk aligned.apk
& "$tools\apksigner.bat" verify --print-certs signed.apk
```

Linux / GitHub Actions 用 `$ANDROID_HOME/build-tools/30.0.3/` 下的同名工具，参数一致。
`build-release.yml` 已内置这一步。

签名后**必须**核对证书 SHA-256 是 `2feb63c5…fa9c`，再发布。

## 安全提醒（重要）

本仓库是 **PUBLIC**。把私钥放进公开仓库意味着任何能看到仓库的人都能用同一把证书
签出可覆盖安装的 APK。这对侧载 debug 包通常可接受（用户诉求本就是"任何机器都能打包覆盖"），
但如果以后需要收紧：

1. 更安全的做法是把它放进 **GitHub Actions Secret**（`DEBUG_KEYSTORE_BASE64`），
   CI 解码后签名；`build-release.yml` 会优先读该 Secret，未配置时才回退到本文件。
2. 从仓库删除本文件**不能**收回已公开的密钥 —— 它曾留在 git 历史里，
   彻底轮换需要换证书，而换证书会让所有已安装用户只能卸载重装。

因此不要把本文件误当成正式发布密钥使用，也不要用它签其他应用。
