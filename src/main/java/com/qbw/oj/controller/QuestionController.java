package com.qbw.oj.controller;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.gson.Gson;
import com.qbw.oj.annotation.AuthCheck;
import com.qbw.oj.common.BaseResponse;
import com.qbw.oj.common.DeleteRequest;
import com.qbw.oj.common.ErrorCode;
import com.qbw.oj.common.ResultUtils;
import com.qbw.oj.constant.RedisKeyConstant;
import com.qbw.oj.manager.RedisLimiterManager;
import com.qbw.oj.model.dto.question.*;
import com.qbw.oj.model.dto.questionsubmit.QuestionSubmitAddRequest;
import com.qbw.oj.model.dto.questionsubmit.QuestionSubmitQueryRequest;
import com.qbw.oj.model.entity.Question;
import com.qbw.oj.model.entity.QuestionSubmit;
import com.qbw.oj.model.enums.QuestionSubmitLanguageEnum;
import com.qbw.oj.model.vo.QuestionSubmitVO;
import com.qbw.oj.model.vo.QuestionVO;
import com.qbw.oj.service.QuestionService;
import com.qbw.oj.service.QuestionSubmitService;
import com.qbw.oj.constant.UserConstant;
import com.qbw.oj.exception.BusinessException;
import com.qbw.oj.exception.ThrowUtils;
import com.qbw.oj.model.entity.User;
import com.qbw.oj.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.qbw.oj.constant.RedisConstant.*;
import static com.qbw.oj.constant.RedisKeyConstant.CACHE_QUESTION_KEY;
import static com.qbw.oj.constant.RedisKeyConstant.QUESTION_D0_SUBMIT;

/**
 * 题目接口
 *
 * @author <a href="https://github.com/liqbw">qbw</a>
  
 */
@RestController
@RequestMapping("/question")
@Slf4j
public class QuestionController {

    @Resource
    private QuestionService questionService;

    @Resource
    private UserService userService;

    @Resource
    private QuestionSubmitService questionSubmitService;

    private final static Gson GSON = new Gson();

    @Resource
    private RedisLimiterManager redisLimiterManager;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // region 增删改查

