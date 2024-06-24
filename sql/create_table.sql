# 数据库初始化
# @author <a href="https://github.com/liqbw">qbw</a>
# @from <a href="https://qbw.icu">编程导航知识星球</a>

-- 创建库
create database if not exists my_db;

-- 切换库
use my_db;

-- 用户表
create table if not exists user
(
    id           bigint auto_increment comment 'id' primary key,
    userAccount  varchar(256)                           not null comment '账号',
    userPassword varchar(512)                           not null comment '密码',
    unionId      varchar(256)                           null comment '微信开放平台id',
    mpOpenId     varchar(256)                           null comment '公众号openId',
    userName     varchar(256)                           null comment '用户昵称',
    userAvatar   varchar(1024)                          null comment '用户头像',
    userProfile  varchar(512)                           null comment '用户简介',
    userRole     varchar(256) default 'user'            not null comment '用户角色：user/admin/ban',
    createTime   datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint      default 0                 not null comment '是否删除',
    INDEX idx_userAccount  (userAccount)
) comment '用户' collate = utf8mb4_unicode_ci;

-- 题目表
create table if not exists question
(
    id         bigint auto_increment comment 'id' primary key,
    title      varchar(512)                       null comment '标题',
    content    text                               null comment '内容',
    tags       varchar(1024)                      null comment '标签列表（json 数组）',
    answer     text                               null comment '题目答案',
    submitNum  int     default 0                  not null comment '题目提交数',
    acceptedNum  int   default 0                  not null comment '题目通过数',
    judgeCase  text                               null comment '判题用例（json数组）',
    judgeConfig text                              null comment '判题配置（json对象）',
    thumbNum   int      default 0                 not null comment '点赞数',
    favourNum  int      default 0                 not null comment '收藏数',
    userId     bigint                             not null comment '创建用户 id',
    createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete   tinyint  default 0                 not null comment '是否删除',
    index idx_userId (userId)
) comment '帖子' collate = utf8mb4_unicode_ci;

-- 题目提交表
create table if not exists question_submit
(
    id         bigint auto_increment comment 'id' primary key,
    language   varchar(128)                       not null comment '编程语言',
    code       text                               not null comment '用户代码',
    judgeInfo  text                               null comment '判题结果信息(json对象)',
    status     int      default 0                 not null comment '判题状态（0 - 待判题，1 - 判题中，2 - 成功， 3 - 失败）',
    questionId bigint                             not null comment '题目 id',
    userId     bigint                             not null comment '创建用户 id',
    createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    index idx_postId (questionId),
    index idx_userId (userId)
) comment '题目提交';

-- auto-generated definition
create table question_comment
(
    id          bigint                                     not null comment '主键id'
        primary key,
    questionId  bigint           default 0                 not null comment '问题id',
    userId      bigint           default 0                 not null comment '用户id',
    userName    varchar(50)                                null comment '用户昵称',
    userAvatar  varchar(255)                               null comment '用户头像',
    content     varchar(500)                               null comment '评论内容',
    parentId    bigint           default -1                null comment '父级评论id
',
    commentNum  int              default 0                 null comment '回复条数',
    likeCount   int              default 0                 null comment '点赞数量',
    isLike      tinyint(1)       default 0                 null comment '是否点赞',
    likeListId  varchar(255)                               null comment '点赞id列表',
    inputShow   tinyint(1)       default 0                 null comment '是否显示输入框',
    fromId      bigint                                     null comment '回复记录id
',
    fromName    varchar(255) collate utf8mb4_bin           null comment '回复人名称
',
    gmtCreate   datetime         default CURRENT_TIMESTAMP not null comment '创建时间',
    gmtModified datetime         default CURRENT_TIMESTAMP not null comment '更新时间',
    isDeleted   tinyint unsigned default '0'               not null comment '逻辑删除 1（true）已删除， 0（false）未删除'
) comment '评论' collate = utf8mb4_general_ci
                   row_format = DYNAMIC;

create index idx_questionId
    on question_comment (questionId);

create index idx_userId
    on question_comment (userId);

-- 帖子收藏表（硬删除）
create table if not exists post_favour
(
    id         bigint auto_increment comment 'id' primary key,
    postId     bigint                             not null comment '帖子 id',
    userId     bigint                             not null comment '创建用户 id',
    createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    index idx_postId (postId),
    index idx_userId (userId)
) comment '帖子收藏';
