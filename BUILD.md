# OctoDroid Debug 版从头构建指南

本文档描述如何在 Muse 服务器上从零开始完整构建出 OctoDroid debug APK。
按顺序执行每一步，即可准确复现产物。如只做日常重打包，直接运行
`~/workspace/7Z1H/scripts/OctoDroid/build-octodroid.sh` 即可（跳到第 12 节）。

> 本指南已于 2026-10-05 用 fresh clone 实测验证：按步骤执行可一次构建成功。
> 构建必需的 gitignore 文件有两个：`client.properties`（第 3 节）与
> `signing.properties`（第 4 节），fresh clone 后必须补齐。

## 0. 目录约定

| 用途 | 目录 |
|---|---|
| 源码 | `~/workspace/7Z1H/codes` |
| 工具（JDK / SDK / Gradle） | `~/workspace/7Z1H/tools` |
| 依赖（Gradle 缓存） | `~/workspace/7Z1H/dependencies` |
| 产物（APK） | `~/workspace/7Z1H/apps/OctoDroid` |
| 脚本 | `~/workspace/7Z1H/scripts/OctoDroid` |

> 约定：安装目录如需变更，须先通知用户并获得审批。

## 1. 前置准备（需要用户提供）

1. 在 GitHub → Settings → Developer settings → OAuth Apps 新建应用，
   **Authorization callback URL 填 `gh4a://oauth`**（否则登录后跳不回来）。
2. 拿到该应用的 **Client ID** 和 **Client Secret**，下面第 3 节要用。
   Secret 会以明文存于服务器并编译进 APK，这是项目本身的设计。
3. release keystore（如需打出带正式签名的 release 包；只在服务器验证
   构建流程时可跳过，用第 4 节方案 B 的临时 keystore 代替）。

## 2. 克隆源码

```bash
mkdir -p ~/workspace/7Z1H/codes
cd ~/workspace/7Z1H/codes
git clone https://github.com/3299074036/OctoDroid.git
cd OctoDroid
git log --oneline -1   # 确认分支与提交
```

## 3. 写入 GitHub OAuth 凭据

在源码根目录创建 `client.properties`。**注意：值必须带双引号**——
`app/build.gradle` 用 `buildConfigField` 把它直接拼成 Java 字符串字面量，
不带引号会导致编译失败（`illegal start of expression`）：

```bash
cat > ~/workspace/7Z1H/codes/OctoDroid/client.properties <<'EOF'
ClientId="你的ClientId"
ClientSecret="你的ClientSecret"
EOF
chmod 600 ~/workspace/7Z1H/codes/OctoDroid/client.properties
```

> 说明：该文件是构建必需——缺失或格式错误都会编译失败（build.gradle 里
> M-13 注释声称"无文件时给空默认值不断构建"，实际空值无引号同样编不过，
> 属已知问题；暂按本节要求提供文件）。
> 如果只是验证构建流程，可以先填任意假值；但 APK 装到手机上后 GitHub
> 登录会不可用，届时填入真实凭据重编即可。

## 4. 写入签名配置（构建必需）

`app/build.gradle` 在配置阶段强制要求 `signing.properties` 存在且四个键
齐全，缺失则直接中断构建——**即使只编 debug 包也一样**。这是有意设计
（L-12）：避免静默产出未签名的 release 包。

在源码根目录创建 `signing.properties`，二选一：

方案 A：你手头有正式 release keystore（要出正式包）：

```bash
cat > ~/workspace/7Z1H/codes/OctoDroid/signing.properties <<'EOF'
STORE_FILE=/path/to/octodroid-release.keystore
STORE_PASSWORD=你的keystore口令
KEY_ALIAS=octodroid
KEY_PASSWORD=你的key口令
EOF
chmod 600 ~/workspace/7Z1H/codes/OctoDroid/signing.properties
```

方案 B：只验证构建流程（没有正式 keystore）：先生成临时 keystore 再指向它：

```bash
export PATH=~/workspace/7Z1H/tools/jdk-21/bin:$PATH
mkdir -p /tmp/tmpkeystore
keytool -genkeypair -noprompt \
  -keystore /tmp/tmpkeystore/tmp-release.keystore \
  -alias tmpkey -keyalg RSA -keysize 2048 -validity 365 \
  -storepass tmp123 -keypass tmp123 -dname "CN=Tmp"
cat > ~/workspace/7Z1H/codes/OctoDroid/signing.properties <<'EOF'
STORE_FILE=/tmp/tmpkeystore/tmp-release.keystore
STORE_PASSWORD=tmp123
KEY_ALIAS=tmpkey
KEY_PASSWORD=tmp123
EOF
chmod 600 ~/workspace/7Z1H/codes/OctoDroid/signing.properties
```

