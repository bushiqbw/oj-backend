package com.yupi.oj.judge;

import com.yupi.oj.judge.strategy.DefaultJudgeStrategy;
import com.yupi.oj.judge.strategy.JavaLanguageJudgeStrategy;
import com.yupi.oj.judge.strategy.JudgeContext;
import com.yupi.oj.judge.strategy.JudgeStrategy;
import com.yupi.oj.judge.codesandbox.model.JudgeInfo;
import com.yupi.oj.model.entity.QuestionSubmit;
import org.springframework.stereotype.Service;

/**
 * 判题管理，根据不同策略选择不同的实现类
 */
@Service
public class JudgeManager {

    /**
     * 执行不同策略的判断模式
     */
    JudgeInfo doJudge(JudgeContext judgeContext){
        QuestionSubmit questionSubmit = judgeContext.getQuestionSubmit();
        String language = questionSubmit.getLanguage();
        JudgeStrategy judgeStrategy = new DefaultJudgeStrategy();
        if("java".equals(language)){
            judgeStrategy = new JavaLanguageJudgeStrategy();
        }
        return judgeStrategy.doJudge(judgeContext);
    }

}
