package com.careerq.dao;

import com.careerq.dataobject.UserDO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * SSM / MyBatis 连接验证：DAO 直接读写数据库。
 */
@SpringBootTest
class DaoSsmTests {

    @Autowired
    private UserDao userDao;

    @Test
    void mybatisReadsAndWritesUsersTable() {
        long before = userDao.count();

        UserDO user = new UserDO();
        user.setRole("student");
        user.setPhoneHash("ssm-dao-test-" + System.nanoTime());
        user.setNickname("DAO 测试用户");
        user.setStatus("active");

        assertEquals(1, userDao.insert(user));
        assertNotNull(user.getId(), "自增主键应回填");
        assertEquals(before + 1, userDao.count());

        UserDO loaded = userDao.findById(user.getId()).orElseThrow();
        assertEquals("student", loaded.getRole());
        assertEquals("DAO 测试用户", loaded.getNickname());
        assertEquals("active", loaded.getStatus());
        assertNotNull(loaded.getCreatedAt());
        assertNotNull(loaded.getUpdatedAt());
    }
}
