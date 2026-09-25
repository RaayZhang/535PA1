# COMP535 PA1 组员共享版

这里是 PA1 的完整提交项目。Java 源码、7 个路由器配置文件和 Maven 配置在 `comp535_sketch_code/` 中；该目录内的 `README.md` 是教授提供的英文说明，保持原样。根目录的 `AI_Usage_Report.md` 记录当前版本开发过程中的生成式 AI 使用情况；组员应按自己的实际使用情况核对报告内容。

## 已实现的功能

- 启动路由器时显示 Process IP、Process Port 和 Simulated IP。
- `attach`：用目标路由器的真实 IP、端口、模拟 IP 和链路权重请求连接；目标路由器可以输入 `Y` 接受或 `N` 拒绝。每台路由器最多有 4 个直接邻居，端口满时会拒绝新的连接。
- `start`：与已连接的邻居交换 HELLO 消息，使邻居状态达到 `TWO_WAY`。
- `neighbors`：只显示已经达到 `TWO_WAY` 状态的直接邻居的模拟 IP。

PA1 尚不执行链路状态数据库同步或最短路径计算。

## 编译和运行

需要 **JDK 8 或更新版本**、**Maven 3 或更新版本**。从仓库根目录打开终端，依次执行：

```powershell
cd comp535_sketch_code
java -version
mvn -version
mvn clean package assembly:single
java -jar target/COMP535-1.0-SNAPSHOT-jar-with-dependencies.jar conf/router1.conf
```

看到 Maven 的 `BUILD SUCCESS` 后，最后一行会启动 router1。另开一个终端，进入同一目录，用以下命令启动 router2；不需要再次打包：

```powershell
java -jar target/COMP535-1.0-SNAPSHOT-jar-with-dependencies.jar conf/router2.conf
```

其他路由器可将配置文件名改为 `router3.conf` 到 `router7.conf`，每台使用独立终端。`target/` 是 Maven 自动生成的构建结果，不是需要手写或提交的源码。

## 两台路由器快速检查

1. 记下 router2 启动时显示的 **Process IP** 和 **Process Port**。
2. 在 router1 输入 `attach <router2的Process IP> <router2的Process Port> 192.168.1.100 1`。最后的 `1` 是链路权重，不能省略。
3. 在 router2 输入 `Y`。此时两边输入 `neighbors` 仍不会显示对方，因为尚未完成 HELLO 握手。
4. 在 router1 输入 `start`，再在两边输入 `neighbors`；两边应分别显示对方的 Simulated IP。

要测试拒绝，请重新启动两台路由器并在第 3 步输入 `N`。要停止程序，在各终端按 `Ctrl+C`。

## 文件位置

- `comp535_sketch_code/src/main/java/socs/network/Main.java`：程序入口，读取配置并启动路由器。
- `comp535_sketch_code/src/main/java/socs/network/node/Router.java`：`attach`、`start`、`neighbors` 及网络通信的主要实现。
- `comp535_sketch_code/src/main/java/socs/network/node/RouterDescription.java`、`Link.java`：路由器身份、状态和链路数据。
- `comp535_sketch_code/src/main/java/socs/network/message/SOSPFPacket.java`：路由器之间传递的消息。
- `comp535_sketch_code/conf/`：7 台路由器的配置文件。
- `comp535_sketch_code/pom.xml`：教授提供的 Maven 构建配置。

教授原有的 Java 语句保留；PA1 的实现是在空方法中填代码，并追加必要的字段、方法和语句。
