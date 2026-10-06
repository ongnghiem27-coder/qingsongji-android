# 轻松记 · Android APP 1.2.0

这是可以安装的安卓 APK 工程。界面、React 运行时与功能全部随 APK 打包，在应用自己的窗口中运行；不连接网页服务器。应用未声明 INTERNET 权限，没有广告、登录或云同步。

- 包名：com.qingsongji.diary
- Android 8.0+（API 26），target API 35；系统 WebView 需支持现代网页组件。
- 使用 Android 官方 WebView/文件选择 API，复用现有日记界面与 TypeScript 校验规则。评估过 Capacitor，但本项目仅需本机记录和系统文件读写，采用平台 API 可减少框架依赖与安装包体积。代码不依赖 Google Chrome 浏览器。
- 数据保存在 APP 私有文件目录；先写另一份文件并校验，再提交索引，失败保留旧记录。未填写项目仍代表未观察。
- JSON 备份/恢复和 CSV 导出使用系统文件选择器。与网页版/微信小程序的轻松记 v1/v2 JSON 备份兼容。
- 不使用浏览器的下载锚点导出备份；可直接选择手机文件夹保存。二维码保存也使用系统选择器。
- APP 与网页版不会自动同步。卸载、清除应用数据或换机前，先导出 JSON 备份。已导出的文件须自行保管/删除。

## 安装

用安卓手机浏览器下载 `qingsongji-1.2.0.apk`，打开下载文件；按系统提示允许该浏览器来源安装，然后安装。安装后在桌面打开“轻松记”。无需通过“添加到主屏幕”安装网页。

本次提供直接分发的签名安装包，未上架应用商店。安装过程由手机系统和设备管理策略控制。

## 构建与验证

根项目 `npm run prepare:android` 生成随包资源；GitHub Actions 使用 Android SDK 工具编译 Java、D8、AAPT2，并用项目签名密钥生成 APK。没有运行时框架服务器，也没有远端加载 URL。

签名证书用于后续覆盖升级，私钥保存在本地 `.android-signing/`（未提交），云构建只读取 GitHub 加密 Secrets；不得把此目录上传公开仓库或附在 APK 分发包里。

`tests/RecordStoreTest.java` 检查本机读写、失败保护与重置。`tests/smoke.py` 在 Android 模拟器上验证离线启动、记录、重启读取、文件选择取消和二维码入口。实际执行结果以交付验证说明为准。

框架文件使用平台 API 编写；随包 JavaScript 依赖许可证在 `assets/web/third-party-notices.txt` 中。App 图标来自已有轻松记图标资源，本次没有调用图片生成工具。
