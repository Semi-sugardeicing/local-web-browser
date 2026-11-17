# 本地网页浏览器

一个只读本地文件的安卓网页浏览器。把手机里的网页文件夹导入进来，之后不联网也能翻，页面之间的跳转、图片、子文件夹都照旧可用。

- 不联网：应用不申请网络权限，也不含任何联网代码。
- 只读本地：导入时把文件复制进应用私有目录，原文件删了也不影响。
- 不搜索、不上传、无账号。

最低系统：Android 14（API 34）。包名 `com.sugardeicing.localserver`。

## 功能

- 首页列出已导入的站点与单独导入的文件，点进去直接看
- 支持整文件夹导入（保留目录层级与子文件夹）
- 内置《使用说明》和《示例站点》两个演示页
- 深色 / 浅色 / 跟随系统三种主题
- 网页默认锁死缩放，可在设置里放开
- 环境自检：启动时可查 8 项运行环境
- 日志：最近 1000 条，超过 512 KB 自动截断，可导出

## 编译

需要 JDK 17 与 Android SDK 35。

```
gradle assembleRelease
```

产物在 `app/build/outputs/apk/release/`。

签名信息不写进仓库：在根目录 `local.properties`（已被 `.gitignore` 忽略）里填
`RELEASE_STORE_FILE` / `RELEASE_STORE_PASSWORD` / `RELEASE_KEY_ALIAS` / `RELEASE_KEY_PASSWORD`，
或用同名环境变量。没有配置密钥时 release 会退回调试签名，仅供本机测试，**不可用于发布**。

## 目录

```
app/src/main/java/com/sugardeicing/localserver/   源码
app/src/main/assets/builtin/                      内置说明页与示例站点
app/src/main/res/                                  布局、颜色、图标
```

## 许可

MIT，见 LICENSE。
