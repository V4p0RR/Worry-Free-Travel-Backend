package com.wft.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_intent_case")
public class IntentCase implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    private String intention;
    private String intentionKey;
    private String keywords;
    private String conditions;
    private String examples;
    private Integer forceTrigger;
    private Integer priority;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
