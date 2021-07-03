-- auto-generated definition
create table record
(
  id          bigint unsigned auto_increment
    primary key,
  biz_no      varchar(23)                         not null,
  account     varchar(10)                         not null,
  amount      decimal(17, 2)                      null,
  step        varchar(1)                          null,
  status      varchar(1)                          null,
  create_time timestamp default CURRENT_TIMESTAMP null,
  update_time timestamp default CURRENT_TIMESTAMP null,
  constraint record_biz_no_uindex
  unique (biz_no)
);