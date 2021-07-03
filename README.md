测试`JdbcTemplate`在MySQL上的读写性能。单线程。

## 1. insert
### 1.1  单条insert，使用jdbcTemplate.update()

结果：写入200条，tps=8条/s
### 1.2  批量insert，使用jdbcTemplate.batchUpdate()

结果：写入1100条，tps=10

```java
String sql = "insert into record(biz_no, account, amount, step, status, create_time, update_time) value (?,?,?,?,?,?,?)";
int batchSize=200;
List<Object[]> args = new ArrayList<>();
for (int i = 0; i < batchSize; i++) {
    Object[] objects = new Object[7];
    objects[0] = Helper.getBizNo();
    objects[1] = Helper.getAccount(i);
    objects[2] = 3.14;
    objects[3] = "1";
    objects[4] = "P";
    objects[5] = LocalDateTime.now();
    objects[6] = LocalDateTime.now();
    args.add(objects);
    inserted++;
}
jdbcTemplate.batchUpdate(sql, args);
```
连接串已设置`rewriteBatchedStatements=true`，不知道为什么这么慢，似乎批量插入并没有生效，还是单条插入的。

换了另一种写法，速度没有提升。

```java
String sql = "insert into record(biz_no, account, amount, step, status, create_time, update_time) value (?,?,?,?,?,?,?)";
int batchSize = 200;
int inserted = 0;
while (true) {
    List<Record> records = new ArrayList<>();
    for (int i = 0; i < batchSize && inserted < num; i++) {
        records.add(Record.builder()
                .bizNo(Helper.getBizNo())
                .account(Helper.getAccount(i))
                .amount(3.14)
                .step("1")
                .status("P")
                .createTime(Timestamp.valueOf(LocalDateTime.now()))
                .updateTime(Timestamp.valueOf(LocalDateTime.now()))
                .build());
        inserted++;
    }
    jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
        @Override
        public void setValues(PreparedStatement ps, int i) throws SQLException {
            ps.setString(1, records.get(i).getBizNo());
            ps.setString(2, records.get(i).getAccount());
            ps.setDouble(3, records.get(i).getAmount());
            ps.setString(4, records.get(i).getStep());
            ps.setString(5, records.get(i).getStatus());
            ps.setTimestamp(6, records.get(i).getCreateTime());
            ps.setTimestamp(7, records.get(i).getUpdateTime());
        }

        @Override
        public int getBatchSize() {
            return records.size();
        }
    });
}
```

上面的代码是手动切分`batchSize`，今天发现其实`api`能自动处理：

```java
String sql = "insert into `record`(`biz_no`, `account`, `amount`, `step`, `status`, `create_time`, `update_time`) values  (?,?,?,?,?,?,?)";
        int[][] updateCounts = jdbcTemplate.batchUpdate(sql, records, batchSize, new ParameterizedPreparedStatementSetter<Record>() {
            @Override
            public void setValues(PreparedStatement ps, Record argument) throws SQLException {
                ps.setString(1, argument.getBizNo());
                ps.setString(2, argument.getAccount());
                ps.setDouble(3, argument.getAmount());
                ps.setString(4, argument.getStep());
                ps.setString(5, argument.getStatus());
                ps.setTimestamp(6, argument.getCreateTime());
                ps.setTimestamp(7, argument.getUpdateTime());
            }
        });
```



#### 更新[bugfix]

花了一上午时间查资料，都是说连接串添加`&rewriteBatchedStatements=true`，并没解决我的问题。只能去查看源码了：

```java
//org.springframework.jdbc.core.JdbcTemplate.java
@Override
public <T> int[][] batchUpdate(String sql, final Collection<T> batchArgs, final int batchSize, final ParameterizedPreparedStatementSetter<T> pss) throws DataAccessException {
    ...
	boolean batchSupported = JdbcUtils.supportsBatchUpdates(ps.getConnection());// true
    ...
    rowsAffected.add(ps.executeBatch()); //从这里进去
    ...
}
```

```java
//com.zaxxer.hikari.pool.ProxyStatement.java
@Override
public int[] executeBatch() throws SQLException{
    connection.markCommitStateDirty();
    return delegate.executeBatch(); // 这里
}
```

```java
// com.mysql.cj.jdbc.StatementImpl.java
@Override
public int[] executeBatch() throws SQLException {
    return Util.truncateAndConvertToInt(executeBatchInternal());
}

protected long[] executeBatchInternal() throws SQLException {
    ...
        
}
```

虽然`StatementImpl`自己也有`executeBatchInternal()`方法，但是`ClientPreparedStatement` 扩展了`StatementImpl`类，并覆盖了其方法，调试后发现是进到了这里：

```java
//com.mysql.cj.jdbc.ClientPreparedStatement.java
@Override
protected long[] executeBatchInternal() throws SQLException {
    ...
	if (!this.batchHasPlainStatements && this.rewriteBatchedStatements.getValue()) {
        if (((PreparedQuery<?>) this.query).getParseInfo().canRewriteAsMultiValueInsertAtSqlLevel()) {
            return executeBatchedInserts(batchTimeout); // 这里
        }
        ...
    }
}

protected long[] executeBatchedInserts(int batchTimeout) throws SQLException {
synchronized (checkClosed().getConnectionMutex()) {
	String valuesClause = ((PreparedQuery<?>) this.query).getParseInfo().getValuesClause();

	JdbcConnection locallyScopedConn = this.connection;
    if (valuesClause == null) {
        return executeBatchSerially(batchTimeout); // 这里
    }
    ...
}   
```

`executeBatchSerially(int batchTimeout)`方法的注释写了：Executes the current batch of statements by executing them one-by-one.  进入这个分支的原因是`valuesClause`为`null`.

再去看一下为什么values子句是空的。