> 注意：方案 B 编出的 release 包是临时签名，不能用于覆盖升级正式包，
> 仅证明构建流程走通。debug 包不受影响（走第 8 节的 debug keystore）。

## 5. 安装 JDK 21（Temurin）

```bash
mkdir -p ~/workspace/7Z1H/tools
cd ~/workspace/7Z1H/tools
curl -sSL -o jdk21.tar.gz "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse"
mkdir -p jdk-21
tar xzf jdk21.tar.gz -C jdk-21 --strip-components=1
rm -f jdk21.tar.gz
./jdk-21/bin/java -version   # 应显示 21.x Temurin
```

## 6. 安装 Android SDK

### 6.1 command-line tools

```bash
cd ~/workspace/7Z1H/tools
curl -sSL -o cmdtools.zip "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
mkdir -p android-sdk/cmdline-tools
unzip -q -o cmdtools.zip -d android-sdk/cmdline-tools
mv android-sdk/cmdline-tools/cmdline-tools android-sdk/cmdline-tools/latest
rm -f cmdtools.zip
ls android-sdk/cmdline-tools/latest/bin   # 应看到 sdkmanager
```

### 6.2 platform-36（必须用 curl 手装）

> 注意：`sdkmanager` 经本服务器的认证代理访问会抛 Java `NoSuchElementException`
> 而失败，因此 platform 与 build-tools 改用 curl 下载后手动解压安装。

```bash
cd /tmp
curl -sSL --max-time 300 -o p36.zip https://dl.google.com/android/repository/platform-36_r02.zip
SDK=~/workspace/7Z1H/tools/android-sdk
mkdir -p $SDK/platforms
rm -rf $SDK/platforms/android-36
unzip -q -o p36.zip -d $SDK/platforms
rm -f p36.zip
ls $SDK/platforms/android-36/android.jar   # 确认存在
```

### 6.3 build-tools 36.0.0（必须用 curl 手装）

```bash
cd /tmp
curl -sSL --max-time 300 -o bt36.zip https://dl.google.com/android/repository/build-tools_r36_linux.zip
mkdir -p bt36x && unzip -q -o bt36.zip -d bt36x
SDK=~/workspace/7Z1H/tools/android-sdk
rm -rf $SDK/build-tools/36.0.0
mkdir -p $SDK/build-tools/36.0.0
mv bt36x/android-16/* $SDK/build-tools/36.0.0/
rm -rf bt36x bt36.zip
ls $SDK/build-tools/36.0.0/aapt2 $SDK/build-tools/36.0.0/apksigner   # 确认存在
```

### 6.4 写入 SDK licenses

```bash
SDK=~/workspace/7Z1H/tools/android-sdk
mkdir -p $SDK/licenses
printf '8933bad161af4178b1185d1a37fbf41ea5269c55\nd56f5187479451eabf01fb78af6dfcb131a6481e\n24333f8a63b6825a5c5514c16c2589d1e4d839df\n' > $SDK/licenses/android-sdk-license
printf '84831b9409646a918e30573bab4c9c91346d6847\n' > $SDK/licenses/android-sdk-preview-license
```

## 7. 安装 Gradle 8.13（独立安装，不用 wrapper）

> 不用 `gradlew` wrapper：wrapper 分发包需 Java 直连下载，会被认证代理拦下。

```bash
cd /tmp
curl -sSL --max-time 300 -o gradle.zip https://services.gradle.org/distributions/gradle-8.13-bin.zip
rm -rf ~/workspace/7Z1H/tools/gradle-8.13
unzip -q -o gradle.zip -d ~/workspace/7Z1H/tools
rm -f gradle.zip
export PATH=~/workspace/7Z1H/tools/jdk-21/bin:~/workspace/7Z1H/tools/gradle-8.13/bin:$PATH
export JAVA_HOME=~/workspace/7Z1H/tools/jdk-21
gradle --version | head -8   # 应显示 Gradle 8.13, JVM 21
```

## 8. 生成固定 debug keystore