    /**
     * 创建
     *
     * @param questionAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    public BaseResponse<Long> addQuestion(@RequestBody QuestionAddRequest questionAddRequest, HttpServletRequest request) {
        if (questionAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Question question = new Question();
        BeanUtils.copyProperties(questionAddRequest, question);
        List<String> tags = questionAddRequest.getTags();
        if (tags != null) {
            question.setTags(JSONUtil.toJsonStr(tags));
        }
        List<JudgeCase> judgeCase = questionAddRequest.getJudgeCase();
        if (judgeCase != null) {
            question.setJudgeCase(GSON.toJson(judgeCase));
        }
        JudgeConfig judgeConfig = questionAddRequest.getJudgeConfig();
        if (judgeConfig != null) {
            question.setJudgeConfig(GSON.toJson(judgeConfig));
        }
        questionService.validQuestion(question, true);
        User loginUser = userService.getLoginUser(request);
        question.setUserId(loginUser.getId());
        question.setFavourNum(0);
        question.setThumbNum(0);
        boolean result = questionService.save(question);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        long newQuestionId = question.getId();
        //如果添加了新的问题，就重新刷新缓存
        stringRedisTemplate.delete(CACHE_QUESTION_KEY);
        return ResultUtils.success(newQuestionId);
    }

    /**
     * 删除
     *
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteQuestion(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        Question oldQuestion = questionService.getById(id);
        ThrowUtils.throwIf(oldQuestion == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!oldQuestion.getUserId().equals(user.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean b = questionService.removeById(id);
        return ResultUtils.success(b);
    }

    /**
     * 更新（仅管理员）
     *
     * @param questionUpdateRequest
     * @return
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateQuestion(@RequestBody QuestionUpdateRequest questionUpdateRequest) {
        if (questionUpdateRequest == null || questionUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Question question = new Question();
        BeanUtils.copyProperties(questionUpdateRequest, question);
        List<String> tags = questionUpdateRequest.getTags();
        if (tags != null) {
            question.setTags(JSONUtil.toJsonStr(tags));
        }
        List<JudgeCase> judgeCase = questionUpdateRequest.getJudgeCase();
        if (judgeCase != null) {
            question.setJudgeCase(GSON.toJson(judgeCase));
        }
        JudgeConfig judgeConfig = questionUpdateRequest.getJudgeConfig();
        if (judgeConfig != null) {
            question.setJudgeConfig(GSON.toJson(judgeConfig));
        }
        // 参数校验
        questionService.validQuestion(question, false);
        long id = questionUpdateRequest.getId();
        // 判断是否存在
        Question oldQuestion = questionService.getById(id);
        ThrowUtils.throwIf(oldQuestion == null, ErrorCode.NOT_FOUND_ERROR);
        boolean result = questionService.updateById(question);
        return ResultUtils.success(result);
    }

    /**
     * 根据 id 获取
     *
     * @param id
     * @return
     */
    @GetMapping("/get")
    public BaseResponse<Question> getQuestionById(  long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Question question = questionService.getById(id);
        if (question == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        // 不是本人或管理员，不能直接获取所有信息
        if (!question.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        return ResultUtils.success(question);
    }


    /**
     * 根据 id 获取
     *
     * @param id
     * @return
     */
    //todo redis优化
    @GetMapping("/get/vo")
    public BaseResponse<QuestionVO> getQuestionVOById(long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String key = CACHE_QUESTION_KEY + QUESTION_D0_SUBMIT +id;
        String questionJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(questionJson)) {
            //命中直接返回
            Question question = JSONUtil.toBean(questionJson, Question.class);
            log.info("加载该题目缓存");
            return ResultUtils.success(questionService.getQuestionVO(question, request));
        }
        log.info("加载该题目数据库");
        Question question = questionService.getById(id);
        if (question == null) {
           stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL + RandomUtil.randomLong(1, 10), TimeUnit.MINUTES);
            //解决缓存穿透，如果不存在，返回一个空值，避免过多不存在请求到达数据库
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        } else {
            //缓存到redis中，加入随机的缓存时间防止缓存穿透
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(question), CACHE_QUESTION_TTL + RandomUtil.randomLong(1, 3), TimeUnit.MINUTES);
        }
        return ResultUtils.success(questionService.getQuestionVO(question, request));
//        Question question = questionService.getById(id);
//        if (question == null) {
//            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
//        }
//        return ResultUtils.success(questionService.getQuestionVO(question, request));
    }

    /**
     * 返回对应语言的枚举值给前端
     *
     * @return
     */
    @GetMapping("/languages")
    public BaseResponse<List<String>> getAllLanguages() {
        List<String> statusMap = new ArrayList<>();
            for (QuestionSubmitLanguageEnum status : QuestionSubmitLanguageEnum.values()) {
            statusMap.add(status.getValue());
        }
        return ResultUtils.success(statusMap);
    }

    /**
     * 分页获取列表（仅管理员）
     *
     * @param questionQueryRequest
     * @return
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Question>> listQuestionByPage(@RequestBody QuestionQueryRequest questionQueryRequest) {
        long current = questionQueryRequest.getCurrent();
        long size = questionQueryRequest.getPageSize();
        Page<Question> questionPage = questionService.page(new Page<>(current, size),
                questionService.getQueryWrapper(questionQueryRequest));
        return ResultUtils.success(questionPage);
    }

    /**
     * 分页获取列表（封装类）
     *
     * @param questionQueryRequest
     * @param request
     * @return
     */
    //todo redis优化
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<QuestionVO>> listQuestionVOByPage(@RequestBody QuestionQueryRequest questionQueryRequest,
            HttpServletRequest request) {
        long current = questionQueryRequest.getCurrent();
        long size = questionQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        String pageJson = stringRedisTemplate.opsForValue().get(CACHE_QUESTION_KEY + current);
        Page<Question> questionPage = null;
        Page<QuestionVO> questionVoPage = null;
        //将分页查询到的数据缓存到redis
        if (StrUtil.isBlank(pageJson)) {
            //3.从数据库取出来之后
            questionPage = questionService.page(new Page<>(current, size), questionService.getQueryWrapper(questionQueryRequest));
            questionVoPage = questionService.getQuestionVOPage(questionPage,request);
            log.info("从数据库中查询");
            //4.再次缓存到redis,设置缓存的过期时间，30分钟，重新缓存查询一次
            stringRedisTemplate.opsForValue().set(CACHE_QUESTION_KEY + current, JSONUtil.toJsonStr((questionVoPage)), CACHE_QUESTION_PAGE_TTL, TimeUnit.MINUTES);
        } else {
            log.info("加载缓存");
            questionVoPage = JSONUtil.toBean(pageJson, new TypeReference<Page<QuestionVO>>() {
            }, true);
        }
        return ResultUtils.success(questionVoPage);
    }

    /**
     * 分页获取当前用户创建的资源列表
     *
     * @param questionQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<QuestionVO>> listMyQuestionVOByPage(@RequestBody QuestionQueryRequest questionQueryRequest,
            HttpServletRequest request) {
        if (questionQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        questionQueryRequest.setUserId(loginUser.getId());
        long current = questionQueryRequest.getCurrent();
        long size = questionQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<Question> questionPage = questionService.page(new Page<>(current, size),
                questionService.getQueryWrapper(questionQueryRequest));
        return ResultUtils.success(questionService.getQuestionVOPage(questionPage, request));
    }

    /**
     * 编辑（用户）
     *
     * @param questionEditRequest
     * @param request
     * @return
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editQuestion(@RequestBody QuestionEditRequest questionEditRequest, HttpServletRequest request) {
        if (questionEditRequest == null || questionEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Question question = new Question();
        BeanUtils.copyProperties(questionEditRequest, question);
        List<String> tags = questionEditRequest.getTags();
        if (tags != null) {
            question.setTags(JSONUtil.toJsonStr(tags));
        }
        List<JudgeCase> judgeCase = questionEditRequest.getJudgeCase();
        if (judgeCase != null) {
            question.setJudgeCase(GSON.toJson(judgeCase));
        }
        JudgeConfig judgeConfig = questionEditRequest.getJudgeConfig();
        if (judgeConfig != null) {
            question.setJudgeConfig(GSON.toJson(judgeConfig));
        }
        // 参数校验
        questionService.validQuestion(question, false);
        User loginUser = userService.getLoginUser(request);
        long id = questionEditRequest.getId();
        // 判断是否存在
        Question oldQuestion = questionService.getById(id);
        ThrowUtils.throwIf(oldQuestion == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可编辑
        if (!oldQuestion.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean result = questionService.updateById(question);
        return ResultUtils.success(result);
    }

    /**
     * 提交答案
     *
     * @param id 提交id
     * @return resultNum 本次点赞变化数
     */
    @GetMapping("/question_submit/get/id")
    public BaseResponse<QuestionSubmitVO> getJudgeResult(Long id, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        QuestionSubmit questionSubmit = questionSubmitService.getById(id);
        if (questionSubmit == null) {
            //不存在，直接报错，返回一个空
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "提交答案不存在");
        }
        QuestionSubmitVO questionCommentVO = questionSubmitService.getQuestionSubmitVO(questionSubmit, loginUser);
        return ResultUtils.success(questionCommentVO);
    }

    /**
     * 提交题目
     *
     * @param questionSubmitAddRequest
     * @param request
     * @return 提交记录的 id
     */
    //todo redis优化
    @PostMapping("/question_submit/do")
    public BaseResponse<Long> doQuestionSubmit(@RequestBody QuestionSubmitAddRequest questionSubmitAddRequest,
                                               HttpServletRequest request) {
        if (questionSubmitAddRequest == null || questionSubmitAddRequest.getQuestionId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 获取题目Id
        final User loginUser = userService.getLoginUser(request);
        boolean rateLimit = redisLimiterManager.doRateLimit(RedisKeyConstant.LIMIT_KEY_PREFIX + loginUser.getId().toString());
        if(!rateLimit){
            throw new BusinessException(ErrorCode.TOO_MANY_REQUEST, "提交过于频繁,请稍后重试");
        }else {
            log.info("提交成功,题号：{},用户：{}", questionSubmitAddRequest.getQuestionId(), loginUser.getId());
            Long result = questionSubmitService.doQuestionSubmit(questionSubmitAddRequest, loginUser);
            stringRedisTemplate.delete(CACHE_QUESTION_KEY);
            return ResultUtils.success(result);
        }
    }

    /**
     * 分页获取题目提交列表（除了管理员外，普通用户只能看到非答案、提交代码等公开信息）
     *
     * @param questionSubmitQueryRequest
     * @param request
     * @return
     */
    @PostMapping("question_submit/list/page/vo")
    public BaseResponse<Page<QuestionSubmitVO>> listQuestionSubmitByPage(@RequestBody QuestionSubmitQueryRequest questionSubmitQueryRequest,
                                                                         HttpServletRequest request) {
        long current = questionSubmitQueryRequest.getCurrent();
        long size = questionSubmitQueryRequest.getPageSize();
        // 从数据库中查询原始的题目提交分页信息
        Page<QuestionSubmit> questionSubmitPage = questionSubmitService.page(new Page<>(current, size),
                questionSubmitService.getQueryWrapper(questionSubmitQueryRequest));
        final User loginUser = userService.getLoginUser(request);
        // 返回脱敏信息
        return ResultUtils.success(questionSubmitService.getQuestionSubmitVOPage(questionSubmitPage, loginUser));
    }

    /**
     * 分页获取自己的题目提交列表
     *
     * @param questionSubmitQueryRequest
     * @param request
     * @return
     */
    @PostMapping("question_submit/my/list/page/vo")
    public BaseResponse<Page<QuestionSubmitVO>> listMyQuestionSubmitVOByPage(@RequestBody QuestionSubmitQueryRequest questionSubmitQueryRequest,
            HttpServletRequest request) {
        long current = questionSubmitQueryRequest.getCurrent();
        long size = questionSubmitQueryRequest.getPageSize();
        User loginUser = userService.getLoginUser(request);
        questionSubmitQueryRequest.setUserId(loginUser.getId());
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<QuestionSubmit> questionSubmitPage = questionSubmitService.page(new Page<>(current, size),
                questionSubmitService.getQueryWrapper(questionSubmitQueryRequest));
        return ResultUtils.success(questionSubmitService.getQuestionSubmitVOPage(questionSubmitPage, loginUser));
    }

    /**
     * 根据 id 获取
     *
     * @param id
     * @return
     */
    @GetMapping("/question_submit/get/vo")
    public BaseResponse<QuestionSubmitVO> getQuestionSubmitVOById(long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        QuestionSubmit questionSubmit = questionSubmitService.getById(id);
        if (questionSubmit == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        final User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(questionSubmitService.getQuestionSubmitVO(questionSubmit, loginUser));
    }

    @PostMapping("question_submit/my_record/list/vo")
    public BaseResponse<List<QuestionSubmitVO>> listMyQuestionSubmitVORecord(@RequestBody QuestionSubmitQueryRequest questionSubmitQueryRequest, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        questionSubmitQueryRequest.setUserId(loginUser.getId());

        // 获取所有 QuestionSubmit 记录
        List<QuestionSubmit> questionSubmitList = questionSubmitService.list(questionSubmitService.getQueryWrapper(questionSubmitQueryRequest));

        // 将 QuestionSubmit 列表转换为 QuestionSubmitVO 列表
        List<QuestionSubmitVO> questionSubmitVOList = questionSubmitService.getQuestionSubmitVOList(questionSubmitList, loginUser);

        // 返回数据
        return ResultUtils.success(questionSubmitVOList);
    }

}
