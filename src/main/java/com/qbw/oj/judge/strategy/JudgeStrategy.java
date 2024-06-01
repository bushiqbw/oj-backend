package com.qbw.oj.judge.strategy;

import com.qbw.oj.judge.codesandbox.model.JudgeInfo;

/**
 * 判题策略接口
 */
public interface JudgeStrategy {

    /**
     * 执行判题
     * @param judgeContext
     * @return
     */
    JudgeInfo doJudge(JudgeContext judgeContext);
}

