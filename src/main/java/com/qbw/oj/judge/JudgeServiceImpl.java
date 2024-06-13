package com.qbw.oj.judge;

import  cn.hutool.json.JSONUtil;
import com.qbw.oj.common.ErrorCode;
import com.qbw.oj.judge.codesandbox.CodeSandbox;
import com.qbw.oj.judge.codesandbox.CodeSandboxFactory;
import com.qbw.oj.judge.codesandbox.model.ExecuteCodeRequest;
import com.qbw.oj.model.entity.Question;
import com.qbw.oj.model.enums.JudgeInfoMessageEnum;
import com.qbw.oj.service.QuestionService;
import com.qbw.oj.exception.BusinessException;
import com.qbw.oj.judge.codesandbox.CodeSandboxProxy;
import com.qbw.oj.judge.codesandbox.model.ExecuteCodeResponse;
import com.qbw.oj.judge.strategy.JudgeContext;
import com.qbw.oj.model.dto.question.JudgeCase;
import com.qbw.oj.judge.codesandbox.model.JudgeInfo;
import com.qbw.oj.model.entity.QuestionSubmit;
import com.qbw.oj.model.enums.QuestionSubmitStatusEnum;
import com.qbw.oj.service.QuestionSubmitService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class JudgeServiceImpl implements JudgeService {

    @Resource
    private QuestionService questionService;

    @Resource
    private QuestionSubmitService questionSubmitService;

    @Resource
    private JudgeManager judgeManager;

    @Value("${codesandbox.type}")
    private String type;


    @Override
    public QuestionSubmit doJudge(long questionSubmitId) {
        //通过传入的题目id，获取题目的具体信息
        QuestionSubmit questionSubmit = questionSubmitService.getById(questionSubmitId);
        if(questionSubmit == null){
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "提交信息错误！");
        }
        //判断是否已经在等待中了（即是否在判题中）
        if(!questionSubmit.getStatus().equals(QuestionSubmitStatusEnum.WAITING.getValue())){
            throw new BusinessException(ErrorCode.OPERATION_ERROR,"题目已经在判题中了！");
        }
        //获取题目相关信息
        Long questionId = questionSubmit.getQuestionId();
        Question question = questionService.getById(questionId);
        if(question == null){
            throw  new BusinessException(ErrorCode.NOT_FOUND_ERROR,"题目不存在！");
        }
        QuestionSubmit questionSubmitUpdate = new QuestionSubmit();
        questionSubmitUpdate.setId(questionSubmit.getId());
        questionSubmitUpdate.setStatus(QuestionSubmitStatusEnum.WAITING.getValue());
        boolean update = questionSubmitService.updateById(questionSubmitUpdate);
        if(!update){
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"题目更新错误！");
        }

        ///构造代码沙箱的请求参数,调用代码沙箱，获取执行结果
        CodeSandbox codeSandbox = CodeSandboxFactory.newInstance(type);
        codeSandbox = new CodeSandboxProxy(codeSandbox);
        String language = questionSubmit.getLanguage();
        String code = questionSubmit.getCode();
        String judgeCaseStr = question.getJudgeCase();
        List<JudgeCase> judgeCaseList = JSONUtil.toList(judgeCaseStr, JudgeCase.class);
        List<String> inputList = judgeCaseList.stream()
                .map(JudgeCase::getInput)
                .collect(Collectors.toList());
        ExecuteCodeRequest executeCodeRequest = ExecuteCodeRequest.builder()
                .code(code)
                .language(language)
                .inputList(inputList)
                .build();
        ExecuteCodeResponse executeCodeResponse = codeSandbox.executeCode(executeCodeRequest);
        //先检查是否运行成功了，如果错误直接不用检查outputlist了，返回编译错误
        if(executeCodeResponse.getStatus() == QuestionSubmitStatusEnum.FAILED.getValue()){
            JudgeInfo ErrorJudgeInfo = executeCodeResponse.getJudgeInfo();
            ErrorJudgeInfo.setMessage(JudgeInfoMessageEnum.COMPILE_ERROR.getValue());
            QuestionSubmit questionSubmitErrorUpdate = new QuestionSubmit();
            questionSubmitErrorUpdate.setId(questionSubmitId);
            questionSubmitErrorUpdate.setStatus(QuestionSubmitStatusEnum.FAILED.getValue());
            questionSubmitErrorUpdate.setJudgeInfo(JSONUtil.toJsonStr(ErrorJudgeInfo));
            boolean ErrorUpdate = questionSubmitService.updateById(questionSubmitErrorUpdate);
            if (!ErrorUpdate) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "题目编译错误");
            }
            QuestionSubmit questionSubmitErrorResult = questionSubmitService.getById(questionId);
            return questionSubmitErrorResult;
        }
        List<String> outputList = executeCodeResponse.getOutputList();
        //根据沙箱返回结果，设置题目的判题状态和信息
        JudgeContext judgeContext = new JudgeContext();
        judgeContext.setJudgeInfo(executeCodeResponse.getJudgeInfo());
        judgeContext.setInputList(inputList);
        judgeContext.setOutputList(outputList);
        judgeContext.setJudgeCaseList(judgeCaseList);
        judgeContext.setQuestion(question);
        judgeContext.setQuestionSubmit(questionSubmit);
//        CompletableFuture.runAsync(()->{
//            JudgeInfo judgeInfo = judgeManager.doJudge(judgeContext);
//        });
        JudgeInfo judgeInfo = judgeManager.doJudge(judgeContext);
        //修改数据库中的判题结果
        questionSubmitUpdate = new QuestionSubmit();
        questionSubmitUpdate.setId(questionSubmitId);
        questionSubmitUpdate.setStatus(QuestionSubmitStatusEnum.SUCCEED.getValue());
        questionSubmitUpdate.setJudgeInfo(JSONUtil.toJsonStr(judgeInfo));
        update = questionSubmitService.updateById(questionSubmitUpdate);
        if (!update) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "题目状态更新错误");
        }
        QuestionSubmit questionSubmitResult = questionSubmitService.getById(questionId);
        return questionSubmitResult;
    }
}
