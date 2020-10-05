create table bmsql_config (
  cfg_name    varchar2(30) primary key,
  cfg_value   varchar2(50)
);

create table bmsql_warehouse (
  w_id        int   not null,
  w_ytd       decimal(12,2),
  w_tax       decimal(4,4),
  w_name      varchar2(10),
  w_street_1  varchar2(20),
  w_street_2  varchar2(20),
  w_city      varchar2(20),
  w_state     char(2),
  w_zip       char(9)
);

create table bmsql_district (
  d_w_id       int       not null,
  d_id         int       not null,
  d_ytd        decimal(12,2),
  d_tax        decimal(4,4),
  d_next_o_id  int,
  d_name       varchar2(10),
  d_street_1   varchar2(20),
  d_street_2   varchar2(20),
  d_city       varchar2(20),
  d_state      char(2),
  d_zip        char(9)
);

create table bmsql_customer (
  c_w_id         int        not null,
  c_d_id         int        not null,
  c_id           int        not null,
  c_discount     decimal(4,4),
  c_credit       char(2),
  c_last         varchar2(16),
  c_first        varchar2(16),
  c_credit_lim   decimal(12,2),
  c_balance      decimal(12,2),
  c_ytd_payment  decimal(12,2),
  c_payment_cnt  int,
  c_delivery_cnt int,
  c_street_1     varchar2(20),
  c_street_2     varchar2(20),
  c_city         varchar2(20),
  c_state        char(2),
  c_zip          char(9),
  c_phone        char(16),
  c_since        datetime,
  c_middle       char(2),
  c_data         varchar2(500)
);

create table bmsql_history (
  hist_id  identity,
  h_c_id   int,
  h_c_d_id int,
  h_c_w_id int,
  h_d_id   int,
  h_w_id   int,
  h_date   datetime,
  h_amount decimal(6,2),
  h_data   varchar2(24)
);

create table bmsql_new_order (
  no_w_id  int   not null,
  no_d_id  int   not null,
  no_o_id  int   not null
);

create table bmsql_oorder (
  o_w_id       int      not null,
  o_d_id       int      not null,
  o_id         int      not null,
  o_c_id       int,
  o_carrier_id int,
  o_ol_cnt     int,
  o_all_local  int,
  o_entry_d    datetime
);

create table bmsql_order_line (
  ol_w_id         int   not null,
  ol_d_id         int   not null,
  ol_o_id         int   not null,
  ol_decimal      int   not null,
  ol_i_id         int   not null,
  ol_delivery_d   datetime,
  ol_amount       decimal(6,2),
  ol_supply_w_id  int,
  ol_quantity     int,
  ol_dist_info    char(24)
);

create table bmsql_item (
  i_id     int      not null,
  i_name   varchar2(24),
  i_price  decimal(5,2),
  i_data   varchar2(50),
  i_im_id  int
);

create table bmsql_stock (
  s_w_id       int       not null,
  s_i_id       int       not null,
  s_quantity   int,
  s_ytd        int,
  s_order_cnt  int,
  s_remote_cnt int,
  s_data       varchar2(50),
  s_dist_01    char(24),
  s_dist_02    char(24),
  s_dist_03    char(24),
  s_dist_04    char(24),
  s_dist_05    char(24),
  s_dist_06    char(24),
  s_dist_07    char(24),
  s_dist_08    char(24),
  s_dist_09    char(24),
  s_dist_10    char(24)
);