默认的 `~/.android/debug.keystore` 在家目录之外，VM 更换会丢失，导致重装时签名冲突。
因此生成一份持久化 keystore，构建配置会优先使用它：

```bash
mkdir -p ~/workspace/7Z1H/codes/keystores
~/workspace/7Z1H/tools/jdk-21/bin/keytool -genkeypair -v \
  -keystore ~/workspace/7Z1H/codes/keystores/octodroid-debug.keystore \
  -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10950 \
  -storepass android -keypass android \
  -dname "CN=Android Debug, O=Android, C=US"
chmod 600 ~/workspace/7Z1H/codes/keystores/octodroid-debug.keystore
```

口令/别名必须与 `app/build.gradle` 中 `signingConfigs.debug` 的引用一致
（storeFile `../../keystores/octodroid-debug.keystore`，口令均为 `android`，
别名 `androiddebugkey`；文件不存在时自动回退为 AGP 默认签名）。

## 9. 配置 Gradle 用户目录（含出口代理）

```bash
mkdir -p ~/workspace/7Z1H/dependencies/gradle
```

代理（含认证）从环境变量生成，避免把密码写进文档。执行下面这段 Python，
它会读取 `https_proxy` 环境变量并写入 `gradle.properties`：

```bash
python3 - <<'EOF'
import os, urllib.parse
u = urllib.parse.urlparse(os.environ["https_proxy"])
lines = []
for scheme in ("http", "https"):
    lines += [
        f"systemProp.{scheme}.proxyHost={u.hostname}",
        f"systemProp.{scheme}.proxyPort={u.port}",
        f"systemProp.{scheme}.proxyUser={u.username}",
        f"systemProp.{scheme}.proxyPassword={u.password}",
        f"systemProp.{scheme}.nonProxyHosts=localhost|127.0.0.1",
    ]
lines.append("org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8")
p = os.path.expanduser("~/workspace/7Z1H/dependencies/gradle/gradle.properties")
open(p, "w").write("\n".join(lines) + "\n")
os.chmod(p, 0o600)
print("written:", p)
EOF
```

## 10. 导入出口 TLS 拦截 CA 到 JDK

服务器出口有 TLS 拦截：curl 能用是因为系统 CA  bundle 信任了拦截 CA，
而 Java 用自己的 `cacerts`，不导入则 Gradle 下载依赖时报
`PKIX path building failed`。导入一次即可：

```bash
export PATH=~/workspace/7Z1H/tools/jdk-21/bin:$PATH
keytool -importcert -noprompt -trustcacerts \
  -alias hatch-egress-ca \
  -file /usr/local/share/ca-certificates/hatch-egress-ca.crt \
  -keystore ~/workspace/7Z1H/tools/jdk-21/lib/security/cacerts \
  -storepass changeit
keytool -list -alias hatch-egress-ca \
  -keystore ~/workspace/7Z1H/tools/jdk-21/lib/security/cacerts \
  -storepass changeit   # 应显示 trustedCertEntry
```

> 注意：如果以后重装/升级了 JDK，需对新 JDK 重做这一步。
> 另注意：出口 CA 的密钥会不定期轮换。若之前导过、现在依赖下载报
> `PKIX path validation failed: ... signature check failed`，说明 JDK 里是
> 同名旧 CA，先删掉再重导：
> `keytool -delete -alias hatch-egress-ca -cacerts`，然后重跑本节命令。

## 11. 构建脚本

脚本位于 `~/workspace/7Z1H/scripts/OctoDroid/build-octodroid.sh`，内容要点：

- 设置 `JAVA_HOME`、`ANDROID_HOME`、`ANDROID_SDK_ROOT`、`GRADLE_USER_HOME`、
  `PATH`（JDK 21 + Gradle 8.13）。
- `export _JAVA_OPTIONS="-Djava.net.preferIPv4Stack=true"` —— **必需**。
  本沙箱 IPv6 映射路由（`::ffff:0:0/96`）是黑洞：不强制 IPv4，
  Gradle client 连不上本地 daemon，daemon 也连不上出口代理。
  必须走 `_JAVA_OPTIONS`（JVM 级别，每个 Java 进程自动生效），
  因为 Gradle 会过滤 `org.gradle.jvmargs` 里的自定义 `-D`
  （只保留 `-Xmx` / `-Dfile.encoding`），写 gradle.properties 无效。
