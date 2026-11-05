# 模拟摄像头：第三步

摄像头尚未安装，本步先跑通页面到图像驱动的完整调用过程。

## 已实现

- 摄像头导航和独立页面。
- 每秒获取一张 JPEG 作为动态预览。
- 手动抓拍、当前会话缩略图和浏览器下载。
- 页面与生成图像同时标明“模拟”，不宣称海康实物已连接。
- 不写数据库、不保存服务端文件；刷新页面后抓拍列表清空。

## 调用关系

预览：`CameraPage → GET /api/camera/preview → CameraController → CameraService → CameraDriver`。

抓拍：`CameraPage → POST /api/camera/captures → CameraController → CameraService → CameraDriver → 浏览器内存`。

当前 `SimulatedCameraDriver` 生成 960×540 JPEG。等 DS-2CD3T25-I3 安装完成后，增加真实网络摄像头驱动并替换 Spring 注入；Controller、Service、页面和下载流程不需要改写。

实物接入时再增加 IP、用户名、密码和超时配置。密码只放本机环境变量，不提交到仓库。先验证抓图接口，再决定是否增加 RTSP 转流预览。
