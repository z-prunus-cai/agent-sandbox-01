## 接口设计

所有接口遵循 RESTful 风格，返回统一的 JSON 结构。

### 创建订单

`POST /api/v1/orders`

请求参数：

| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| userId | long | 是 | 下单用户 |
| items | array | 是 | 商品列表，至少 1 项 |
| couponId | string | 否 | 优惠券 ID |

响应示例：

```json
{
  "code": 0,
  "data": {
    "orderId": "202610150001",
    "status": "CREATED"
  }
}
```

### 查询订单

`GET /api/v1/orders/{orderId}`，订单不存在时返回 `404`。
