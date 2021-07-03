package cn.whu.wy.dbtest.entity;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Record {

    private long id;
    private String bizNo;
    private String account;
    private double amount;
    private String step;
    private String status;
    private java.sql.Timestamp createTime;
    private java.sql.Timestamp updateTime;

}
