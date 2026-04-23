# hm-dianping 接口文档

> 基础URL: `http://localhost:8081`
> 所有接口均返回统一响应体 `Result`，需要登录的接口须在请求头携带 `authorization: <token>`。

---

## 通用响应结构

```json
{
  "success": true,
  "errorMsg": null,
  "data": {},
  "total": 0
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `success` | Boolean | 是否成功 |
| `errorMsg` | String | 失败时的错误信息 |
| `data` | Object | 响应数据 |
| `total` | Long | 分页总数（分页接口使用） |

---

## 数据模型

### UserDTO（精简用户信息）
| 字段 | 类型 |
|---|---|
| `id` | Long |
| `nickName` | String |
| `icon` | String |

### LoginFormDTO（登录表单）
| 字段 | 类型 | 说明 |
|---|---|---|
| `phone` | String | 手机号 |
| `code` | String | 短信验证码（与password二选一） |
| `password` | String | 密码（与code二选一） |

### ScrollResult（滚动分页结果）
| 字段 | 类型 | 说明 |
|---|---|---|
| `list` | List | 当前页数据 |
| `minTime` | Long | 当前页最小时间戳（作为下次请求的游标） |
| `offset` | Integer | 相同时间戳的偏移量 |

### Shop（店铺）
| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | Long | 店铺ID |
| `name` | String | 店铺名称 |
| `typeId` | Long | 分类ID |
| `images` | String | 图片，多个逗号分隔 |
| `area` | String | 商圈 |
| `address` | String | 地址 |
| `x` | Double | 经度 |
| `y` | Double | 纬度 |
| `avgPrice` | Long | 人均价格 |
| `sold` | Integer | 销量 |
| `comments` | Integer | 评论数 |
| `score` | Integer | 评分（×10存储，45=4.5星） |
| `openHours` | String | 营业时间 |
| `distance` | Double | 距离（非DB字段，查询附近时返回） |

### Voucher（优惠券）
| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | Long | 优惠券ID |
| `shopId` | Long | 关联店铺ID |
| `title` | String | 标题 |
| `subTitle` | String | 副标题 |
| `rules` | String | 使用规则 |
| `payValue` | Long | 支付金额（分） |
| `actualValue` | Long | 抵扣金额（分） |
| `type` | Integer | 类型：0=普通券，1=秒杀券 |
| `status` | Integer | 状态：1=上架，2=下架，3=过期 |
| `stock` | Integer | 库存（秒杀券专有） |
| `beginTime` | LocalDateTime | 秒杀开始时间（秒杀券专有） |
| `endTime` | LocalDateTime | 秒杀结束时间（秒杀券专有） |

### Blog（笔记/博客）
| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | Long | 笔记ID |
| `shopId` | Long | 关联店铺ID |
| `userId` | Long | 作者用户ID |
| `title` | String | 标题 |
| `images` | String | 图片，多个逗号分隔 |
| `content` | String | 文字内容 |
| `liked` | Integer | 点赞数 |
| `comments` | Integer | 评论数 |
| `icon` | String | 作者头像（非DB字段） |
| `name` | String | 作者昵称（非DB字段） |
| `isLike` | Boolean | 当前用户是否已点赞（非DB字段） |

---

## 一、用户模块 `/user`

### 1.1 发送短信验证码

- **POST** `/user/code`
- **说明：** 向指定手机号发送短信验证码，验证码存入 Session/Redis。
- **需要登录：** 否

**请求参数（Query）**

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `phone` | String | 是 | 手机号 |

**请求示例**
```
POST /user/code?phone=13812345678
```

**响应示例**
```json
{ "success": true }
```

---

### 1.2 登录

- **POST** `/user/login`
- **说明：** 手机号+验证码登录，或手机号+密码登录。成功后返回 token。
- **需要登录：** 否

**请求体（JSON）**

```json
{
  "phone": "13812345678",
  "code": "123456"
}
```
或
```json
{
  "phone": "13812345678",
  "password": "mypassword"
}
```

**响应示例**
```json
{
  "success": true,
  "data": "eyJhbGciOi..."
}
```

---

### 1.3 退出登录

- **POST** `/user/logout`
- **说明：** 使当前用户的 token 失效。
- **需要登录：** 是

**响应示例**
```json
{ "success": true }
```

---

### 1.4 获取当前登录用户信息

- **GET** `/user/me`
- **说明：** 返回当前登录用户的基本信息（UserDTO）。
- **需要登录：** 是

**响应示例**
```json
{
  "success": true,
  "data": {
    "id": 1001,
    "nickName": "小明",
    "icon": "/imgs/user.jpg"
  }
}
```

---

### 1.5 获取用户基本信息

- **GET** `/user/{id}`
- **说明：** 根据用户ID获取基本信息（UserDTO）。
- **需要登录：** 是

**路径参数**

| 参数 | 类型 | 说明 |
|---|---|---|
| `id` | Long | 用户ID |

**响应示例**
```json
{
  "success": true,
  "data": {
    "id": 1001,
    "nickName": "小明",
    "icon": "/imgs/user.jpg"
  }
}
```

---

### 1.6 获取用户详细信息

- **GET** `/user/info/{id}`
- **说明：** 获取用户扩展资料（UserInfo），包含城市、粉丝数、等级等。
- **需要登录：** 是

**路径参数**

| 参数 | 类型 | 说明 |
|---|---|---|
| `id` | Long | 用户ID |

**响应示例**
```json
{
  "success": true,
  "data": {
    "userId": 1001,
    "city": "北京",
    "introduce": "吃货一枚",
    "fans": 120,
    "followee": 60,
    "gender": 0,
    "birthday": "1995-05-20",
    "credits": 2000,
    "level": 3
  }
}
```

---

### 1.7 用户签到

- **POST** `/user/sign`
- **说明：** 记录当前用户今天的签到（使用 Redis BitMap 按月存储）。
- **需要登录：** 是

**响应示例**
```json
{ "success": true }
```

---

### 1.8 获取连续签到天数

- **GET** `/user/sign/count`
- **说明：** 统计当前用户本月截至今天的连续签到天数。
- **需要登录：** 是

**响应示例**
```json
{
  "success": true,
  "data": 7
}
```

---

## 二、店铺模块 `/shop`

### 2.1 根据ID查询店铺

- **GET** `/shop/{id}`
- **说明：** 获取店铺详情，结果缓存于 Redis（支持逻辑过期防击穿）。
- **需要登录：** 否

**路径参数**

| 参数 | 类型 | 说明 |
|---|---|---|
| `id` | Long | 店铺ID |

**响应示例**
```json
{
  "success": true,
  "data": {
    "id": 1,
    "name": "某某火锅",
    "typeId": 2,
    "address": "北京市朝阳区xxx街88号",
    "x": 116.3975,
    "y": 39.9096,
    "avgPrice": 80,
    "score": 45
  }
}
```

---

### 2.2 新增店铺

- **POST** `/shop`
- **说明：** 新建一个店铺记录。
- **需要登录：** 是

**请求体（JSON）**

```json
{
  "name": "某某火锅",
  "typeId": 2,
  "address": "北京市朝阳区xxx街88号",
  "x": 116.3975,
  "y": 39.9096,
  "avgPrice": 80
}
```

**响应示例**
```json
{
  "success": true,
  "data": 10
}
```
> `data` 为新建店铺的ID。

---

### 2.3 更新店铺信息

- **PUT** `/shop`
- **说明：** 更新店铺信息，操作完成后删除 Redis 缓存。
- **需要登录：** 是

**请求体（JSON）**

```json
{
  "id": 10,
  "name": "某某火锅（新名）",
  "address": "北京市朝阳区xxx街99号"
}
```

**响应示例**
```json
{ "success": true }
```

---

### 2.4 按类型分页查询店铺

- **GET** `/shop/of/type`
- **说明：** 按店铺分类筛选，分页返回。若传入经纬度，则同时返回距离并按距离排序（基于 Redis GEO）。
- **需要登录：** 否

**请求参数（Query）**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `typeId` | Integer | 是 | — | 分类ID |
| `current` | Integer | 否 | 1 | 页码 |
| `x` | Double | 否 | — | 用户经度（附近排序时传） |
| `y` | Double | 否 | — | 用户纬度（附近排序时传） |

**响应示例**
```json
{
  "success": true,
  "data": [
    { "id": 1, "name": "某某火锅", "distance": 1230.5 }
  ]
}
```

---

### 2.5 按名称搜索店铺

- **GET** `/shop/of/name`
- **说明：** 模糊搜索店铺名称，分页返回。
- **需要登录：** 否

**请求参数（Query）**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `name` | String | 否 | — | 搜索关键字 |
| `current` | Integer | 否 | 1 | 页码 |

**响应示例**
```json
{
  "success": true,
  "data": [
    { "id": 1, "name": "某某火锅" }
  ]
}
```

---

## 三、店铺类型模块 `/shop-type`

### 3.1 查询所有店铺类型

- **GET** `/shop-type/list`
- **说明：** 返回所有店铺分类列表，按 `sort` 字段升序，结果缓存于 Redis。
- **需要登录：** 否

**响应示例**
```json
{
  "success": true,
  "data": [
    { "id": 1, "name": "美食", "icon": "/icons/food.png", "sort": 1 },
    { "id": 2, "name": "KTV", "icon": "/icons/ktv.png", "sort": 2 }
  ]
}
```

---

## 四、优惠券模块 `/voucher`

### 4.1 新增普通优惠券

- **POST** `/voucher`
- **说明：** 添加一张普通优惠券（非限时秒杀）。
- **需要登录：** 是（管理员）

**请求体（JSON）**

```json
{
  "shopId": 1,
  "title": "100元代金券",
  "subTitle": "周一至周五可用",
  "rules": "全场通用\\n无需预约\\n可无限叠加",
  "payValue": 8000,
  "actualValue": 10000,
  "type": 0,
  "status": 1
}
```

**响应示例**
```json
{
  "success": true,
  "data": 5
}
```
> `data` 为新建优惠券ID。

---

### 4.2 新增秒杀优惠券

- **POST** `/voucher/seckill`
- **说明：** 添加一张秒杀优惠券，需填写库存、开始时间、结束时间。同步写入 Redis 库存。
- **需要登录：** 是（管理员）

**请求体（JSON）**

```json
{
  "shopId": 1,
  "title": "限时秒杀券",
  "subTitle": "仅限今日",
  "rules": "全场通用",
  "payValue": 5000,
  "actualValue": 10000,
  "type": 1,
  "status": 1,
  "stock": 100,
  "beginTime": "2024-01-01T10:00:00",
  "endTime": "2024-01-01T12:00:00"
}
```

**响应示例**
```json
{
  "success": true,
  "data": 6
}
```

---

### 4.3 查询店铺的优惠券列表

- **GET** `/voucher/list/{shopId}`
- **说明：** 获取指定店铺的所有可用优惠券。
- **需要登录：** 否

**路径参数**

| 参数 | 类型 | 说明 |
|---|---|---|
| `shopId` | Long | 店铺ID |

**响应示例**
```json
{
  "success": true,
  "data": [
    {
      "id": 5,
      "title": "100元代金券",
      "payValue": 8000,
      "actualValue": 10000,
      "type": 0
    },
    {
      "id": 6,
      "title": "限时秒杀券",
      "stock": 100,
      "beginTime": "2024-01-01T10:00:00",
      "endTime": "2024-01-01T12:00:00",
      "type": 1
    }
  ]
}
```

---

## 五、优惠券订单模块 `/voucher-order`

### 5.1 秒杀抢券下单

- **POST** `/voucher-order/seckill/{id}`
- **说明：** 对指定秒杀券发起抢购下单。同一用户同一优惠券只能下单一次。使用 Redis + Lua 脚本实现原子扣减与幂等校验，异步写入数据库。
- **需要登录：** 是

**路径参数**

| 参数 | 类型 | 说明 |
|---|---|---|
| `id` | Long | 秒杀优惠券ID |

**响应示例（成功）**
```json
{
  "success": true,
  "data": 1234567890
}
```
> `data` 为生成的订单ID（雪花算法）。

**响应示例（失败）**
```json
{
  "success": false,
  "errorMsg": "库存不足"
}
```

**可能的失败原因：**
- 秒杀未开始或已结束
- 库存不足
- 同一用户重复下单

---

## 六、笔记/博客模块 `/blog`

### 6.1 发布笔记

- **POST** `/blog`
- **说明：** 发布一篇探店笔记，同时推送到粉丝的 Feed 收件箱（Redis ZSET）。
- **需要登录：** 是

**请求体（JSON）**

```json
{
  "shopId": 1,
  "title": "这家火锅太好吃了！",
  "images": "img1.jpg,img2.jpg",
  "content": "汤底非常鲜美，强烈推荐！"
}
```

**响应示例**
```json
{
  "success": true,
  "data": 100
}
```
> `data` 为新建笔记ID。

---

### 6.2 查询热门笔记

- **GET** `/blog/hot`
- **说明：** 按点赞数排序的热门笔记列表，分页返回。
- **需要登录：** 否

**请求参数（Query）**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `current` | Integer | 否 | 1 | 页码 |

**响应示例**
```json
{
  "success": true,
  "data": [
    {
      "id": 100,
      "title": "这家火锅太好吃了！",
      "liked": 258,
      "name": "小明",
      "icon": "/imgs/user.jpg",
      "isLike": true
    }
  ]
}
```

---

### 6.3 根据ID查询笔记

- **GET** `/blog/{id}`
- **说明：** 获取单篇笔记的详情，包含作者信息和当前用户是否已点赞。
- **需要登录：** 否（已登录则返回 isLike 状态）

**路径参数**

| 参数 | 类型 | 说明 |
|---|---|---|
| `id` | Long | 笔记ID |

**响应示例**
```json
{
  "success": true,
  "data": {
    "id": 100,
    "shopId": 1,
    "title": "这家火锅太好吃了！",
    "images": "img1.jpg,img2.jpg",
    "content": "汤底非常鲜美，强烈推荐！",
    "liked": 258,
    "name": "小明",
    "icon": "/imgs/user.jpg",
    "isLike": true
  }
}
```

---

### 6.4 点赞/取消点赞

- **PUT** `/blog/like/{id}`
- **说明：** 对笔记点赞或取消点赞（幂等），使用 Redis ZSET 记录点赞用户及时间。
- **需要登录：** 是

**路径参数**

| 参数 | 类型 | 说明 |
|---|---|---|
| `id` | Long | 笔记ID |

**响应示例**
```json
{ "success": true }
```

---

### 6.5 查询笔记的点赞排行榜

- **GET** `/blog/likes/{id}`
- **说明：** 返回最早点赞的前5位用户（基于 Redis ZSET 按时间戳排序）。
- **需要登录：** 否

**路径参数**

| 参数 | 类型 | 说明 |
|---|---|---|
| `id` | Long | 笔记ID |

**响应示例**
```json
{
  "success": true,
  "data": [
    { "id": 1001, "nickName": "小明", "icon": "/imgs/user.jpg" },
    { "id": 1002, "nickName": "小红", "icon": "/imgs/user2.jpg" }
  ]
}
```

---

### 6.6 查询我的笔记

- **GET** `/blog/of/me`
- **说明：** 分页查询当前登录用户发布的所有笔记。
- **需要登录：** 是

**请求参数（Query）**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `current` | Integer | 否 | 1 | 页码 |

**响应示例**
```json
{
  "success": true,
  "data": [
    { "id": 100, "title": "这家火锅太好吃了！", "liked": 258 }
  ]
}
```

---

### 6.7 查询指定用户的笔记

- **GET** `/blog/of/user`
- **说明：** 分页查询指定用户发布的所有笔记。
- **需要登录：** 否

**请求参数（Query）**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | Long | 是 | — | 用户ID |
| `current` | Integer | 否 | 1 | 页码 |

**响应示例**
```json
{
  "success": true,
  "data": [
    { "id": 100, "title": "这家火锅太好吃了！", "liked": 258 }
  ]
}
```

---

### 6.8 关注用户的笔记 Feed 流

- **GET** `/blog/of/follow`
- **说明：** 基于游标的滚动分页，获取当前用户所关注用户的最新笔记（推模式，从 Redis ZSET 收件箱读取）。
- **需要登录：** 是

**请求参数（Query）**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `lastId` | Long | 是 | — | 上次请求返回的 `minTime`，首次请求传当前时间戳 |
| `offset` | Integer | 否 | 0 | 上次请求返回的 `offset` |

**响应示例**
```json
{
  "success": true,
  "data": {
    "list": [
      { "id": 100, "title": "这家火锅太好吃了！" }
    ],
    "minTime": 1700000000000,
    "offset": 1
  }
}
```

---

## 七、关注模块 `/follow`

### 7.1 关注/取关用户

- **PUT** `/follow/{id}/{isFollow}`
- **说明：** 关注或取关指定用户，同时维护 Redis Set（用于共同关注查询）。
- **需要登录：** 是

**路径参数**

| 参数 | 类型 | 说明 |
|---|---|---|
| `id` | Long | 目标用户ID |
| `isFollow` | Boolean | `true`=关注，`false`=取关 |

**响应示例**
```json
{ "success": true }
```

---

### 7.2 查看是否已关注某用户

- **GET** `/follow/or/not/{id}`
- **说明：** 判断当前用户是否已关注指定用户。
- **需要登录：** 是

**路径参数**

| 参数 | 类型 | 说明 |
|---|---|---|
| `id` | Long | 目标用户ID |

**响应示例**
```json
{
  "success": true,
  "data": true
}
```

---

### 7.3 查询共同关注

- **GET** `/follow/common/{id}`
- **说明：** 获取当前用户与指定用户共同关注的用户列表（基于 Redis Set 求交集）。
- **需要登录：** 是

**路径参数**

| 参数 | 类型 | 说明 |
|---|---|---|
| `id` | Long | 另一个用户的ID |

**响应示例**
```json
{
  "success": true,
  "data": [
    { "id": 1003, "nickName": "小华", "icon": "/imgs/user3.jpg" }
  ]
}
```

---

## 八、文件上传模块 `/upload`

### 8.1 上传博客图片

- **POST** `/upload/blog`
- **说明：** 上传一张图片文件，保存至服务器本地文件系统，返回相对路径。
- **需要登录：** 是
- **Content-Type：** `multipart/form-data`

**请求参数（Form-Data）**

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `file` | File | 是 | 图片文件（jpg/png等） |

**响应示例**
```json
{
  "success": true,
  "data": "/blogs/3/a/uuid-filename.jpg"
}
```

---

### 8.2 删除博客图片

- **GET** `/upload/blog/delete`
- **说明：** 根据文件名删除已上传的图片。
- **需要登录：** 是

**请求参数（Query）**

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `name` | String | 是 | 文件名（含路径） |

**响应示例**
```json
{ "success": true }
```

---

## 接口汇总

| 序号 | 方法 | 路径 | 说明 | 需要登录 |
|---|---|---|---|---|
| 1 | POST | `/user/code` | 发送短信验证码 | 否 |
| 2 | POST | `/user/login` | 登录 | 否 |
| 3 | POST | `/user/logout` | 退出登录 | 是 |
| 4 | GET | `/user/me` | 获取当前用户信息 | 是 |
| 5 | GET | `/user/{id}` | 获取用户基本信息 | 是 |
| 6 | GET | `/user/info/{id}` | 获取用户详细信息 | 是 |
| 7 | POST | `/user/sign` | 用户签到 | 是 |
| 8 | GET | `/user/sign/count` | 获取连续签到天数 | 是 |
| 9 | GET | `/shop/{id}` | 根据ID查询店铺 | 否 |
| 10 | POST | `/shop` | 新增店铺 | 是 |
| 11 | PUT | `/shop` | 更新店铺信息 | 是 |
| 12 | GET | `/shop/of/type` | 按类型分页查询店铺 | 否 |
| 13 | GET | `/shop/of/name` | 按名称搜索店铺 | 否 |
| 14 | GET | `/shop-type/list` | 查询所有店铺类型 | 否 |
| 15 | POST | `/voucher` | 新增普通优惠券 | 是 |
| 16 | POST | `/voucher/seckill` | 新增秒杀优惠券 | 是 |
| 17 | GET | `/voucher/list/{shopId}` | 查询店铺的优惠券列表 | 否 |
| 18 | POST | `/voucher-order/seckill/{id}` | 秒杀抢券下单 | 是 |
| 19 | POST | `/blog` | 发布笔记 | 是 |
| 20 | GET | `/blog/hot` | 查询热门笔记 | 否 |
| 21 | GET | `/blog/{id}` | 根据ID查询笔记 | 否 |
| 22 | PUT | `/blog/like/{id}` | 点赞/取消点赞 | 是 |
| 23 | GET | `/blog/likes/{id}` | 查询笔记点赞排行榜 | 否 |
| 24 | GET | `/blog/of/me` | 查询我的笔记 | 是 |
| 25 | GET | `/blog/of/user` | 查询指定用户的笔记 | 否 |
| 26 | GET | `/blog/of/follow` | 关注用户的 Feed 流 | 是 |
| 27 | PUT | `/follow/{id}/{isFollow}` | 关注/取关用户 | 是 |
| 28 | GET | `/follow/or/not/{id}` | 查看是否已关注某用户 | 是 |
| 29 | GET | `/follow/common/{id}` | 查询共同关注 | 是 |
| 30 | POST | `/upload/blog` | 上传博客图片 | 是 |
| 31 | GET | `/upload/blog/delete` | 删除博客图片 | 是 |