```java
//com.mysql.cj.ParseInfo.java
private void buildRewriteBatchedParams(String sql, Session session, String encoding) {
    this.valuesClause = extractValuesClause(sql, session.getIdentifierQuoteString());
    ...
}
```

关键就是这个`extractValuesClause`方法：

```java
//com.mysql.cj.ParseInfo.java
private String extractValuesClause(String sql, String quoteCharStr) {
    int indexOfValues = -1;
    int valuesSearchStart = this.statementStartPos;

    while (indexOfValues == -1) {
        if (quoteCharStr.length() > 0) {
            indexOfValues = StringUtils.indexOfIgnoreCase(valuesSearchStart, sql, "VALUES", quoteCharStr, quoteCharStr, StringUtils.SEARCH_MODE__MRK_COM_WS);
        } else {
            //...
        }

        if (indexOfValues > 0) {
            //省略
        } else {
            break;
        }
    }

    if (indexOfValues == -1) {
        return null;
    }
}
```

单步断点时，发现`indexOfValues`为-1，然后就`return null`。仔细看了`indexOfValues = StringUtils.indexOfIgnoreCase(valuesSearchStart, sql, "VALUES", quoteCharStr, quoteCharStr, StringUtils.SEARCH_MODE__MRK_COM_WS);`这一行，然后发现自己传入的sql模板语句为：

```java
String sql = "insert into record(biz_no, account, amount, step, status, create_time, update_time) value  (?,?,?,?,?,?,?)";
```

发现问题了吗？`value`，而源码中是`"VALUES"`。虽然在MySQL中，二者都能执行，但是`JdbcTemplate`中只认`values`。`value`可以认为是一个不正规的别名。

#### 更新后的性能

更正sql模板后，再测试，insert性能如下：

```
num=100000, batchSize=1000, cost=38441, tps=2601/s
```

两种不同的batchUpdate()方法tps都在2400~2600左右，与SQL原生的构造多值插入差不多，应该是达到了这台PC的极限。

### 1.3 批量insert，使用`insert into... values...`

先构造好sql，然后一次插入，形如：
```sql
insert into record(biz_no, account, amount, step, status, create_time, update_time) values
      ('20200827213118932636370','0755000000', 3.14, '1', 'P', '2020-08-27T21:31:18.936','2020-08-27T21:31:18.936'),
      ('20200827213118936649543','0755000001', 3.14, '1', 'P', '2020-08-27T21:31:18.936','2020-08-27T21:31:18.936'),
      ('20200827213118936821032','0755000002', 3.14, '1', 'P', '2020-08-27T21:31:18.936','2020-08-27T21:31:18.936')
```
关键代码：
```java
int batchSize = 500;
        StringBuilder sql=new StringBuilder();
        sql.append("insert into record(biz_no, account, amount, step, status, create_time, update_time) values ");
        for(int i=0;i<batchSize; i++){
    sql.append("('" + Helper.getBizNo() + "',");
    sql.append("'" + Helper.getAccount(i) + "', 3.14, '1', 'P', ");
    sql.append("'" + LocalDateTime.now() + "',");
    sql.append("'" + LocalDateTime.now() + "')");
    sql.append(",");
}
sql.deleteCharAt(sql.length() - 1);
jdbcTemplate.update(sql.toString());
```
结果如下：
```
batchSize=200：tps=1034/s
batchSize=500：tps=2084/s
```

## 2. update 
更新200条，tps=8条/s

## 3. 测试环境说明

### 环境1
硬件配置：很差的普通PC，i5-2400 CPU， 机械盘，12GB内存  
系统：Windows 7  
MySQL 版本：8.0.15

上述结果基于此环境。

---

### 环境2
系统：Windows 7  
MySQL 版本：8.0.15
同样的程序，在我的DELL-Inspiron 14R笔记本上，

i5-3230m CPU，8GB内存，128GB 联想SSD。

性能强很多：

```
单条insert：
num=100, cost=569, tps=175/s
num=1000, cost=3524, tps=283/s

batchInsert：
jdbcTemplate.batchUpdate 手动分batchSize
num=110000, batchSize=1000, cost=9769, tps=11260/s

batchInsert2：
jdbcTemplate.batchUpdate 自动分batchSize
num=110000, batchSize=200, cost=10243, tps=10739/s
num=110000, batchSize=1000, cost=9114, tps=12069/s
num=110000, batchSize=5000, cost=7757, tps=14180/s

batchInsert3：
sql.append()
num=110000, batchSize=1000, cost=9885, tps=11127/s
```

批量写的极限速度是1.2万条/s，对比上面那个渣渣台式机，2600条/s，差不多是5倍的差距。单条insert差不多是30倍的差距。  
这应该主要是固态与机械盘的差距。

---

### 环境3
系统：Windows 10  
MySQL 版本：8.0.15  
DIY主机：AMD 4750G CPU，金士顿A2000 512G SSD硬盘，海盗船16G双通道3200内存

```
单条insert：
num=10000, cost=19398, tps=515/s
2021/1/1 update:
num=1000, cost=1428, tps=700/s

batchInsert:
jdbcTemplate.batchUpdate 手动分batchSize
num=110000, batchSize=1000, cost=2919, tps=37684/s
2021/1/1 update:
num=110000, batchSize=1000, cost=2327, tps=47271/s

batchInsert2:
jdbcTemplate.batchUpdate 自动分batchSize
num=110000, batchSize=1000, cost=2655, tps=41431/s
2021/1/1 update:
num=110000, batchSize=1000, cost=2663, tps=41306/s

batchInsert3:
sql.append()
num=110000, batchSize=1000, cost=1947, tps=56497/s
2021/1/1 update:
num=110000, batchSize=1000, cost=1964, tps=56008/s
```




