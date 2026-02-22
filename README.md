# 黑马点评（hm-dianping）

## 📖 项目简介

黑马点评是一个仿「大众点评」的后端学习实战项目，主要围绕 **Redis** 的各种应用场景展开，涵盖短信登录、商户缓存、优惠券秒杀、社交关注、附近商户、用户签到、UV 统计等核心业务功能。项目以 Spring Boot 为基础框架，配合 MySQL 持久化存储与 Redis 高性能缓存，辅以 Nginx 做前端静态资源服务和反向代理，是一个前后端分离的单体应用。

---

## 🧩 核心功能模块

### 1. 短信登录
基于 Redis 实现短信验证码的存储与校验，替代传统 Session 方案，天然支持集群环境下的会话共享。使用拦截器（Interceptor）实现登录状态刷新与鉴权。

### 2. 商户查询缓存
对商户信息进行 Redis 缓存，并重点解决了以下缓存问题：
- **缓存穿透**：利用缓存空值或布隆过滤器防止恶意请求直达数据库。
- **缓存雪崩**：对 key 设置随机 TTL，避免大量缓存同时失效。
- **缓存击穿**：采用互斥锁（SETNX）或逻辑过期方案，保护热点 key。

### 3. 优惠券秒杀
实现限时、限量优惠券的抢购功能，涉及：
- 全局唯一 ID 生成器（基于时间戳 + Redis 自增）。
- 基于 Redis + Lua 脚本的库存预扣减与一人一单校验，保证原子性。
- 异步下单：利用阻塞队列（或 Redis Stream）将订单创建异步化，提升吞吐量。
- 分布式锁：自研简易分布式锁，并进一步引入 Redisson 解决锁误删、可重入、主从一致性等问题。

### 4. 社交关注（Feed 流）
- 关注 / 取关功能，基于 Redis Set 实现共同关注查询。
- 基于 Redis SortedSet 实现 Feed 流的 **推模式（Timeline）**，支持滚动分页查询。

### 5. 附近商户（GEO）
利用 Redis 的 GEO 数据结构，按照地理坐标检索附近商户并按距离排序。

### 6. 用户签到（BitMap）
使用 Redis BitMap 记录用户每月签到情况，支持签到、查询连续签到天数等操作，极大节省存储空间。

### 7. UV 统计（HyperLogLog）
使用 Redis HyperLogLog 进行页面独立访客（UV）统计，在极小内存开销下完成海量去重计数。

---

## 🛠️ 技术栈

| 层次 | 技术 |
|------|------|
| 后端框架 | Spring Boot |
| 持久层 | MyBatis-Plus |
| 数据库 | MySQL |
| 缓存/中间件 | Redis（Jedis / Lettuce / Redisson） |
| 前端 | HTML + CSS + JavaScript（静态页面） |
| Web 服务器 | Nginx 1.18.0（静态资源托管 & 反向代理） |
| 构建工具 | Maven |
| 其他 | Lua 脚本（Redis 原子操作）、Hutool 工具库 |

---

## 📁 项目结构

```
hm-dianping/
├── nginx-1.18.0/        # Nginx 及前端静态资源（HTML/CSS/JS）
├── src/                 # Java 后端源代码（Spring Boot）
│   └── main/
│       ├── java/        # 业务代码（Controller、Service、Utils 等）
│       └── resources/   # 配置文件（application.yaml 等）
├── hmdp.sql             # MySQL 数据库初始化脚本
├── pom.xml              # Maven 依赖配置
└── .gitignore
```

---

## 🚀 本地部署指南

### 环境要求

- **JDK** 8 或以上
- **Maven** 3.6+
- **MySQL** 5.7 或以上
- **Redis** 5.0 或以上（推荐 6.x+）
- **Nginx** 1.18.0（项目已自带）

### 第一步：克隆项目

```bash
git clone https://github.com/Citrus-bit/hm-dianping.git
cd hm-dianping
```

### 第二步：初始化数据库

1. 启动本地 MySQL 服务。
2. 创建数据库（名称参考 SQL 文件头部，通常为 `hmdp`）：
   ```sql
   CREATE DATABASE hmdp;
   USE hmdp;
   ```
3. 导入项目根目录下的 `hmdp.sql`：
   ```bash
   mysql -u root -p hmdp < hmdp.sql
   ```

### 第三步：启动 Redis

确保本地 Redis 服务已启动，默认端口 `6379`：

```bash
redis-server
```

### 第四步：修改后端配置

编辑 `src/main/resources/application.yaml`（或 `application.properties`），根据本地环境修改以下配置项：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/hmdp?useSSL=false&serverTimezone=UTC
    username: root          # 改为你的 MySQL 用户名
    password: your_password # 改为你的 MySQL 密码
  redis:
    host: localhost
    port: 6379
    # password: 如果 Redis 设置了密码，在此填写
```

### 第五步：构建并启动后端

```bash
mvn clean package -DskipTests
java -jar target/*.jar
```

或者直接在 IDE（如 IntelliJ IDEA）中运行主启动类（通常为 `HmDianPingApplication`）。

后端默认运行在 `http://localhost:8081`。

### 第六步：启动 Nginx（前端）

1. 进入项目中的 `nginx-1.18.0` 目录。
2. **Windows**：双击 `nginx.exe` 或在命令行执行：
   ```bash
   start nginx.exe
   ```
3. **macOS / Linux**：如果使用项目自带的 Nginx 不兼容当前系统，可自行安装 Nginx，然后将 `nginx-1.18.0/html` 下的前端文件复制到你的 Nginx 的 `html` 目录，并参考项目中的 `nginx.conf` 配置反向代理：
   ```nginx
   upstream backend {
       server 127.0.0.1:8081;
   }

   server {
       listen 8080;
       server_name localhost;

       location / {
           root html;
           index index.html;
       }

       location /api/ {
           proxy_pass http://backend;
       }
   }
   ```

### 第七步：访问项目

在浏览器中打开 `http://localhost:8080`，即可进入黑马点评前端页面。

---

## ⚠️ 常见问题

**Q: 启动报数据库连接失败？**
A: 检查 MySQL 是否启动，以及 `application.yaml` 中的数据库地址、用户名、密码是否正确。

**Q: Redis 连接超时？**
A: 确认 Redis 已启动，端口为 6379，如有密码需在配置中填写。

**Q: 前端页面空白 / 接口 404？**
A: 确认 Nginx 已正确启动，且 `nginx.conf` 中的反向代理地址与后端端口一致。

---

## 📚 参考

本项目源自 [黑马程序员 Redis 实战课程](https://www.bilibili.com/)，适合用于学习 Redis 在实际业务中的应用场景。
```
