package com.qbw.oj.judge.codesandbox.impl;

import com.qbw.oj.judge.codesandbox.CodeSandbox;
import com.qbw.oj.judge.codesandbox.model.ExecuteCodeRequest;
import com.qbw.oj.judge.codesandbox.model.JudgeInfo;
import com.qbw.oj.judge.codesandbox.model.ExecuteCodeResponse;
import com.qbw.oj.model.enums.JudgeInfoMessageEnum;
import com.qbw.oj.model.enums.QuestionSubmitStatusEnum;

import java.util.List;

public class ExampleCodeSandbox implements CodeSandbox {

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        List<String> inputList = executeCodeRequest.getInputList();
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setOutputList(inputList);
        executeCodeResponse.setMessage("测试执行成功");
        executeCodeResponse.setStatus(QuestionSubmitStatusEnum.SUCCEED.getValue());
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setMessage(JudgeInfoMessageEnum.ACCEPTED.getText());
        judgeInfo.setMemory(100L);
        judgeInfo.setTime(100L);
        executeCodeResponse.setJudgeInfo(judgeInfo);
        return executeCodeResponse;

    }
}