- `gradle assembleDebug --no-daemon`，版本号从 `app/build.gradle` 的
  `versionName` 自动读取，产物复制到
  `~/workspace/7Z1H/apps/OctoDroid/OctoDroid-debug-<版本>.apk`。

## 12. 执行构建

```bash
bash ~/workspace/7Z1H/scripts/OctoDroid/build-octodroid.sh
```

首次构建约 10 分钟（含下载 AGP / AndroidX 等全部依赖，约数百 MB）；
依赖缓存后再次构建只需 1 分钟左右。

## 13. 验证产物

版本号从 `app/build.gradle` 读取（与构建脚本同法，不要手写版本号）：

```bash
VERSION=$(grep -a 'versionName' ~/workspace/7Z1H/codes/OctoDroid/app/build.gradle | head -1 | sed -E 's/.*"([^"]+)".*/\1/')
APK=~/workspace/7Z1H/apps/OctoDroid/OctoDroid-debug-${VERSION}.apk
export PATH=~/workspace/7Z1H/tools/jdk-21/bin:$PATH
BT=~/workspace/7Z1H/tools/android-sdk/build-tools/36.0.0

# 1) 包名与版本
$BT/aapt2 dump badging $APK | grep -E "^package"
# 应为 package: name='com.gh4a.debug' versionCode='<数字>' versionName='<版本>' ...

# 2) 签名有效且来自固定 keystore
$BT/apksigner verify --print-certs $APK | grep -E "Signer #1|SHA-256"
keytool -list -v -keystore ~/workspace/7Z1H/codes/keystores/octodroid-debug.keystore \
  -storepass android | grep -i sha256
# 两处 SHA-256 指纹必须一致

# 3) 完整性
sha256sum $APK
```

## 14. 运行单测（可选）

```bash
cp ~/workspace/7Z1H/scripts/robo-init.gradle /tmp/robo-init.gradle  # /tmp 会被 VM 更换清空，每次重拷
export JAVA_HOME=~/workspace/7Z1H/tools/jdk-21
export ANDROID_HOME=~/workspace/7Z1H/tools/android-sdk
export GRADLE_USER_HOME=~/workspace/7Z1H/dependencies/gradle
export _JAVA_OPTIONS="-Djava.net.preferIPv4Stack=true"
export PATH=~/workspace/7Z1H/tools/jdk-21/bin:~/workspace/7Z1H/tools/gradle-8.13/bin:$PATH
cd ~/workspace/7Z1H/codes/OctoDroid
gradle testDebugUnitTest -I /tmp/robo-init.gradle --no-daemon
```

init 脚本已配好 Robolectric 离线模式（`robolectric.offline=true`，
依赖目录 `~/.robolectric-deps`），无需额外下载。

## 附录：故障排查

| 现象 | 原因与处理 |
|---|---|
| `signing.properties not found or incomplete, refusing to build ...` | 漏了第 4 节。即使只编 debug，配置阶段也强制要求该文件，按第 4 节补齐。 |
| `BuildConfig.java: error: illegal start of expression`（CLIENT_ID / CLIENT_SECRET） | `client.properties` 的值没带双引号，或文件缺失。按第 3 节重写（值必须 `"..."` 带引号）。 |
| `Could not receive a message from the daemon` | 缺 IPv4 强制。确认构建环境里 `export _JAVA_OPTIONS="-Djava.net.preferIPv4Stack=true"` 生效（看日志有无 `Picked up _JAVA_OPTIONS`）。 |
| `PKIX path building failed` | 漏了第 10 节 CA 导入，或 JDK 被重装后未重做。 |
| `PKIX path validation failed: ... signature check failed` | 出口拦截 CA 密钥已轮换，JDK 里是同名旧 CA。`keytool -delete -alias hatch-egress-ca -cacerts` 删掉后按第 10 节重导。 |
| `Could not GET https://dl.google.com/...`（daemon 报连接重置） | daemon 的代理连接走了 IPv6 黑洞，同上，补 `_JAVA_OPTIONS`。 |
| `Timeout waiting to lock journal cache` | 有残留 Gradle daemon 占着缓存锁，执行 `gradle --stop` 后重试。 |
| 依赖 404（Maven Central） | 正常。AGP 只发布在 Google Maven（`dl.google.com`），不在 Central。 |
| 产物签名指纹与 keystore 不一致 | 检查 `codes/keystores/octodroid-debug.keystore` 是否存在；不存在时会回退为 AGP 默认 debug 签名。 |
