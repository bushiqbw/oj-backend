package com.qbw.oj.manager;

import javax.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Cos 操作测试
 *
 * @author <a href="https://github.com/liqbw">qbw</a>
  
 */
@SpringBootTest
class CosManagerTest {

    @Resource
    private CosManager cosManager;

    @Test
    void putObject() {
        cosManager.putObject("test", "D:\\IdeaProjects\\oj-backend\\src\\test\\java\\com\\qbw\\oj\\manager\\test.json");
    }

    @Test
    void putImage(){
        cosManager.putObject("Default_Avatar","D:\\IdeaProjects\\oj-backend\\src\\test\\java\\com\\qbw\\oj\\manager\\OJ_Avatar.jpg");
    }
}