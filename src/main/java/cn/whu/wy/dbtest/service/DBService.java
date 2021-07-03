package cn.whu.wy.dbtest.service;

import cn.whu.wy.dbtest.entity.Record;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * @Author WangYong
 * @Date 2020/08/27
 * @Time 16:57
 */
@Service
@Slf4j
public class DBService {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private int interval = 100;

    private List<String> bizNos = new ArrayList<>();


    /**
     * insert num 条记录，单条insert
     * tps=10
     *
     * @param num
     */
    public void insert(int num) {
        long t1 = System.currentTimeMillis();
        log.info("start insert");
        String sql = "insert into record(biz_no, account, amount, step, status, create_time, update_time) values (?,?,?,?,?,?,?)";
        for (int i = 0; i < num; i++) {
            String bizNo = Helper.getBizNo();
            jdbcTemplate.update(sql, bizNo, Helper.getAccount(i), 3.14, "1", "P",
                    LocalDateTime.now(), LocalDateTime.now());
            if (i % interval == 0) {
                log.info("inserted {}, current tps={}/s", i, i * 1000 / (System.currentTimeMillis() - t1));
            }
            bizNos.add(bizNo);
        }
        long cost = System.currentTimeMillis() - t1;
        log.info("done. num={}, cost={}, tps={}/s", num, cost, num * 1000 / cost);
    }

    /**
     * num=10000, batchSize=300, cost=11908, tps=839/s
     * num=10000, batchSize=1000, cost=10484, tps=953/s
     * num=100000, batchSize=1000, cost=38441, tps=2601/s
     */
    public void batchInsert(int num, int batchSize) {
        long t1 = System.currentTimeMillis();
        log.info("start batchInsert, num={}, batchSize={}", num, batchSize);

        String sql = "insert into record(biz_no, account, amount, step, status, create_time, update_time) values  (?,?,?,?,?,?,?)";
        int inserted = 0;
        while (true) {
            List<Object[]> args = new ArrayList<>();
            for (int i = 0; i < batchSize && inserted < num; i++) {
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
            if (inserted % interval == 0) {
                log.info("inserted {}, current tps={}/s", inserted, inserted * 1000 / (System.currentTimeMillis() - t1));
            }
            if (inserted == num) break;
        }

        long cost = System.currentTimeMillis() - t1;
        log.info("done. num={}, batchSize={}, cost={}, tps={}/s", num, batchSize, cost, num * 1000 / cost);
    }

    /**
     * num=10000, batchSize=1000, cost=9925, tps=1007/s
     * num=100000, batchSize=1000, cost=39284, tps=2545/s
     *
     * @param num
     * @param batchSize
     * @return num=100, batchSize=40, 返回如下：
     * [
     * [1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1],
     * [1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1],
     * [1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1]
     * ]
     */
    public int[][] batchInsert2(int num, int batchSize) {
        long t1 = System.currentTimeMillis();
        log.info("start batchInsert2, num={}, batchSize={}", num, batchSize);

        // 先构造指定数量的记录
        List<Record> records = new ArrayList<>();
        for (int i = 0; i < num; i++) {
            records.add(Record.builder()
                    .bizNo(Helper.getBizNo())
                    .account(Helper.getAccount(i))
                    .amount(3.14)
                    .step("1")
                    .status("P")
                    .createTime(Timestamp.valueOf(LocalDateTime.now()))
                    .updateTime(Timestamp.valueOf(LocalDateTime.now()))
                    .build());
        }

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

        long cost = System.currentTimeMillis() - t1;
        log.info("done. num={}, batchSize={}, cost={}, tps={}/s", num, batchSize, cost, num * 1000 / cost);

        return updateCounts;
    }


    /**
     * 构造的sql形如：
     * insert into record(biz_no, account, amount, step, status, create_time, update_time) value
     * ('20200827213118932636370','0755000000', 3.14, '1', 'P', '2020-08-27T21:31:18.936','2020-08-27T21:31:18.936'),
     * ('20200827213118936649543','0755000001', 3.14, '1', 'P', '2020-08-27T21:31:18.936','2020-08-27T21:31:18.936'),
     * ('20200827213118936821032','0755000002', 3.14, '1', 'P', '2020-08-27T21:31:18.936','2020-08-27T21:31:18.936')
     * <p>
     * 结果：
     * batchSize=200：num=110000, cost=106370, tps=1034/s
     * batchSize=500：num=110000, cost=52770, tps=2084/s
     * batchSize=1000：num=100000, cost=40684, tps=2457/s
     *
     * @param num
     */
    public void batchInsert3(int num, int batchSize) {
        long t1 = System.currentTimeMillis();
        log.info("start batchInsert3, num={}, batchSize={}", num, batchSize);

        int inserted = 0;
        while (true) {
            StringBuilder sql = new StringBuilder();
            sql.append("insert into record(biz_no, account, amount, step, status, create_time, update_time) values ");
            for (int i = 0; i < batchSize && inserted < num; i++) {
                sql.append("('" + Helper.getBizNo() + "',");
                sql.append("'" + Helper.getAccount(i) + "', 3.14, '1', 'P', ");
                sql.append("'" + LocalDateTime.now() + "',");
                sql.append("'" + LocalDateTime.now() + "')");
                sql.append(",");
                inserted++;
            }
            sql.deleteCharAt(sql.length() - 1);
            jdbcTemplate.update(sql.toString());
            if (inserted % interval == 0) {
                log.info("inserted {}, current tps={}/s", inserted, inserted * 1000 / (System.currentTimeMillis() - t1));
            }
            if (inserted == num) break;
        }
        long cost = System.currentTimeMillis() - t1;
        log.info("done. num={}, batchSize={}, cost={}, tps={}/s", num, batchSize, cost, num * 1000 / cost);

    }


    public void init() {
        String sql = "truncate table record";
        jdbcTemplate.execute(sql);
    }
}
